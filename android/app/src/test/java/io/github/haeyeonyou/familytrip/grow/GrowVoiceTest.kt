package io.github.haeyeonyou.familytrip.grow

import io.github.haeyeonyou.familytrip.data.Visit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class GrowVoiceTest {
    private var nextId = 1L
    private val now = LocalDateTime.parse("2026-11-11T12:00:00")
    private val today: LocalDate = now.toLocalDate()

    private fun visit(name: String, date: String, theme: String? = null, lat: Double? = 37.5) = Visit(
        id = nextId++, placeName = name, role = "outing", theme = theme, arrivedAt = "${date}T10:00:00",
        departedAt = "${date}T11:00:00", latitude = lat, longitude = lat?.let { 127.0 },
    )

    private val list = experiences(
        listOf(
            visit("서울숲", "2026-10-03"),
            visit("서울숲", "2026-10-20"),
            visit("국립중앙박물관 어린이박물관", "2026-10-12"),
            visit("물빛 워터파크", "2026-10-25"),
            visit("별마당 도서관", "2026-11-02", lat = null),
            visit("올림픽공원", "2026-11-09"),
        ),
        now,
    )

    @Test
    fun 서버_값을_기간과_분류로_바꾼다() {
        assertEquals(GrowPeriod.LAST_MONTH, GrowPeriod.of("last_month"))
        assertNull(GrowPeriod.of("last_year"))
        assertEquals(JellyKind.SCIENCE, jellyKindOf("science"))
        assertNull(jellyKindOf("dragon"))
        assertEquals(YearMonth.of(2026, 10), GrowPeriod.LAST_MONTH.month(today))
        assertEquals(YearMonth.of(2026, 1).minusMonths(1), GrowPeriod.LAST_MONTH.month(LocalDate.parse("2026-01-05")))
    }

    @Test
    fun 지난달_간_곳은_지난달_장소를_최근_순으로_읽는다() {
        val answer = growShowAnswer(list, GrowView(period = GrowPeriod.LAST_MONTH), today)
        assertEquals("지난달에는 3곳 다녀왔어요. 물빛 워터파크, 서울숲, 국립중앙박물관 어린이박물관.", answer)
        assertEquals("이번 달에는 2곳 다녀왔어요. 올림픽공원, 별마당 도서관.", growShowAnswer(list, GrowView(period = GrowPeriod.THIS_MONTH), today))
        assertEquals("지금까지 5곳 다녀왔어요. 올림픽공원, 별마당 도서관, 물빛 워터파크 외 2곳.", growShowAnswer(list, GrowView(), today))
    }

    @Test
    fun 분류는_성장_단계와_함께_답한다() {
        // 서울숲 2번 + 올림픽공원 = 자연 3개 → 곰 방울
        assertEquals(
            "자연·숲 경험 3개, 곰 방울이에요. 몽글까지 1개 남았어요. 올림픽공원, 서울숲.",
            growShowAnswer(list, GrowView(kind = JellyKind.NATURE), today),
        )
        assertTrue(growShowAnswer(list, GrowView(kind = JellyKind.MUSIC), today).startsWith("아직 비어 있는 별이에요."))
        assertEquals("지난달 물놀이 1곳 다녀왔어요. 물빛 워터파크.", growShowAnswer(list, GrowView(JellyKind.WATER, GrowPeriod.LAST_MONTH), today))
        assertEquals("이번 달 물놀이 다녀온 곳은 없어요.", growShowAnswer(list, GrowView(JellyKind.WATER, GrowPeriod.THIS_MONTH), today))
    }

    @Test
    fun 다녀온_곳이_없으면_없다고_답한다() {
        assertEquals("아직 다녀온 곳이 없어요.", growShowAnswer(emptyList(), GrowView(), today))
        assertEquals("지난달 다녀온 곳이 없어요.", growShowAnswer(emptyList(), GrowView(period = GrowPeriod.LAST_MONTH), today))
    }

    @Test
    fun 서버에_보내는_장소_이름은_최근_순_중복_없이() {
        assertEquals(
            listOf("올림픽공원", "별마당 도서관", "물빛 워터파크", "서울숲", "국립중앙박물관 어린이박물관"),
            voicePlaceNames(list),
        )
        assertEquals(2, voicePlaceNames(list, limit = 2).size)
    }

    @Test
    fun 음성_다시_가볼_곳은_좌표가_있는_곳만() {
        // 별마당 도서관(좌표 없음)과 올림픽공원(7일 안)은 빠진다. 서울숲은 2번이라 먼저.
        val rows = voiceRevisits(list, today)
        assertEquals(listOf("서울숲", "물빛 워터파크"), rows.map { it.placeName })
        assertEquals("다시 가 볼 곳 2곳이에요. 1번은 서울숲, 2번은 물빛 워터파크. 번호로 말씀해 주세요.", revisitAnswer(rows, today))
        assertEquals("3주 전 · 자연·숲 · 2번 방문", revisitVoiceNote(rows[0], today))
        assertEquals("3주 전에 간 서울숲이에요. 다시 안내할까요?", revisitProposalMessage(rows[0], today))
        assertTrue(revisitAnswer(emptyList(), today).startsWith("아직 다시 가 볼 곳이 없어요"))
    }

    @Test
    fun 다녀온_곳_하나를_찾는다() {
        val r = revisitOf(list, "서울숲")!!
        assertEquals(2, r.count)
        assertEquals(LocalDate.parse("2026-10-20"), r.last)
        assertEquals(JellyKind.NATURE, r.kind)
        assertNull(revisitOf(list, "에버랜드"))
        assertNull(revisitOf(list, "별마당 도서관")!!.latitude)
        assertEquals("오늘 간 서울숲이에요. 다시 안내할까요?", revisitProposalMessage(r.let { Revisit(it.placeName, it.kind, 1, today, 1.0, 1.0, false) }, today))
    }

    @Test
    fun 기간만_말하면_G03_캘린더_분류나_장소가_있으면_G01() {
        assertTrue(GrowView(period = GrowPeriod.LAST_MONTH).opensCalendar)
        assertFalse(GrowView(kind = JellyKind.ART, period = GrowPeriod.LAST_MONTH).opensCalendar)
        assertFalse(GrowView(kind = JellyKind.ART).opensCalendar)
        assertFalse(GrowView().opensCalendar)
    }
}
