package io.github.haeyeonyou.familytrip.care

import io.github.haeyeonyou.familytrip.data.ClimateSettings
import io.github.haeyeonyou.familytrip.data.PresetState
import io.github.haeyeonyou.familytrip.data.withApplyAt
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

// 케어 모드 적용 시점 (C08, #168)
class ModeApplyAtTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private fun at(h: Int, m: Int) = LocalDateTime.of(2026, 11, 11, h, m).atZone(seoul).toInstant().toEpochMilli()
    private fun preset(slot: String, applyAt: String? = null) = PresetState(slot = slot, applyAt = applyAt).withApplyAt()

    @Test
    fun `슬롯 기본값은 예전 고정 문구와 같다`() {
        assertEquals(listOf("boarding", "manual", "manual"), listOf("boarding", "nap", "feeding_stop").map { applyAtOf(PresetState(slot = it)) })
        // 모르는 값은 기본값으로
        assertEquals("manual", applyAtOf(PresetState(slot = "nap", applyAt = "later")))
        assertEquals(listOf("탑승하면 바로", "출발 시각", "직접 실행"), APPLY_AT_CHOICES.map { it.second })
    }

    @Test
    fun `서버 값은 설정 묶음에 실리고 편집한 값이 이긴다`() {
        val p = preset("nap", "departure")
        assertEquals("departure", p.settings.applyAt)
        assertEquals("boarding", applyAtOf(p.copy(settings = p.settings.copy(applyAt = "boarding"))))
        // 예전 서버(apply_at 없음)는 그대로
        assertNull(PresetState(slot = "nap").withApplyAt().settings.applyAt)
    }

    @Test
    fun `적용 시점은 PUT 본문에 실리고 비면 보내지 않는다`() {
        val json = Json
        assertTrue(json.encodeToString(ClimateSettings.serializer(), ClimateSettings(applyAt = "departure")).contains("\"apply_at\":\"departure\""))
        assertFalse(json.encodeToString(ClimateSettings.serializer(), ClimateSettings()).contains("apply_at"))
    }

    @Test
    fun `탑승·출발 시각에 켤 모드를 슬롯 순서로 고른다`() {
        val presets = listOf(preset("feeding_stop", "departure"), preset("boarding"), preset("nap", "departure"))
        assertEquals(listOf("boarding"), autoSlots(presets, APPLY_BOARDING))
        assertEquals(listOf("nap", "feeding_stop"), autoSlots(presets, APPLY_DEPARTURE))
        assertEquals(emptyList<String>(), autoSlots(presets, APPLY_MANUAL).filter { it == "boarding" })
        assertEquals(emptyList<String>(), autoSlots(emptyList(), APPLY_BOARDING))
    }

    @Test
    fun `출발 시각은 고른 코스의 오늘 시각이고 지났거나 코스가 없으면 켜지 않는다`() {
        assertEquals(30 * 60_000L, departureDelay("10:30", at(10, 0), seoul))
        assertNull(departureDelay("09:50", at(10, 0), seoul))
        assertNull(departureDelay("10:00", at(10, 0), seoul))
        assertNull(departureDelay(null, at(10, 0), seoul))
        assertNull(departureDelay("", at(10, 0), seoul))
        assertNull(departureDelay("곧", at(10, 0), seoul))
    }

    @Test
    fun `C08 안내와 켠 뒤 안내 문구`() {
        assertEquals("코스를 고르면 출발 시각에 켜져요", departureHint(null))
        assertEquals("고른 코스 출발 시각 10:30에 켜져요 · 차 앱이 켜져 있을 때만", departureHint("10:30"))
        assertEquals("탑승해서 베이비케어 모드를 켰어요", autoApplyNotice(APPLY_BOARDING, listOf("boarding")))
        assertEquals("출발 시각이라 수면 모드 · 수유 정차 모드를 켰어요", autoApplyNotice(APPLY_DEPARTURE, listOf("nap", "feeding_stop")))
    }
}
