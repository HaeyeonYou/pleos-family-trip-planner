package io.github.haeyeonyou.familytrip.navi

import io.github.haeyeonyou.familytrip.data.AppLog
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TrafficStatus
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.TrafficCongestionType
import ai.pleos.playground.navi.constants.TrafficEventType
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import io.github.haeyeonyou.familytrip.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Pleos NaviHelper wrapper. Events come from the built-in navigation app over IPC. */
class PleosNaviRepository(context: Context) : NaviRepository {

    private val appContext = context.applicationContext
    private val naviHelper = NaviHelper(appContext)
    private val _state = MutableStateFlow(NaviState())
    override val state: StateFlow<NaviState> = _state.asStateFlow()

    /** 출발 때 예상 대비 도착 지연을 계산한다 (#17). 내비 콜백 스레드에서만 쓴다. */
    private val delayTracker = DelayTracker()

    /** 디버그 빌드에서만: 내비가 알려 주는 남은 시간에 더해 지연을 흉내 낸다 (시뮬레이션은 교통이 바뀌지 않아서). */
    @Volatile
    private var debugExtraSeconds = 0

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(info: RouteStartInfo) {
            log("경로 시작: ${info.name}, ${info.distance}m, ${info.duration}초")
            delayTracker.start(info.duration, info.distance, System.currentTimeMillis())
            _state.update {
                it.copy(
                    routing = true,
                    arrivedAt = null,
                    remainingDistanceMeters = info.distance,
                    remainingSeconds = info.duration,
                    delay = null,
                )
            }
        }

        /**
         * 경로 위 교통 정보. 경로를 시작할 때와, 안내 중 차가 움직일 때 몇 초~십수 초 간격으로 온다(에뮬레이터 확인).
         * 정체 구간의 offset은 "지금 위치에서 정체 시작까지"의 거리라 차가 가면서 달라진다.
         * 주행 시뮬레이션 중에는 naviMode가 SIMULATION_GUIDING이다. 안내 중이 아닐 때(SAFE_GUIDING)
         * 오는 값은 직전 경로의 것이 남아 있어 버린다.
         */
        override fun onRouteStateInfo(info: RouteStateInfo) {
            val guiding = info.naviMode == NaviMode.ROUTE_GUIDING || info.naviMode == NaviMode.SIMULATION_GUIDING
            val traffic = if (guiding) info.trafficStatus?.toSnapshot() else null
            _state.update { it.copy(traffic = traffic) }
        }

        /** 경유지를 넣거나 빼서 경로가 바뀜: 지연 기준을 새로 잡는다. */
        override fun onWaypointChanged(info: WaypointChangedInfo) {
            delayTracker.rebase(System.currentTimeMillis())
        }

        override fun onRoutingCompleted() = log("경로 탐색 완료")

        override fun onRouteEnded() {
            log("경로 종료")
            _state.update { it.copy(routing = false) }
        }

        override fun onRouteCancelled() {
            log("경로 취소")
            delayTracker.reset()
            _state.update { it.copy(routing = false, traffic = null, delay = null) }
        }

        override fun onDestinationArrived(info: DestinationArrivedInfo) {
            log("목적지 도착: ${info.name} (${info.address})")
            delayTracker.reset()
            _state.update { it.copy(routing = false, arrivedAt = info.name, traffic = null, delay = null) }
        }

        override fun onWayPointArrived(info: WaypointArrivedInfo) =
            log("경유지 도착: ${info.index} ${info.name}")

        override fun onDrivingInfo(info: DrivingInfo) {
            val destination = info.destination
            val delay = delayTracker.update(destination.duration + debugExtraSeconds, destination.distance, System.currentTimeMillis())
            _state.update {
                it.copy(
                    remainingDistanceMeters = destination.distance,
                    remainingSeconds = destination.duration,
                    delay = delay,
                )
            }
        }

