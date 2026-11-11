package io.github.haeyeonyou.familytrip.care

import io.github.haeyeonyou.familytrip.data.Child
import io.github.haeyeonyou.familytrip.data.FeedingRoutine
import io.github.haeyeonyou.familytrip.data.PresetState
import io.github.haeyeonyou.familytrip.data.ServerChild
import io.github.haeyeonyou.familytrip.data.withApplyAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// 수유 표시를 아이 월령으로 나누기 (QD1, #217)
class InfantAgeTest {
    private val today = LocalDate.of(2026, 11, 11)

    @Test
    fun `경계는 12개월 상수 하나`() {
        assertEquals(12, INFANT_MAX_MONTHS)
        assertTrue(isInfant(Child(2025, 11), today)) // 12개월
        assertFalse(isInfant(Child(2025, 10), today)) // 13개월
        assertTrue(isInfant(Child(2026, 5), today))
    }

    @Test
    fun `태어난 날을 알면 정확히 12개월 되는 날까지 수유를 보인다`() {
        // 2025-10-12생: 11-11은 생일 전날이라 12개월, 11-12부터 13개월
        val child = Child(2025, 10, birthDay = 12)
        assertTrue(isInfant(child, today))
        assertFalse(isInfant(child, today.plusDays(1)))
        // 2025-11-11생은 오늘 정확히 12개월
        assertTrue(isInfant(Child(2025, 11, birthDay = 11), today))
        assertFalse(isInfant(Child(2025, 11, birthDay = 11), LocalDate.of(2026, 12, 11)))
    }

    @Test
    fun `아이 정보가 없으면 지금처럼 수유를 보인다`() {
        assertTrue(isInfant(null, today))
        assertTrue(anyInfant(emptyList(), today))
    }

    @Test
    fun `아이 선택이 없는 화면은 12개월 이하가 한 명이라도 있으면 보인다`() {
        assertTrue(anyInfant(listOf(Child(2024, 3), Child(2026, 6)), today))
        assertFalse(anyInfant(listOf(Child(2024, 3), Child(2025, 1)), today))
    }

    @Test
    fun `유아는 수유 정차 모드가 없다`() {
        val presets = listOf("boarding", "nap", "feeding_stop").map { PresetState(slot = it) }
        assertEquals(listOf("boarding", "nap", "feeding_stop"), presetsForAge(presets, infant = true).map { it.slot })
        assertEquals(listOf("boarding", "nap"), presetsForAge(presets, infant = false).map { it.slot })
        assertEquals("서아 · 14개월 · 유아기", toddlerChipLabel("서아 · 14개월"))
    }

    @Test
    fun `유아는 수유 정차 모드를 자동으로 켜지 않는다`() {
        // 예전에 저장했거나 웹에서 '탑승하면 바로'로 바꾼 수유 정차 값
        val presets = listOf(
            PresetState(slot = "boarding").withApplyAt(),
            PresetState(slot = "nap").withApplyAt(),
            PresetState(slot = "feeding_stop", applyAt = APPLY_BOARDING).withApplyAt(),
        )
        assertEquals(listOf("boarding", "feeding_stop"), autoSlots(presets, APPLY_BOARDING))
        assertEquals(listOf("boarding", "feeding_stop"), autoSlots(presets, APPLY_BOARDING, infant = true))
        assertEquals(listOf("boarding"), autoSlots(presets, APPLY_BOARDING, infant = false))
    }

    @Test
    fun `유아 정차 모드는 수유로 기록하지 않는다`() {
        val start = 1_000_000L
        val later = start + 30 * 60_000L
        val rest = FeedingStopSession(start, climateApplied = true, feeding = false)
        assertFalse(shouldRecordFeeding(rest, later, auto = false))
        assertFalse(shouldRecordFeeding(rest, later, auto = true))
        assertEquals("정차 모드를 끝냈어요", feedingStopEndMessage(rest, recorded = false, auto = false))
        assertEquals("출발해서 정차 모드를 끝냈어요", feedingStopEndMessage(rest, recorded = false, auto = true))
        // 아기의 수유 정차는 그대로
        assertTrue(shouldRecordFeeding(FeedingStopSession(start), later, auto = true))
    }

    @Test
    fun `아이 선택 목록의 유아 줄에는 수유가 없다`() {
        val kids = listOf(Child(2026, 5, name = "서아"), Child(2025, 1, name = "하준", serverId = "c2"))
        val rows = childOptions(kids, listOf(ServerChild(id = "c2", nickname = "하준", birthMonth = "2025-01")), null, routineOf = { FeedingRoutine() }, today = today)
        assertTrue(rows[0].line.contains("간격"))
        assertFalse(rows[1].line.contains("유아기")) // 유아기는 이름 옆 배지로 (C07, #247)
        assertFalse(rows[1].line.contains("수유"))
        assertEquals(listOf("영아기", "유아기"), rows.map { it.stage })
    }

    @Test
    fun `월령 배지는 수유와 같은 12개월 경계`() {
        val d = java.time.LocalDate.of(2026, 11, 12)
        assertEquals("영아기", ageStageLabel(Child(2025, 11, name = "열두달", birthDay = 12), d)) // 딱 12개월
        assertEquals("유아기", ageStageLabel(Child(2025, 10, name = "열석달", birthDay = 12), d)) // 13개월
        assertEquals("유아기", ageStageLabel(Child(2025, 9, name = "서아"), d)) // Figma의 14개월도 유아기 (임시 결정 2번)
        assertEquals("영아기", ageStageLabel(Child(2026, 9, name = "아기"), d))
    }

    @Test
    fun `유아기 앱 음량 칸`() {
        assertEquals(listOf(0, 1, 2), listOf(10, 30, 50).map { toddlerVolumeIndex(it) })
        assertEquals(null, toddlerVolumeIndex(null))
    }
}
