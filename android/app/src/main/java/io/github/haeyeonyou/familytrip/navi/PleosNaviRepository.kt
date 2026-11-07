package io.github.haeyeonyou.familytrip.navi

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
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Pleos NaviHelper wrapper. Events come from the built-in navigation app over IPC. */
class PleosNaviRepository(context: Context) : NaviRepository {

    private val naviHelper = NaviHelper(context.applicationContext)
    private val _state = MutableStateFlow(NaviState())
    override val state: StateFlow<NaviState> = _state.asStateFlow()

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(info: RouteStartInfo) {
            log("경로 시작: ${info.name}, ${info.distance}m, ${info.duration}초")
            _state.update {
                it.copy(
                    routing = true,
                    arrivedAt = null,
                    remainingDistanceMeters = info.distance,
                    remainingSeconds = info.duration,
                )
            }
        }

        override fun onRoutingCompleted() = log("경로 탐색 완료")

        override fun onRouteEnded() {
            log("경로 종료")
            _state.update { it.copy(routing = false) }
        }

        override fun onRouteCancelled() {
            log("경로 취소")
            _state.update { it.copy(routing = false) }
        }

        override fun onDestinationArrived(info: DestinationArrivedInfo) {
            log("목적지 도착: ${info.name} (${info.address})")
            _state.update { it.copy(routing = false, arrivedAt = info.name) }
        }

        override fun onWayPointArrived(info: WaypointArrivedInfo) =
            log("경유지 도착: ${info.index} ${info.name}")

        override fun onDrivingInfo(info: DrivingInfo) {
            val destination = info.destination
            _state.update {
                it.copy(
                    remainingDistanceMeters = destination.distance,
                    remainingSeconds = destination.duration,
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

    override fun start() {
        naviHelper.addListener(listener)
        naviHelper.initialize(
            {
                log("NaviHelper 연결됨")
                _state.update { it.copy(connected = true) }
                naviHelper.getCurrentLocationInfo()
            },
            { e ->
                Log.w(TAG, "NaviHelper initialize failed", e)
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

    private fun log(message: String) {
        Log.d(TAG, message)
        val line = "${LocalTime.now().format(TIME_FORMAT)} $message"
        _state.update { it.copy(events = (listOf(line) + it.events).take(MAX_EVENTS)) }
    }

    private companion object {
        const val TAG = "PleosNavi"
        const val MAX_EVENTS = 12
        const val PENDING_MS = 30_000L
        const val READY_FALLBACK_MS = 6_000L
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
