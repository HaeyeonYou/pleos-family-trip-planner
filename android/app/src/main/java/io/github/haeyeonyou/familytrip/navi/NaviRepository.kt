package io.github.haeyeonyou.familytrip.navi

import kotlinx.coroutines.flow.StateFlow

data class Place(val name: String, val latitude: Double, val longitude: Double)

data class NaviState(
    val connected: Boolean = false,
    val routing: Boolean = false,
    val currentLatitude: Double? = null,
    val currentLongitude: Double? = null,
    val remainingDistanceMeters: Int? = null,
    val remainingSeconds: Int? = null,
    val arrivedAt: String? = null,
    /** 안내 중 내비가 알려 준 경로 위 교통 정보. 안내 중이 아니면 null. */
    val traffic: TrafficSnapshot? = null,
    /** 출발 때 예상 대비 도착 지연 (#17). 안내 중이 아니거나 아직 계산 전이면 null. */
    val delay: DelayState? = null,
    /** Most recent navigation events, newest first, for spike verification. */
    val events: List<String> = emptyList(),
)

/**
 * Turn-by-turn navigation. The course is guided one leg at a time because
 * NaviHelper accepts at most two waypoints (see development-plan.md 4-2).
 */
interface NaviRepository {
    val state: StateFlow<NaviState>
    fun start()
    fun stop()
    fun requestRoute(destination: Place)
    fun cancelRoute()

    /**
     * 안내 중인 경로에 경유지를 넣는다. 안내 중이 아니면 [place]를 목적지로 길안내를 시작한다.
     * 돌려주는 값: 경유지로 넣었으면 true, 목적지로 시작했으면 false.
     */
    fun addWaypointOrRoute(place: Place): Boolean

    /** 안내 중인 경로의 경유지를 모두 뺀다. 안내 중이 아니면 false. */
    fun removeWaypoints(): Boolean
    fun refreshCurrentLocation()

    /**
     * 지연 알림에 대한 사용자의 선택을 알린다. [addedWaypoint]가 true면 경유지를 넣어 경로가 바뀌므로 지연 기준을 새로 잡고,
     * false("그대로 가기")면 한동안 같은 지연으로는 다시 알리지 않는다.
     */
    fun resolveDelayAlert(addedWaypoint: Boolean)

    /** 내장 내비 화면을 앞으로 가져온다 (안내 중 지도를 보이게). */
    fun showNavigation()
}
