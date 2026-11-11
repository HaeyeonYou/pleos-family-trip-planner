package io.github.haeyeonyou.familytrip.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 좁은 창(분할 화면) 배치: 폭 → 열 수, 캔버스 배율 (#245) */
class NarrowLayoutTest {

    @Test
    fun `캔버스 폭으로 열 수를 정한다`() {
        assertEquals(3, SitterGrid.columnsFor(1256f))
        assertEquals(3, SitterGrid.columnsFor(SitterGrid.STACK_BELOW_W.toFloat()))
        assertEquals(2, SitterGrid.columnsFor(SitterGrid.STACK_BELOW_W - 1f))
        assertEquals(2, SitterGrid.columnsFor(848f))
        assertEquals(1, SitterGrid.columnsFor(847f))
        assertEquals(1, SitterGrid.columnsFor(440f))
    }

    @Test
    fun `전체 화면은 지금과 같은 3열 캔버스와 배율`() {
        // 에뮬레이터 전체 화면 프레임 (1256x886을 약 1.31배)
        val s = 1682f / 1280f
        val l = SitterGrid.layoutFor(1256 * s, 886 * s)
        assertEquals(3, l.columns)
        assertFalse(l.stacked)
        assertEquals(s, l.scale, 0.001f)
        assertEquals(1256f, l.width, 0.01f)
        assertEquals(886f, l.height, 0.01f)
    }

    @Test
    fun `분할 칸은 높이 기준 배율을 유지하고 1열로 쌓는다`() {
        // 분할 칸 약 840dp → 셸 여백 8을 빼고 824 x 1169
        val l = SitterGrid.layoutFor(824f, 1169f)
        assertTrue(l.stacked)
        assertEquals(1, l.columns)
        assertEquals(1169f / 886f, l.scale, 0.001f)
        assertEquals(886f, l.height, 0.01f)
        assertEquals(824f / (1169f / 886f), l.width, 0.01f)
    }

    @Test
    fun `조금 좁은 창은 2열`() {
        val l = SitterGrid.layoutFor(1150f, 1169f)
        assertEquals(2, l.columns)
        assertEquals(1169f / 886f, l.scale, 0.001f)
    }

    @Test
    fun `열 하나가 안 들어갈 만큼 좁으면 그만큼만 줄인다`() {
        val l = SitterGrid.layoutFor(400f, 1169f)
        assertEquals(1, l.columns)
        assertEquals(400f / SitterGrid.ONE_COLUMN_MIN_W, l.scale, 0.001f)
        assertEquals(SitterGrid.ONE_COLUMN_MIN_W.toFloat(), l.width, 0.01f)
    }

    @Test
    fun `쌓을 때 칸을 열 수만큼 줄로 나눈다`() {
        assertEquals(listOf(listOf(0 to 1), listOf(1 to 1), listOf(2 to 1)), stackRows(listOf(1, 1, 1), 1))
        assertEquals(listOf(listOf(0 to 1, 1 to 1), listOf(2 to 1)), stackRows(listOf(1, 1, 1), 2))
        // 넓은 열(2칸)은 1열이면 한 칸으로 줄고, 2열이면 한 줄을 다 쓴다
        assertEquals(listOf(listOf(0 to 1), listOf(1 to 1)), stackRows(listOf(2, 1), 1))
        assertEquals(listOf(listOf(0 to 2), listOf(1 to 1)), stackRows(listOf(2, 1), 2))
    }
}
