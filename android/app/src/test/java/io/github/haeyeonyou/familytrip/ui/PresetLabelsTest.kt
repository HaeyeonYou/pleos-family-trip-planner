package io.github.haeyeonyou.familytrip.ui

import io.github.haeyeonyou.familytrip.data.ClimateSettings
import io.github.haeyeonyou.familytrip.data.PresetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresetLabelsTest {
    private fun preset(settings: ClimateSettings, whenLabel: String = "정차 시") = PresetState(slot = "nap", whenLabel = whenLabel, settings = settings)

    @Test
    fun 풍량_단계를_말로_바꾼다() {
        assertEquals("미풍", windLabel(ClimateSettings(fanSpeed = 1)))
        assertEquals("약풍", windLabel(ClimateSettings(fanSpeed = 2)))
        assertEquals("중풍", windLabel(ClimateSettings(fanSpeed = 4)))
        assertEquals("강풍", windLabel(ClimateSettings(fanSpeed = 6)))
        assertEquals("공조 끔", windLabel(ClimateSettings(power = false)))
    }

    @Test
    fun 음량을_말로_바꾼다() {
        assertEquals("음량 최소", volumeLabel(10))
        assertEquals("음량 낮게", volumeLabel(30))
        assertEquals("음량 보통", volumeLabel(50))
        assertEquals("음량 높게", volumeLabel(70))
        assertNull(volumeLabel(null))
    }

    @Test
    fun 디자인의_한_줄_요약과_같은_모양이다() {
        assertEquals("23°C · 미풍 · 음량 최소", presetShortSummary(preset(ClimateSettings(temperature = 23f, fanSpeed = 1, mediaVolume = 10))))
        assertEquals("24°C · 약풍 · 음량 낮게", presetShortSummary(preset(ClimateSettings(temperature = 24f, fanSpeed = 2, mediaVolume = 30))))
        assertEquals("22.5°C · 미풍 · 정차 시", presetShortSummary(preset(ClimateSettings(temperature = 22.5f, fanSpeed = 1, mediaVolume = null))))
    }
}