        override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
            log("현재 위치: %.5f, %.5f %s".format(info.latitude, info.longitude, info.address))
            _state.update { it.copy(currentLatitude = info.latitude, currentLongitude = info.longitude) }
        }

        override fun onNavigationStatus(status: NavigationStatus) {
            log("내비 상태: $status")
            _state.update { it.copy(routing = status == NavigationStatus.ROUTE_DRIVE) }
            if (status != NavigationStatus.ROUTE_DRIVE) {
                delayTracker.reset()
                _state.update { it.copy(traffic = null, delay = null) }
            }
            navReady = status == NavigationStatus.FREE_DRIVE || status == NavigationStatus.ROUTE_DRIVE
            // 내비가 꺼져 있다 막 켜진 경우: 기다리던 목적지를 이제 보낸다.
            val waiting = pendingDestination
            if (waiting != null && navReady) {
                pendingDestination = null
                if (System.currentTimeMillis() - pendingAt < PENDING_MS) requestRoute(waiting)
            }
        }

        override fun onError(code: NaviErrorCode) = log("오류: $code")
    }

    override fun resolveDelayAlert(addedWaypoint: Boolean) {
        val now = System.currentTimeMillis()
        if (addedWaypoint) {
            delayTracker.rebase(now)
        } else {
            delayTracker.dismiss(now, _state.value.delay?.delaySeconds ?: 0)
        }
        // 알림이 곧바로 사라지게 상태에도 바로 반영한다 (다음 내비 이벤트를 기다리지 않는다).
        _state.update { it.copy(delay = it.delay?.copy(alert = false)) }
    }

    override fun start() {
        naviHelper.addListener(listener)
        registerDebugDelay()
        naviHelper.initialize(
            {
                log("NaviHelper 연결됨")
                _state.update { it.copy(connected = true) }
                naviHelper.getCurrentLocationInfo()
            },
            { e ->
                AppLog.w(TAG, "NaviHelper initialize failed", e)
                log("NaviHelper 연결 실패: ${e.message}")
            },
        )
    }

    override fun stop() {
        naviHelper.removeListener(listener)
        naviHelper.release()
    }

    override fun requestRoute(destination: Place) {
        log("경로 요청: ${destination.name}")
        // 이전 구간 도착이 새 구간 도착으로 읽히지 않게 지운다.
        _state.update { it.copy(arrivedAt = null) }
        naviHelper.requestRoute(
            RouteInfo(
                destination.latitude,
                destination.longitude,
                null,
                null,
                destination.name,
                "",
                "",
                "",
                RouteOption.RECOMMENDED,
                "",
                false,
            )
        )
    }

    override fun cancelRoute() = naviHelper.cancelRoute()

    /** 내비가 안내 가능한 상태인지 (켜지는 중이면 false). 상태를 한 번도 못 받았으면 true로 보고 시도한다. */
    @Volatile
    private var navReady = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingDestination: Place? = null
    private var pendingAt = 0L

    override fun removeWaypoints(): Boolean {
        if (!_state.value.routing) return false
        log("경유지 모두 빼기")
        naviHelper.removeWaypoint(WaypointIndex.ALL)
        return true
    }

    override fun addWaypointOrRoute(place: Place): Boolean {
        if (!navReady) {
            // 내비 앱이 막 켜지는 중이라 지금 보내면 사라진다. 준비되면 보낸다.
            log("내비 준비 대기: ${place.name}")
            pendingDestination = place
            pendingAt = System.currentTimeMillis()
            naviHelper.requestNavigationScreen(NavigationScreenRequest.Foreground)
            // 이미 켜져 있어 상태 알림이 오지 않는 경우를 위해, 일정 시간 뒤에는 그대로 보낸다.
            handler.postDelayed({
                val waiting = pendingDestination
                if (waiting != null) {
                    pendingDestination = null
                    requestRoute(waiting)
                }
            }, READY_FALLBACK_MS)
            return false
        }
        if (!_state.value.routing) {
            requestRoute(place)
            return false
        }
        log("경유지 추가: ${place.name}")
        naviHelper.addWaypoint(
            RequestWaypointInfo(place.latitude, place.longitude, place.name, WaypointIndex.FIRST, "", "", "", "")
        )
        return true
    }

    override fun refreshCurrentLocation() = naviHelper.getCurrentLocationInfo()

    override fun showNavigation() = naviHelper.requestNavigationScreen(NavigationScreenRequest.Foreground)

    /**
     * 디버그 빌드 전용 시험 장치. 에뮬레이터 주행 시뮬레이션은 교통이 바뀌지 않아 지연이 저절로 생기지 않으므로,
     * `adb shell am broadcast -a io.github.haeyeonyou.familytrip.DEBUG_DELAY --ei seconds 480 -p io.github.haeyeonyou.familytrip`
     * 으로 내비가 알려 주는 남은 시간에 480초를 더해, 실제로 지연이 생겼을 때와 같은 경로(계산 → 알림)를 태운다.
     * `seconds`를 0으로 주면 끈다. 릴리스 빌드에서는 등록하지 않는다.
     */
    private fun registerDebugDelay() {
        if (!BuildConfig.DEBUG) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                debugExtraSeconds = intent.getIntExtra("seconds", 0)
                log("[디버그] 남은 시간에 ${debugExtraSeconds}초 더함")
            }
        }
        appContext.registerReceiver(receiver, IntentFilter(DEBUG_DELAY_ACTION), Context.RECEIVER_EXPORTED)
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        val line = "${LocalTime.now().format(TIME_FORMAT)} $message"
        _state.update { it.copy(events = (listOf(line) + it.events).take(MAX_EVENTS)) }
    }

    private companion object {
        const val TAG = "PleosNavi"
        const val DEBUG_DELAY_ACTION = "io.github.haeyeonyou.familytrip.DEBUG_DELAY"
        const val MAX_EVENTS = 12
        const val PENDING_MS = 30_000L
        const val READY_FALLBACK_MS = 6_000L
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}

