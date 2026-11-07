package io.github.haeyeonyou.familytrip.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.haeyeonyou.familytrip.AppContainer
import io.github.haeyeonyou.familytrip.data.CoursePlace
import io.github.haeyeonyou.familytrip.data.CourseRequest
import io.github.haeyeonyou.familytrip.data.CourseResponse
import io.github.haeyeonyou.familytrip.data.LatLng
import io.github.haeyeonyou.familytrip.data.ParseRequest
import io.github.haeyeonyou.familytrip.data.ScheduleRequest
import io.github.haeyeonyou.familytrip.data.ScheduleStop
import io.github.haeyeonyou.familytrip.data.TripConditions
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
    /** 코스를 만들 때 쓴 출발지. 장소를 바꿔 시간표를 다시 계산할 때 같은 출발지를 쓴다. */
    val courseOrigin: LatLng? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class TripViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(TripUiState())
    val state: StateFlow<TripUiState> = _state.asStateFlow()

    val vehicle = container.vehicle.state
    val guide = container.guide.state

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
        val origin = currentOrigin()
        launchRequest {
            val course = container.api.course(CourseRequest(conditions = conditions, origin = origin))
            _state.update { it.copy(course = course, courseOrigin = origin) }
            onSuccess()
        }
    }

    /**
     * [stopIndex] 장소를 교체 후보 [place]로 바꾸고 시간표를 다시 계산한다.
     * 빠진 장소는 같은 역할의 교체 후보로 돌려놓아 다시 고를 수 있게 한다.
     */
    fun swapPlace(stopIndex: Int, place: CoursePlace) {
        val s = _state.value
        val course = s.course ?: return
        val old = course.stops.getOrNull(stopIndex) ?: return
        val stops = course.stops.filter { it.role != "home" }.mapIndexed { i, stop ->
            ScheduleStop(stop.role, if (i == stopIndex) place else stop.place)
        }
        launchRequest {
            val updated = container.api.schedule(
                ScheduleRequest(
                    conditions = s.conditions ?: TripConditions(),
                    origin = s.courseOrigin ?: currentOrigin(),
                    departureTime = course.departureAt.ifBlank { null },
                    stops = stops,
                )
            )
            val alternatives = course.alternatives.toMutableMap().apply {
                this[old.role] = (this[old.role].orEmpty() - place) + old.place
            }
            _state.update { it.copy(course = updated.copy(alternatives = alternatives)) }
        }
    }

    /** 첫 장소부터 안내. 안내 상태는 앱 단위 GuideSession이 가진다 (앱이 뒤에 있어도 도착 알림). */
    fun startGuide() {
        _state.value.course?.let { container.guide.start(it.stops) }
    }

    fun goToNext() = container.guide.goToNext()

    fun markArrived() = container.guide.markArrived()

    fun endGuide() = container.guide.end()

    fun dismissError() = _state.update { it.copy(error = null) }

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
