package io.github.haeyeonyou.familytrip.vehicle

import kotlinx.coroutines.flow.StateFlow

/** Gear position reduced to what the app cares about: parked vs. moving. */
enum class GearState { PARK, DRIVE, REVERSE, NEUTRAL, UNKNOWN }

data class VehicleState(
    val stateOfChargePercent: Int? = null,
    val rangeRemainingMeters: Float? = null,
    val gear: GearState = GearState.UNKNOWN,
    val lastError: String? = null,
) {
    val isParked: Boolean get() = gear == GearState.PARK
}

/**
 * Vehicle data source. The real implementation talks to the Pleos Vehicle SDK;
 * a simulated one can be swapped in for the offline demo mode.
 */
interface VehicleRepository {
    val state: StateFlow<VehicleState>
    fun start()
    fun stop()
}
