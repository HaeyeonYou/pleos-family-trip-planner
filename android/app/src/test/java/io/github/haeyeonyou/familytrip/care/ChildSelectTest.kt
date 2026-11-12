package io.github.haeyeonyou.familytrip.care

import io.github.haeyeonyou.familytrip.data.Child
import io.github.haeyeonyou.familytrip.data.FeedingRoutine
import io.github.haeyeonyou.familytrip.data.ServerChild
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChildSelectTest {
    private val seoa = Child(2025, 9, "서아", serverId = "s1")
    private val doyun = Child(2023, 9, "도윤", serverId = "s2")
    private val server = listOf(
        ServerChild("s1", "서아", "2025-09", feedingType = "formula", feedIntervalMinutes = 180, napCount = 2),
        ServerChild("s2", "도윤", "2023-09", feedingType = "toddler", napCount = 1),
    )

    @Test
    fun `첫째는 키 null, 둘째는 서버 id`() {
        assertEquals(null, childKey(listOf(seoa, doyun), 0))
        assertEquals("s2", childKey(listOf(seoa, doyun), 1))
        assertEquals(1, selectedChildIndex(listOf(seoa, doyun), "s2"))
        assertEquals(0, selectedChildIndex(listOf(seoa, doyun), "없는 아이"))
    }

    @Test
    fun `행 설명은 간격·다음 수유·낮잠, 12개월 넘으면 유아기`() {
        // 2026-03-01: 서아(2025-09) 6개월, 도윤(2023-09) 유아기 (#217)
        val options = childOptions(listOf(seoa, doyun), server, null, routineOf = { key ->
            if (key == null) FeedingRoutine(lastFedAt = null, intervalMinutes = 180) else FeedingRoutine()
        }, today = java.time.LocalDate.of(2026, 3, 1))
        assertEquals("3시간 간격 · 수유 기록 없음 · 낮잠 2회", options[0].line)
        assertTrue(options[0].selected)
        assertEquals("낮잠 1회", options[1].line) // 유아기는 이름 옆 배지 (#247)
        assertEquals("유아기", options[1].stage)
        assertFalse(options[1].selected)
    }

    @Test
    fun `서버 id 없는 둘째는 고를 수 없다`() {
        val local = Child(2023, 9, "도윤")
        val o = childOptions(listOf(seoa, local), server, null) { FeedingRoutine() }
        assertFalse(o[1].enabled)
        assertEquals("아이", childChipLabel(emptyList(), null))
        assertTrue(childChipLabel(listOf(seoa, doyun), "s2").startsWith("도윤 · "))
    }

    // 케어 모드 값 아이별 (QO5, #192)

    @Test
    fun `행마다 그 아이의 베이비케어 값을 보여 준다`() {
        val o = childOptions(listOf(seoa, doyun), server, null, modeOf = { c -> if (c.serverId == "s2") "22°C · 미풍" else "24°C · 약풍" }) { FeedingRoutine() }
        assertEquals(listOf("24°C · 약풍", "22°C · 미풍"), o.map { it.mode })
    }

    @Test
    fun `아이를 바꾸면 공조를 쓰는 모드 하나를 새 아이 값으로 다시 넣는다`() {
        assertEquals("feeding_stop", activeModeSlot(babyCare = true, sleepClimate = true, feedingStopClimate = true))
        assertEquals("nap", activeModeSlot(babyCare = true, sleepClimate = true, feedingStopClimate = false))
        assertEquals("boarding", activeModeSlot(babyCare = true, sleepClimate = false, feedingStopClimate = false))
        assertEquals(null, activeModeSlot(babyCare = false, sleepClimate = false, feedingStopClimate = false))
        assertEquals("수면 모드", modeNoticeName("nap"))
    }
}
