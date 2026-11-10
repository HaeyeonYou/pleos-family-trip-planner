package io.github.haeyeonyou.familytrip.care

import io.github.haeyeonyou.familytrip.data.ClimateSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModeEditTest {
    @Test
    fun `온도는 0점5도씩, 16~30도 안에서`() {
        assertEquals(24.5f, stepTemperature(24f, up = true))
        assertEquals(16f, stepTemperature(16f, up = false))
        assertEquals(30f, stepTemperature(29.8f, up = true))
    }

    @Test
    fun `풍량 선택은 카드 요약과 같은 단계`() {
        assertEquals(0, fanChoiceIndex(1))
        assertEquals(1, fanChoiceIndex(2))
        assertEquals(2, fanChoiceIndex(3))
        assertEquals(3, fanChoiceIndex(6))
        assertEquals(0, fanChoiceIndex(0))
    }

    @Test
    fun `음량 빠른 선택에 없으면 직접`() {
        assertEquals(0, volumeChoiceIndex(10))
        assertEquals(2, volumeChoiceIndex(50))
        assertNull(volumeChoiceIndex(35))
        assertNull(volumeChoiceIndex(null))
        assertEquals(35, snapVolume(36.4f))
        assertEquals(35, stepVolume(30, up = true))
        assertEquals(25, stepVolume(null, up = false))
        assertEquals(100, stepVolume(100, up = true))
    }

    @Test
    fun `바뀐 모드만 저장한다`() {
        val a = ClimateSettings(temperature = 24f)
        val original = mapOf("boarding" to a, "nap" to a)
        val drafts = mapOf("boarding" to a, "nap" to a.copy(temperature = 23f))
        assertEquals(setOf("nap"), changedModes(original, drafts).keys)
        assertEquals("낮잠 · 수면", modeTabLabel("nap"))
    }
}
