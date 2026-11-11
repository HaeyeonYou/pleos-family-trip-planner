package io.github.haeyeonyou.familytrip.grow

import io.github.haeyeonyou.familytrip.data.CoursePlace
import io.github.haeyeonyou.familytrip.data.SuggestResponse
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/*
 * 그로우 G01 "경험 젤리"(354:3033)·G03 월별 캘린더(387:8031)의 순수 계산 (#216). 화면과 분리해 단위 테스트한다.
 * 분류·단계 규칙은 [JellyModel]을 그대로 쓴다 (경험 1개 = 10분 이상 머문 방문, 1–3 방울·4–9 몽글·10+ 탄생).
 */

// ───────────────────────── 탄생 ─────────────────────────

/** 분류별 탄생일: 그 분류의 10번째 경험 날짜. 아직 10개가 안 됐으면 없다. */
fun birthDates(list: List<Experience>): Map<JellyKind, LocalDate> =
    list.groupBy { it.kind }.mapNotNull { (kind, xs) ->
        xs.sortedBy { it.visit.arrivedAt }.getOrNull(JellyStage.BORN.min - 1)?.let { kind to it.date }
    }.toMap()

/** 가장 최근 탄생 (G01 말풍선 "11.07 유니콘 탄생"). 같은 날이면 순서가 앞선 분류. */
fun latestBirth(list: List<Experience>): Pair<JellyKind, LocalDate>? =
    birthDates(list).entries.sortedWith(compareByDescending<Map.Entry<JellyKind, LocalDate>> { it.value }.thenBy { it.key.ordinal })
        .firstOrNull()?.toPair()

/** "11.07" */
fun dotDate(date: LocalDate): String = "${date.monthValue}.${date.dayOfMonth.toString().padStart(2, '0')}"

fun birthBubble(kind: JellyKind, date: LocalDate): String = "${dotDate(date)} ${kind.character} 탄생"

// ───────────────────────── G01 제목 ─────────────────────────

/** "37개의 경험 · 8개의 젤리가 자라는 중 · 한 분야 10개면 캐릭터가 태어나요". 자라는 중 = 경험이 있는 분야 수. */
fun jellySubtitle(experiences: Int, states: List<JellyState>): String {
    if (experiences == 0) return "나들이 장소에 10분 이상 머물면 첫 젤리가 생겨요 · 한 분야 10개면 캐릭터가 태어나요"
    return "${experiences}개의 경험 · ${states.count { it.count > 0 }}개의 젤리가 자라는 중 · 한 분야 10개면 캐릭터가 태어나요"
}

/** "곰 7/10 · 3번 더 가면 곰이 태어나요" / "유니콘 12번 · 캐릭터가 태어났어요" / "공룡 0/10 · 새 젤리를 깨워요" */
fun jellyProgress(state: JellyState): String {
    val k = state.kind
    return when (state.stage) {
        JellyStage.NONE -> "${k.character} 0/${JellyStage.BORN.min} · 새 젤리를 깨워요"
        JellyStage.BORN -> "${k.character} ${state.count}번 · 캐릭터가 태어났어요"
        else -> "${k.character} ${state.count}/${JellyStage.BORN.min} · ${JellyStage.BORN.min - state.count}번 더 가면 ${k.character}${subjectJosa(k.character)} 태어나요"
    }
}

/** 받침이 있으면 "이", 없으면 "가". */
internal fun subjectJosa(word: String): String {
    val c = word.lastOrNull() ?: return "가"
    if (c !in '가'..'힣') return "가"
    return if ((c - '가') % 28 != 0) "이" else "가"
}

// ───────────────────────── 경험젤리 만들러 갈까요? (QF1 ③) ─────────────────────────

enum class MakeTag(val label: String) { MANY("많이 감"), FEW("적게 감"), NEW("안가봄") }

/** 추천 한 줄. 좌표가 없으면 안내 버튼 대신 홈 추천으로 보낸다. */
class MakeJellyRow(
    val tag: MakeTag,
    val kind: JellyKind,
    val stage: JellyStage,
    val placeName: String?,
    val caption: String,
    val latitude: Double?,
    val longitude: Double?,
)

/** 안가봄 줄에 쓸 검색 결과 (그 분야 근처 장소 1곳). */
class UnvisitedPick(val kind: JellyKind, val place: CoursePlace)

/** 분야의 대표 장소: 많이 간 곳(같으면 최근) 중 좌표가 있는 곳을 먼저. */
private fun favoritePlace(list: List<Experience>, kind: JellyKind): Revisit? {
    val byPlace = list.filter { it.kind == kind }.groupBy { it.visit.placeName }
    val rows = byPlace.keys.mapNotNull { revisitOf(list, it) }
        .sortedWith(compareByDescending<Revisit> { it.latitude != null }.thenByDescending { it.count }.thenByDescending { it.last })
    return rows.firstOrNull()
}

