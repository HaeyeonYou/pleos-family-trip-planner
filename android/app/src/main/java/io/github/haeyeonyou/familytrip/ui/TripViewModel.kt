package io.github.haeyeonyou.familytrip.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.haeyeonyou.familytrip.AppContainer
import io.github.haeyeonyou.familytrip.data.CarePreferences
import io.github.haeyeonyou.familytrip.data.CoursePlace
import io.github.haeyeonyou.familytrip.data.NapWindow
import io.github.haeyeonyou.familytrip.data.TripApiEraseRemote
import io.github.haeyeonyou.familytrip.data.eraseServerData
import io.github.haeyeonyou.familytrip.data.Preference
import io.github.haeyeonyou.familytrip.data.FamilyProfile
import io.github.haeyeonyou.familytrip.data.FeedingRoutine
import io.github.haeyeonyou.familytrip.data.CourseOption
import io.github.haeyeonyou.familytrip.data.VisitSummary
import io.github.haeyeonyou.familytrip.data.CourseRequest
import io.github.haeyeonyou.familytrip.data.StartBody
import io.github.haeyeonyou.familytrip.data.WeatherInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import io.github.haeyeonyou.familytrip.data.CourseResponse
import io.github.haeyeonyou.familytrip.data.LatLng
import io.github.haeyeonyou.familytrip.data.ParseRequest
import io.github.haeyeonyou.familytrip.data.ClimateSettings
import io.github.haeyeonyou.familytrip.data.PresetState
import io.github.haeyeonyou.familytrip.data.SavedCourse
import io.github.haeyeonyou.familytrip.data.SyncChange
import io.github.haeyeonyou.familytrip.data.SyncTracker
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
    /** 서버의 공조 프리셋 슬롯 3개 (탑승 준비·낮잠·수유 정차). 로그인 전이면 비어 있다. */
    val presets: List<PresetState> = emptyList(),
    /** 슬롯별 적용·저장 결과 문구 */
    val presetMessages: Map<String, String> = emptyMap(),
    /** 차에 적용하는 중인 슬롯 */
    val applyingSlot: String? = null,
    /** 수유 루틴 (#67). 기기에 저장하고, 로그인했다면 서버와 맞춘다 */
    val feeding: FeedingRoutine = FeedingRoutine(),
    /** 설정의 선호 조건 4개 (홈 칩의 시작 상태, 문장 요청에도 더해짐) */
    val prefs: CarePreferences = CarePreferences(),
    /** 설정의 낮잠 시간 */
    val nap: NapWindow = NapWindow(),
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
) {
    /** 폰에서 보냈는데 아직 차에 적용하지 않은 프리셋 중 가장 최근에 보낸 것 (코스 입력 화면의 안내 카드) */
    val pendingPreset: PresetState? get() = presets.filter { it.pending }.maxByOrNull { it.sentAt.orEmpty() }

    /** 베이비케어를 켤 때 쓰는 탑승 준비 프리셋 (폰에서 보낸 값, 보낸 적 없으면 서버 기본값). 로그인 전이면 null */
    val babyCarePreset: PresetState? get() = presets.firstOrNull { it.slot == "boarding" }
}

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
/** 웹과 맞추는 확인 주기. 5~10초 사이. */
private const val SYNC_INTERVAL_MS = 7_000L
private const val MIN_STAY_MINUTES = 10
private const val MAX_STAY_MINUTES = 300

class TripViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(container.profile.load().let { profile ->
        val prefs = loadPreferences(profile)
        TripUiState(
            profile = profile,
            feeding = container.feeding.load(),
            prefs = prefs,
            nap = container.settings.loadNap(),
            homeFilters = HomeFilters().withPreferences(prefs),
        )
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
        // 서버에서 받은 프리셋 슬롯과 가족 정보를 화면 상태에 반영한다.
        viewModelScope.launch {
            container.serverSync.presets.collect { list ->
                // 적용하는 중에는 같은 프리셋이 다시 대기로 보이지 않게 건드리지 않는다.
                if (_state.value.applyingSlot == null) _state.update { it.copy(presets = list) }
            }
        }
        viewModelScope.launch {
            container.profile.profile.collect { p ->
                if (p != null && p != _state.value.profile) _state.update { it.copy(profile = p, conditions = it.conditions?.let(p::applyTo)) }
            }
        }
    }

    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** 로그인한 직후 등 서버와 바로 맞추고 싶을 때 (다음 주기를 기다리지 않는다). */
    fun syncNow() {
        wake.trySend(Unit)
    }

    /**
     * 앱이 화면에 보이는 동안 [SYNC_INTERVAL_MS]마다 서버 `/me/sync`를 확인해, 웹(폰)에서 바뀐 영역만 다시 받는다.
     * 화면이 꺼지거나 백그라운드로 가면 호출한 쪽에서 취소한다. 로그인하지 않았으면 쉰다.
     * 요청 실패는 조용히 넘기고 다음 주기에 다시 시도하며, 401(웹에서 차 연결을 끊음)이면 로그인 정보를 지우고 [onUnauthorized]를 부른다.
     */
    suspend fun runSyncLoop(onUnauthorized: () -> Unit) {
        val tracker = SyncTracker()
        var lastToken: String? = null
        while (true) {
            val token = container.account.token
            if (token != null) {
                if (token != lastToken) tracker.reset()
                lastToken = token
                syncOnce(tracker, onUnauthorized)
            } else {
                lastToken = null
            }
            // 다음 주기까지 쉬되, 로그인 직후처럼 바로 맞춰야 할 때는 [syncNow]가 깨운다.
            withTimeoutOrNull(SYNC_INTERVAL_MS) { wake.receive() }
        }
    }

    private suspend fun syncOnce(tracker: SyncTracker, onUnauthorized: () -> Unit) {
        var unauthorized = false
        val marks = try {
            container.api.sync()
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 401) unauthorized = true else Log.w(TAG, "sync failed: ${e.code()}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "sync failed", e)
            null
        }
        if (unauthorized) {
            handleUnauthorized(onUnauthorized)
            return
        }
        if (marks == null) return
        val change = tracker.diff(marks)
        // 표시가 바뀐 영역과, 전에 못 올린 것이 남은 영역을 서버와 맞춘다. 반영을 마친 영역만 기억해 실패한 영역은 다음 주기에 다시 한다.
        val result = container.serverSync.refresh(change)
        if (result.unauthorized) {
            handleUnauthorized(onUnauthorized)
            return
        }
        if (change.any) Log.d(TAG, "sync changed: $change, done: ${result.done}")
        tracker.accept(marks, SyncChange(
            presets = !change.presets || result.done.presets,
            feedings = !change.feedings || result.done.feedings,
            visits = !change.visits || result.done.visits,
            family = !change.family || result.done.family,
            cars = true,
        ))
    }

    private fun handleUnauthorized(onUnauthorized: () -> Unit) {
        Log.i(TAG, "sync: 401, 로그인 정보를 지웁니다")
        container.account.clear()
        _state.update { it.copy(presets = emptyList(), applyingSlot = null) }
        onUnauthorized()
    }

    /** [slot]의 프리셋을 차 공조(와 미디어 음량)에 적용하고, 폰에서 보낸 것이었다면 서버에 적용 완료로 알린다. */
    fun applyPreset(slot: String) {
        val preset = _state.value.presets.firstOrNull { it.slot == slot } ?: return
        if (_state.value.applyingSlot != null) return
        viewModelScope.launch {
            _state.update { it.copy(applyingSlot = slot, presetMessages = it.presetMessages - slot) }
            val result = container.vehicle.applyClimate(preset.settings)
            // 차에 하나라도 적용됐을 때만 '적용했어요'로 알린다 (시동이 꺼져 아무것도 못 바꿨다면 대기로 남긴다).
            val applied = result.applied.isNotEmpty()
            if (applied && preset.pending && preset.id != null) container.serverSync.onPresetApplied(slot, preset.id)
            val message = presetResultMessage(result.applied, result.failed)
            _state.update { s ->
                s.copy(
                    applyingSlot = null,
                    presets = container.serverSync.presets.value.ifEmpty { s.presets },
                    presetMessages = s.presetMessages + (slot to message),
                )
            }
        }
    }

    /**
     * 정차 중 고친 [settings]를 차에 적용하고 슬롯에 저장한다 (`PUT /presets/{slot}?applied_on_car=true`).
     * 서버에 올리지 못해도 차에는 적용되어 있고, 저장은 다음 주기에 다시 시도한다.
     */
    fun savePreset(slot: String, settings: ClimateSettings) {
        if (_state.value.applyingSlot != null) return
        viewModelScope.launch {
            _state.update { it.copy(applyingSlot = slot, presetMessages = it.presetMessages - slot) }
            val result = container.vehicle.applyClimate(settings)
            val saved = container.serverSync.savePresetEdit(slot, settings)
            val message = presetResultMessage(result.applied, result.failed) + if (saved) "\n폰에도 저장했어요" else "\n폰에는 연결되면 저장할게요"
            _state.update { s ->
                s.copy(
                    applyingSlot = null,
                    presets = container.serverSync.presets.value.ifEmpty { s.presets },
                    presetMessages = s.presetMessages + (slot to message),
                )
            }
        }
    }

    private fun presetResultMessage(applied: List<String>, failed: List<String>) = buildString {
        if (applied.isNotEmpty()) append("적용했어요: ${applied.joinToString()}")
        if (failed.isNotEmpty()) {
            if (isNotEmpty()) append("\n")
            append("이 차에서 바꾸지 못한 항목: ${failed.joinToString()}")
        }
        if (isEmpty()) append("이 차에서는 공조를 제어할 수 없어요 (지원 차량에서만 · 시동이 켜져 있어야 해요)")
    }

    /** 안내 문구를 닫는다. 서버에는 그대로 남아 있다. */
    fun dismissPreset(slot: String? = null) = _state.update {
        it.copy(presetMessages = if (slot == null) emptyMap() else it.presetMessages - slot)
    }

    /** '지금 수유했어요': 마지막 수유를 지금으로 기록한다 (케어 컨트롤러가 차에 저장하고 서버에도 올린다). */
    fun markFed() = container.care.markFed()

    fun setFeedingInterval(minutes: Int) = container.care.setFeedingInterval(minutes)

    // 홈 추천 (HF01)

    private var homeJob: Job? = null

    /** 홈에서 이미 보여 준 나들이 장소 키. '다른 코스 보기'가 이 곳들을 빼고 요청한다. 조건이 바뀌면 비운다. */
    private val shownKeys = linkedSetOf<String>()

    /** 최근 90일 나들이 방문을 서버 요청용 요약으로 (기기 기록에서). 장소 키가 없는 예전 기록도 분야 가점에는 쓴다. */
    private fun recentVisits(): List<VisitSummary> {
        val since = java.time.LocalDate.now().minusDays(90)
        return container.visits.all()
            .filter { it.role == "outing" && !it.date.isBefore(since) }
            .map { VisitSummary(placeKey = it.placeKey.orEmpty(), theme = it.theme, date = it.date.toString()) }
    }

    /** 홈 '다른 코스 보기': 지금 보여 준 장소를 빼고 다시 추천받는다. */
    fun anotherOptions() {
        shownKeys += _state.value.options.map { it.placeKey }
        refreshOptions(0)
    }
    private var indoorTouched = false

    /** 홈에 들어올 때: 추천이 없으면 새로 받고, 있으면 날씨·'가는 중' 수만 갱신한다. */
    fun loadHome() {
        if (_state.value.options.isEmpty() && !_state.value.optionsLoading) refreshOptions(0) else refreshGoing()
    }

    fun toggleFilter(change: (HomeFilters) -> HomeFilters) {
        indoorTouched = true
        shownKeys.clear()
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
                    val current = _state.value
                    if (shouldPreferIndoor(current.prefs, w, indoorTouched, current.homeFilters)) {
                        _state.update { it.copy(homeFilters = it.homeFilters.copy(indoor = true)) }
                    }
                }
                .onFailure { e -> Log.w(TAG, "weather failed", e) }
            val s = _state.value
            val filters = s.homeFilters
            val conditions = homeConditions(s.profile, filters)
            try {
                val res = container.api.suggest(
                    CourseRequest(conditions = conditions, origin = origin, visits = recentVisits(), excludePlaceKeys = shownKeys.toList()),
                )
                shownKeys += res.options.map { it.placeKey }
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
                it.copy(conditions = withDefaults(it, res.conditions), parsedBy = res.parser)
            }
        }
    }

    /** 문장 없이 가족 정보만으로 조건을 만든다 (테마 등은 화면에서 고른다). */
    fun conditionsFromProfile() {
        val profile = _state.value.profile ?: return
        _state.update { it.copy(conditions = withDefaults(it, TripConditions()), parsedBy = null) }
    }

    fun saveProfile(profile: FamilyProfile) {
        container.profile.save(profile)
        container.serverSync.onFamilyEdited()
        val prefs = loadPreferences(profile)
        _state.update {
            it.copy(
                profile = profile,
                prefs = prefs,
                conditions = it.conditions?.let { c -> prefs.applyTo(profile.applyTo(c)) },
                homeFilters = HomeFilters().withPreferences(prefs),
                options = emptyList(),
            )
        }
    }

    /** 설정의 선호 조건 하나를 바꾼다. 홈 칩을 새 기본값으로 되돌리고 추천을 다시 받는다. */
    fun setPreference(preference: Preference, on: Boolean) {
        container.settings.setPreference(preference, on)
        shownKeys.clear()
        indoorTouched = false
        _state.update {
            val prefs = loadPreferences(it.profile)
            it.copy(prefs = prefs, homeFilters = it.homeFilters.withPreferences(prefs), options = emptyList())
        }
    }

    /**
     * '데이터 전체 삭제'. [includeServer]이면 서버 데이터를 먼저 지우고, 실패하면 기기 데이터는 건드리지 않고 안내 문구를 돌려준다.
     * 성공하면 null.
     */
    suspend fun wipeData(includeServer: Boolean): String? {
        if (includeServer) eraseServerData(TripApiEraseRemote(container.api))?.let { return it }
        wipeLocalData()
        return null
    }

    /**
     * '데이터 전체 삭제'의 기기 쪽: 가족 정보·방문 기록·저장한 코스·수유 기록·선호 조건·낮잠 시간을 지우고 화면 상태를 처음으로 되돌린다.
     * 로그인 정보는 그대로 둔다(로그아웃은 따로). 서버 데이터는 [wipeServerData]가 따로 지운다.
     */
    fun wipeLocalData() {
        container.serverSync.forgetForWipe()
        container.profile.clear()
        container.visits.clear()
        container.courses.clear()
        container.feeding.save(FeedingRoutine())
        container.settings.clear()
        shownKeys.clear()
        indoorTouched = false
        val prefs = loadPreferences(null)
        _state.update {
            it.copy(
                profile = null, conditions = null, parsedBy = null, course = null, courseConditions = null, savedCourseId = null,
                feeding = FeedingRoutine(), prefs = prefs, nap = NapWindow(),
                homeFilters = HomeFilters().withPreferences(prefs), options = emptyList(), going = emptyMap(),
                message = null, error = null,
            )
        }
    }

    fun setNap(nap: NapWindow) {
        container.settings.saveNap(nap)
        _state.update { it.copy(nap = nap) }
    }

    /** 저장한 선호 조건. 안 만진 토글은 가족 정보에서 정한 기본값을 따른다. */
    private fun loadPreferences(profile: FamilyProfile?): CarePreferences =
        CarePreferences.resolve(container.settings.storedPreferences(), profile)

    /** 문장에서 읽은 조건에 가족 정보와 선호 조건을 더한다. */
    private fun withDefaults(state: TripUiState, parsed: TripConditions): TripConditions =
        state.prefs.applyTo(state.profile?.applyTo(parsed) ?: parsed)

    fun updateConditions(transform: (TripConditions) -> TripConditions) =
        _state.update { it.copy(conditions = transform(it.conditions ?: TripConditions())) }

    fun requestCourse(onSuccess: () -> Unit) {
        val conditions = _state.value.conditions ?: TripConditions()
        val origin = currentOrigin()
        launchRequest {
            val course = container.api.course(CourseRequest(conditions = conditions, origin = origin, visits = recentVisits()))
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
