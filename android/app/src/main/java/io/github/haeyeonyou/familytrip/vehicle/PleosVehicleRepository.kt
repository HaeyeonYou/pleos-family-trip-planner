package io.github.haeyeonyou.familytrip.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.RangeRemainingListener
import android.content.Context
import android.util.Log
import io.github.haeyeonyou.familytrip.data.ClimateSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pleos Vehicle SDK wrapper. Reads the current value once, then follows changes
 * through listeners. SDK callbacks arrive on binder threads; StateFlow is thread-safe.
 */
class PleosVehicleRepository(context: Context) : VehicleRepository {

    private val appContext = context.applicationContext
    private val vehicle = Vehicle(appContext)
    private val _state = MutableStateFlow(VehicleState())
    override val state: StateFlow<VehicleState> = _state.asStateFlow()

    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(value: Int?) {
            Log.d(TAG, "SoC updated: $value")
            _state.update { it.copy(stateOfChargePercent = value) }
        }

        override fun onFailed(e: Exception) = reportError("SoC listener", e)
    }

    private val rangeListener = object : RangeRemainingListener {
        override fun onRangeRemainingChanged(value: Float?) {
            Log.d(TAG, "Range updated: $value")
            _state.update { it.copy(rangeRemainingMeters = value) }
        }

        override fun onFailed(e: Exception) = reportError("Range listener", e)
    }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            Log.d(TAG, "Gear updated: $gear")
            _state.update { it.copy(gear = gear.toGearState()) }
        }

        override fun onFailed(e: Exception) = reportError("Gear listener", e)
    }

    override fun start() {
        vehicle.initialize()

        val evBattery = vehicle.getEvBattery()
        evBattery.getEvBatteryStateOfCharge(
            { value -> _state.update { it.copy(stateOfChargePercent = value) } },
            { e -> reportError("getEvBatteryStateOfCharge", e) },
        )
        evBattery.registerEvBatteryStateOfCharge(socListener)

        val odometer = vehicle.getOdometer()
        odometer.getRangeRemainingDistance(
            { value -> _state.update { it.copy(rangeRemainingMeters = value) } },
            { e -> reportError("getRangeRemainingDistance", e) },
        )
        odometer.registerRangeRemainingListener(rangeListener)

        val gear = vehicle.getGear()
        gear.getCurrentGearSelectionState(
            { value -> _state.update { it.copy(gear = value.toGearState()) } },
            { e -> reportError("getCurrentGearSelectionState", e) },
        )
        gear.registerGearSelection(gearListener)
    }

    override fun stop() {
        vehicle.getEvBattery().unregisterEvBatteryStateOfCharge(socListener)
        vehicle.getOdometer().unregisterRangeRemainingListener(rangeListener)
        vehicle.getGear().unregisterGearSelection(gearListener)
        vehicle.release()
    }

    override suspend fun applyClimate(settings: ClimateSettings): ClimateApplyResult {
        val applied = mutableListOf<String>()
        val failed = mutableListOf<String>()
        suspend fun step(label: String, block: suspend () -> Unit) {
            // 공조를 막 켠 직후에는 항목이 잠깐 준비되지 않아 실패할 수 있어 한 번 더 시도한다.
            repeat(2) { attempt ->
                try {
                    withTimeout(SDK_TIMEOUT_MS) { block() }
                    applied += label
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "climate $label failed (try ${attempt + 1})", e)
                    if (attempt == 0) delay(RETRY_DELAY_MS)
                }
            }
            failed += label
        }

        val hvac = vehicle.getHVAC()
        // 공조 항목마다 지원 구역이 다르다 (예: 온도는 운전석·조수석, 전원·풍량은 앞좌석 전체 한 구역).
        // 항목별로 지원 구역을 물어 그 구역에만 적용한다.
        val tempZones = capability(emptyMap()) { ok, fail -> hvac.checkHvacTemperatureCapabilities({ _, m -> ok(m) }, fail) }
        val fanZones = capability(emptyMap()) { ok, fail -> hvac.checkHvacFanSpeedCapabilities({ _, m -> ok(m) }, fail) }
        val powerZones = capability(emptyList()) { ok, fail -> hvac.checkHvacPowerCapabilities({ _, l -> ok(l) }, fail) }
        val acZones = capability(emptyList()) { ok, fail -> hvac.checkHvacAcCapabilities({ _, l -> ok(l) }, fail) }
        val cleanerZones = capability(emptyList()) { ok, fail -> hvac.checkHvacAirCleanerCapabilities({ _, l -> ok(l) }, fail) }
        Log.d(TAG, "climate zones temp=${tempZones.size} fan=${fanZones.size} power=${powerZones.size} ac=${acZones.size} cleaner=${cleanerZones.size}")

        step(if (settings.power) "공조 켜기" else "공조 끄기") {
            requireZones(powerZones).forEach { area ->
                sdk<Unit> { ok, fail -> hvac.setHvacPower(area, settings.power, { _, _ -> ok(Unit) }, fail) }
            }
        }
        if (settings.power) {
            step("온도 ${settings.temperature}°C") {
                requireZones(tempZones.keys).forEach { area ->
                    val bounds = tempZones.getValue(area)
                    val value = settings.temperature.coerceIn(bounds.minValue, bounds.maxValue)
                    sdk<Unit> { ok, fail -> hvac.setHvacTemperature(area, value, TemperatureUnit.CELSIUS, { _, _ -> ok(Unit) }, fail) }
                }
            }
            step("풍량 ${settings.fanSpeed}단") {
                requireZones(fanZones.keys).forEach { area ->
                    val bounds = fanZones.getValue(area)
                    val value = settings.fanSpeed.coerceIn(bounds.minValue, bounds.maxValue)
                    sdk<Unit> { ok, fail -> hvac.setHvacFanSpeed(area, value, { _, _ -> ok(Unit) }, fail) }
                }
            }
            step(if (settings.ac) "에어컨 켜기" else "에어컨 끄기") {
                requireZones(acZones).forEach { area ->
                    sdk<Unit> { ok, fail -> hvac.setHvacAc(area, settings.ac, { _, _ -> ok(Unit) }, fail) }
                }
            }
            if (settings.airCleaner) {
                step("공기청정") {
                    requireZones(cleanerZones).forEach { area ->
                        sdk<Unit> { ok, fail -> hvac.setHvacAirCleaner(area, true, { _, _ -> ok(Unit) }, fail) }
                    }
                }
            }
        }
        val seat = vehicle.getSeat()
        step(heatLabel("운전석 열선", settings.driverSeatHeat)) {
            sdk<Unit> { ok, fail -> seat.setHvacSeatTemperature(SeatArea(SeatValue.ROW_1_LEFT), settings.driverSeatHeat, { _, _ -> ok(Unit) }, fail) }
        }
        step(heatLabel("조수석 열선", settings.passengerSeatHeat)) {
            sdk<Unit> { ok, fail -> seat.setHvacSeatTemperature(SeatArea(SeatValue.ROW_1_RIGHT), settings.passengerSeatHeat, { _, _ -> ok(Unit) }, fail) }
        }
        step(heatLabel("핸들 열선", settings.steeringWheelHeat)) {
            sdk<Unit> { ok, fail -> vehicle.getSteeringWheel().setHvacSteeringWheelHeat(settings.steeringWheelHeat, { ok(Unit) }, fail) }
        }
        // 미디어 음량: Vehicle SDK에는 음량 제어가 없어 안드로이드 미디어 스트림 음량으로 맞춘다.
        // 차가 막으면(권한·정책) 값이 그대로 남으므로 다시 읽어 확인하고, 다르면 실패로 알린다.
        settings.mediaVolume?.let { percent ->
            step("음량 ${percent}%") {
                val audio = appContext.getSystemService(android.media.AudioManager::class.java)
                    ?: error("오디오 서비스를 쓸 수 없어요")
                val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                val target = Math.round(max * percent.coerceIn(0, 100) / 100f).coerceIn(0, max)
                audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, 0)
                val now = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                check(now == target) { "음량이 $now/$max 로 남았어요" }
            }
        }
        // 차가 실제로 받아들였는지 확인용으로 설정 온도를 다시 읽어 남긴다.
        tempZones.keys.forEach { area ->
            val now = runCatching {
                withTimeout(SDK_TIMEOUT_MS) { sdk<Float?> { ok, fail -> hvac.getHvacTemperature(area, TemperatureUnit.CELSIUS, ok, fail) } }
            }.getOrNull()
            Log.i(TAG, "climate readback temperature area=${area.values.joinToString()} value=$now")
        }
        Log.i(TAG, "climate preset applied=$applied failed=$failed")
        return ClimateApplyResult(applied, failed)
    }

    override suspend fun applyClimateChange(change: ClimateChange): ClimateApplyResult {
        val applied = mutableListOf<String>()
        val failed = mutableListOf<String>()
        suspend fun step(label: String, block: suspend () -> Unit) {
            repeat(2) { attempt ->
                try {
                    withTimeout(SDK_TIMEOUT_MS) { block() }
                    applied += label
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "climate change $label failed (try ${attempt + 1})", e)
                    if (attempt == 0) delay(RETRY_DELAY_MS)
                }
            }
            failed += label
        }
        val hvac = vehicle.getHVAC()
        val seat = vehicle.getSeat()
        change.power?.let { on ->
            val zones = capability(emptyList()) { ok, fail -> hvac.checkHvacPowerCapabilities({ _, l -> ok(l) }, fail) }
            step(if (on) "공조 켜기" else "공조 끄기") {
                requireZones(zones).forEach { area -> sdk<Unit> { ok, fail -> hvac.setHvacPower(area, on, { _, _ -> ok(Unit) }, fail) } }
            }
        }
        change.temperature?.let { t ->
            val zones = capability(emptyMap()) { ok, fail -> hvac.checkHvacTemperatureCapabilities({ _, m -> ok(m) }, fail) }
            step("온도 ${t}°C") {
                requireZones(zones.keys).forEach { area ->
                    val b = zones.getValue(area)
                    sdk<Unit> { ok, fail -> hvac.setHvacTemperature(area, t.coerceIn(b.minValue, b.maxValue), TemperatureUnit.CELSIUS, { _, _ -> ok(Unit) }, fail) }
                }
            }
        }
        change.fanSpeed?.let { level ->
            val zones = capability(emptyMap()) { ok, fail -> hvac.checkHvacFanSpeedCapabilities({ _, m -> ok(m) }, fail) }
            step("풍량 ${level}단") {
                requireZones(zones.keys).forEach { area ->
                    val b = zones.getValue(area)
                    sdk<Unit> { ok, fail -> hvac.setHvacFanSpeed(area, level.coerceIn(b.minValue, b.maxValue), { _, _ -> ok(Unit) }, fail) }
                }
            }
        }
        change.ac?.let { on ->
            val zones = capability(emptyList()) { ok, fail -> hvac.checkHvacAcCapabilities({ _, l -> ok(l) }, fail) }
            step(if (on) "에어컨 켜기" else "에어컨 끄기") {
                requireZones(zones).forEach { area -> sdk<Unit> { ok, fail -> hvac.setHvacAc(area, on, { _, _ -> ok(Unit) }, fail) } }
            }
        }
        change.driverSeatHeat?.let { level ->
            step(heatLabel("운전석 열선", level)) {
                sdk<Unit> { ok, fail -> seat.setHvacSeatTemperature(SeatArea(SeatValue.ROW_1_LEFT), level, { _, _ -> ok(Unit) }, fail) }
            }
        }
        change.passengerSeatHeat?.let { level ->
            step(heatLabel("조수석 열선", level)) {
                sdk<Unit> { ok, fail -> seat.setHvacSeatTemperature(SeatArea(SeatValue.ROW_1_RIGHT), level, { _, _ -> ok(Unit) }, fail) }
            }
        }
        change.steeringWheelHeat?.let { level ->
            step(heatLabel("핸들 열선", level)) {
                sdk<Unit> { ok, fail -> vehicle.getSteeringWheel().setHvacSteeringWheelHeat(level, { ok(Unit) }, fail) }
            }
        }
        Log.i(TAG, "climate change applied=$applied failed=$failed")
        return ClimateApplyResult(applied, failed)
    }

    override suspend fun setFanSpeed(level: Int): Boolean {
        val hvac = vehicle.getHVAC()
        val zones = capability(emptyMap()) { ok, fail -> hvac.checkHvacFanSpeedCapabilities({ _, m -> ok(m) }, fail) }
        if (zones.isEmpty()) return false
        return runCatching {
            withTimeout(SDK_TIMEOUT_MS) {
                zones.forEach { (area, bounds) ->
                    val value = level.coerceIn(bounds.minValue, bounds.maxValue)
                    sdk<Unit> { ok, fail -> hvac.setHvacFanSpeed(area, value, { _, _ -> ok(Unit) }, fail) }
                }
            }
        }.onFailure { Log.w(TAG, "setFanSpeed failed", it) }.isSuccess
    }

    override suspend fun readClimate(): ClimateReading {
        val hvac = vehicle.getHVAC()
        val tempZones = capability(emptyMap()) { ok, fail -> hvac.checkHvacTemperatureCapabilities({ _, m -> ok(m) }, fail) }
        val area = tempZones.keys.firstOrNull() ?: return ClimateReading(supported = false)
        val temperature = runCatching {
            withTimeout(SDK_TIMEOUT_MS) { sdk<Float?> { ok, fail -> hvac.getHvacTemperature(area, TemperatureUnit.CELSIUS, ok, fail) } }
        }.getOrNull()
        val powerZones = capability(emptyList()) { ok, fail -> hvac.checkHvacPowerCapabilities({ _, l -> ok(l) }, fail) }
        val power = powerZones.firstOrNull()?.let { zone ->
            runCatching { withTimeout(SDK_TIMEOUT_MS) { sdk<Boolean?> { ok, fail -> hvac.getHvacPowerState(zone, ok, fail) } } }.getOrNull()
        }
        val fanZones = capability(emptyMap()) { ok, fail -> hvac.checkHvacFanSpeedCapabilities({ _, m -> ok(m) }, fail) }
        val fan = fanZones.keys.firstOrNull()?.let { zone ->
            runCatching { withTimeout(SDK_TIMEOUT_MS) { sdk<Int?> { ok, fail -> hvac.getHvacFanSpeed(zone, ok, fail) } } }.getOrNull()
        }
        // 공조가 꺼져 있으면 설정 온도는 'PropertyNotAvailable'로 읽히지 않는다. 지원 구역이 있으면 지원 차량으로 본다.
        return ClimateReading(supported = true, power = power, temperature = temperature, fanSpeed = fan)
    }

    private fun reportError(where: String, e: Exception?) {
        Log.w(TAG, "$where failed", e)
        _state.update { it.copy(lastError = "$where: ${e?.message ?: e?.javaClass?.simpleName}") }
    }

    private companion object {
        const val TAG = "PleosVehicle"
        const val SDK_TIMEOUT_MS = 3_000L
        const val RETRY_DELAY_MS = 800L
    }
}

