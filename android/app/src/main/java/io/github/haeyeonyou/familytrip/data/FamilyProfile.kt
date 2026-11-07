package io.github.haeyeonyou.familytrip.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.YearMonth

/** 아이 한 명. 생년월만 저장하고 나이는 그때그때 계산한다. */
@Serializable
data class Child(val birthYear: Int, val birthMonth: Int, val name: String = "") {
    fun ageMonths(now: YearMonth = YearMonth.now()): Int =
        ((now.year - birthYear) * 12 + (now.monthValue - birthMonth)).coerceAtLeast(0)
}

/**
 * 한 번 등록해 두는 가족 정보 (지수 덱 개인정보 원칙, 혜연 P09).
 * 생년월은 차 안에만 두고, 서버로는 계산한 나이(개월 수 포함)만 보낸다 (#47 결정).
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

/** 가족 정보를 앱 내부 파일에만 저장한다. */
class ProfileStore(context: Context) {
    private val file = File(context.filesDir, "family_profile.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): FamilyProfile? =
        runCatching { json.decodeFromString<FamilyProfile>(file.readText()) }.getOrNull()

    fun save(profile: FamilyProfile) = file.writeText(json.encodeToString(profile))
}
