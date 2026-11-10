package io.github.haeyeonyou.familytrip.care

import io.github.haeyeonyou.familytrip.data.CarePlace
import io.github.haeyeonyou.familytrip.data.FeedingRoutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrgentCareTest {
    private fun place(name: String, onRoute: Boolean = false, detour: Int? = null, category: String = "", drive: Int = 4, dist: Int = 1200) =
        CarePlace(name = name, latitude = 37.0, longitude = 127.0, distanceM = dist, driveMinutes = drive, category = category, detourMinutes = detour, onRoute = onRoute)

    private val three = listOf(place("A"), place("B"), place("C"), place("D"))

    @Test
    fun `찾은 곳이 있으면 3곳까지 보인다`() {
        val sheet = urgentSheetOf(CareKind.NURSING, three, null, emptyList(), routeBased = true)
        assertEquals(UrgentSheetMode.Found, sheet.mode)
        assertEquals(listOf("A", "B", "C"), sheet.places.map { it.name })
        assertEquals("가까운 수유실 3곳", urgentTitle(sheet))
    }

    @Test
    fun `수유실이 없으면 정차 공간 2곳을 대신 보인다 (C10)`() {
        val sheet = urgentSheetOf(CareKind.NURSING, emptyList(), three, emptyList(), routeBased = false)
        assertEquals(UrgentSheetMode.Fallback, sheet.mode)
        assertEquals(2, sheet.places.size)
        assertEquals("근처에 수유실이 없어요", urgentTitle(sheet))
    }

    @Test
    fun `서버 실패면 자주 가는 케어 장소를 보이고, 정차 공간은 대체하지 않는다`() {
        val offline = urgentSheetOf(CareKind.NURSING, null, null, listOf(place("자주")), routeBased = false)
        assertEquals(UrgentSheetMode.Offline, offline.mode)
        val rest = urgentSheetOf(CareKind.REST, emptyList(), null, emptyList(), routeBased = false)
        assertEquals(UrgentSheetMode.Empty, rest.mode)
        assertEquals("근처에 정차 공간이 없어요", urgentTitle(rest))
        assertEquals("근처에 기저귀 교환대가 없어요", urgentTitle(UrgentSheet(CareKind.DIAPER, UrgentSheetMode.Empty)))
    }

    @Test
    fun `행 설명은 거리·시간·경로·자주 가는 곳`() {
        assertEquals("1.2km · 4분 · 경로에서 +3분", urgentPlaceLine(place("A", detour = 3), frequent = false))
        assertEquals("1.2km · 4분 · 경로 위 · 자주 가는 곳", urgentPlaceLine(place("A", onRoute = true, detour = 0), frequent = true))
        assertEquals("800m · 2분", urgentPlaceLine(place("A", dist = 800, drive = 2), frequent = false))
        assertEquals(listOf("휴게소", "경로 위"), urgentTags(place("A", onRoute = true, category = "휴게소")))
    }

    @Test
    fun `토스트 문구는 조사와 우회 시간을 맞춘다`() {
        val now = 1_000_000L
        assertEquals("OO몰 2F 수유실을 경유지로 추가했어요 · +3분", waypointToastText(AddedWaypoint(place("OO몰 2F 수유실", detour = 3), true, now)))
        assertEquals("△△ 휴게소를 경유지로 추가했어요 · 경로 위", waypointToastText(AddedWaypoint(place("△△ 휴게소", onRoute = true), true, now)))
        assertEquals("공영주차장으로 안내를 시작했어요", waypointToastText(AddedWaypoint(place("공영주차장"), false, now)))
        assertEquals("서울로 안내를 시작했어요", waypointToastText(AddedWaypoint(place("서울"), false, now)))
    }

    @Test
    fun `경유지 슬롯은 도착 시각과 수유 여유를 알린다`() {
        val min = FeedingRoutine.MS_PER_MINUTE
        val added = AddedWaypoint(place("A", drive = 10), true, addedAt = 0L)
        val roomy = FeedingRoutine(lastFedAt = 0L, intervalMinutes = 180)
        assertTrue(waypointSlotLine(added, roomy).contains("전에 여유 있게"))
        val late = FeedingRoutine(lastFedAt = -175 * min, intervalMinutes = 180)
        assertTrue(waypointSlotLine(added, late).endsWith("수유 시각보다 5분 늦어요"))
        assertTrue(waypointSlotLine(added, FeedingRoutine()).endsWith("도착"))
    }

    @Test
    fun `경유지 슬롯은 지연보다 뒤, 오늘 기록보다 앞`() {
        assertEquals(CareSlot.Waypoint, pickCareSlot(FeedingPhase.Normal, delayActive = false, waypointActive = true))
        assertEquals(CareSlot.Delay, pickCareSlot(FeedingPhase.Normal, delayActive = true, waypointActive = true))
        assertEquals(CareSlot.FeedingSoon, pickCareSlot(FeedingPhase.Soon, delayActive = false, waypointActive = true))
    }
}
