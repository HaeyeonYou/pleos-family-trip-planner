package io.github.haeyeonyou.familytrip.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.RangeRemainingListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pleos Vehicle SDK wrapper. Reads the current value once, then follows changes
 * through listeners. SDK callbacks arrive on binder threads; StateFlow is thread-safe.
 */
class PleosVehicleRepository(context: Context) : VehicleRepository {

    private val vehicle = Vehicle(context.applicationContext)
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

    private fun reportError(where: String, e: Exception?) {
        Log.w(TAG, "$where failed", e)
        _state.update { it.copy(lastError = "$where: ${e?.message ?: e?.javaClass?.simpleName}") }
    }

    private companion object {
        const val TAG = "PleosVehicle"
    }
}

private fun VehicleGear?.toGearState(): GearState = when (this) {
    VehicleGear.GEAR_PARK -> GearState.PARK
    VehicleGear.GEAR_REVERSE -> GearState.REVERSE
    VehicleGear.GEAR_NEUTRAL -> GearState.NEUTRAL
    null, VehicleGear.GEAR_UNKNOWN -> GearState.UNKNOWN
    else -> GearState.DRIVE
}