/** 지원 구역 조회. 실패하면 [fallback] (빈 값)을 돌려 그 항목은 '바꾸지 못함'으로 남는다. */
private suspend fun <T> capability(fallback: T, call: (onComplete: (T) -> Unit, onFailed: (Exception) -> Unit) -> Unit): T =
    runCatching { withTimeout(3_000L) { sdk(call) } }.getOrElse { e ->
        Log.w("PleosVehicle", "capability check failed", e)
        fallback
    }

private fun <T> requireZones(zones: Collection<T>): Collection<T> =
    zones.ifEmpty { throw IllegalStateException("이 차에서 지원하지 않는 항목") }

private fun heatLabel(name: String, level: Int) = if (level == 0) "$name 끄기" else "$name ${level}단"

/** 콜백 두 개(성공·실패)로 끝나는 SDK 호출을 suspend로 바꾼다. */
private suspend fun <T> sdk(call: (onComplete: (T) -> Unit, onFailed: (Exception) -> Unit) -> Unit): T =
    suspendCancellableCoroutine { cont ->
        call({ if (cont.isActive) cont.resume(it) }, { if (cont.isActive) cont.resumeWithException(it) })
    }

private fun VehicleGear?.toGearState(): GearState = when (this) {
    VehicleGear.GEAR_PARK -> GearState.PARK
    VehicleGear.GEAR_REVERSE -> GearState.REVERSE
    VehicleGear.GEAR_NEUTRAL -> GearState.NEUTRAL
    null, VehicleGear.GEAR_UNKNOWN -> GearState.UNKNOWN
    else -> GearState.DRIVE
}
