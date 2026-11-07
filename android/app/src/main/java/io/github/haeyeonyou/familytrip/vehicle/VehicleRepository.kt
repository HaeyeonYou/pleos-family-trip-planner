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

/**
 * 지금 공조가 가진 설정값. Vehicle SDK에는 실내 온도 센서가 없어 '설정 온도'만 읽을 수 있다.
 * [supported]가 false면 이 차(또는 시동 꺼진 상태)에서 공조를 읽을 수 없다.
 */
data class ClimateReading(
    val supported: Boolean,
    val power: Boolean? = null,
    val temperature: Float? = null,
    val fanSpeed: Int? = null,
)

/** 공조에서 바꿀 항목만 담는다. null인 항목은 건드리지 않는다. */
data class ClimateChange(
    val power: Boolean? = null,
    val temperature: Float? = null,
    val fanSpeed: Int? = null,
    val ac: Boolean? = null,
    val driverSeatHeat: Int? = null,
    val passengerSeatHeat: Int? = null,
    val steeringWheelHeat: Int? = null,
)

interface VehicleRepository {
    val state: StateFlow<VehicleState>
    fun start()
    fun stop()

    /** 폰에서 보낸 공조 프리셋을 차에 적용한다 (항목 하나가 실패해도 나머지는 계속). */
    suspend fun applyClimate(settings: ClimateSettings): ClimateApplyResult

    /** 공조의 현재 설정(전원·설정 온도·풍량)을 읽는다. 읽지 못하면 supported=false. */
    suspend fun readClimate(): ClimateReading

    /** 다른 설정은 건드리지 않고 풍량만 [level]단으로 바꾼다 (지원 구역의 범위 안으로 맞춤). 바꾸면 true. */
    suspend fun setFanSpeed(level: Int): Boolean

    /** [change]에 담긴 항목만 차에 적용한다 (음성 명령용). 지원하지 않는 항목은 failed에 담긴다. */
    suspend fun applyClimateChange(change: ClimateChange): ClimateApplyResult
}
