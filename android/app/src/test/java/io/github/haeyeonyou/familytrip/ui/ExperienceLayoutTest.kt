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
import java.time.LocalDateTime
import kotlin.math.abs

class ExperienceLayoutTest {
    private var nextId = 1L
    private val now = LocalDateTime.parse("2026-11-10T12:00:00")

    private fun visit(name: String, date: String, theme: String? = null) =
        Visit(id = nextId++, placeName = name, role = "outing", theme = theme, arrivedAt = "${date}T10:00:00", departedAt = "${date}T11:00:00")

    @Test
    fun 분류는_12시부터_시계_방향_고정_자리() {
        val top = kindAnchor(JellyKind.ART)
        assertEquals(CENTER.x, top.x, 0.01f)
        assertEquals(CENTER.y - RING_RY, top.y, 0.01f)
        val right = kindAnchor(JellyKind.ANIMAL) // 4번째(30도씩 3칸) = 3시
        assertEquals(CENTER.x + RING_RX, right.x, 0.01f)
        assertEquals(CENTER.y, right.y, 0.01f)
    }

    @Test
    fun 젤리_크기는_단계마다_커지고_추천_빈_별은_점선_크기() {
        assertTrue(jellyRadius(JellyStage.NONE) < jellyRadius(JellyStage.DROP))
        assertTrue(jellyRadius(JellyStage.DROP) < jellyRadius(JellyStage.PUFF))
        assertTrue(jellyRadius(JellyStage.PUFF) < jellyRadius(JellyStage.BORN))
        val states = jellyStates(emptyList(), now.toLocalDate())
        val g = computeJellyGeometry(states, emptyList(), JellyKind.NATURE)
        assertEquals(12, g.kinds.size)
        assertEquals(22f, g.kinds.first { it.state.kind == JellyKind.NATURE }.r)
        assertEquals(jellyRadius(JellyStage.NONE), g.kinds.first { it.state.kind == JellyKind.ART }.r)
    }

    @Test
    fun 장소는_분류마다_최근_두_곳만_바깥쪽에_놓고_영역_안에_둔다() {
        val visits = listOf(
            visit("미술관 A", "2026-11-01"), visit("미술관 B", "2026-11-02"), visit("미술관 C", "2026-11-03"),
            visit("생태공원", "2026-10-01"),
        )
        val list = experiences(visits, now)
        val places = growPlaces(list)
        assertEquals("미술관 C", places.first().name)
        assertTrue(places.first().latest)
        val g = computeJellyGeometry(jellyStates(list, now.toLocalDate()), places, null)
        val art = g.places.filter { it.place.kind == JellyKind.ART }
        assertEquals(listOf("미술관 C", "미술관 B"), art.map { it.place.name })
        val node = g.kinds.first { it.state.kind == JellyKind.ART }
        // 12시 분류의 장소는 분류보다 위(가운데에서 먼 쪽)
        assertTrue(art.all { it.y < node.y })
        g.places.forEach { assertTrue(it.x in 0f..DESIGN_W && it.y in 0f..DESIGN_H) }
    }

    @Test
    fun 누르면_가까운_장소를_먼저_찾고_없으면_분류() {
        val list = experiences(listOf(visit("생태공원", "2026-11-01")), now)
        val g = computeJellyGeometry(jellyStates(list, now.toLocalDate()), growPlaces(list), null)
        val p = g.places.single()
        assertEquals(UniverseHit.Place(0), hitUniverse(g, p.x + 2f, p.y, 24f))
        val k = g.kinds.first { it.state.kind == JellyKind.NATURE }
        assertEquals(UniverseHit.Kind(JellyKind.NATURE), hitUniverse(g, k.x, k.y, 10f))
        assertNull(hitUniverse(g, CENTER.x, CENTER.y, 10f))
    }

    @Test
    fun 상자에_비율을_지켜_맞춘다() {
        val fit = fitDesign(DESIGN_W * 2, DESIGN_H * 3)
        assertEquals(2f, fit.unit)
        assertEquals(0f, fit.left)
        assertTrue(abs(fit.top - DESIGN_H / 2f) < 0.01f)
    }

    @Test
    fun 성장_안내_문구() {
        val states = jellyStates(experiences(List(5) { visit("숲 공원", "2026-11-0${it + 1}") }, now), now.toLocalDate())
        assertEquals("자연·숲 경험 5개 · 탄생까지 5개", growthCaption(states.first { it.kind == JellyKind.NATURE }))
        assertEquals("과학·박물관 나들이를 하면 별이 생겨요", growthCaption(states.first { it.kind == JellyKind.SCIENCE }))
    }

    @Test
    fun 원본_그림은_12종_3단계가_모두_다르고_문구는_자랄_수_있어요로_끝난다() {
        val ids = JellyKind.entries.flatMap { k -> listOf(JellyStage.DROP, JellyStage.PUFF, JellyStage.BORN).map { jellyRes(k, it) } }
        assertEquals(36, ids.toSet().size)
        assertEquals(jellyRes(JellyKind.ART, JellyStage.DROP), jellyRes(JellyKind.ART, JellyStage.NONE))
        assertTrue(JellyKind.entries.all { it.power.endsWith("자랄 수 있어요") })
        assertEquals(listOf("음악", "맛", "몸놀이", "여행"), listOf(JellyKind.MUSIC, JellyKind.FOOD, JellyKind.MOVE, JellyKind.TRAVEL).map { it.chip })
    }
}
