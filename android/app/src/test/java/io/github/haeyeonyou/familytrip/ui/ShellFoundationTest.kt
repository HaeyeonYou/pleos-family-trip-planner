package io.github.haeyeonyou.familytrip.ui

import androidx.compose.ui.graphics.Color
import io.github.haeyeonyou.familytrip.care.CareTone
import io.github.haeyeonyou.familytrip.vehicle.GearState
import io.github.haeyeonyou.familytrip.vehicle.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellFoundationTest {
    @Test
    fun `정차는 P단일 때만이고 기어를 모르면 주행으로 본다`() {
        assertTrue(DriveMode.from(VehicleState(gear = GearState.PARK)).isStopped)
        listOf(GearState.DRIVE, GearState.REVERSE, GearState.NEUTRAL, GearState.UNKNOWN).forEach {
            val mode = DriveMode.from(VehicleState(gear = it))
            assertTrue("$it", mode.isDriving)
            assertFalse("$it", mode.isStopped)
        }
    }

    @Test
    fun `차속 API가 없어도 정차 판단은 기어만 본다`() {
        val mode = DriveMode.from(VehicleState(gear = GearState.PARK), speedKph = 62f)
        assertTrue(mode.isStopped)
        assertNull(DriveMode.from(VehicleState(gear = GearState.DRIVE)).speedKph)
    }

    @Test
    fun `셸 변형은 주행이면 LightOn 수면이면 DarkOff 나머지는 LightOff`() {
        assertEquals(ShellVariant.LightOff, ShellVariant.of(dark = false, drive = DriveMode.Stopped))
        assertEquals(ShellVariant.DarkOff, ShellVariant.of(dark = true, drive = DriveMode.Stopped))
        assertEquals(ShellVariant.LightOn, ShellVariant.of(dark = false, drive = DriveMode.Driving))
        assertEquals(ShellVariant.LightOn, ShellVariant.of(dark = true, drive = DriveMode.Driving))
    }

    @Test
    fun `1682x1210 창에서 앱 프레임은 피그마 비율 그대로 키워 가운데에 놓인다`() {
        val g = FrameGeometry.of(1682f, 1210f)
        // 노출 영역 1280x902를 폭 기준(1682/1280)으로 키운다 (높이 기준 1210/902는 더 크다)
        assertEquals(1682f / 1280f, g.scale, 1e-4f)
        assertEquals(1256f * g.scale, g.width, 1e-2f)
        assertEquals(886f * g.scale, g.height, 1e-2f)
        assertEquals(FrameGeometry.FRAME_W / FrameGeometry.FRAME_H, g.width / g.height, 1e-4f)
        // 프레임은 창 안에 들어가고 좌우로 대칭이다
        assertEquals(1682f - g.left - g.width, g.left, 1e-2f)
        assertTrue(g.top >= 0f && g.top + g.height <= 1210f)
    }

    @Test
    fun `정확히 1280x902 창에서는 Figma 좌표 그대로다`() {
        val g = FrameGeometry.of(1280f, 902f)
        assertEquals(1f, g.scale, 1e-6f)
        assertEquals(12f, g.left, 1e-4f)
        assertEquals(8f, g.top, 1e-4f)
        assertEquals(1256f, g.width, 1e-4f)
        assertEquals(886f, g.height, 1e-4f)
    }

    @Test
    fun `좁은 창은 작은 여백만 두고 창 전체를 쓰며 배율은 줄어든다`() {
        val g = FrameGeometry.of(840f, 1210f)
        assertEquals(8f, g.left, 1e-4f)
        assertEquals(840f - 16f, g.width, 1e-4f)
        assertEquals(1210f - 16f, g.height, 1e-4f)
        assertTrue(g.scale < 1f)
    }

    @Test
    fun `그리드 열 위치는 24 432 840이고 3열째가 프레임 오른쪽 여백 24에서 끝난다`() {
        assertEquals(listOf(24, 432, 840), (0 until SitterGrid.COLUMN_COUNT).map { SitterGrid.columnX(it) })
        assertEquals(SitterGrid.FRAME_W - SitterGrid.MARGIN, SitterGrid.columnX(2) + SitterGrid.COLUMN_W)
        assertEquals(800, SitterGrid.DOUBLE_W)
        assertEquals(SitterGrid.FRAME_H - SitterGrid.MARGIN, SitterGrid.BODY_TOP + SitterGrid.BODY_H)
    }

    @Test
    fun `팔레트 7색은 Figma 값이다`() {
        assertEquals(
            listOf(0xFF4D55F0, 0xFF1F80FF, 0xFF8A1EE6, 0xFFEEEFFE, 0xFFE9A6F7, 0xFF22C3EE, 0xFF7E9BF5).map { Color(it) },
            with(SitterPalette) { listOf(Primary, Blue, Violet, Soft, Pink, Cyan, Periwinkle) },
        )
        assertEquals(listOf(0f, 0.55f, 1f), SitterPalette.GradientStops.map { it.first })
        assertEquals(listOf(SitterPalette.Blue, Color(0xFF5B4BF5), SitterPalette.Violet), SitterPalette.GradientStops.map { it.second })
    }

    @Test
    fun `수면 톤은 Dark 기본 톤은 Light 상황 톤은 옛 톤 색을 쓴다`() {
        assertSame(SitterSurface.Light, SitterSurface.of(CareTone.NONE))
        assertSame(SitterSurface.Dark, SitterSurface.of(CareTone.SLEEP))
        assertTrue(SitterSurface.Dark.dark)
        assertEquals(CareTone.FEED.panel, SitterSurface.of(CareTone.FEED).app)
        assertEquals(CareTone.FEED.ink, SitterSurface.of(CareTone.FEED).ink)
    }

    private fun assertSame(a: Any, b: Any) = org.junit.Assert.assertSame(a, b)
}
