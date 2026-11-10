package io.github.haeyeonyou.familytrip.care

import io.github.haeyeonyou.familytrip.data.FeedingRoutine
import org.junit.Assert.assertEquals
import org.junit.Test

class CareSlotTest {
    private val min = FeedingRoutine.MS_PER_MINUTE
    private val now = 1_000_000_000_000L
    // 마지막 수유 3시간 간격: 다음 = last + 180분
    private fun routine(nextInMinutes: Long) = FeedingRoutine(lastFedAt = now + nextInMinutes * min - 180 * min, intervalMinutes = 180)

    @Test fun `기록이 없으면 NoRecord`() = assertEquals(FeedingPhase.NoRecord, feedingPhase(FeedingRoutine(), now))
    @Test fun `여유 있으면 Normal`() = assertEquals(FeedingPhase.Normal, feedingPhase(routine(16), now))
    @Test fun `15분 이하면 Soon`() {
        assertEquals(FeedingPhase.Soon, feedingPhase(routine(15), now))
        assertEquals(FeedingPhase.Soon, feedingPhase(routine(0), now))
    }
    @Test fun `지나면 Overdue와 지난 분`() {
        assertEquals(FeedingPhase.Overdue, feedingPhase(routine(-10), now))
        assertEquals(10L, overdueMinutes(routine(-10), now))
        assertEquals(0L, overdueMinutes(routine(5), now))
    }

    @Test fun `우선순위는 지남 임박 지연 순이고 아무것도 없으면 오늘 기록`() {
        assertEquals(CareSlot.Today, pickCareSlot(FeedingPhase.Normal, delayActive = false))
        assertEquals(CareSlot.Delay, pickCareSlot(FeedingPhase.Normal, delayActive = true))
        assertEquals(CareSlot.FeedingSoon, pickCareSlot(FeedingPhase.Soon, delayActive = true))
        assertEquals(CareSlot.FeedingOverdue, pickCareSlot(FeedingPhase.Overdue, delayActive = true))
    }

    @Test fun `닫은 슬롯은 건너뛰고 다음 우선순위가 보인다`() {
        assertEquals(CareSlot.Delay, pickCareSlot(FeedingPhase.Overdue, true, setOf(CareSlot.FeedingOverdue)))
        assertEquals(CareSlot.Today, pickCareSlot(FeedingPhase.Soon, false, setOf(CareSlot.FeedingSoon)))
    }

    @Test fun `빠른 칩은 N분 전 시각을 만든다`() {
        assertEquals(now, fedAtMinutesAgo(0, now))
        assertEquals(now - 60 * min, fedAtMinutesAgo(60, now))
    }

    @Test fun `우회 문구`() {
        assertEquals("경로에서 +3분", detourLabel(false, 3, 5))
        assertEquals("경로 위 · +6분", detourLabel(true, 6, 5))
        assertEquals("5분", detourLabel(false, null, 5))
    }
}
