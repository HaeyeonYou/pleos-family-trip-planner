package io.github.haeyeonyou.familytrip.grow

import io.github.haeyeonyou.familytrip.data.CourseOption
import io.github.haeyeonyou.familytrip.data.CoursePlace
import io.github.haeyeonyou.familytrip.data.CourseResponse
import io.github.haeyeonyou.familytrip.data.CourseStop
import io.github.haeyeonyou.familytrip.data.SuggestResponse
import io.github.haeyeonyou.familytrip.data.Visit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * G01 경험 젤리·G03 캘린더 계산 (#216). 장소 이름은 서버 테스트 캐시(`server/tests/api_cache`)의
 * 관광공사 실제 응답에서 가져왔다 (역삼개나리공원·소리울도서관·경기도행정역사관·해움미술관 등).
 */
class GrowJellyTest {
    private var nextId = 1L
    private val now = LocalDateTime.parse("2026-11-11T12:00:00")
    private val today: LocalDate = now.toLocalDate()

    private fun visit(name: String, date: String, hour: Int = 10, lat: Double? = 37.5, jelly: String? = null) = Visit(
        id = nextId++, placeName = name, role = "outing", jelly = jelly,
        arrivedAt = "${date}T${hour.toString().padStart(2, '0')}:00:00", departedAt = "${date}T${(hour + 1).toString().padStart(2, '0')}:00:00",
        latitude = lat, longitude = lat?.let { 127.0 },
    )

    private fun many(name: String, n: Int, from: LocalDate) = (0 until n).map { visit(name, from.plusDays(it.toLong()).toString()) }

    @Test
    fun 열번째_경험_날이_탄생일이고_가장_최근_탄생을_말풍선에_쓴다() {
        val list = experiences(many("해움미술관", 12, LocalDate.parse("2026-10-27")) + many("역삼개나리공원", 10, LocalDate.parse("2026-09-01")), now)
        val births = birthDates(list)
        assertEquals(LocalDate.parse("2026-11-05"), births[JellyKind.ART])
        assertEquals(LocalDate.parse("2026-09-10"), births[JellyKind.NATURE])
        val (kind, date) = latestBirth(list)!!
        assertEquals(JellyKind.ART, kind)
        assertEquals("11.05 유니콘 탄생", birthBubble(kind, date))
        assertNull(latestBirth(experiences(many("해움미술관", 9, today.minusDays(20)), now)))
    }

    @Test
    fun 제목_설명은_경험_수와_자라는_젤리_수() {
        val list = experiences(listOf(visit("해움미술관", "2026-11-01"), visit("역삼개나리공원", "2026-11-02"), visit("역삼개나리공원", "2026-11-03")), now)
        assertEquals("3개의 경험 · 2개의 젤리가 자라는 중 · 한 분야 10개면 캐릭터가 태어나요", jellySubtitle(list.size, jellyStates(list, today)))
    }

    @Test
    fun 진행_문구는_받침에_맞춰_조사를_고른다() {
        fun st(kind: JellyKind, n: Int) = JellyState(kind, n, n, 1, null, null)
        assertEquals("곰 7/10 · 3번 더 가면 곰이 태어나요", jellyProgress(st(JellyKind.NATURE, 7)))
        assertEquals("카피바라 3/10 · 7번 더 가면 카피바라가 태어나요", jellyProgress(st(JellyKind.WATER, 3)))
        assertEquals("공룡 0/10 · 새 젤리를 깨워요", jellyProgress(st(JellyKind.SCIENCE, 0)))
        assertEquals("유니콘 12번 · 캐릭터가 태어났어요", jellyProgress(st(JellyKind.ART, 12)))
    }

    @Test
    fun 추천_3줄은_곧_태어날_분야_적게_간_분야_안_가본_분야() {
        val visits = many("해움미술관", 11, LocalDate.parse("2026-09-01")) + // 이미 탄생
            many("역삼개나리공원", 7, LocalDate.parse("2026-10-01")) + // 곧 탄생
            listOf(visit("소리울도서관", "2026-10-20"), visit("소리울도서관", "2026-10-21"), visit("안산미디어도서관", "2026-10-22", lat = null))
        val list = experiences(visits, now)
        val states = jellyStates(list, today)
        val pick = UnvisitedPick(JellyKind.SCIENCE, place("경기도행정역사관", jelly = "science", distance = 12_300))
        val rows = makeJellyRows(list, states, pick)
        assertEquals(listOf(MakeTag.MANY, MakeTag.FEW, MakeTag.NEW), rows.map { it.tag })
        assertEquals(JellyKind.NATURE, rows[0].kind)
        assertEquals("역삼개나리공원", rows[0].placeName)
        assertEquals("곰 7/10 · 3번 더 가면 곰이 태어나요", rows[0].caption)
        assertEquals(JellyKind.BOOK, rows[1].kind)
        // 좌표 있는 곳을 먼저 (안산미디어도서관은 좌표 없음)
        assertEquals("소리울도서관", rows[1].placeName)
        assertEquals(JellyKind.SCIENCE, rows[2].kind)
        assertEquals("경기도행정역사관", rows[2].placeName)
        assertEquals("공룡 0/10 · 새 젤리를 깨워요 · 12.3km", rows[2].caption)
    }

    @Test
    fun 검색이_없으면_안가봄은_분야만_보이고_한_분야만_가면_적게_감은_없다() {
        val list = experiences(listOf(visit("역삼개나리공원", "2026-11-01")), now)
        val rows = makeJellyRows(list, jellyStates(list, today), null)
        assertEquals(listOf(MakeTag.MANY, MakeTag.NEW), rows.map { it.tag })
        assertEquals(JellyKind.SCIENCE, rows[1].kind) // 자연 다음 권하는 순서
        assertNull(rows[1].placeName)
        assertNull(rows[1].latitude)
        assertTrue(makeJellyRows(emptyList(), jellyStates(emptyList(), today), null).single().tag == MakeTag.NEW)
    }

    private fun place(name: String, jelly: String? = null, theme: String? = null, distance: Int? = null, id: String = name) =
        CoursePlace(name = name, latitude = 37.3, longitude = 126.8, source = "tour", sourceId = id, theme = theme, jelly = jelly, distanceM = distance)

    private fun option(outing: CoursePlace, alternatives: List<CoursePlace>) = CourseOption(
        name = "코스", summary = outing.name, totalMinutes = 120, firstDriveMinutes = 20, placeKey = "tour:${outing.sourceId}",
        course = CourseResponse(
            stops = listOf(CourseStop(role = "outing", place = outing, arriveAt = "2026-11-11T11:00:00", driveMinutes = 20, driveDistanceMeters = 9000)),
            totalDriveMinutes = 40, returnAt = "", routeSource = "fallback",
            alternatives = mapOf("outing" to alternatives),
        ),
    )

    @Test
    fun 근처_검색_결과에서_빈_분야_장소를_가까운_순으로_고른다() {
        val res = SuggestResponse(
            listOf(
                option(place("해움미술관", jelly = "art", distance = 3000), listOf(place("경기도행정역사관", jelly = "science", distance = 9000))),
                option(place("소리울도서관", jelly = "book", distance = 4000), listOf(place("용인시기후변화체험교육센터", theme = "exhibition", distance = 5000))),
            ),
        )
        // 서버 jelly가 없으면 이름 규칙 → 서버 분야(exhibition = 과학). 같은 분야면 가까운 곳
        val pick = pickUnvisited(res, listOf(JellyKind.SCIENCE, JellyKind.BOOK), visitedNames = emptySet())!!
        assertEquals(JellyKind.SCIENCE, pick.kind)
        assertEquals("용인시기후변화체험교육센터", pick.place.name)
        // 다녀온 곳은 빼고
        assertEquals("경기도행정역사관", pickUnvisited(res, listOf(JellyKind.SCIENCE), setOf("용인시기후변화체험교육센터"))!!.place.name)
        // 앞 분야가 없으면 다음 분야
        val other = pickUnvisited(res, listOf(JellyKind.SCIENCE, JellyKind.BOOK), visitedNames = setOf("경기도행정역사관", "용인시기후변화체험교육센터"))!!
        assertEquals(JellyKind.BOOK, other.kind)
        assertNull(pickUnvisited(res, listOf(JellyKind.WATER), emptySet()))
    }

    @Test
    fun 빈_분야_검색은_서버_분야로_거른다() {
        assertEquals("nature", searchTheme(JellyKind.NATURE))
        assertEquals("exhibition", searchTheme(JellyKind.SCIENCE))
        assertEquals("pet", searchTheme(JellyKind.ANIMAL))
        assertNull(searchTheme(JellyKind.TRAVEL))
    }

    @Test
    fun 달력은_일요일부터_칸을_채우고_날짜별로_젤리를_묶는다() {
        // 2026-11-01은 일요일
        val visits = listOf(
            visit("역삼개나리공원", "2026-11-01"),
            visit("해움미술관", "2026-11-08", hour = 10), visit("소리울도서관", "2026-11-08", hour = 13), visit("해움미술관", "2026-11-08", hour = 15),
        )
        val cells = calendarCells(experiences(visits, now), YearMonth.of(2026, 11))
        assertEquals(0, cells.indexOfFirst { it != null })
        assertEquals(35, cells.size) // 30일 + 뒤 빈칸 5
        val d8 = cells.first { it?.date?.dayOfMonth == 8 }!!
        assertEquals(3, d8.visits.size)
        assertEquals(listOf(JellyKind.ART, JellyKind.BOOK), d8.kinds)
        // 2026-10-01은 목요일 → 앞 빈칸 4
        assertEquals(4, calendarCells(emptyList(), YearMonth.of(2026, 10)).indexOfFirst { it != null })
    }

    @Test
    fun 달력에_탄생한_날을_표시하고_달_요약을_낸다() {
        val visits = many("해움미술관", 8, LocalDate.parse("2026-10-20")) + // 10.20~10.27 8개
            listOf(visit("해움미술관", "2026-11-03"), visit("해움미술관", "2026-11-07")) + // 10번째 = 11.07 탄생
            listOf(visit("역삼개나리공원", "2026-11-01"), visit("역삼개나리공원", "2026-11-08"))
        val list = experiences(visits, now)
        val nov = YearMonth.of(2026, 11)
        val d7 = calendarCells(list, nov).first { it?.date?.dayOfMonth == 7 }!!
        assertEquals(listOf(JellyKind.ART), d7.births)
        val sum = monthSummary(list, nov)
        assertEquals(2, sum.places)
        assertEquals(4, sum.days)
        assertEquals(4, sum.grown)
        assertEquals(listOf(JellyKind.ART to LocalDate.parse("2026-11-07")), sum.births)
        val art = sum.growth.first { it.kind == JellyKind.ART }
        assertEquals(2, art.added)
        assertEquals(10, art.total)
        assertEquals(JellyStage.BORN, art.stage)
        // 지난달 기준 누적은 그달 끝까지
        val oct = monthSummary(list, YearMonth.of(2026, 10)).growth.single()
        assertEquals(8, oct.total)
        assertTrue(monthSummary(list, YearMonth.of(2026, 9)).growth.isEmpty())
        assertEquals(12, yearSummary(list, 2026).grown)
    }

    @Test
    fun 캘린더_칩은_이번_달과_지난_3달() {
        assertEquals(listOf(11, 10, 9, 8), calendarMonths(today).map { it.monthValue })
        assertEquals(listOf(JellyKind.NATURE, JellyKind.ART), kindsInMonth(experiences(listOf(visit("해움미술관", "2026-11-02"), visit("역삼개나리공원", "2026-11-03"), visit("역삼개나리공원", "2026-11-04")), now), YearMonth.of(2026, 11)))
    }
}
