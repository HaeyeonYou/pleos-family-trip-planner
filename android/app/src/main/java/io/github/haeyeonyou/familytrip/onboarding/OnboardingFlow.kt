package io.github.haeyeonyou.familytrip.onboarding

import io.github.haeyeonyou.familytrip.data.Child
import io.github.haeyeonyou.familytrip.data.FamilyProfile
import io.github.haeyeonyou.familytrip.data.NapWindow
import java.time.LocalDate
import java.time.YearMonth

/**
 * 차 앱 온보딩 O01~O06 (11/10 디자인, 이슈 #184). 화면 없이 경로·단계 번호·저장값만 정하는 순수 로직이라 단위 테스트한다.
 * 결정 근거는 `docs/agent-tasks/1110-onboarding-o01.md` 7절 (QO1~QO9, 2026-11-11 사용자).
 */
enum class OnboardingStep { START, QR, PERMISSION, CHILD, CONFIRM, READY }

/** 온보딩을 어디서 열었나. 설정 > 아이 정보에서 열면 아이 단계(O04·O05)만 돌고 설정으로 돌아간다 (QO7). */
enum class OnboardingMode { FIRST_RUN, EDIT_CHILDREN }

object OnboardingFlow {
    /**
     * 지금 갈 경로 (QO1): `O01 → (QR이면 O02) → O03 권한 → O04 → O05 → O06`.
     * QR로 아이를 불러왔으면 O03은 거치고 O04는 건너뛴다. 아이 정보 편집은 O04(아이가 없을 때)·O05만.
     */
    fun path(mode: OnboardingMode, viaQr: Boolean, hasChildren: Boolean): List<OnboardingStep> = when (mode) {
        OnboardingMode.EDIT_CHILDREN -> if (hasChildren) listOf(OnboardingStep.CONFIRM) else listOf(OnboardingStep.CHILD, OnboardingStep.CONFIRM)
        OnboardingMode.FIRST_RUN -> buildList {
            add(OnboardingStep.START)
            if (viaQr) add(OnboardingStep.QR)
            add(OnboardingStep.PERMISSION)
            if (!(viaQr && hasChildren)) add(OnboardingStep.CHILD)
            add(OnboardingStep.CONFIRM)
            add(OnboardingStep.READY)
        }
    }

    /** Topbar의 `N / M`. 시작(O01)·완료(O06)는 세지 않고, 경로마다 다시 센다. 세지 않는 화면은 null. */
    fun stepLabel(step: OnboardingStep, path: List<OnboardingStep>): String? {
        val counted = path.filter { it != OnboardingStep.START && it != OnboardingStep.READY }
        val at = counted.indexOf(step)
        if (at < 0 || counted.size < 2) return null
        return "${at + 1} / ${counted.size}"
    }

    fun next(step: OnboardingStep, path: List<OnboardingStep>): OnboardingStep? = path.getOrNull(path.indexOf(step) + 1)

    fun previous(step: OnboardingStep, path: List<OnboardingStep>): OnboardingStep? = path.getOrNull(path.indexOf(step) - 1)

    /**
     * 앱을 켤 때 처음 보여 줄 화면: 안내 중이면 안내 > 온보딩을 마치지 않았으면 온보딩 > 홈.
     * 온보딩이 생기기 전에 이미 쓰던 차(계정이나 아이 정보가 있음)는 마친 것으로 본다.
     */
    fun startsWithOnboarding(guideActive: Boolean, done: Boolean, loggedIn: Boolean, profile: FamilyProfile?): Boolean =
        !guideActive && !done && !loggedIn && profile?.children.isNullOrEmpty()
}

/** 개월 수 표시 (O04 미리보기·O05 카드): 36개월 미만은 "생후 N개월", 그 이상은 "N개월" (Figma 서아 생후 14개월 · 도윤 38개월). */
fun monthsLabel(months: Int): String = if (months < 36) "생후 ${months}개월" else "${months}개월"

/** "2025.9.3" (일을 모르면 "2025.9") */
fun birthLabel(child: Child): String = child.birthDay?.let { "${child.birthYear}.${child.birthMonth}.$it" } ?: "${child.birthYear}.${child.birthMonth}"

/** O04 '낮잠 시간' 칩. [napTimes]는 서버 `nap_times` 글, [window]는 설정의 낮잠 시간(수면 모드 제안). */
enum class NapChoice(val label: String, val napTimes: String, val window: NapWindow?) {
    MORNING("오전 10시쯤", "오전 10시쯤", NapWindow(10 * 60, 11 * 60 + 30)),
    AFTERNOON("오후 1시쯤", "오후 1시쯤", NapWindow(13 * 60, 15 * 60)),
    IRREGULAR("일정하지 않아요", "일정하지 않음", null),
    ;

