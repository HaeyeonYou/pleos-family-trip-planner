package io.github.haeyeonyou.familytrip.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.CurrentLocationInfo
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
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
