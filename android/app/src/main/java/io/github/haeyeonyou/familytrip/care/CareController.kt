package io.github.haeyeonyou.familytrip.care

import android.content.Context
import android.util.Log
import io.github.haeyeonyou.familytrip.data.CarePlace
import io.github.haeyeonyou.familytrip.data.ClimateSettings
import io.github.haeyeonyou.familytrip.data.FeedingRoutine
import io.github.haeyeonyou.familytrip.data.FeedingStore
import io.github.haeyeonyou.familytrip.data.LatLng
import io.github.haeyeonyou.familytrip.data.PresetState
import io.github.haeyeonyou.familytrip.data.ServerSync
import io.github.haeyeonyou.familytrip.data.TripApi
import io.github.haeyeonyou.familytrip.data.VisitStore
import io.github.haeyeonyou.familytrip.guide.GuideNotifier
import io.github.haeyeonyou.familytrip.guide.GuideSession
import io.github.haeyeonyou.familytrip.location.LocationRepository
import io.github.haeyeonyou.familytrip.navi.NaviRepository
import io.github.haeyeonyou.familytrip.navi.Place
import io.github.haeyeonyou.familytrip.vehicle.ClimateReading
import io.github.haeyeonyou.familytrip.vehicle.VehicleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 급한 케어 찾기의 종류. [api]는 서버 kind 값. */
enum class CareKind(val api: String, val label: String) {
    NURSING("nursing", "수유실"),
    /** 음성으로만 찾는다 (케어 화면 카드에는 없다: 피그마 HF03 v2는 수유실·정차 공간 두 장) */
    DIAPER("diaper", "기저귀 교환대"),
    REST("rest", "정차 공간"),

    /** 음성으로만 찾는다 (케어 화면 카드에는 없다) */
    RESTROOM("restroom", "화장실"),
    ;

    companion object {
        /** 케어 화면에 카드로 보이는 종류 */
        val cards: List<CareKind> = listOf(NURSING, REST)
    }
}

/** 가까운 곳 찾기 결과. */
sealed interface Nearby {
    data object Loading : Nearby
    data class Found(val places: List<CarePlace>) : Nearby
    data class Failed(val message: String) : Nearby
}

data class CareState(
    val babyCare: Boolean = false,
    /** 베이비케어 켜는 중/끄는 중 */
    val babyCareBusy: Boolean = false,
    val targetTemperature: Float = BABY_TARGET_TEMPERATURE,
    /** 공조가 지금 가진 설정 온도 (SDK에 실내 온도 센서가 없어 설정값을 읽는다). 읽지 못하면 null. */
    val currentTemperature: Float? = null,
    /** 베이비케어 적용 결과 문구 (적용됨 / 지원 안 함 / 시동 꺼짐) */
    val climateMessage: String? = null,
    val sleepMode: Boolean = false,
    /** 수면 모드를 켠 시각 (C04 "잠든 지 N분"). 모르면 null. */
    val sleepStartedAt: Long? = null,
    val feeding: FeedingRoutine = FeedingRoutine(),
    val nearby: Map<CareKind, Nearby> = emptyMap(),
    /** 경유지 추가 결과 문구 */
    val waypointMessage: String? = null,
    /** 자주 가는 케어 장소 (방문 기록에서). 없으면 케어 화면이 그 영역을 그리지 않는다. */
    val frequent: List<FrequentCarePlace> = emptyList(),
    /** 열려 있는 급한 케어 시트 (C09~C11). 닫혀 있으면 null. */
    val urgent: UrgentSheet? = null,
    /** 케어에서 넣은 경유지 (C12 토스트·"다음 경유지" 슬롯). 빼거나 안내가 끝나면 null. */
    val addedWaypoint: AddedWaypoint? = null,
    /** 케어 대상 아이 (서버 아이 id, null = 첫째). 수유 타이머와 케어 모드 값(공조 프리셋)이 아이별이다 (QO5, #192). */
    val selectedChildId: String? = null,
    /** 진행 중인 수유 정차 (C03 "수유 중 N분째"). 없으면 null. */
    val feedingStop: FeedingStopSession? = null,
    /** 경유지에 도착해 정차했을 때 수유 정차 모드를 켤지 묻는다 (C10·C12 안내). */
    val askFeedingStop: Boolean = false,
    /** 케어 화면 맨 위 한 줄 안내 (수유 정차 모드 시작·종료 결과). [noticeAt]이 바뀌면 다시 보인다. */
    val notice: String? = null,
    val noticeAt: Long = 0L,
)

