package io.github.haeyeonyou.familytrip.navi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1140초(19분)·8400m 경로를 t=0에 시작한 것으로 두고, "t초 시점에 계획보다 d초 늦은 값"을 내비가 알려 주는 상황을 만든다.
 * 계획대로 가면 t초 시점의 남은 시간은 1140−t초다.
 */
class DelayTrackerTest {
    private val sec = 1000L

    private fun started() = DelayTracker().apply { start(1140, 8400, 0) }

    /** t초 시점에 계획보다 [delay]초 늦다는 값을 먹인다. 거리는 시간에 비례해 줄인다. */
    private fun DelayTracker.feed(t: Int, delay: Int): DelayState {
        val meters = (8400 - t * 7).coerceAtLeast(0)
        return update(1140 - t + delay, meters, t * sec)!!
    }

    /** 출발한 상태(길이 200m 넘게 줄었음) */
    private fun departed() = started().also { it.feed(60, 0) }

    /** 지연 300초가 t=60부터 이어져 t=90에 알림이 켜진 상태 */
    private fun alerting(): DelayTracker {
        val t = departed()
        t.feed(60, 300)
        assertTrue(t.feed(90, 300).alert)
        return t
    }

    @Test
    fun 안내_중이_아니면_상태가_없다() {
        assertNull(DelayTracker().update(600, 4000, 0))
    }

    @Test
    fun 계획대로_가면_지연은_0이다() {
        val s = departed().feed(120, 0)
        assertEquals(0, s.delaySeconds)
        assertFalse(s.alert)
    }

    @Test
    fun 남은_시간이_늘면_그만큼_지연이고_시각을_함께_준다() {
        val s = departed().feed(360, 300)
        assertEquals(300, s.delaySeconds)
        assertEquals(1140 * sec, s.plannedArrivalMillis)
        assertEquals((360 + 780 + 300) * sec, s.expectedArrivalMillis)
    }

    @Test
    fun 빨리_가면_지연이_음수다() {
        assertEquals(-120, departed().feed(120, -120).delaySeconds)
    }

    @Test
    fun 출발_전에_기다린_시간은_지연이_아니다() {
        val t = started()
        // 10분을 서 있어도 길이도 남은 시간도 그대로
        assertEquals(0, t.update(1140, 8400, 600 * sec)!!.delaySeconds)
    }

    @Test
    fun 출발_전에_내비가_남은_시간을_늘리면_지연으로_본다() {
        assertEquals(420, started().update(1140 + 420, 8400, 300 * sec)!!.delaySeconds)
    }

    @Test
    fun 문턱을_넘어도_30초_이어져야_알린다() {
        val t = departed()
        assertFalse(t.feed(60, 300).alert)
        assertFalse(t.feed(80, 300).alert)
        assertTrue(t.feed(90, 300).alert)
    }

    @Test
    fun 잠깐_튄_값은_알리지_않고_다시_넘으면_처음부터_센다() {
        val t = departed()
        t.feed(60, 300)
        assertFalse(t.feed(70, 0).alert) // 10초 만에 되돌아옴
        t.feed(80, 300)
        assertFalse(t.feed(100, 300).alert) // 80초부터 다시 세어 20초
        assertTrue(t.feed(110, 300).alert)
    }

    @Test
    fun 문턱_바로_아래는_알리지_않는다() {
        val t = departed()
        repeat(10) { assertFalse(t.feed(60 + it * 30, 299).alert) }
    }

    @Test
    fun 알림이_뜬_뒤_지연이_3분_아래로_30초_이어지면_거둔다() {
        val t = alerting()
        assertTrue(t.feed(100, 179).alert) // 해제 기준 아래로 막 내려옴
        assertTrue(t.feed(120, 179).alert) // 20초
        assertFalse(t.feed(130, 179).alert) // 30초
    }

    @Test
    fun 지연이_3분과_5분_사이에서는_알림이_계속_유지된다() {
        val t = alerting()
        repeat(20) { assertTrue(t.feed(100 + it * 30, 240).alert) }
    }

    @Test
    fun 해제_기준_아래로_내려가다_다시_올라가면_해제를_처음부터_센다() {
        val t = alerting()
        t.feed(100, 150)
        t.feed(120, 150)
        assertTrue(t.feed(125, 250).alert) // 3분 이상으로 복귀
        t.feed(130, 150)
        assertTrue(t.feed(150, 150).alert) // 20초
        assertFalse(t.feed(160, 150).alert)
    }

