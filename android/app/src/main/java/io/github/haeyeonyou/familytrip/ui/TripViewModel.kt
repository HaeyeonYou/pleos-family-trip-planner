package io.github.haeyeonyou.familytrip.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.haeyeonyou.familytrip.AppContainer
import io.github.haeyeonyou.familytrip.data.CourseRequest
import io.github.haeyeonyou.familytrip.data.CourseResponse
import io.github.haeyeonyou.familytrip.data.CourseStop
import io.github.haeyeonyou.familytrip.data.LatLng
import io.github.haeyeonyou.familytrip.data.ParseRequest
import io.github.haeyeonyou.familytrip.data.TripConditions
import io.github.haeyeonyou.familytrip.navi.Place
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** 위치를 아직 못 받았을 때 쓰는 출발지 (PT 장소, 강남). */
private val DEFAULT_ORIGIN = LatLng(37.4979, 127.0276)

data class TripUiState(
    val requestText: String = "",
    val conditions: TripConditions? = null,
    val parsedBy: String? = null,
    val course: CourseResponse? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** 구간 안내 중인 stops 인덱스. null이면 안내 전. */
    val guideIndex: Int? = null,
    /** 현재 구간 목적지에 도착했는지. */
    val arrived: Boolean = false,
) {
    val currentStop: CourseStop? get() = guideIndex?.let { course?.stops?.getOrNull(it) }
    val nextStop: CourseStop? get() = guideIndex?.let { course?.stops?.getOrNull(it + 1) }
    val finished: Boolean get() = currentStop?.role == "home" && arrived
}

class TripViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(TripUiState())
    val state: StateFlow<TripUiState> = _state.asStateFlow()

    val vehicle = container.vehicle.state
    val location = container.location.state

    init {
        // 도착 판정(FusedLocation 근접 알림)이 현재 구간 목적지면 도착 처리
        viewModelScope.launch {
            container.location.state.collect { loc ->
                val current = _state.value.currentStop ?: return@collect
                if (loc.arrivedAt == current.place.name && !_state.value.arrived) {
                    _state.update { it.copy(arrived = true) }
                }
            }
        }
    }

    fun onRequestTextChange(text: String) = _state.update { it.copy(requestText = text) }

    fun parse() {
        val text = _state.value.requestText.trim()
        if (text.isEmpty()) return
        launchRequest {
            val res = container.api.parse(ParseRequest(text))
            _state.update { it.copy(conditions = res.conditions, parsedBy = res.parser) }
        }
    }

    fun updateConditions(transform: (TripConditions) -> TripConditions) =
        _state.update { it.copy(conditions = transform(it.conditions ?: TripConditions())) }

    fun requestCourse(onSuccess: () -> Unit) {
        val conditions = _state.value.conditions ?: TripConditions()
        launchRequest {
            val course = container.api.course(CourseRequest(conditions = conditions, origin = currentOrigin()))
            _state.update { it.copy(course = course, guideIndex = null, arrived = false) }
            onSuccess()
        }
    }

    /** 첫 장소부터 안내. 경유지 제한(최대 2개) 때문에 한 구간씩 목적지로 넘긴다. */
    fun startGuide() = guideTo(0)

    fun goToNext() {
        val index = _state.value.guideIndex ?: return
        guideTo(index + 1)
    }

    /** 근접 판정이 안 될 때를 위한 수동 도착. */
    fun markArrived() = _state.update { it.copy(arrived = true) }

    fun endGuide() {
        container.navi.cancelRoute()
        container.location.clearArrival()
        _state.update { it.copy(guideIndex = null, arrived = false) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun guideTo(index: Int) {
        val stop = _state.value.course?.stops?.getOrNull(index) ?: return
        val place = Place(stop.place.name, stop.place.latitude, stop.place.longitude)
        container.navi.requestRoute(place)
        container.location.watchArrival(place)
        _state.update { it.copy(guideIndex = index, arrived = false) }
    }

    private fun currentOrigin(): LatLng {
        val loc = container.location.state.value
        return if (loc.latitude != null && loc.longitude != null) LatLng(loc.latitude, loc.longitude) else DEFAULT_ORIGIN
    }

    private fun launchRequest(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                block()
            } catch (e: HttpException) {
                val message = if (e.code() == 404) "조건에 맞는 장소를 찾지 못했어요" else "서버 오류 (${e.code()})"
                _state.update { it.copy(error = message) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "서버에 연결하지 못했어요: ${e.message}") }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}
