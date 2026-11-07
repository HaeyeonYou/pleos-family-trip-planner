package io.github.haeyeonyou.familytrip.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.haeyeonyou.familytrip.AppContainer
import io.github.haeyeonyou.familytrip.data.CoursePlace
import io.github.haeyeonyou.familytrip.data.FamilyProfile
import io.github.haeyeonyou.familytrip.data.CourseRequest
import io.github.haeyeonyou.familytrip.data.CourseResponse
import io.github.haeyeonyou.familytrip.data.LatLng
import io.github.haeyeonyou.familytrip.data.ParseRequest
import io.github.haeyeonyou.familytrip.data.SavedCourse
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
    /** 이 코스를 저장했으면 그 id. 다시 저장하면 덮어쓴다. */
    val savedCourseId: Long? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** 잠깐 보여 줄 안내 (저장 완료 등). */
    val message: String? = null,
    /** 한 번 등록한 가족 정보. 없으면 등록을 권한다. */
    val profile: FamilyProfile? = null,
)

private const val MIN_STAY_MINUTES = 10
private const val MAX_STAY_MINUTES = 300

class TripViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(TripUiState(profile = container.profile.load()))
    val state: StateFlow<TripUiState> = _state.asStateFlow()

    val vehicle = container.vehicle.state
    val guide = container.guide.state

    fun onRequestTextChange(text: String) = _state.update { it.copy(requestText = text) }

    fun parse() {
        val text = _state.value.requestText.trim()
        if (text.isEmpty()) return
        launchRequest {
            val res = container.api.parse(ParseRequest(text))
            _state.update {
                it.copy(conditions = it.profile?.applyTo(res.conditions) ?: res.conditions, parsedBy = res.parser)
            }
        }
    }

    /** 문장 없이 가족 정보만으로 조건을 만든다 (테마 등은 화면에서 고른다). */
    fun conditionsFromProfile() {
        val profile = _state.value.profile ?: return
        _state.update { it.copy(conditions = profile.applyTo(TripConditions()), parsedBy = null) }
    }

    fun saveProfile(profile: FamilyProfile) {
        container.profile.save(profile)
        _state.update { it.copy(profile = profile, conditions = it.conditions?.let(profile::applyTo)) }
    }

    fun updateConditions(transform: (TripConditions) -> TripConditions) =
        _state.update { it.copy(conditions = transform(it.conditions ?: TripConditions())) }

    fun requestCourse(onSuccess: () -> Unit) {
        val conditions = _state.value.conditions ?: TripConditions()
        val origin = currentOrigin()
        launchRequest {
            val course = container.api.course(CourseRequest(conditions = conditions, origin = origin))
            _state.update { it.copy(course = course, courseOrigin = origin, savedCourseId = null, message = null) }
            onSuccess()
        }
    }

    // 코스 편집: 바꾼 장소·순서·체류 시간 그대로 서버가 시간표를 다시 계산한다.

    /**
     * [stopIndex] 장소를 교체 후보 [place]로 바꾼다. 빠진 장소는 교체 후보로 돌려놓는다.
     * 나들이를 바꾸면 그 근처 식사·카페 후보를 새로 받는다.
     */
    fun swapPlace(stopIndex: Int, place: CoursePlace) {
        val stops = editableStops() ?: return
        val old = stops.getOrNull(stopIndex) ?: return
        val edited = stops.toMutableList().apply { this[stopIndex] = ScheduleStop(old.role, place) }
        reschedule(edited, suggestNearby = old.role == "outing") { alternatives ->
            alternatives + (old.role to (alternatives[old.role].orEmpty() - place + old.place))
        }
    }

    /** 순서를 [delta]만큼 옮긴다 (-1 위로, +1 아래로). */
    fun moveStop(stopIndex: Int, delta: Int) {
        val stops = editableStops() ?: return
        val target = stopIndex + delta
        if (target !in stops.indices) return
        val edited = stops.toMutableList().apply { add(target, removeAt(stopIndex)) }
        reschedule(edited)
    }

    /** 장소를 코스에서 뺀다. 같은 역할의 후보로 돌려놓아 다시 추가할 수 있다. */
    fun removeStop(stopIndex: Int) {
        val stops = editableStops() ?: return
        if (stops.size <= 1) return
        val removed = stops[stopIndex]
        reschedule(stops.filterIndexed { i, _ -> i != stopIndex }) { alternatives ->
            alternatives + (removed.role to listOf(removed.place) + alternatives[removed.role].orEmpty())
        }
    }

    /** 체류 시간을 [deltaMinutes]만큼 바꾼다 (최소 10분). */
    fun changeStay(stopIndex: Int, deltaMinutes: Int) {
        val stops = editableStops() ?: return
        val stop = stops.getOrNull(stopIndex) ?: return
        val stay = ((stop.stayMinutes ?: 0) + deltaMinutes).coerceIn(MIN_STAY_MINUTES, MAX_STAY_MINUTES)
        reschedule(stops.toMutableList().apply { this[stopIndex] = stop.copy(stayMinutes = stay) })
    }

    /** 후보 장소를 코스 끝(귀가 전)에 추가한다. 순서는 위아래로 옮길 수 있다. */
    fun addStop(role: String, place: CoursePlace) {
        val stops = editableStops() ?: return
        reschedule(stops + ScheduleStop(role, place)) { alternatives ->
            alternatives + (role to (alternatives[role].orEmpty() - place))
        }
    }

    /** 지금 코스를 차 안에 저장한다 (서버로 보내지 않음). */
    fun saveCourse() {
        val s = _state.value
        val course = s.course ?: return
        val now = System.currentTimeMillis()
        val title = course.stops.firstOrNull { it.role == "outing" }?.place?.name
            ?: course.stops.first().place.name
        container.courses.save(
            SavedCourse(
                id = s.savedCourseId ?: now,
                savedAt = now,
                title = title,
                conditions = s.conditions ?: TripConditions(),
                origin = s.courseOrigin ?: currentOrigin(),
                course = course,
            )
        )
        _state.update { it.copy(savedCourseId = it.savedCourseId ?: now, message = "코스를 저장했어요") }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    /** 귀가를 뺀 방문 장소. 지금 체류 시간을 그대로 넘겨 사용자가 바꾼 값이 유지되게 한다. */
    private fun editableStops(): List<ScheduleStop>? =
        _state.value.course?.stops
            ?.filter { it.role != "home" }
            ?.map { ScheduleStop(it.role, it.place, it.stayMinutes) }

    private fun reschedule(
        stops: List<ScheduleStop>,
        suggestNearby: Boolean = false,
        updateAlternatives: (Map<String, List<CoursePlace>>) -> Map<String, List<CoursePlace>> = { it },
    ) {
        val s = _state.value
        val course = s.course ?: return
        launchRequest {
            val updated = container.api.schedule(
                ScheduleRequest(
                    conditions = s.conditions ?: TripConditions(),
                    origin = s.courseOrigin ?: currentOrigin(),
                    departureTime = course.departureAt.ifBlank { null },
                    stops = stops,
                    suggestNearby = suggestNearby,
                )
            )
            // 근처 후보를 새로 받았으면 식사·카페 후보는 그것으로 바꾼다.
            val base = course.alternatives + updated.alternatives
            _state.update {
                it.copy(
                    course = updated.copy(alternatives = updateAlternatives(base)),
                    message = if (updated.alternatives.isNotEmpty()) "나들이 장소 근처 식당·카페 후보를 새로 찾았어요" else null,
                )
            }
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
