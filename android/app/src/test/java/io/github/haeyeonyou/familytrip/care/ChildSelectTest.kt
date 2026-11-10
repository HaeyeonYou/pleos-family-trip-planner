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
    fun `행 설명은 간격·다음 수유·낮잠, 유아식이면 수유 없음`() {
        val options = childOptions(listOf(seoa, doyun), server, null) { key ->
            if (key == null) FeedingRoutine(lastFedAt = null, intervalMinutes = 180) else FeedingRoutine()
        }
        assertEquals("3시간 간격 · 수유 기록 없음 · 낮잠 2회", options[0].line)
        assertTrue(options[0].selected)
        assertEquals("수유 없음 · 낮잠 1회", options[1].line)
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
}
