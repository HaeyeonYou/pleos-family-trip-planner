package io.github.haeyeonyou.familytrip.ui

import io.github.haeyeonyou.familytrip.data.Visit
import io.github.haeyeonyou.familytrip.grow.JellyKind
import io.github.haeyeonyou.familytrip.grow.JellyStage
import io.github.haeyeonyou.familytrip.grow.experiences
import io.github.haeyeonyou.familytrip.grow.jellyStates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.hypot

/** G01 경험 젤리 궤도 배치 (#216). */
class ExperienceLayoutTest {
    private var nextId = 1L
    private val now = LocalDateTime.parse("2026-11-11T12:00:00")

    private fun visits(name: String, n: Int, from: String = "2026-09-01") = (0 until n).map {
        val d = LocalDate.parse(from).plusDays(it.toLong())
        Visit(id = nextId++, placeName = name, role = "outing", arrivedAt = "${d}T10:00:00", departedAt = "${d}T11:00:00")
    }

    private fun orbitOf(vararg v: List<Visit>) = computeJellyOrbit(jellyStates(experiences(v.toList().flatten(), now), now.toLocalDate()))

    @Test
    fun 가장_많이_간_분야가_가운데_크게_나머지는_궤도에() {
        val o = orbitOf(visits("해움미술관", 12), visits("역삼개나리공원", 7), visits("소리울도서관", 3))
        val c = o.center!!
        assertEquals(JellyKind.ART, c.kind)
        assertEquals(CENTER.x, c.x)
        assertEquals(CENTER_FRAME, c.frame)
        val nature = o.nodes.first { it.kind == JellyKind.NATURE }
        val book = o.nodes.first { it.kind == JellyKind.BOOK }
        // 몽글은 바깥 궤도, 방울은 조금 안쪽
        assertEquals(1f, ringRatio(nature.x, nature.y), 0.01f)
        assertEquals(DROP_RING, ringRatio(book.x, book.y), 0.01f)
        assertTrue(nature.frame < c.frame)
    }

    private fun ringRatio(x: Float, y: Float): Float = hypot((x - CENTER.x) / OUTER_RX, (y - CENTER.y) / OUTER_RY)

    @Test
    fun 빈_분야는_최대_4개_권하는_순서로_점선_원() {
        val o = orbitOf(visits("역삼개나리공원", 2))
        val empty = o.nodes.filter { it.empty }
        assertEquals(MAX_EMPTY, empty.size)
        // 자연은 다녀왔으니 과학·책·만들기·동물
        assertEquals(listOf(JellyKind.SCIENCE, JellyKind.BOOK, JellyKind.MAKE, JellyKind.ANIMAL).toSet(), empty.map { it.kind }.toSet())
        assertEquals("과학 ?", orbitLabel(empty.first { it.kind == JellyKind.SCIENCE }.state))
        assertEquals(5, o.nodes.size)
    }

    @Test
    fun 방문이_없으면_가운데_없이_빈_원_4개만() {
        val o = orbitOf()
        assertNull(o.center)
        assertEquals(4, o.nodes.size)
        assertTrue(o.nodes.all { it.empty })
    }

    @Test
    fun 열두_분야를_다_가도_가운데_하나와_궤도_열하나() {
        val names = listOf("해움미술관", "경기도행정역사관", "역삼개나리공원", "동물원", "물놀이장", "놀이터", "공연장", "소리울도서관", "식당", "육아종합지원센터", "공방", "전망대")
        val o = orbitOf(*names.mapIndexed { i, n -> visits(n, 12 - i) }.toTypedArray())
        assertEquals(12, o.nodes.size)
        assertEquals(1, o.nodes.count { it.center })
        assertTrue(o.nodes.none { it.empty })
        // 모두 그림 영역 안 (프레임 반쪽 여유는 바깥 궤도 크기로 보장)
        o.nodes.forEach { assertTrue(it.x in 0f..DESIGN_W && it.y in 0f..DESIGN_H) }
    }

    @Test
    fun 빈_원은_한쪽에_몰리지_않게_사이사이에() {
        val filled = (1..4).map { io.github.haeyeonyou.familytrip.grow.JellyState(JellyKind.entries[it], 5, 5, 1, null, null) }
        val empty = (8..9).map { io.github.haeyeonyou.familytrip.grow.JellyState(JellyKind.entries[it], 0, 0, 0, null, null) }
        val order = orbitOrder(filled, empty).map { it.count == 0 }
        assertEquals(6, order.size)
        assertEquals(listOf(false, true, false, false, true, false), order)
    }

    @Test
    fun 누르면_가까운_젤리_빈_곳은_없음() {
        val o = orbitOf(visits("해움미술관", 5), visits("역삼개나리공원", 2))
        assertEquals(JellyKind.ART, hitOrbit(o, CENTER.x + 10f, CENTER.y, 20f))
        val n = o.nodes.first { it.kind == JellyKind.NATURE }
        assertEquals(JellyKind.NATURE, hitOrbit(o, n.x + 5f, n.y, 20f))
        assertNull(hitOrbit(o, 5f, 5f, 20f))
        assertEquals(JellyStage.DROP, n.state.stage)
    }

    @Test
    fun 상자에_비율을_지켜_맞춘다() {
        val fit = fitDesign(DESIGN_W * 2, DESIGN_H * 3)
        assertEquals(2f, fit.unit)
        assertEquals(0f, fit.left)
        assertTrue(abs(fit.top - DESIGN_H / 2f) < 0.01f)
    }
}