    companion object {
        fun of(napTimes: String?): NapChoice? = entries.firstOrNull { it.napTimes == napTimes }
    }
}

/** O04 '함께 챙길 것' 칩 (QO4): 유모차 → 아이 값, 카시트 뒤보기 → 아이 값(서버 rear_facing_seat), 반려견 → 가족 값. */
enum class TagAlong(val label: String) { STROLLER("유모차"), REAR_FACING_SEAT("카시트 뒤보기"), PET("반려견") }

/**
 * O04에서 입력 중인 아이. 수유 간격·수유 방식은 받지 않는다 (QO3: 수유 기록 기능 삭제 예정).
 * [editing]은 고치는 중인 원래 아이 (새 아이면 null) — 화면에 없는 값(서버 id 등)을 지키는 데 쓴다.
 */
data class ChildDraft(
    val name: String = "",
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
    val nap: NapChoice? = null,
    val tags: Set<TagAlong> = emptySet(),
    val editing: Child? = null,
) {
    val birthDate: LocalDate?
        get() {
            val y = year ?: return null
            val m = month ?: return null
            val d = day ?: return null
            return runCatching { LocalDate.of(y, m, d) }.getOrNull()
        }

    /** 이름(1~10자)과 오늘까지의 생년월일이 있어야 다음으로 간다 (미래 날짜 = 출산 예정일 모드는 범위 밖, QO2). */
    fun isComplete(today: LocalDate): Boolean = name.isNotBlank() && birthDate?.let { !it.isAfter(today) } == true

    fun ageMonths(today: LocalDate): Int? = birthDate?.let { Child(it.year, it.monthValue, birthDay = it.dayOfMonth).ageMonthsOn(today) }

    /** 저장할 아이. 유모차·카시트는 고르면 true, 안 골랐으면 원래 값이 있을 때만 false(지운 것), 없으면 모름(null). */
    fun toChild(): Child {
        val born = requireNotNull(birthDate) { "생년월일이 없어요" }
        val base = editing
        fun flag(tag: TagAlong, old: Boolean?): Boolean? = if (tag in tags) true else if (old != null) false else null
        return (base ?: Child(born.year, born.monthValue)).copy(
            birthYear = born.year,
            birthMonth = born.monthValue,
            birthDay = born.dayOfMonth,
            name = name.trim().take(NAME_MAX),
            stroller = flag(TagAlong.STROLLER, base?.stroller),
            rearFacingSeat = flag(TagAlong.REAR_FACING_SEAT, base?.rearFacingSeat),
            napTimes = nap?.napTimes ?: base?.napTimes,
        )
    }

    companion object {
        const val NAME_MAX = 10

        fun from(child: Child, petAlong: Boolean): ChildDraft = ChildDraft(
            name = child.name,
            year = child.birthYear,
            month = child.birthMonth,
            day = child.birthDay,
            nap = NapChoice.of(child.napTimes),
            tags = buildSet {
                if (child.stroller == true) add(TagAlong.STROLLER)
                if (child.rearFacingSeat == true) add(TagAlong.REAR_FACING_SEAT)
                if (petAlong) add(TagAlong.PET)
            },
            editing = child,
        )
    }
}

/** 고친 아이를 프로필에 넣는다 (새 아이는 뒤에 붙인다). 반려견은 가족 값이다. */
fun FamilyProfile?.withChild(draft: ChildDraft): FamilyProfile {
    val base = this ?: FamilyProfile()
    val child = draft.toChild()
    val at = draft.editing?.let { e -> base.children.indexOfFirst { it == e } } ?: -1
    val children = if (at >= 0) base.children.toMutableList().also { it[at] = child } else base.children + child
    return base.copy(children = children, petAlong = TagAlong.PET in draft.tags)
}

/** 생년월일 휠 (O04-1)의 고를 수 있는 값. 미래 날짜는 고를 수 없다 (QO2). */
object BirthWheel {
    const val YEARS_BACK = 12

    fun years(today: LocalDate): List<Int> = ((today.year - YEARS_BACK)..today.year).toList()

    fun months(year: Int, today: LocalDate): List<Int> = (1..if (year == today.year) today.monthValue else 12).toList()

    fun days(year: Int, month: Int, today: LocalDate): List<Int> {
        val last = YearMonth.of(year, month).lengthOfMonth()
        return (1..if (year == today.year && month == today.monthValue) today.dayOfMonth else last).toList()
    }

