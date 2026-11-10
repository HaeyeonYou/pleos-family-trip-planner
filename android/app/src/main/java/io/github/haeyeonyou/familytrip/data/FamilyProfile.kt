package io.github.haeyeonyou.familytrip.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/**
 * 아이 한 명. 생년월(일)을 저장하고 나이는 그때그때 계산한다.
 * 온보딩 O04(#184)에서 받는 값([birthDay]·[stroller]·[rearFacingSeat]·[napTimes])은 서버 아이 정보와 같은 값이다.
 * null은 "아직 모름"이라 서버 값을 따른다 (예전 화면이 이 값을 빼고 저장해도 서버 값이 지워지지 않는다).
 */
@Serializable
data class Child(
    val birthYear: Int,
    val birthMonth: Int,
    val name: String = "",
    /** 서버(웹)에 등록된 이 아이의 id. 서버와 맞춘 아이에게만 있다. */
    val serverId: String? = null,
    /** 태어난 날 (1~31). 달만 아는 아이는 null (서버 `birth_date`) */
    val birthDay: Int? = null,
    /** 유모차를 쓰는지 (서버 `stroller_use`) */
    val stroller: Boolean? = null,
    /** 카시트 뒤보기 (서버 `rear_facing_seat`) */
    val rearFacingSeat: Boolean? = null,
    /** 평소 낮잠 시간대 글 (서버 `nap_times`, 예: "오후 1시쯤") */
    val napTimes: String? = null,
) {
    fun ageMonths(now: YearMonth = YearMonth.now()): Int =
        ((now.year - birthYear) * 12 + (now.monthValue - birthMonth)).coerceAtLeast(0)

    /** 날짜 기준 개월 수. 태어난 날을 알면 생일 전날까지는 한 달 덜 센다 (서버 `age_months`와 같은 규칙). */
    fun ageMonthsOn(today: LocalDate): Int {
        val months = (today.year - birthYear) * 12 + (today.monthValue - birthMonth)
        val day = birthDay
        return (if (day != null && today.dayOfMonth < day) months - 1 else months).coerceAtLeast(0)
    }

    /** 생년월일 날짜 (일을 모르면 null) */
    fun birthDate(): LocalDate? = birthDay?.let { runCatching { LocalDate.of(birthYear, birthMonth, it) }.getOrNull() }
}

/**
 * 한 번 등록해 두는 가족 정보 (지수 덱 개인정보 원칙, 혜연 P09).
 * 코스 요청에는 생년월이 아니라 계산한 나이(개월 수 포함)만 보낸다 (#47 결정). 로그인한 사용자의 아이 정보는
 * 서버 `Child.birth_month`와 같은 값이라 가족 정보 API로는 생년월을 맞춘다 (docs/research/web-server-contract.md 6절).
 */
@Serializable
data class FamilyProfile(
    val children: List<Child> = emptyList(),
    val adults: Int = 2,
    /** 나들이에 반려견도 보통 함께 가는지 */
    val petAlong: Boolean = false,
) {
    fun ageMonths(now: YearMonth = YearMonth.now()): List<Int> = children.map { it.ageMonths(now) }

    /** 메뉴 아이 카드에 보일 첫째 아이 이름 */
    val childName: String get() = children.firstOrNull()?.name.orEmpty()

    /**
     * 문장에 없는 정보를 프로필로 채운다. 문장에 아이 나이가 있으면 그날 사정(예: 조카와 외출)이므로 문장을 따른다.
     */
    fun applyTo(conditions: TripConditions, now: YearMonth = YearMonth.now()): TripConditions {
        val filled = conditions.copy(adults = conditions.adults ?: adults, withPet = conditions.withPet || petAlong)
        if (children.isEmpty() || conditions.childAges.isNotEmpty()) return filled
        val months = ageMonths(now)
        return filled.copy(childAges = months.map { it / 12 }, childAgeMonths = months)
    }
}

/** 개월 수를 사람이 읽는 나이로: 36개월 미만은 개월, 그 이상은 살. */
fun ageLabel(months: Int): String = when {
    months < 36 -> "${months}개월"
    else -> "${months / 12}살"
}

/**
 * 가족 정보를 앱 내부 파일에 저장한다. 로그인했다면 [FamilySync]가 아이 정보를 서버와 맞춘다.
 * 바뀔 때마다 [profile] 흐름이 알려, 서버에서 받은 변경도 화면에 바로 반영된다.
 */
class ProfileStore(private val file: File) : ProfileRepo {
    constructor(context: Context) : this(File(context.filesDir, "family_profile.json"))

    private val json = Json { ignoreUnknownKeys = true }
    private val _profile = MutableStateFlow(read())
    val profile: StateFlow<FamilyProfile?> = _profile.asStateFlow()

    private fun read(): FamilyProfile? =
        runCatching { json.decodeFromString<FamilyProfile>(file.readText()) }.getOrNull()

    override fun load(): FamilyProfile? = _profile.value

    @Synchronized
    override fun save(profile: FamilyProfile) {
        file.writeText(json.encodeToString(profile))
        _profile.value = profile
    }

    /** 데이터 전체 삭제: 저장한 가족 정보를 지운다 (그러면 '아직 등록하지 않음'으로 돌아간다). */
    @Synchronized
    fun clear() {
        file.delete()
        _profile.value = null
    }
}
