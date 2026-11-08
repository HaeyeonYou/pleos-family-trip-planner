package io.github.haeyeonyou.familytrip.navi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficCauseTest {
    private fun jam(road: String, type: Congestion = Congestion.HEAVY, meters: Int = 100, seconds: Int = 60) =
        CongestionSection(road, type, meters, seconds)

    private val now = 1_000_000L

    @Test
    fun 교통_정보가_없으면_이유를_모른다고_한다() {
        assertEquals(TrafficCause.UNKNOWN, TrafficCause.describe(null, now))
        assertEquals(TrafficCause.UNKNOWN, TrafficCause.describe(TrafficSnapshot(), now))
    }

    @Test
    fun 정체_시간이_가장_큰_도로를_이름으로_쓴다() {
        val c = TrafficCause.describe(
            TrafficSnapshot(
                sections = listOf(
                    jam("서현로", seconds = 60, meters = 300),
                    jam("대왕판교로", seconds = 60, meters = 100),
                    jam("대왕판교로", seconds = 120, meters = 100),
                ),
            ),
            now,
        )
        assertEquals("정체", c.reason)
        assertTrue(c.detail, c.detail.startsWith("대왕판교로 정체 외 1곳"))
        assertTrue(c.detail, c.detail.endsWith("정체 구간 합계 0.5km"))
    }

    @Test
    fun 정체_구간이_한_도로뿐이면_외_몇곳을_붙이지_않는다() {
        val c = TrafficCause.describe(TrafficSnapshot(sections = listOf(jam("분당내곡로", meters = 200))), now)
        assertEquals("분당내곡로 구간 정체 · 정체 구간 합계 0.2km", c.detail)
    }

    @Test
    fun 심한_정체가_있으면_서행은_보지_않는다() {
        val c = TrafficCause.describe(
            TrafficSnapshot(sections = listOf(jam("A로", Congestion.SLOW, seconds = 600), jam("B로", Congestion.HEAVY, seconds = 60))),
            now,
        )
        assertTrue(c.detail, c.detail.startsWith("B로 구간 정체"))
    }

    @Test
    fun 심한_정체가_없으면_서행도_이유로_본다() {
        val c = TrafficCause.describe(TrafficSnapshot(sections = listOf(jam("A로", Congestion.SLOW))), now)
        assertEquals("정체", c.reason)
    }

    @Test
    fun 원활한_구간만_있으면_이유를_모른다고_한다() {
        val c = TrafficCause.describe(TrafficSnapshot(sections = listOf(jam("A로", Congestion.FAST), jam("B로", Congestion.UNKNOWN))), now)
        assertEquals(TrafficCause.UNKNOWN, c)
    }

    @Test
    fun 사고가_정체보다_먼저다() {
        val c = TrafficCause.describe(
            TrafficSnapshot(
                sections = listOf(jam("대왕판교로")),
                events = listOf(TrafficEventItem(TrafficEventKind.ACCIDENT, "", null)),
            ),
            now,
        )
        assertEquals("사고", c.reason)
        assertTrue(c.detail, c.detail.startsWith("앞쪽 경로에 사고이 있어요")) // 설명이 비면 기본 문구
    }

    @Test
    fun 사고_설명이_있으면_그것을_쓴다() {
        val c = TrafficCause.describe(
            TrafficSnapshot(events = listOf(TrafficEventItem(TrafficEventKind.ACCIDENT, "2차로 추돌사고", null))),
            now,
        )
        assertEquals(DelayCause("사고", "2차로 추돌사고"), c)
    }

    @Test
    fun 이미_끝난_사고는_무시한다() {
        val c = TrafficCause.describe(
            TrafficSnapshot(events = listOf(TrafficEventItem(TrafficEventKind.ACCIDENT, "추돌", now - 1))),
            now,
        )
        assertEquals(TrafficCause.UNKNOWN, c)
    }

    @Test
    fun 공사는_도로_파손보다_먼저다() {
        val c = TrafficCause.describe(
            TrafficSnapshot(
                events = listOf(
                    TrafficEventItem(TrafficEventKind.ROAD_DAMAGE, "", null),
                    TrafficEventItem(TrafficEventKind.REPAIRING, "", null),
                ),
            ),
            now,
        )
        assertEquals("공사", c.reason)
    }

    @Test
    fun 도로이름이_비면_앞쪽으로_쓴다() {
        val c = TrafficCause.describe(TrafficSnapshot(sections = listOf(jam(""))), now)
        assertTrue(c.detail, c.detail.startsWith("앞쪽 구간 정체"))
    }

    @Test
    fun 받침에_따라_로와_으로를_고른다() {
        assertEquals("정체로", TrafficCause.withRo("정체"))
        assertEquals("사고로", TrafficCause.withRo("사고"))
        assertEquals("공사로", TrafficCause.withRo("공사"))
        assertEquals("도로 파손으로", TrafficCause.withRo("도로 파손"))
        assertEquals("교통 상황으로", TrafficCause.withRo("교통 상황"))
        assertEquals("재난 상황으로", TrafficCause.withRo("재난 상황"))
        assertEquals("행사로", TrafficCause.withRo("행사"))
        assertEquals("서울로", TrafficCause.withRo("서울"))
    }
}
