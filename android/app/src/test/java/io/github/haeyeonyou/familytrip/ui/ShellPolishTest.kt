package io.github.haeyeonyou.familytrip.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellPolishTest {
    @Test
    fun `서버가 주는 날씨 문구마다 아이콘이 정해진다`() {
        val expected = mapOf(
            "맑음" to WeatherKind.SUN,
            "구름 많음" to WeatherKind.PARTLY_CLOUDY,
            "흐림" to WeatherKind.CLOUD,
            "비" to WeatherKind.RAIN,
            "비/눈" to WeatherKind.RAIN,
            "빗방울" to WeatherKind.RAIN,
            "빗방울/눈날림" to WeatherKind.RAIN,
            "눈" to WeatherKind.SNOW,
            "눈날림" to WeatherKind.SNOW,
            "강수 없음" to WeatherKind.SUN,
            "" to WeatherKind.SUN,
        )
        expected.forEach { (text, kind) -> assertEquals(text, kind, weatherKindOf(text)) }
    }

    @Test
    fun `지역이 붙어도 날씨 문구만 본다`() {
        assertEquals(WeatherKind.CLOUD, weatherKindOf("흐림 · 구리시"))
        assertEquals(WeatherKind.SUN, weatherKindOf("맑음 · 비산동"))
        assertEquals(WeatherKind.SNOW, weatherKindOf("눈 · 남양주시"))
    }

    @Test
    fun `알림 띠는 첫 실행 온보딩 동안 숨긴다`() {
        assertFalse(notificationBannerVisible(allowed = false, onboarding = true))
        assertTrue(notificationBannerVisible(allowed = false, onboarding = false))
        assertFalse(notificationBannerVisible(allowed = true, onboarding = false))
    }
}