    @Test
    fun 그대로_가기를_누르면_바로_꺼지고_10분_안에는_다시_알리지_않는다() {
        val t = alerting()
        t.dismiss(100 * sec, 300)
        assertFalse(t.feed(100, 300).alert)
        // 지연이 10분(+5분 이상)으로 커져도 10분(=700초)이 안 지났다
        repeat(10) { assertFalse(t.feed(120 + it * 40, 600).alert) }
    }

    @Test
    fun 그대로_가기_뒤_10분이_지나도_5분_더_늦어지지_않았으면_알리지_않는다() {
        val t = alerting()
        t.dismiss(100 * sec, 300)
        // 지연이 599초(<300+300)로 이어져도 알리지 않는다
        repeat(10) { assertFalse(t.feed(720 + it * 60, 599).alert) }
    }

    @Test
    fun 그대로_가기_뒤_10분이_지나고_5분_더_늦어지면_30초_뒤_다시_알린다() {
        val t = alerting()
        t.dismiss(100 * sec, 300)
        assertFalse(t.feed(720, 600).alert)
        assertFalse(t.feed(740, 600).alert)
        assertTrue(t.feed(750, 600).alert)
    }

    @Test
    fun 알림이_스스로_풀린_뒤에는_10분_조용하고_그_뒤에는_문턱만_넘으면_다시_알린다() {
        val t = alerting()
        t.feed(100, 100)
        assertFalse(t.feed(130, 100).alert) // 풀림 (t=130)
        // 곧바로 다시 지연 300초: 10분(t=730) 전이라 알리지 않는다
        repeat(10) { assertFalse(t.feed(140 + it * 40, 300).alert) }
        // 10분 뒤에는 5분 문턱만 넘으면 된다 (그대로 가기와 달리 5분 더 늦어질 필요 없음)
        t.feed(740, 300)
        assertTrue(t.feed(770, 300).alert)
    }

    @Test
    fun 경유지를_넣으면_알림이_거두어지고_바뀐_남은_시간이_새_기준이_된다() {
        val t = alerting()
        t.rebase(120 * sec)
        // 경유지 때문에 남은 시간이 갑자기 +240초: 지연이 아니다
        assertEquals(0, t.feed(125, 300 + 240).delaySeconds)
        assertFalse(t.feed(125, 300 + 240).alert)
        // 유예(60초) 동안은 계속 새 값을 기준으로 따라간다
        assertEquals(0, t.feed(170, 300 + 250).delaySeconds)
        // 유예가 끝난 뒤(t>=180)에는 그때 기준과 비교한다: 기준은 t=170의 값(지연 550 상당)
        assertEquals(0, t.feed(190, 550).delaySeconds)
        assertEquals(300, t.feed(300, 850).delaySeconds)
    }

    @Test
    fun 경유지를_넣은_뒤에도_이후에_늦어지면_다시_알린다() {
        val t = alerting()
        t.rebase(120 * sec)
        t.feed(150, 540)
        t.feed(200, 540) // 유예 끝, 기준 확정
        t.feed(300, 840) // 지연 300초
        assertTrue(t.feed(330, 840).alert)
    }

    @Test
    fun 안내가_끝나면_상태가_없고_새_안내는_처음부터_센다() {
        val t = alerting()
        t.reset()
        assertNull(t.update(600, 3000, 600 * sec))
        t.start(600, 4000, 1000 * sec)
        val s = t.update(600, 4000, 1000 * sec)!!
        assertEquals(0, s.delaySeconds)
        assertFalse(s.alert)
    }

    @Test
    fun 새_안내를_시작하면_이전의_그대로_가기_기록을_잊는다() {
        val t = alerting()
        t.dismiss(100 * sec, 300)
        t.start(1140, 8400, 200 * sec)
        // 새 안내는 t=200 기준. 출발 후 지연 300초가 30초 이어지면 바로 알린다
        t.update(1080, 7900, 260 * sec)
        t.update(1080 + 300 - 0, 7500, 260 * sec + 1)
        t.update(1080 + 300, 7500, 261 * sec)
        assertTrue(t.update(1050 + 300, 7400, 295 * sec)!!.alert)
    }
}