private fun TrafficStatus.toSnapshot(): TrafficSnapshot = TrafficSnapshot(
    sections = congestionItemList.map {
        CongestionSection(
            roadName = it.roadName,
            type = it.trafficCongestionType.toCongestion(),
            lengthMeters = it.distance.toInt(),
            seconds = it.trafficCongestionTime.toInt(),
        )
    },
    events = trafficEventList.map {
        TrafficEventItem(
            kind = it.eventType.toKind(),
            description = it.description,
            endMillis = parseEventTime(it.endTime),
        )
    },
)

private fun TrafficCongestionType.toCongestion() = when (this) {
    TrafficCongestionType.FAST -> Congestion.FAST
    TrafficCongestionType.MODERATE -> Congestion.MODERATE
    TrafficCongestionType.SLOW -> Congestion.SLOW
    TrafficCongestionType.HEAVY -> Congestion.HEAVY
    else -> Congestion.UNKNOWN
}

private fun TrafficEventType.toKind() = when (this) {
    TrafficEventType.ACCIDENT -> TrafficEventKind.ACCIDENT
    TrafficEventType.DISASTER -> TrafficEventKind.DISASTER
    TrafficEventType.REPAIRING -> TrafficEventKind.REPAIRING
    TrafficEventType.ROAD_DAMAGE -> TrafficEventKind.ROAD_DAMAGE
    TrafficEventType.EVENT -> TrafficEventKind.EVENT
    else -> TrafficEventKind.ETC
}

/** 내비가 주는 시각 문자열("2023-11-05T22:08:28")을 epoch ms로. 읽지 못하면 null (끝나는 때를 모르는 것으로 본다). */
private fun parseEventTime(text: String): Long? = try {
    LocalDateTime.parse(text.trim().replace(' ', 'T')).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
} catch (e: Exception) {
    null
}
