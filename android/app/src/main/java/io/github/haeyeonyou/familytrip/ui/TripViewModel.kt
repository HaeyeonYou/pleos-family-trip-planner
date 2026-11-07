package io.github.haeyeonyou.familytrip.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.haeyeonyou.familytrip.AppContainer
import io.github.haeyeonyou.familytrip.data.CoursePlace
import io.github.haeyeonyou.familytrip.data.FamilyProfile
import io.github.haeyeonyou.familytrip.data.FeedingRoutine
import io.github.haeyeonyou.familytrip.data.CourseOption
import io.github.haeyeonyou.familytrip.data.CourseRequest
import io.github.haeyeonyou.familytrip.data.StartBody
import io.github.haeyeonyou.familytrip.data.WeatherInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import io.github.haeyeonyou.familytrip.data.CourseResponse
import io.github.haeyeonyou.familytrip.data.LatLng
import io.github.haeyeonyou.familytrip.data.ParseRequest
import io.github.haeyeonyou.familytrip.data.PendingPreset
import io.github.haeyeonyou.familytrip.data.SavedCourse
import io.github.haeyeonyou.familytrip.data.ScheduleRequest
import io.github.haeyeonyou.familytrip.data.ScheduleStop
import io.github.haeyeonyou.familytrip.data.TripConditions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    /** 폰에서 보내 아직 적용하지 않은 공조 프리셋 (#62) */
    val pendingPreset: PendingPreset? = null,
    /** 프리셋 적용 결과 문구 */
    val presetMessage: String? = null,
    val applyingPreset: Boolean = false,
    /** 수유 루틴 (#67). 기기에만 저장 */
    val feeding: FeedingRoutine = FeedingRoutine(),
    val homeFilters: HomeFilters = HomeFilters(),
    /** 홈 추천을 만든 조건 (코스를 열면 이 조건으로 편집한다) */
    val courseConditions: TripConditions? = null,
    /** 홈 추천 코스 (서버 /course/suggest) */
    val options: List<CourseOption> = emptyList(),
    val optionsLoading: Boolean = false,
    val optionsError: String? = null,
    val weather: WeatherInfo? = null,
    /** 장소 키 → 최근 1시간 안에 시작한 수 */
    val going: Map<String, Int> = emptyMap(),
)

/** 홈 필터 칩 (HF01). 칩을 바꾸면 추천을 다시 계산한다. */
data class HomeFilters(
    val nursingRoom: Boolean = false,
    val stroller: Boolean = false,
    val pet: Boolean = false,
    val indoor: Boolean = false,
    val kidsMenu: Boolean = false,
    val playroom: Boolean = false,
    val parking: Boolean = false,
) {
    fun needs(): List<String> = buildList {
        if (nursingRoom) add("nursing_room")
        if (stroller) add("stroller")
        if (kidsMenu) add("kids_chair")
        if (playroom) add("kids_zone")
        if (parking) add("parking")
    }
}

private const val TAG = "TripViewModel"
private const val MIN_STAY_MINUTES = 10
private const val MAX_STAY_MINUTES = 300

class TripViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(container.profile.load().let { profile ->
        TripUiState(profile = profile, feeding = container.feeding.load(), homeFilters = defaultFilters(profile))
    })
    val state: StateFlow<TripUiState> = _state.asStateFlow()

    val vehicle = container.vehicle.state
    val guide = container.guide.state

    init {
        // 케어 탭에서 수유를 기록하면 홈의 수유 카드도 같은 값을 본다.
        viewModelScope.launch {
            container.care.state.map { it.feeding }.distinctUntilChanged().collect { f ->
                _state.update { it.copy(feeding = f) }
            }
        }
        checkPendingPreset()
    }

    /** 로그인한 계정으로 폰에서 보낸 프리셋이 있는지 본다 (앱 시작, 로그인 직후). */
    fun checkPendingPreset() {
        if (container.account.token == null) return
        viewModelScope.launch {
            runCatching { container.api.pendingPreset() }
                .onSuccess { res -> _state.update { it.copy(pendingPreset = res.body().takeIf { res.isSuccessful }) } }
                .onFailure { e -> Log.w(TAG, "pending preset check failed", e) }
        }
    }

    /** 받은 프리셋을 차 공조에 적용하고 서버에 적용 완료로 알린다. */
    fun applyPreset() {
        val preset = _state.value.pendingPreset ?: return
        viewModelScope.launch {
            _state.update { it.copy(applyingPreset = true) }
            val result = container.vehicle.applyClimate(preset.settings)
            runCatching { container.api.presetApplied(preset.id) }
            val message = buildString {
                if (result.applied.isNotEmpty()) append("적용했어요: ${result.applied.joinToString()}")
                if (result.failed.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append("이 차에서 바꾸지 못한 항목: ${result.failed.joinToString()}")
                }
            }
            _state.update { it.copy(pendingPreset = null, applyingPreset = false, presetMessage = message) }
        }
    }

    /** 이번에는 적용하지 않는다. 서버에는 그대로 남아 다음에 앱을 켜면 다시 보인다. */
    fun dismissPreset() = _state.update { it.copy(pendingPreset = null, presetMessage = null) }

    /** '지금 수유했어요': 마지막 수유를 지금으로 기록한다. */
    fun markFed() = updateFeeding { it.copy(lastFedAt = System.currentTimeMillis()) }

    fun setFeedingInterval(minutes: Int) = updateFeeding { it.copy(intervalMinutes = minutes) }

    private fun updateFeeding(transform: (FeedingRoutine) -> FeedingRoutine) {
        val updated = transform(_state.value.feeding)
        container.feeding.save(updated)
        _state.update { it.copy(feeding = updated) }
    }

    // 홈 추천 (HF01)

    private var homeJob: Job? = null
    private var indoorTouched = false

    /** 홈에 들어올 때: 추천이 없으면 새로 받고, 있으면 날씨·'가는 중' 수만 갱신한다. */
    fun loadHome() {
        if (_state.value.options.isEmpty() && !_state.value.optionsLoading) refreshOptions(0) else refreshGoing()
    }

    fun toggleFilter(change: (HomeFilters) -> HomeFilters) {
        indoorTouched = true
        _state.update { it.copy(homeFilters = change(it.homeFilters)) }
        refreshOptions(400)
    }

    fun retryHome() = refreshOptions(0)

    private fun refreshOptions(debounceMs: Long) {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            _state.update { it.copy(optionsLoading = true, optionsError = null) }
            delay(debounceMs)
            val origin = currentOrigin()
            // 날씨: 비·눈이면 실내를 먼저 (칩을 직접 바꾸지 않았을 때). 실패해도 추천은 계속한다.
            runCatching { container.api.weather(origin.latitude, origin.longitude) }
                .onSuccess { w ->
                    _state.update { it.copy(weather = w) }
                    if (w.rainy && !indoorTouched && !_state.value.homeFilters.indoor) {
                        _state.update { it.copy(homeFilters = it.homeFilters.copy(indoor = true)) }
                    }
                }
                .onFailure { e -> Log.w(TAG, "weather failed", e) }
            val s = _state.value
            val filters = s.homeFilters
            val base = s.profile?.applyTo(TripConditions()) ?: TripConditions()
            val conditions = base.copy(withPet = filters.pet, indoorOnly = filters.indoor, needs = filters.needs())
            try {
                val res = container.api.suggest(CourseRequest(conditions = conditions, origin = origin))
                _state.update { it.copy(options = res.options, optionsLoading = false, courseConditions = conditions) }
                refreshGoing()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: HttpException) {
                val message = if (e.code() == 404) "조건에 맞는 코스를 찾지 못했어요. 칩을 줄여 보세요" else "서버 오류 (${e.code()})"
                _state.update { it.copy(options = emptyList(), optionsLoading = false, optionsError = message) }
            } catch (e: Exception) {
                _state.update { it.copy(options = emptyList(), optionsLoading = false, optionsError = "서버에 연결하지 못했어요") }
            }
        }
    }

    private fun refreshGoing() {
        val keys = _state.value.options.map { it.placeKey }.distinct()
        if (keys.isEmpty()) return
        viewModelScope.launch {
            runCatching { container.api.going(keys.joinToString(",")) }
                .onSuccess { going -> _state.update { it.copy(going = going) } }
                .onFailure { e -> Log.w(TAG, "going failed", e) }
        }
    }

    /** 홈 카드를 눌러 코스 상세로 들어간다. */
    fun openOption(option: CourseOption) = _state.update {
        it.copy(
            course = option.course,
            conditions = it.courseConditions ?: it.conditions,
            courseOrigin = currentOrigin(),
            savedCourseId = null,
            error = null,
            message = null,
        )
    }

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
        _state.update {
            it.copy(profile = profile, conditions = it.conditions?.let(profile::applyTo), homeFilters = defaultFilters(profile), options = emptyList())
        }
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

    /** 저장한 코스를 다시 연다 (경험 우주에서 장소를 눌렀을 때). */
    fun openSavedCourse(saved: SavedCourse) = _state.update {
        it.copy(
            course = saved.course,
            conditions = saved.conditions,
            courseOrigin = saved.origin,
            savedCourseId = saved.id,
            error = null,
            message = null,
        )
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
        val course = _state.value.course ?: return
        container.guide.start(course.stops)
        // '지금 N대 가는 중' 집계: 나들이 장소 키만 서버에 알린다 (실패해도 안내는 계속).
        course.stops.firstOrNull { it.role == "outing" }?.place?.let { place ->
            viewModelScope.launch {
                runCatching { container.api.recordStart(StartBody("${place.source}:${place.sourceId}")) }
                    .onFailure { e -> Log.w(TAG, "record start failed", e) }
            }
        }
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

/** 가족 정보로 정하는 칩 기본값: 36개월 미만이면 수유실·유모차, 반려견이 함께면 반려동물. */
private fun defaultFilters(profile: FamilyProfile?): HomeFilters {
    val infant = profile?.ageMonths()?.minOrNull()?.let { it < 36 } == true
    return HomeFilters(nursingRoom = infant, stroller = infant, pet = profile?.petAlong == true)
}
