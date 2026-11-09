package io.github.haeyeonyou.familytrip.data

import android.content.Context
import java.time.YearMonth

/** 설정(HF05) '선호 조건' 토글 4개. */
enum class Preference(val key: String) {
    /** 수유실 있는 곳만 → 코스 추천 조건 `needs`의 nursing_room */
    NURSING_ROOM("nursing_room_only"),

    /** 유모차 진입 가능 → `needs`의 stroller */
    STROLLER("stroller_ok"),

    /** 비·눈 오면 실내 우선 → 날씨가 비·눈이면 홈 추천의 실내 칩을 자동으로 켠다 */
    INDOOR_ON_RAIN("indoor_on_rain"),

    /** 반려견 동반 가능만 → `with_pet` */
    PET("pet_only"),
}

/**
 * 가족이 한 번 정해 두는 코스 추천 기본 조건.
 * 홈 필터 칩의 시작 상태이고, 문장으로 요청하는 코스(조건 바꾸기)에도 더해진다.
 * 칩은 그날만 바꾸는 것이고, 여기 값은 다음에도 그대로다.
 */
data class CarePreferences(
    val nursingRoomOnly: Boolean = false,
    val strollerOk: Boolean = false,
    val indoorOnRain: Boolean = true,
    val petOnly: Boolean = false,
) {
    fun isOn(p: Preference): Boolean = when (p) {
        Preference.NURSING_ROOM -> nursingRoomOnly
        Preference.STROLLER -> strollerOk
        Preference.INDOOR_ON_RAIN -> indoorOnRain
        Preference.PET -> petOnly
    }

    fun with(p: Preference, on: Boolean): CarePreferences = when (p) {
        Preference.NURSING_ROOM -> copy(nursingRoomOnly = on)
        Preference.STROLLER -> copy(strollerOk = on)
        Preference.INDOOR_ON_RAIN -> copy(indoorOnRain = on)
        Preference.PET -> copy(petOnly = on)
    }

    /**
     * 코스 요청 조건에 선호 조건을 더한다 (이미 있는 조건은 그대로 두고 빠진 것만 채운다).
     * 비·눈 실내 우선은 날씨를 본 뒤 홈에서 정하므로 여기서는 다루지 않는다.
     */
    fun applyTo(conditions: TripConditions): TripConditions = conditions.copy(
        needs = (conditions.needs + needs()).distinct(),
        withPet = conditions.withPet || petOnly,
    )

    /** 선호 조건이 만드는 육아 편의 필수 조건 (서버 `TripConditions.needs`) */
    fun needs(): List<String> = buildList {
        if (nursingRoomOnly) add("nursing_room")
        if (strollerOk) add("stroller")
    }

    companion object {
        /**
         * 아직 토글을 만진 적이 없을 때의 기본값. 예전 홈 칩 기본 규칙과 같다:
         * 36개월 미만 아이가 있으면 수유실·유모차, 반려견이 함께면 반려견, 비·눈이면 실내.
         */
        fun defaultsFor(profile: FamilyProfile?, now: YearMonth = YearMonth.now()): CarePreferences {
            val infant = profile?.ageMonths(now)?.minOrNull()?.let { it < 36 } == true
            return CarePreferences(nursingRoomOnly = infant, strollerOk = infant, indoorOnRain = true, petOnly = profile?.petAlong == true)
        }

        /** 사용자가 정한 값([stored])이 있으면 그것을, 없으면 가족 정보에서 정한 기본값을 쓴다. */
        fun resolve(stored: Map<Preference, Boolean>, profile: FamilyProfile?, now: YearMonth = YearMonth.now()): CarePreferences =
            Preference.entries.fold(defaultsFor(profile, now)) { acc, p -> stored[p]?.let { acc.with(p, it) } ?: acc }
    }
}

/** 낮잠 시간 한 구간 (하루 중 분 단위, 0~1439). 아이가 낮잠 자는 대표 시간대. */
data class NapWindow(val startMinutes: Int = 13 * 60, val endMinutes: Int = 15 * 60) {
    /** "13:00 – 15:00" */
    fun label(): String = "${clock(startMinutes)} – ${clock(endMinutes)}"

    /** 시작·끝을 [step]분 단위로 옮긴다. 하루를 넘지 않고 최소 [MIN_LENGTH]분은 남긴다. */
    fun shiftStart(delta: Int): NapWindow =
        copy(startMinutes = (startMinutes + delta).coerceIn(0, endMinutes - MIN_LENGTH))

    fun shiftEnd(delta: Int): NapWindow =
        copy(endMinutes = (endMinutes + delta).coerceIn(startMinutes + MIN_LENGTH, MAX_MINUTE))

    companion object {
        const val STEP = 30
        const val MIN_LENGTH = 30
        const val MAX_MINUTE = 24 * 60 - 1
        private fun clock(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)
    }
}

/** 수유 간격 −/+ 단계. 서버 `feed_interval_minutes` 허용 범위(60~360분)와 같다. */
object IntervalStepper {
    const val STEP = 30
    const val MIN = 60
    const val MAX = 360

    fun step(current: Int, direction: Int): Int = (current + direction * STEP).coerceIn(MIN, MAX)
}

/**
 * 설정 화면 값을 기기에 저장한다 (SharedPreferences "settings"). 서버로 보내지 않는다.
 * - 선호 조건 4개: 만진 항목만 저장하고, 안 만진 항목은 가족 정보에서 정한 기본값을 따른다.
 * - 낮잠 시간: 앱에 따로 있던 값이 없어 여기에 둔다. (수유 간격은 [FeedingStore]의 값을 그대로 쓴다.)
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun storedPreferences(): Map<Preference, Boolean> =
        Preference.entries.filter { prefs.contains(it.key) }.associateWith { prefs.getBoolean(it.key, false) }

    fun setPreference(p: Preference, on: Boolean) = prefs.edit().putBoolean(p.key, on).apply()

    fun loadNap(): NapWindow = NapWindow(
        startMinutes = prefs.getInt(KEY_NAP_START, NapWindow().startMinutes),
        endMinutes = prefs.getInt(KEY_NAP_END, NapWindow().endMinutes),
    )

    fun saveNap(nap: NapWindow) = prefs.edit().putInt(KEY_NAP_START, nap.startMinutes).putInt(KEY_NAP_END, nap.endMinutes).apply()

    /** 데이터 전체 삭제: 저장한 선호 조건과 낮잠 시간을 지운다. */
    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_NAP_START = "nap_start"
        const val KEY_NAP_END = "nap_end"
    }
}
