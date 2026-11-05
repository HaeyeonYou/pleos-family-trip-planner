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
    fun refreshCurrentLocation()
}
