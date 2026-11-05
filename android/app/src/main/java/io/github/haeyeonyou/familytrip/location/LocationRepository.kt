package io.github.haeyeonyou.familytrip.location

import io.github.haeyeonyou.familytrip.navi.Place
import kotlinx.coroutines.flow.StateFlow

const val FUSED_LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"

data class LocationState(
    val supported: Boolean? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Place being watched for arrival, if any. */
    val watching: Place? = null,
    val arrivedAt: String? = null,
    /** Most recent location events, newest first, for spike verification. */
    val events: List<String> = emptyList(),
)

/**
 * Vehicle position and arrival detection. Arrival is judged here (proximity
 * to the destination) because the built-in navigation's position cannot be
 * moved on the emulator (see issue #4).
 */
interface LocationRepository {
    val state: StateFlow<LocationState>

    /** Call after the FUSED_LOCATION runtime permission is granted. */
    fun start()
    fun stop()
    fun watchArrival(place: Place, radiusMeters: Float = DEFAULT_ARRIVAL_RADIUS_METERS)
    fun clearArrival()

    companion object {
        const val DEFAULT_ARRIVAL_RADIUS_METERS = 150f
    }
}
