package io.github.haeyeonyou.familytrip.care

import io.github.haeyeonyou.familytrip.data.CarePlace
import io.github.haeyeonyou.familytrip.data.FeedingRoutine
import io.github.haeyeonyou.familytrip.data.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedingStopTest {
    private val min = FeedingRoutine.MS_PER_MINUTE
    private val session = FeedingStopSession(startedAt = 0L, climateApplied = true)

    @Test
    fun `N분째와 진행 막대`() {
        assertEquals(12, feedingStopMinutes(session, 12 * min + 30_000))
        assertEquals(0.4f, feedingStopProgress(session, 12 * min), 0.001f)
        assertEquals(1f, feedingStopProgress(session, 90 * min))
        assertEquals(0, feedingStopMinutes(session, -min))
    }

    @Test
    fun `손으로 끝내면 항상 기록, 출발로 끝나면 3분 이상만`() {
        assertTrue(shouldRecordFeeding(session, min, auto = false))
        assertFalse(shouldRecordFeeding(session, 2 * min, auto = true))
        assertTrue(shouldRecordFeeding(session, 3 * min, auto = true))
        assertTrue(feedingStopEndMessage(session, recorded = true, auto = true).startsWith("출발해서"))
        assertTrue(feedingStopEndMessage(session, recorded = false, auto = true).endsWith("기록하지 않았어요"))
    }

    @Test
    fun `케어 경유지 300m 안에 P단이면 도착`() {
        val place = CarePlace(name = "수유실", latitude = 37.4000, longitude = 127.1000, distanceM = 0, driveMinutes = 1)
        val added = AddedWaypoint(place, asWaypoint = true, addedAt = 0L)
        assertTrue(arrivedAtWaypoint(true, added, LatLng(37.4010, 127.1000)))
        assertFalse(arrivedAtWaypoint(false, added, LatLng(37.4010, 127.1000)))
        assertFalse(arrivedAtWaypoint(true, added, LatLng(37.4100, 127.1000)))
        assertFalse(arrivedAtWaypoint(true, null, LatLng(37.4, 127.1)))
        assertFalse(arrivedAtWaypoint(true, added, null))
    }

    @Test
    fun `수유 중은 슬롯 맨 앞`() {
        assertEquals(CareSlot.FeedingNow, pickCareSlot(FeedingPhase.Overdue, delayActive = true, waypointActive = true, feedingNow = true))
    }
}