/**
 * 오른쪽 아래 3줄 (G-4). QF1 제안 ③:
 * - 많이 감: 아직 태어나지 않은 분야 중 가장 많이 간 분야(곧 태어날 젤리)의 자주 간 장소. 모두 태어났으면 가장 많이 간 분야.
 * - 적게 감: 다녀온 분야 중 가장 적게 간 분야(많이 감과 다른 분야)의 장소.
 * - 안가봄: 아직 0개인 분야 근처 장소 검색 결과 [pick] (없으면 분야만 보이고 홈 추천으로).
 */
fun makeJellyRows(list: List<Experience>, states: List<JellyState>, pick: UnvisitedPick?): List<MakeJellyRow> {
    val visited = states.filter { it.count > 0 }
    val rows = ArrayList<MakeJellyRow>()
    val many = visited.sortedWith(
        compareBy<JellyState> { it.stage == JellyStage.BORN }.thenByDescending { it.count }.thenByDescending { it.last },
    ).firstOrNull()
    fun visitedRow(tag: MakeTag, st: JellyState) {
        val p = favoritePlace(list, st.kind)
        rows += MakeJellyRow(tag, st.kind, st.stage, p?.placeName, jellyProgress(st), p?.latitude, p?.longitude)
    }
    many?.let { visitedRow(MakeTag.MANY, it) }
    visited.filter { it.kind != many?.kind }
        .sortedWith(compareBy<JellyState> { it.count }.thenByDescending { it.last })
        .firstOrNull()?.let { visitedRow(MakeTag.FEW, it) }
    val empty = unvisitedTargets(states)
    val newKind = pick?.kind?.takeIf { it in empty } ?: empty.firstOrNull()
    if (newKind != null) {
        val st = states.first { it.kind == newKind }
        val place = pick?.takeIf { it.kind == newKind }?.place
        val caption = jellyProgress(st) + (place?.distanceM?.let { " · ${distanceText(it)}" } ?: "")
        rows += MakeJellyRow(MakeTag.NEW, newKind, JellyStage.NONE, place?.name, caption, place?.latitude, place?.longitude)
    }
    return rows
}

fun distanceText(meters: Int): String = if (meters < 1000) "${meters}m" else "${Math.round(meters / 100.0) / 10.0}km".replace(".0km", "km")

/** 아직 0개인 분야 (권하는 순서). */
fun unvisitedTargets(states: List<JellyState>): List<JellyKind> {
    val byKind = states.associateBy { it.kind }
    return SUGGEST_KIND_ORDER.filter { (byKind[it]?.count ?: 0) == 0 }
}

/** 빈 분야 근처 장소 검색에 쓰는 서버 분야(5종, app/schemas.py Theme). 맞는 분야가 없으면 null (분야 없이 찾는다). */
fun searchTheme(kind: JellyKind): String? = when (kind) {
    JellyKind.NATURE -> "nature"
    JellyKind.SCIENCE, JellyKind.ART, JellyKind.MUSIC, JellyKind.BOOK -> "exhibition"
    JellyKind.MAKE -> "experience"
    JellyKind.MOVE, JellyKind.WATER, JellyKind.FRIEND -> "play"
    JellyKind.ANIMAL -> "pet"
    JellyKind.FOOD, JellyKind.TRAVEL -> null
}

/**
 * 추천 응답(코스 후보·대안 장소 모두)에서 [targets] 분야 장소 1곳. 앞 분야를 먼저 보고, 같은 분야면 가까운 곳.
 * 이미 다녀온 이름([visitedNames])은 뺀다. 분류는 서버 `jelly` 우선, 없으면 이름 규칙.
 */
fun pickUnvisited(res: SuggestResponse, targets: List<JellyKind>, visitedNames: Set<String>): UnvisitedPick? {
    val places = res.options.flatMap { o ->
        o.course.stops.filter { it.role == "outing" }.map { it.place } + o.course.alternatives.values.flatten()
    }.filter { it.name !in visitedNames }.distinctBy { it.source + ":" + it.sourceId }
    val classified = places.map { (JellyKind.fromServer(it.jelly) ?: classify(it.name, "outing", it.theme)) to it }
    for (kind in targets) {
        val hit = classified.filter { it.first == kind }.minByOrNull { it.second.distanceM ?: Int.MAX_VALUE } ?: continue
        return UnvisitedPick(kind, hit.second)
    }
    return null
}

