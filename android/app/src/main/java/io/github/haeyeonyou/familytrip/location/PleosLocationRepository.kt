package io.github.haeyeonyou.familytrip.location

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import android.content.Context
import android.util.Log
import io.github.haeyeonyou.familytrip.navi.Place
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Pleos FusedLocation wrapper. On the emulator, positions injected with
 * `adb emu geo fix` reach this service, so arrival can be tested end to end.
 */
class PleosLocationRepository(context: Context) : LocationRepository {

    private val fusedLocation = FusedLocation(context.applicationContext)
    private val _state = MutableStateFlow(LocationState())
    override val state: StateFlow<LocationState> = _state.asStateFlow()

    private var alertId: String? = null

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            if (location == null) return
            _state.update { it.copy(latitude = location.latitude, longitude = location.longitude) }
        }

        override fun onError(e: Exception) = log("위치 오류: ${e.message}")
    }

    private val proximityListener = object : ProximityAlertListener {
        override fun onRegistered(id: String) {
            alertId = id
            log("근접 알림 등록됨: $id")
        }

        override fun onTransition(event: ProximityAlertEvent) {
            val place = _state.value.watching
            log("근접 이벤트: ${event.transition} ${place?.name}")
            if (event.transition == ProximityTransition.ENTER && place != null) {
                _state.update { it.copy(arrivedAt = place.name) }
            }
        }

        override fun onError(e: Exception) = log("근접 알림 오류: ${e.message}")
    }

    override fun start() {
        val supported = fusedLocation.isSupported()
        _state.update { it.copy(supported = supported) }
        if (!supported) {
            log("FusedLocation 서비스 없음")
            return
        }
        fusedLocation.initialize()
        fusedLocation.getLastKnownLocation(locationListener)
        fusedLocation.registerFusedLocationCallback(locationListener)
        log("FusedLocation 시작")
    }

    override fun stop() {
        clearArrival()
        fusedLocation.unregisterFusedLocationCallback(locationListener)
        fusedLocation.release()
    }

    override fun watchArrival(place: Place, radiusMeters: Float) {
        clearArrival()
        _state.update { it.copy(watching = place, arrivedAt = null) }
        log("도착 감시: ${place.name} 반경 ${radiusMeters.toInt()}m")
        fusedLocation.addProximityAlert(
            ProximityAlertRequest(
                place.latitude,
                place.longitude,
                radiusMeters,
                setOf(ProximityTransition.ENTER, ProximityTransition.EXIT),
            ),
            proximityListener,
        )
    }

    override fun clearArrival() {
        alertId?.let { fusedLocation.removeProximityAlert(it) }
        alertId = null
        _state.update { it.copy(watching = null) }
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        val line = "${LocalTime.now().format(TIME_FORMAT)} $message"
        _state.update { it.copy(events = (listOf(line) + it.events).take(MAX_EVENTS)) }
    }

    private companion object {
        const val TAG = "PleosLocation"
        const val MAX_EVENTS = 8
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