    /** 년·월을 바꿨을 때 고를 수 없게 된 값을 가장 가까운 값으로 당긴다. */
    fun clamp(year: Int, month: Int, day: Int, today: LocalDate): Triple<Int, Int, Int> {
        val y = year.coerceIn(today.year - YEARS_BACK, today.year)
        val m = month.coerceIn(1, months(y, today).last())
        val d = day.coerceIn(1, days(y, m, today).last())
        return Triple(y, m, d)
    }
}

/** O02 '로그인하면 함께 가져와요'에 보여 줄 불러온 값 (수유 기록 줄은 QO3로 뺐다). */
data class ImportedSummary(val children: String?, val careModes: String?, val carePlaces: String?)

fun importedSummary(profile: FamilyProfile?, presetNames: List<String>, carePlaceNames: List<String>, today: LocalDate): ImportedSummary {
    val kids = profile?.children.orEmpty()
    val first = kids.firstOrNull()
    val children = first?.let {
        val who = "${it.name.ifBlank { "아이" }} · ${monthsLabel(it.ageMonthsOn(today))}"
        if (kids.size > 1) "$who 외 ${kids.size - 1}명" else who
    }
    val modes = presetNames.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    val places = carePlaceNames.firstOrNull()?.let { if (carePlaceNames.size > 1) "$it 외 ${carePlaceNames.size - 1}곳" else it }
    return ImportedSummary(children, modes, places)
}

/** O03 '차와 연결할 항목'. 필수 3개 + 선택 3개 (QO6: 캘린더는 기능이 없어 '준비 중'으로 비활성). */
enum class LinkItem(val title: String, val description: String, val required: Boolean) {
    VEHICLE("차량 정보", "연료 · 배터리 · 타이어로 외출 전 준비를 챙겨요", true),
    DOOR("문 · 정차 상태", "뒷문이 닫히면 탑승 준비, 정차 중에만 화면 조작", true),
    LOCATION("위치", "수유실 · 정차 공간 · 추천 코스를 찾아요", true),
    CLIMATE("공조 · 음량", "베이비케어 모드에서 온도 · 바람 · 음량을 맞춰요", false),
    VOICE("음성", "\"애 잔다\" \"수유할게\" 한마디로 조작해요", false),
    CALENDAR("캘린더 (선택)", "외출 일정을 읽고 미리 준비해요", false),
}

/** O03 선택 항목의 상태: 켬/끔, 이 차가 지원하지 않아 안내만, 아직 기능이 없어 준비 중. */
enum class LinkState { ON, OFF, GUIDE_ONLY, COMING_SOON }

fun linkState(item: LinkItem, choices: LinkChoices, climateSupported: Boolean?): LinkState = when (item) {
    LinkItem.CALENDAR -> LinkState.COMING_SOON
    LinkItem.CLIMATE -> if (climateSupported == false) LinkState.GUIDE_ONLY else if (choices.climate) LinkState.ON else LinkState.OFF
    LinkItem.VOICE -> if (choices.voice) LinkState.ON else LinkState.OFF
    else -> LinkState.ON
}

/** O03에서 고른 선택 항목 (차 로컬 저장). PNG 초기값: 공조·음량 켬, 음성 켬. */
data class LinkChoices(val climate: Boolean = true, val voice: Boolean = true)

/** O04 미리보기·O06 요약의 '낮잠 · 수면 모드' 한 줄. */
fun napPreview(nap: NapChoice?): String = when (nap) {
    null -> "낮잠 시간을 고르면 출발 전에 미리 제안해요"
    NapChoice.IRREGULAR -> "\"애 잔다\" 한마디로 바로 켜요"
    else -> "${nap.label} 출발이면 미리 제안"
}

/** O04 미리보기·O06 요약의 '추천 코스' 한 줄 ("유모차 가능 · 수유실 있는 곳 위주"). 월령을 모르면 수유실 조건은 넣지 않는다. */
fun coursePreview(tags: Set<TagAlong>, months: Int?): String {
    val parts = buildList {
        if (TagAlong.STROLLER in tags) add("유모차 가능")
        if (TagAlong.PET in tags) add("반려견 동반")
        if (months != null && months < 36) add("수유실 있는 곳")
    }
    return if (parts.isEmpty()) "월령에 맞는 곳 위주" else parts.joinToString(" · ") + " 위주"
}

/** O05 카드의 '함께 챙길 것' 한 줄. 반려견은 가족 값이라 아이 카드에는 넣지 않는다. */
fun tagAlongLabel(child: Child): String {
    val parts = buildList {
        if (child.stroller == true) add("유모차")
        if (child.rearFacingSeat == true) add("카시트 뒤보기")
    }
    return if (parts.isEmpty()) "함께 챙길 것 없음" else parts.joinToString(" · ")
}
