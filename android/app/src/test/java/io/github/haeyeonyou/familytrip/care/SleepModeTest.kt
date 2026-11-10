package io.github.haeyeonyou.familytrip.care

import io.github.haeyeonyou.familytrip.data.FeedingRoutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepModeTest {
    private val min = FeedingRoutine.MS_PER_MINUTE

    @Test
    fun `안내 음성은 낮잠 프리셋 음량, 없으면 10%`() {
        assertEquals(0.2f, sleepVoiceVolume(20), 0.0001f)
        assertEquals(0.1f, sleepVoiceVolume(null), 0.0001f)
        assertEquals(1f, sleepVoiceVolume(150), 0.0001f)
        assertEquals(10, sleepVoicePercent(null))
    }

    @Test
    fun `잠든 지 N분`() {
        assertEquals("잠든 지 42분", sleepingLabel(0L, 42 * min + 59_000))
        assertEquals("잠든 지 1시간", sleepingLabel(0L, 60 * min))
        assertEquals("잠든 지 1시간 5분", sleepingLabel(0L, 65 * min))
        assertNull(sleepingLabel(null, 0L))
    }

    @Test
    fun `수면은 경유지 다음, 오늘 기록 앞`() {
        assertEquals(CareSlot.Sleep, pickCareSlot(FeedingPhase.Normal, delayActive = false, sleeping = true))
        assertEquals(CareSlot.Waypoint, pickCareSlot(FeedingPhase.Normal, delayActive = false, waypointActive = true, sleeping = true))
        assertEquals(CareSlot.FeedingSoon, pickCareSlot(FeedingPhase.Soon, delayActive = false, sleeping = true))
    }
}
