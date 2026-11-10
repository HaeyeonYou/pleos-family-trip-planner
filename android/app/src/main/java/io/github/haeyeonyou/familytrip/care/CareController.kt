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
    val feeding: FeedingRoutine = FeedingRoutine(),
    val nearby: Map<CareKind, Nearby> = emptyMap(),
    /** 경유지 추가 결과 문구 */
    val waypointMessage: String? = null,
    /** 자주 가는 케어 장소 (방문 기록에서). 없으면 케어 화면이 그 영역을 그리지 않는다. */
    val frequent: List<FrequentCarePlace> = emptyList(),
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
            feeding = feedingStore.load(),
        ),
    )
    val state: StateFlow<CareState> = _state.asStateFlow()

    /** 베이비케어를 켜기 전 공조 설정. 끌 때 되돌리는 데 쓴다. */
    private var beforeBabyCare: ClimateReading? = null
    private var arrivingSoonNotified = false

    init {
        notifier.quiet = _state.value.sleepMode
        ToneState.sleep = _state.value.sleepMode
        // 수유 루틴은 저장소가 하나뿐이다: 홈·음성·서버에서 받은 변경도 같은 값을 본다.
        scope.launch { feedingStore.routine.collect { f -> _state.update { it.copy(feeding = f) } } }
        // 방문 기록이 바뀌면(도착·서버에서 받은 변경) 자주 가는 케어 장소를 다시 고른다.
        scope.launch { visits.revision.collect { refreshFrequent() } }
        // 수면 모드 중 도착 5분 전에 한 번만 조용히 알린다.
        scope.launch {
            navi.state.collect { n ->
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
        feedingStore.save(feedingStore.load().copy(lastFedAt = at))
        serverSync.onFed(at)
    }

    /** 수유 간격을 바꾼다. 로그인했다면 첫째 아이의 수유 간격으로 서버에도 올린다. */
    fun setFeedingInterval(minutes: Int) {
        feedingStore.save(feedingStore.load().copy(intervalMinutes = minutes))
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

    // 수면 모드

    /**
     * 수면 모드: 앱 안내 음성을 줄이고(음성 30%), 알림을 소리 없는 채널로 바꾸고, 도착 5분 전에 조용히 알린다.
     * 차량 공조가 켜져 있으면 바람을 가장 약하게 낮춘다. 차량 음량·화면 테마는 SDK에 제어 수단이 없어
     * 앱 안에서만 처리한다 (docs/research/pleos-care-control.md).
     */
    fun setSleepMode(on: Boolean) {
        prefs.edit().putBoolean(KEY_SLEEP, on).apply()
        notifier.quiet = on
        ToneState.sleep = on
        arrivingSoonNotified = false
        _state.update { it.copy(sleepMode = on) }
        if (on) {
            scope.launch {
                val reading = vehicle.readClimate()
                if (reading.supported && reading.power == true) {
                    val ok = vehicle.setFanSpeed(SLEEP_FAN_LEVEL)
                    _state.update { it.copy(climateMessage = if (ok) "수면 모드: 공조 바람을 약하게 낮췄어요" else it.climateMessage) }
                }
            }
        }
    }

    /** 앱 안내 음성의 크기 (0~1). 수면 모드에서는 30%. */
    val voiceVolume: Float get() = if (_state.value.sleepMode) SLEEP_VOICE_VOLUME else 1f

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

    /** 찾은 곳을 경유지로 넣는다. 안내 중이 아니면 그곳을 목적지로 길안내를 시작한다. */
    fun addWaypoint(place: CarePlace): String {
        val asWaypoint = navi.addWaypointOrRoute(Place(place.name, place.latitude, place.longitude))
        val message = if (asWaypoint) "${place.name}을(를) 경유지로 넣었어요" else "안내 중인 경로가 없어 ${place.name}을(를) 목적지로 안내해요"
        _state.update { it.copy(waypointMessage = message) }
        navi.showNavigation()
        return message
    }

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
        const val SLEEP_VOICE_VOLUME = 0.3f
    }
}
