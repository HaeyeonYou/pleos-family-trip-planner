package io.github.haeyeonyou.familytrip.ui

import io.github.haeyeonyou.familytrip.care.CareKind
import io.github.haeyeonyou.familytrip.care.Nearby
import io.github.haeyeonyou.familytrip.data.CarePlace
import io.github.haeyeonyou.familytrip.data.FeedingRoutine
import io.github.haeyeonyou.familytrip.data.clockLabel
import io.github.haeyeonyou.familytrip.navi.DelayAlertUi
import io.github.haeyeonyou.familytrip.navi.DelayCandidate
import io.github.haeyeonyou.familytrip.navi.TrafficCause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CareScreenTextTest {
    private val min = 60_000L
    private val now = 1_800_000_000_000L

    private fun place(name: String = "구리시 육아종합지원센터", onRoute: Boolean = true, detour: Int? = null) =
        CarePlace(name = name, latitude = 37.5, longitude = 127.0, distanceM = 1200, driveMinutes = 3, onRoute = onRoute, detourMinutes = detour)

    private fun alert(candidates: List<DelayCandidate> = emptyList(), loading: Boolean = false, message: String? = null) = DelayAlertUi(
        delayMinutes = 15,
        cause = TrafficCause.describe(null, now).copy(reason = "정체"),
        expectedArrival = "12:45",
        plannedArrival = "12:30",
        candidates = candidates,
        loading = loading,
        message = message,
    )

    // 지연 알림 줄

    @Test
    fun 후보가_있고_다음_수유_전이면_수유_시각과_후보_수를_알린다() {
        val feeding = FeedingRoutine(lastFedAt = now - 60 * min, intervalMinutes = 180)
        val view = delayBannerView(alert(listOf(DelayCandidate(CareKind.NURSING, place()), DelayCandidate(CareKind.REST, place("주차장")))), feeding, now)
        assertEquals("정체로 도착이 12:45로 늦어져요", view.title)
        assertEquals("다음 수유(${clockLabel(feeding.nextAt!!)}) 전에 들를 수 있는 곳이 경로 위에 2곳 있어요", view.subtitle)
        assertTrue(view.canAddWaypoint)
    }

    @Test
    fun 후보가_경로_밖이면_경로_위라고_하지_않는다() {
        val feeding = FeedingRoutine(lastFedAt = now - 60 * min)
        val view = delayBannerView(alert(listOf(DelayCandidate(CareKind.NURSING, place(onRoute = false)))), feeding, now)
        assertTrue(view.subtitle, view.subtitle.contains("근처에 1곳"))
    }

    @Test
    fun 수유_시각이_지났으면_다음_수유_전이라고_하지_않는다() {
        val feeding = FeedingRoutine(lastFedAt = now - 5 * 60 * min, intervalMinutes = 180)
        val view = delayBannerView(alert(listOf(DelayCandidate(CareKind.NURSING, place()))), feeding, now)
        assertTrue(view.subtitle, view.subtitle.startsWith("수유 시간("))
        assertTrue(view.subtitle, view.subtitle.contains("지났어요"))
    }

    @Test
    fun 수유_기록이_없으면_수유_시각_없이_후보만_알린다() {
        val view = delayBannerView(alert(listOf(DelayCandidate(CareKind.NURSING, place()))), FeedingRoutine(), now)
        assertEquals("들를 수 있는 곳이 경로 위에 1곳 있어요", view.subtitle)
    }

    @Test
    fun 후보를_찾는_중이면_찾는_중이라고_하고_경유지_추가를_막는다() {
        val view = delayBannerView(alert(loading = true), FeedingRoutine(), now)
        assertEquals("쉬어 갈 곳을 찾고 있어요…", view.subtitle)
        assertFalse(view.canAddWaypoint)
    }

    @Test
    fun 후보를_못_찾았으면_이유를_알리고_경유지_추가를_막는다() {
        assertEquals("근처에서 경유지를 찾지 못했어요", delayBannerView(alert(), FeedingRoutine(), now).subtitle)
        val view = delayBannerView(alert(message = "현재 위치를 몰라 근처 경유지를 찾지 못했어요"), FeedingRoutine(), now)
        assertEquals("현재 위치를 몰라 근처 경유지를 찾지 못했어요", view.subtitle)
        assertFalse(view.canAddWaypoint)
    }

    @Test
    fun 케어_화면_급한_카드는_수유실과_정차_공간_두_장이다() {
        assertEquals(listOf(CareKind.NURSING, CareKind.REST), CareKind.cards)
    }

    // 수유 타이머

    @Test
    fun 수유_시각이_남았으면_다음_수유_시각과_마지막_간격을_보인다() {
        val feeding = FeedingRoutine(lastFedAt = now - 30 * min, intervalMinutes = 180)
        val v = feedingTimerView(feeding, now)
        assertEquals(clockLabel(feeding.nextAt!!), v.nextClock)
        assertFalse(v.passed)
        assertNull(v.passedText)
        assertEquals("마지막 ${clockLabel(feeding.lastFedAt!!)} · 간격 3시간", v.info)
        assertEquals(30f / 180f, v.progress, 0.001f)
    }

    @Test
    fun 수유_시각이_지나면_지난_시간을_따로_알린다() {
        val feeding = FeedingRoutine(lastFedAt = now - (8 * 60 + 52 + 180) * min, intervalMinutes = 180)
        val v = feedingTimerView(feeding, now)
        assertTrue(v.passed)
        assertEquals("8시간 52분 지났어요", v.passedText)
        assertFalse("지난 문구는 마지막·간격 줄에 이어 붙이지 않는다", v.info.contains("지났"))
        assertEquals(1f, v.progress, 0f)
    }

    @Test
    fun 수유_기록이_없으면_안내_문구를_보인다() {
        val v = feedingTimerView(FeedingRoutine(), now)
        assertNull(v.nextClock)
        assertFalse(v.passed)
        assertEquals(0f, v.progress, 0f)
        assertTrue(v.info.contains("기록하면"))
    }

    @Test
    fun 간격이_2시간_30분이어도_한_줄_문구에_지난_시간을_섞지_않는다() {
        val feeding = FeedingRoutine(lastFedAt = now - 10 * 60 * min, intervalMinutes = 150)
        assertEquals("마지막 ${clockLabel(feeding.lastFedAt!!)} · 간격 2시간 30분", feedingTimerView(feeding, now).info)
    }
}