const val BABY_TARGET_TEMPERATURE = 24f
private const val BABY_FAN_LEVEL = 1
private const val SLEEP_FAN_LEVEL = 1
private const val ARRIVING_SOON_SECONDS = 300
private const val TAG = "CareController"

/** 위치를 아직 못 받았을 때 쓰는 출발지 (PT 장소, 강남). */
private val DEFAULT_LOCATION = LatLng(37.4979, 127.0276)

/**
 * 케어 기능 전체(베이비케어 공조, 수유 루틴, 수면 모드, 급한 케어 찾기)를 앱 단위로 가진다.
 * 화면(케어 탭)과 음성 명령이 같은 동작을 쓰도록 화면이 아니라 앱에 둔다.
 */
class CareController(
    context: Context,
    private val vehicle: VehicleRepository,
    private val navi: NaviRepository,
    private val location: LocationRepository,
    private val api: TripApi,
    private val guide: GuideSession,
    private val notifier: GuideNotifier,
    private val feedingStore: FeedingStore,
    private val serverSync: ServerSync,
    private val visits: VisitStore,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("care", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        CareState(
            sleepMode = prefs.getBoolean(KEY_SLEEP, false),
            sleepStartedAt = prefs.getLong(KEY_SLEEP_AT, 0L).takeIf { it > 0 && prefs.getBoolean(KEY_SLEEP, false) },
            feeding = feedingStore.load(),
            selectedChildId = feedingStore.selectedChildId.value,
            // 앱이 다시 켜져도 진행 중인 수유 정차는 이어간다.
            feedingStop = prefs.getLong(KEY_FEED_STOP_AT, 0L).takeIf { it > 0 }?.let { FeedingStopSession(it, prefs.getBoolean(KEY_FEED_STOP_CLIMATE, false)) },
        ),
    )
    val state: StateFlow<CareState> = _state.asStateFlow()

    /** 베이비케어를 켜기 전 공조 설정. 끌 때 되돌리는 데 쓴다. */
    private var beforeBabyCare: ClimateReading? = null
    private var arrivingSoonNotified = false
    /** 수유 정차 모드를 켜기 전 공조 설정 (끝낼 때 베이비케어가 꺼져 있으면 되돌린다). */
    private var beforeFeedingStop: ClimateReading? = null
    /** 수면 모드를 켜기 전 공조 설정. */
    private var beforeSleep: ClimateReading? = null
    /** 수유 정차 모드를 물어본 경유지 ([AddedWaypoint.addedAt]). 같은 곳에서 두 번 묻지 않는다. */
    private var askedFor: Long? = null
    private var feedingStopBusy = false

    /** 경유지를 넣은 뒤 안내가 한 번이라도 시작됐는지. 그 뒤 안내가 끝나면 경유지 카드를 내린다. */
    private var waypointRouting = false
    /** 안내가 끝난 것처럼 보일 때 잠시 기다렸다 카드를 내리는 작업. 내비가 경로를 바꿀 때 "종료→시작"을 잠깐 보내서다. */
    private var waypointEndJob: Job? = null

    init {
        notifier.quiet = _state.value.sleepMode
        ToneState.sleep = _state.value.sleepMode
        // 수유 루틴은 저장소가 하나뿐이다: 홈·음성·서버에서 받은 변경도 같은 값을 본다.
        scope.launch { feedingStore.routine.collect { f -> _state.update { it.copy(feeding = f, selectedChildId = feedingStore.selectedChildId.value) } } }
        // 방문 기록이 바뀌면(도착·서버에서 받은 변경) 자주 가는 케어 장소를 다시 고른다.
        scope.launch { visits.revision.collect { refreshFrequent() } }
        // 정차(P단) 감지 (D10): 출발하면 수유 정차를 끝내고, 케어 경유지에 도착해 정차하면 수유 정차 모드를 묻는다.
        scope.launch {
            vehicle.state.collect { v ->
                val st = _state.value
                if (!v.isParked) {
                    if (st.feedingStop != null && v.gear != io.github.haeyeonyou.familytrip.vehicle.GearState.UNKNOWN) finishFeedingStop(auto = true)
                    if (st.askFeedingStop) _state.update { it.copy(askFeedingStop = false) }
                } else if (st.addedWaypoint != null) {
                    Log.i(TAG, "정차: 케어 경유지 있음, 위치 앎=${knownLocation() != null}, 물어봄=${askedFor == st.addedWaypoint.addedAt}")
                }
                if (v.isParked && st.feedingStop == null && st.addedWaypoint != null && askedFor != st.addedWaypoint.addedAt &&
                    arrivedAtWaypoint(true, st.addedWaypoint, knownLocation())
                ) {
                    askedFor = st.addedWaypoint.addedAt
                    _state.update { it.copy(askFeedingStop = true) }
                }
            }
        }
        // 수면 모드 중 도착 5분 전에 한 번만 조용히 알린다.
        scope.launch {
            navi.state.collect { n ->
                if (_state.value.addedWaypoint != null) {
                    if (n.routing) {
                        waypointRouting = true
                        waypointEndJob?.cancel()
                        waypointEndJob = null
                    } else if (waypointRouting && waypointEndJob == null) {
                        waypointEndJob = scope.launch {
                            delay(ROUTE_END_GRACE_MS)
                            if (!navi.state.value.routing) clearAddedWaypoint()
                        }
                    }
                }
                val remaining = n.remainingSeconds
                if (!n.routing || remaining == null) {
                    arrivingSoonNotified = false
                } else if (_state.value.sleepMode && !arrivingSoonNotified && remaining <= ARRIVING_SOON_SECONDS) {
                    arrivingSoonNotified = true
                    notifier.notifyArrivingSoon((remaining + 59) / 60)
                }
            }
        }
    }

    // 수유 루틴 (hf01-home과 같은 FeedingStore 사용)

    /** 방금 수유했다: 차에 저장하고, 로그인했다면 서버에도 기록한다 (못 올리면 쌓아 두었다가 다시 올린다). */
    fun markFed(at: Long = System.currentTimeMillis()) {
        // 수유 정차 중의 "다 먹었어"·"방금 수유했어"는 수유 끝으로 본다 (시작 시각을 기록).
        if (_state.value.feedingStop != null) {
            finishFeedingStop(auto = false)
            return
        }
        feedingStore.save(feedingStore.load().copy(lastFedAt = at))
        serverSync.onFed(at)
    }

    /** 수유 간격을 바꾼다 (설정 화면: 첫째 아이). 로그인했다면 첫째 아이의 수유 간격으로 서버에도 올린다. */
    fun setFeedingInterval(minutes: Int) {
        feedingStore.firstChild.save(feedingStore.firstChild.load().copy(intervalMinutes = minutes))
        serverSync.onFamilyEdited()
    }

    // 베이비케어: 실제 공조 제어

    /**
     * 베이비케어를 켜면 [preset](가족 공조 프리셋)이 있으면 그 값을, 없으면 기본값
     * (24°C, 약한 바람, 시트·핸들 열선 끔)을 차에 적용한다. 끄면 켜기 전 설정으로 되돌린다.
     */
    fun setBabyCare(on: Boolean, preset: PresetState? = null, onApplied: (() -> Unit)? = null): Job {
        if (_state.value.babyCareBusy) return scope.launch { }
        return scope.launch {
            _state.update { it.copy(babyCareBusy = true, climateMessage = null) }
            val used = if (on) enableBabyCare(preset) else { disableBabyCare(); false }
            if (used && preset?.id != null && preset.pending) serverSync.onPresetApplied(preset.slot, preset.id)
            _state.update { it.copy(babyCareBusy = false) }
            if (on && _state.value.babyCare) onApplied?.invoke()
        }
    }

    /** 켜기를 시도한다. 차에 하나라도 적용했으면 true. */
    private suspend fun enableBabyCare(preset: PresetState?): Boolean {
        val before = vehicle.readClimate()
        if (!before.supported) {
            _state.update {
                it.copy(babyCare = false, climateMessage = "이 차에서는 공조를 제어할 수 없어요 (지원 차량에서만 · 시동이 켜져 있어야 해요)")
            }
            return false
        }
        beforeBabyCare = before
        val settings = preset?.settings ?: ClimateSettings(
            name = "베이비케어",
            power = true,
            temperature = BABY_TARGET_TEMPERATURE,
            fanSpeed = BABY_FAN_LEVEL,
            ac = false,
            airCleaner = false,
            driverSeatHeat = 0,
            passengerSeatHeat = 0,
            steeringWheelHeat = 0,
        )
        val result = vehicle.applyClimate(settings)
        // 막 켠 공조는 설정 온도가 잠깐 읽히지 않는다. 읽힐 때까지 몇 번 다시 읽는다.
        var after = vehicle.readClimate()
        repeat(4) {
            if (after.temperature == null) {
                delay(700)
                after = vehicle.readClimate()
            }
        }
        Log.i(TAG, "baby care applied=${result.applied} failed=${result.failed} before=${before.temperature} after=${after.temperature}")
        // 차가 요청을 받았어도 실제로 공조가 켜졌는지는 다시 읽어서 확인한다 (시동이 꺼져 있으면 반영되지 않는다).
        val confirmed = after.power == true
        val message = when {
            result.applied.isEmpty() -> "이 차에서는 공조를 제어할 수 없어요 (지원 차량에서만 · 시동이 켜져 있어야 해요)"
            !confirmed -> "공조에 요청을 보냈지만 차가 아직 반영하지 않았어요 (시동이 켜져 있는지 확인해 주세요)"
            result.failed.isEmpty() -> "공조에 적용했어요: ${result.applied.joinToString()}"
            else -> "일부만 적용했어요: ${result.applied.joinToString()}\n이 차에서 바꾸지 못한 항목: ${result.failed.joinToString()}"
        }
        _state.update {
            it.copy(
                babyCare = result.applied.isNotEmpty(),
                targetTemperature = settings.temperature,
                currentTemperature = after.temperature ?: before.temperature,
                climateMessage = message,
            )
        }
        return result.applied.isNotEmpty()
    }

    private suspend fun disableBabyCare() {
        val before = beforeBabyCare
        if (before?.power != null) {
            val restore = ClimateSettings(
                name = "복원",
                power = before.power,
                // 꺼져 있던 공조는 온도를 읽지 못한다. 켜져 있던 경우에만 값이 있다.
                temperature = before.temperature ?: BABY_TARGET_TEMPERATURE,
                fanSpeed = before.fanSpeed ?: BABY_FAN_LEVEL,
            )
            vehicle.applyClimate(restore)
        }
        beforeBabyCare = null
        val now = vehicle.readClimate()
        _state.update {
            it.copy(
                babyCare = false,
                currentTemperature = now.temperature ?: it.currentTemperature,
                climateMessage = if (before == null) "베이비케어를 껐어요" else "베이비케어를 끄고 이전 공조 설정으로 되돌렸어요",
            )
        }
    }

    /** 화면에 들어올 때 공조 설정 온도를 새로 읽는다. */
    fun refreshClimate() {
        scope.launch {
            val now = vehicle.readClimate()
            _state.update { it.copy(currentTemperature = now.temperature ?: it.currentTemperature) }
        }
    }

    // 아이 선택 (C07)

    /**
     * 케어 대상 아이를 바꾼다 (null = 첫째). 모드 값은 그 아이 것으로 바뀌고(QO5), 마지막 수유를 서버에서 받아 온다.
     * 지금 켜 둔 모드(수유 정차 · 수면 · 베이비케어)가 공조를 쓰고 있으면 새 아이의 같은 모드 값을 바로 공조에 넣는다.
     */
    fun selectChild(childId: String?) {
        val changed = childId != _state.value.selectedChildId
        feedingStore.select(childId)
        _state.update { it.copy(selectedChildId = childId, feeding = feedingStore.load()) }
        serverSync.onChildSelected()
        if (changed) reapplyActiveMode()
    }

    private fun reapplyActiveMode() {
        val st = _state.value
        val slot = activeModeSlot(
            babyCare = st.babyCare,
            sleepClimate = st.sleepMode && beforeSleep != null,
            feedingStopClimate = st.feedingStop?.climateApplied == true,
        ) ?: return
        val preset = serverSync.presets.value.firstOrNull { it.slot == slot } ?: return
        scope.launch {
            val applied = vehicle.applyClimate(preset.settings).applied.isNotEmpty()
            if (!applied) return@launch
            if (preset.pending && preset.id != null) serverSync.onPresetApplied(preset.slot, preset.id)
            _state.update {
                it.copy(
                    targetTemperature = preset.settings.temperature,
                    notice = "${modeNoticeName(slot)}를 이 아이 값으로 바꿨어요 · ${modeLine(preset.settings)}",
                    noticeAt = System.currentTimeMillis(),
                )
            }
        }
    }

    /** [childId] 아이의 수유 루틴 (드롭다운의 다음 수유 표시). */
    fun routineOf(childId: String?): FeedingRoutine = feedingStore.loadFor(childId)

    // 수유 정차 모드 (C03)

    /**
     * 수유 정차 모드를 켠다: 정차(P단) 중에만. 서버 프리셋 `feeding_stop`(없으면 24°C·송풍)을 공조에 넣고 "수유 중" 타이머를 시작한다.
     * 켜면 true. 주행 중이면 false.
     */
    fun startFeedingStop(): Boolean {
        if (!vehicle.state.value.isParked) return false
        if (_state.value.feedingStop != null || feedingStopBusy) return true
        feedingStopBusy = true
        val startedAt = System.currentTimeMillis()
        prefs.edit().putLong(KEY_FEED_STOP_AT, startedAt).putBoolean(KEY_FEED_STOP_CLIMATE, false).apply()
        _state.update { it.copy(feedingStop = FeedingStopSession(startedAt), askFeedingStop = false) }
        scope.launch {
            try {
                val before = vehicle.readClimate()
                val settings = serverSync.presets.value.firstOrNull { it.slot == "feeding_stop" }?.settings ?: FEEDING_STOP_DEFAULT
                val applied = before.supported && vehicle.applyClimate(settings).applied.isNotEmpty()
                if (applied) beforeFeedingStop = before
                prefs.edit().putBoolean(KEY_FEED_STOP_CLIMATE, applied).apply()
                val after = if (applied) vehicle.readClimate().temperature else null
                _state.update { st ->
                    st.copy(
                        feedingStop = st.feedingStop?.copy(climateApplied = applied),
                        targetTemperature = if (applied) settings.temperature else st.targetTemperature,
                        currentTemperature = after ?: st.currentTemperature,
                        notice = if (applied) "수유 정차 모드를 켰어요 · ${modeLine(settings)}" else "수유 시간을 셀게요 · 이 차에서는 공조를 바꾸지 못했어요",
                        noticeAt = System.currentTimeMillis(),
                    )
                }
            } finally {
                feedingStopBusy = false
            }
        }
        return true
    }

    /**
     * 수유 정차 모드를 끝낸다. [auto]는 출발로 끝난 경우. 시작 시각을 마지막 수유로 기록하고(짧은 자동 종료는 제외),
     * 공조는 베이비케어가 켜져 있으면 베이비케어 값으로, 아니면 켜기 전 설정으로 되돌린다.
     */
    fun finishFeedingStop(auto: Boolean = false) {
        val session = _state.value.feedingStop ?: return
        val now = System.currentTimeMillis()
        val record = shouldRecordFeeding(session, now, auto)
        prefs.edit().remove(KEY_FEED_STOP_AT).remove(KEY_FEED_STOP_CLIMATE).apply()
        if (record) {
            feedingStore.save(feedingStore.load().copy(lastFedAt = session.startedAt))
            serverSync.onFed(session.startedAt)
        }
        _state.update { it.copy(feedingStop = null, notice = feedingStopEndMessage(session, record, auto), noticeAt = now) }
        if (!session.climateApplied) return
        val before = beforeFeedingStop
        beforeFeedingStop = null
        scope.launch {
            if (_state.value.babyCare) {
                val boarding = serverSync.presets.value.firstOrNull { it.slot == "boarding" }?.settings
                vehicle.applyClimate(boarding ?: ClimateSettings(name = "베이비케어", power = true, temperature = BABY_TARGET_TEMPERATURE, fanSpeed = BABY_FAN_LEVEL))
                _state.update { it.copy(targetTemperature = boarding?.temperature ?: BABY_TARGET_TEMPERATURE) }
            } else if (before?.power != null) {
                vehicle.applyClimate(
                    ClimateSettings(name = "복원", power = before.power, temperature = before.temperature ?: BABY_TARGET_TEMPERATURE, fanSpeed = before.fanSpeed ?: BABY_FAN_LEVEL),
                )
            }
        }
    }

    /** 도착 질문에 "괜찮아요". */
    fun dismissFeedingStopAsk() = _state.update { it.copy(askFeedingStop = false) }

    private fun modeLine(c: ClimateSettings) = "${if (c.temperature % 1f == 0f) c.temperature.toInt().toString() else c.temperature.toString()}°C · ${if (c.fanSpeed <= 1) "송풍" else "풍량 ${c.fanSpeed}단"}"

    private fun knownLocation(): LatLng? {
        val loc = location.state.value
        if (loc.latitude != null && loc.longitude != null) return LatLng(loc.latitude, loc.longitude)
        val n = navi.state.value
        if (n.currentLatitude != null && n.currentLongitude != null) return LatLng(n.currentLatitude, n.currentLongitude)
        return null
    }

    // 수면 모드

    /**
     * 수면 모드: 앱 안내 음성을 줄이고(낮잠 프리셋 음량, 기본 10%), 알림을 소리 없는 채널로 바꾸고, 도착 5분 전에 조용히 알린다.
     * 차량 공조가 켜져 있으면 바람을 가장 약하게 낮춘다. 차량 음량·화면 테마는 SDK에 제어 수단이 없어
     * 앱 안에서만 처리한다 (docs/research/pleos-care-control.md).
     */
    fun setSleepMode(on: Boolean) {
        val wasOn = _state.value.sleepMode
        val startedAt = if (on) (if (wasOn) _state.value.sleepStartedAt else System.currentTimeMillis()) else null
        prefs.edit().putBoolean(KEY_SLEEP, on).putLong(KEY_SLEEP_AT, startedAt ?: 0L).apply()
        notifier.quiet = on
        ToneState.sleep = on
        arrivingSoonNotified = false
        _state.update { it.copy(sleepMode = on, sleepStartedAt = startedAt) }
        if (on == wasOn) return
        scope.launch {
            if (on) {
                // 낮잠 프리셋("수면 모드와 함께")을 공조에 넣는다. 공조를 읽지 못하면 앱 안(음성·알림·다크)만.
                val before = vehicle.readClimate()
                if (!before.supported) return@launch
                val nap = serverSync.presets.value.firstOrNull { it.slot == "nap" }?.settings
                val applied = if (nap != null) vehicle.applyClimate(nap).applied.isNotEmpty() else before.power == true && vehicle.setFanSpeed(SLEEP_FAN_LEVEL)
                if (applied) beforeSleep = before
                _state.update {
                    it.copy(
                        targetTemperature = if (applied && nap != null) nap.temperature else it.targetTemperature,
                        climateMessage = if (applied) "수면 모드: 공조를 낮잠 설정으로 맞췄어요" else it.climateMessage,
                    )
                }
            } else {
                // 끄면 베이비케어가 켜져 있으면 베이비케어 값, 아니면 켜기 전 공조로 되돌린다 (수유 정차 중이면 그대로 둔다).
                if (_state.value.feedingStop == null) {
                    val boardingTemp = serverSync.presets.value.firstOrNull { it.slot == "boarding" }?.settings?.temperature
                    _state.update { it.copy(targetTemperature = boardingTemp ?: BABY_TARGET_TEMPERATURE) }
                }
                val before = beforeSleep ?: return@launch
                beforeSleep = null
                if (_state.value.feedingStop != null) return@launch
                if (_state.value.babyCare) {
                    val boarding = serverSync.presets.value.firstOrNull { it.slot == "boarding" }?.settings
                    vehicle.applyClimate(boarding ?: ClimateSettings(name = "베이비케어", power = true, temperature = BABY_TARGET_TEMPERATURE, fanSpeed = BABY_FAN_LEVEL))
                    _state.update { it.copy(targetTemperature = boarding?.temperature ?: BABY_TARGET_TEMPERATURE) }
                } else if (before.power != null) {
                    vehicle.applyClimate(
                        ClimateSettings(name = "복원", power = before.power, temperature = before.temperature ?: BABY_TARGET_TEMPERATURE, fanSpeed = before.fanSpeed ?: BABY_FAN_LEVEL),
                    )
                }
            }
        }
    }

    /** 앱 안내 음성의 크기 (0~1). 수면 모드에서는 낮잠 프리셋 음량(없으면 10%) (Q09). */
    val voiceVolume: Float
        get() = if (_state.value.sleepMode) sleepVoiceVolume(napVolume()) else 1f

    /** 낮잠 프리셋의 미디어 음량(%). */
    fun napVolume(): Int? = serverSync.presets.value.firstOrNull { it.slot == "nap" }?.settings?.mediaVolume

    // 급한 케어 찾기

    /** 지금 위치와 가는 곳을 기준으로 [kind] 후보를 찾아 상태에 넣는다. */
    suspend fun findNearby(kind: CareKind, limit: Int = 2): List<CarePlace>? {
        _state.update { it.copy(nearby = it.nearby + (kind to Nearby.Loading)) }
        val here = currentLocation()
        val to = guide.state.value.current?.place
        return try {
            val res = api.careNearby(kind.api, here.latitude, here.longitude, to?.latitude, to?.longitude, limit)
            _state.update { it.copy(nearby = it.nearby + (kind to Nearby.Found(res.places))) }
            refreshFrequent()
            res.places
        } catch (e: Exception) {
            Log.w(TAG, "careNearby $kind failed", e)
            _state.update { it.copy(nearby = it.nearby + (kind to Nearby.Failed("서버에서 ${kind.label}을 찾지 못했어요"))) }
            null
        }
    }

    /** 방문 기록과 지금 위치, 찾아 둔 케어 후보로 '자주 가는 케어 장소'를 다시 고른다. */
    fun refreshFrequent() {
        val nearby = _state.value.nearby.values.flatMap { (it as? Nearby.Found)?.places.orEmpty() }
        val frequent = FrequentCarePlaces.from(visits.all(), currentLocation(), nearby)
        _state.update { if (it.frequent == frequent) it else it.copy(frequent = frequent) }
    }

    /**
     * 찾은 곳을 경유지로 넣는다. 안내 중이 아니면 그곳을 목적지로 길안내를 시작한다.
     * [showNavigation]이 false면 케어 화면에 머물러 C12 토스트와 "다음 경유지" 슬롯을 보인다 (시트·슬롯에서 넣을 때).
     */
    fun addWaypoint(place: CarePlace, showNavigation: Boolean = true): String {
        val asWaypoint = navi.addWaypointOrRoute(Place(place.name, place.latitude, place.longitude))
        val message = if (asWaypoint) "${place.name}을(를) 경유지로 넣었어요" else "안내 중인 경로가 없어 ${place.name}을(를) 목적지로 안내해요"
        waypointRouting = navi.state.value.routing
        _state.update {
            it.copy(
                waypointMessage = message,
                urgent = null,
                addedWaypoint = AddedWaypoint(place, asWaypoint, System.currentTimeMillis()),
            )
        }
        if (showNavigation) navi.showNavigation()
        return message
    }

    /** C12 "되돌리기"·"경유지 빼기": 넣은 경유지를 뺀다 (목적지로 시작했으면 안내를 멈춘다). */
    fun undoWaypoint() {
        val added = _state.value.addedWaypoint ?: return
        if (added.asWaypoint) navi.removeWaypoints() else navi.cancelRoute()
        clearAddedWaypoint()
    }

    /** C12 "안내 계속": 내비 화면을 앞으로. */
    fun continueGuidance() = navi.showNavigation()

    private fun clearAddedWaypoint() {
        waypointRouting = false
        waypointEndJob?.cancel()
        waypointEndJob = null
        _state.update { it.copy(addedWaypoint = null, waypointMessage = null) }
    }

    // 급한 케어 시트 (C09~C11)

    /**
     * 급한 케어 시트를 열고 [kind] 후보를 [URGENT_LIMIT]곳 찾는다. 수유실·기저귀 교환대가 없으면 정차 공간을 대신 찾고(C10),
     * 서버에 닿지 않으면 자주 가는 케어 장소를 대신 보인다.
     */
    fun openUrgent(kind: CareKind): Job {
        _state.update { it.copy(urgent = UrgentSheet(kind)) }
        return scope.launch {
            val routeBased = guide.state.value.current?.place != null
            val found = findNearby(kind, URGENT_LIMIT)
            val fallback = if (found.isNullOrEmpty() && kind.fallsBackToParking()) findNearby(CareKind.REST, URGENT_LIMIT) else null
            val offline = _state.value.frequent.map { it.toCarePlace() }
            val sheet = urgentSheetOf(kind, found, fallback, offline, routeBased)
            // 그사이 닫았거나 다른 종류를 열었으면 덮어쓰지 않는다.
            _state.update { if (it.urgent?.kind == kind) it.copy(urgent = sheet) else it }
        }
    }

    fun closeUrgent() = _state.update { it.copy(urgent = null) }

    fun clearWaypointMessage() = _state.update { it.copy(waypointMessage = null) }

    private fun currentLocation(): LatLng {
        val loc = location.state.value
        if (loc.latitude != null && loc.longitude != null) return LatLng(loc.latitude, loc.longitude)
        val n = navi.state.value
        if (n.currentLatitude != null && n.currentLongitude != null) return LatLng(n.currentLatitude, n.currentLongitude)
        return DEFAULT_LOCATION
    }

    private companion object {
        const val KEY_SLEEP = "sleep_mode"
        const val KEY_SLEEP_AT = "sleep_started_at"
        const val KEY_FEED_STOP_AT = "feeding_stop_started_at"
        const val KEY_FEED_STOP_CLIMATE = "feeding_stop_climate"
        const val ROUTE_END_GRACE_MS = 15_000L
    }
}