/** 빈 분야를 권하는 순서 (자연·과학·책처럼 첫 나들이로 고르기 쉬운 것 먼저, [suggestEmpty]와 같은 순서). */
val SUGGEST_KIND_ORDER = listOf(
    JellyKind.NATURE, JellyKind.SCIENCE, JellyKind.BOOK, JellyKind.MAKE, JellyKind.ANIMAL, JellyKind.MUSIC,
    JellyKind.WATER, JellyKind.MOVE, JellyKind.ART, JellyKind.FRIEND, JellyKind.TRAVEL, JellyKind.FOOD,
)

/** 분야 카드(G-6)의 다녀온 장소: 최근 순, 같은 곳 1개. */
fun kindPlaces(list: List<Experience>, kind: JellyKind): List<Revisit> =
    list.filter { it.kind == kind }.map { it.visit.placeName }.distinct().mapNotNull { revisitOf(list, it) }.sortedByDescending { it.last }

// ───────────────────────── 이번 달 ─────────────────────────

/** 이번 달 만난 젤리: 이번 달 경험이 있는 분야를 많은 순으로 [limit]개. */
fun kindsInMonth(list: List<Experience>, month: YearMonth, limit: Int = 3): List<JellyKind> =
    list.filter { YearMonth.from(it.date) == month }.groupingBy { it.kind }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<JellyKind, Int>> { it.value }.thenBy { it.key.ordinal })
        .take(limit).map { it.key }

// ───────────────────────── G03 월별 캘린더 ─────────────────────────

/** 하루 칸. [kinds]는 그날 자란 분야(처음 간 순, 같은 분야 1개), [births]는 그날 태어난 캐릭터. */
class CalendarDay(val date: LocalDate, val visits: List<Experience>, val kinds: List<JellyKind>, val births: List<JellyKind>)

/** 일요일부터 시작하는 달력 칸. 앞뒤 빈칸은 null, 길이는 7의 배수. */
fun calendarCells(list: List<Experience>, month: YearMonth): List<CalendarDay?> {
    val births = birthDates(list)
    val byDate = list.filter { YearMonth.from(it.date) == month }.groupBy { it.date }
    val lead = month.atDay(1).dayOfWeek.let { if (it == DayOfWeek.SUNDAY) 0 else it.value }
    val cells = ArrayList<CalendarDay?>()
    repeat(lead) { cells += null }
    for (d in 1..month.lengthOfMonth()) {
        val date = month.atDay(d)
        val xs = byDate[date].orEmpty().sortedBy { it.visit.arrivedAt }
        cells += CalendarDay(date, xs, xs.map { it.kind }.distinct(), births.filter { it.value == date }.keys.sortedBy { it.ordinal })
    }
    while (cells.size % 7 != 0) cells += null
    return cells
}

/** 한 분야가 이 기간에 자란 정도: [added]개 늘어 기간 끝 누적 [total]. */
class KindGrowth(val kind: JellyKind, val added: Int, val total: Int) {
    val stage: JellyStage get() = JellyStage.of(total)
}

/** 기간 요약 (G03 오른쪽). [places] 다녀온 곳, [days] 다녀온 날, [grown] 자란 젤리(경험 수), [births] 새로 태어난 캐릭터. */
class PeriodSummary(
    val places: Int,
    val days: Int,
    val grown: Int,
    val births: List<Pair<JellyKind, LocalDate>>,
    val growth: List<KindGrowth>,
)

/** [from]~[to] (둘 다 포함) 요약. 분야별 진행은 많이 자란 순. */
fun periodSummary(list: List<Experience>, from: LocalDate, to: LocalDate): PeriodSummary {
    val inRange = list.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
    val upTo = list.filter { !it.date.isAfter(to) }
    val totals = upTo.groupingBy { it.kind }.eachCount()
    val growth = inRange.groupingBy { it.kind }.eachCount().map { (k, n) -> KindGrowth(k, n, totals[k] ?: n) }
        .sortedWith(compareByDescending<KindGrowth> { it.added }.thenByDescending { it.total }.thenBy { it.kind.ordinal })
    val births = birthDates(list).filter { !it.value.isBefore(from) && !it.value.isAfter(to) }
        .entries.sortedBy { it.value }.map { it.toPair() }
    return PeriodSummary(
        places = inRange.map { it.visit.placeName }.distinct().size,
        days = inRange.map { it.date }.distinct().size,
        grown = inRange.size,
        births = births,
        growth = growth,
    )
}

fun monthSummary(list: List<Experience>, month: YearMonth): PeriodSummary = periodSummary(list, month.atDay(1), month.atEndOfMonth())

fun yearSummary(list: List<Experience>, year: Int): PeriodSummary = periodSummary(list, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))

/** G03 위 칩: 이번 달과 지난 3달 (최근 순). */
fun calendarMonths(today: LocalDate): List<YearMonth> = YearMonth.from(today).let { m -> (0L..3L).map { m.minusMonths(it) } }
