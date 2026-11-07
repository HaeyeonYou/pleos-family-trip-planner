package io.github.haeyeonyou.familytrip.vehicle

import io.github.haeyeonyou.familytrip.data.ClimateSettings
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
/** 프리셋 적용 결과. 항목별로 차가 받아들였는지 나눠 보여 준다. */
data class ClimateApplyResult(val applied: List<String>, val failed: List<String>)

interface VehicleRepository {
    val state: StateFlow<VehicleState>
    fun start()
    fun stop()

    /** 폰에서 보낸 공조 프리셋을 차에 적용한다 (항목 하나가 실패해도 나머지는 계속). */
    suspend fun applyClimate(settings: ClimateSettings): ClimateApplyResult
}
