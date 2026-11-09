package io.github.haeyeonyou.familytrip.ui

import io.github.haeyeonyou.familytrip.data.Visit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.time.LocalDate
import org.junit.Test

class ExperienceLayoutTest {
    private var nextId = 1L

    private fun visit(name: String, theme: String?, date: String, role: String = "outing") =
        Visit(id = nextId++, placeName = name, role = role, theme = theme, arrivedAt = "${date}T10:00:00")

    /** Figma 시안과 같은 구성: 쇼핑·놀이 4곳, 전시·과학 3곳, 체험·클래스 2곳, 자연은 비어 있음. */
    private fun figmaVisits() = listOf(
        visit("△△ 실내 놀이터", "play", "2026-10-25"),
        visit("□□ 키즈카페", "play", "2026-10-20"),
        visit("◎◎ 백화점", "play", "2026-10-18"),
        visit("OO몰", "play", "2026-11-05"),
        visit("OO몰", "play", "2026-11-07"),
        visit("◇◇ 키즈 박물관", "exhibition", "2026-11-01"),
        visit("과학관", "exhibition", "2026-10-12"),
        visit("미술관", "exhibition", "2026-10-02"),
        visit("베이비 클래스", "experience", "2026-10-30"),
        visit("동물 체험", "experience", "2026-10-05"),
    )

    @Test
    fun 방문이_없으면_분야는_모두_비어_있고_장소는_없다() {
        val data = buildUniverse(emptyList())
        assertTrue(data.isEmpty)
        assertEquals(4, data.fields.size)
        assertTrue(data.fields.all { it.count == 0 })
        val g = computeGeometry(data)
        assertTrue(g.places.isEmpty() && g.satellites.isEmpty() && g.tagLinks.isEmpty())
        assertEquals(4, g.tags.size)
        assertTrue(g.fields.all { it.r == 18f })
    }

    @Test
    fun 식사와_카페_분야_없는_방문은_우주에_넣지_않는다() {
        val data = buildUniverse(
            listOf(visit("식당", null, "2026-11-01", role = "meal"), visit("카페", null, "2026-11-02", role = "cafe"), visit("공원", "nature", "2026-11-03")),
        )
        assertEquals(listOf("공원"), data.places.map { it.name })
    }

    @Test
    fun Figma_구성은_시안_좌표_그대로_놓인다() {
        val data = buildUniverse(figmaVisits())
        assertEquals(9, data.places.size)
        assertEquals(listOf(4, 3, 2, 0), data.fields.map { it.count })
        val g = computeGeometry(data)
        // 분야 반지름: 4→24, 3→22, 2→20, 0→18 (점선 원)
        assertEquals(listOf(24f, 22f, 20f, 18f), g.fields.map { it.r })
        // 가장 최근(11.07) OO몰은 시안의 (122.4, 257) 자리
        val oo = g.places.first { it.item.name == "OO몰" }
        assertEquals(122.4f, oo.x, 0.05f)
        assertEquals(257f, oo.y, 0.05f)
        assertTrue(oo.item.latest)
        assertEquals(1, g.places.count { it.item.latest })
        assertEquals(11f, oo.r, 0.01f) // 지름 22
        // 전시·과학의 최근 방문은 시안의 (808.4, 206)
        val museum = g.places.first { it.item.name == "◇◇ 키즈 박물관" }
        assertEquals(808.4f, museum.x, 0.05f)
        assertEquals(206f, museum.y, 0.05f)
        // 위성 점은 장소마다 2개 이상
        assertTrue(g.satellites.size >= 18)
    }

    @Test
    fun 같은_장소를_여러_번_가면_횟수만_늘고_노드는_하나다() {
        val data = buildUniverse(listOf(visit("OO몰", "play", "2026-10-01"), visit("OO몰", "play", "2026-10-09"), visit("OO몰", "play", "2026-11-02")))
        assertEquals(1, data.places.size)
        assertEquals(3, data.places.single().count)
        assertEquals(1, data.fields.first { it.key == "play" }.count) // 분야 숫자는 장소 수
    }

    @Test
    fun 방문이_아주_많아도_분야마다_장소_수는_제한되고_모두_그래프_안에_있다() {
        val many = (1..60).map { visit("장소$it", listOf("play", "exhibition", "experience", "nature")[it % 4], "2026-10-%02d".format(it % 28 + 1)) }
        val g = computeGeometry(buildUniverse(many))
        assertTrue(g.places.groupBy { it.item.field }.values.all { it.size <= MAX_PLACES_PER_FIELD })
        assertTrue(g.places.all { it.x in 0f..DESIGN_W && it.y in 0f..DESIGN_H })
        assertTrue(g.satellites.all { it.x in 0f..DESIGN_W && it.y in 0f..DESIGN_H })
        // 필드 반지름은 방문이 많아도 한도(8회분)를 넘지 않는다
        assertTrue(g.fields.all { it.r <= 32f })
    }

    @Test
    fun 한_곳뿐이면_가운데에서_먼_쪽에_놓인다() {
        val slot = placeSlots("exhibition", 1, 705f, 215f).single()
        // 가운데(470,345) → 전시 노드 방향의 연장선: 오른쪽 위
        assertTrue(slot.x > 0 && slot.y < 0)
    }

    @Test
    fun 시안에_없는_개수는_한_바퀴를_고르게_나눈다() {
        val slots = placeSlots("play", 5, 255f, 225f)
        assertEquals(5, slots.size)
        val angles = slots.map { Math.toDegrees(Math.atan2(it.y.toDouble(), it.x.toDouble())) }.map { (it + 360) % 360 }.sorted()
        val gaps = angles.zipWithNext { a, b -> b - a } + (angles.first() + 360 - angles.last())
        assertTrue(gaps.all { kotlin.math.abs(it - 72.0) < 1.0 })
    }

    @Test
    fun 코스에서_안_속성이_있는_장소만_태그_점선이_생긴다() {
        val visits = listOf(visit("OO몰", "play", "2026-11-07"), visit("공원", "nature", "2026-11-03"))
        val data = buildUniverse(visits, mapOf("OO몰" to setOf("수유실", "유모차")))
        val g = computeGeometry(data)
        assertEquals(2, g.tagLinks.size)
        val labels = g.tagLinks.map { g.tags[it.tag].label }.toSet()
        assertEquals(setOf("수유실", "유모차"), labels)
        assertTrue(g.tagLinks.all { g.places[it.place].item.name == "OO몰" })
    }

    @Test
    fun 빛_번짐은_가운데가_가장_밝고_멀어질수록_옅어진다() {
        val p = glowProfile(34f, 30f)
        assertTrue(p.zip(p.drop(1)).all { (a, b) -> a >= b - 1e-6f })
        assertTrue(p.last() < 0.05f)
        // 큰 원 가장자리는 절반 정도, 작은 원은 σ에 퍼져 중심도 옅다
        assertEquals(0.5f, blurredDisk(200f, 5f, 200f), 0.03f)
        assertTrue(blurredDisk(11f, 12.1f, 0f) < 0.4f)
        assertEquals(1f, blurredDisk(200f, 5f, 0f), 0.001f)
    }

    @Test
    fun 별은_시안의_140개이고_그래프_안에_있다() {
        assertEquals(140, STARS.size)
        assertTrue(STARS.all { it.x in 0f..DESIGN_W && it.y in 0f..DESIGN_H && it.alpha in 0f..1f && it.r > 0f })
    }

    @Test
    fun 창_크기에_맞춰_비율을_지키며_가운데에_놓인다() {
        val same = fitDesign(940f, 690f)
        assertEquals(1f, same.unit, 1e-4f)
        assertEquals(0f, same.left, 1e-3f)
        // 가로가 남는 넓은 상자
        val wide = fitDesign(1880f, 690f)
        assertEquals(1f, wide.unit, 1e-4f)
        assertEquals(470f, wide.left, 1e-3f)
        // 세로가 모자란 상자: 세로에 맞춰 줄어든다
        val short = fitDesign(940f, 345f)
        assertEquals(0.5f, short.unit, 1e-4f)
        assertEquals(235f, short.left, 1e-3f)
        // 크기를 아직 모를 때도 깨지지 않는다
        assertEquals(1f, fitDesign(0f, 0f).unit, 0f)
    }

    @Test
    fun 노드_근처를_누르면_그_장소가_골라진다() {
        val g = computeGeometry(buildUniverse(figmaVisits()))
        val fit = fitDesign(1316f, 966f) // 1.4배
        val toScreen: (Float, Float) -> Pt = { x, y -> Pt(fit.left + x * fit.unit, fit.top + y * fit.unit) }
        val oo = g.places.indexOfFirst { it.item.name == "OO몰" }
        val s = toScreen(g.places[oo].x, g.places[oo].y)
        assertEquals(oo, hitPlace(g.places, s.x + 3f, s.y - 2f, toScreen, fit.unit, 30f))
        assertNull(hitPlace(g.places, 5f, 5f, toScreen, fit.unit, 30f))
    }

    @Test
    fun 안내_배너는_가장_오래_빈_분야를_알려_준다() {
        val today = LocalDate.of(2026, 11, 9)
        // 자연만 3주 전, 나머지는 최근
        val visits = listOf(
            visit("공원", "nature", "2026-10-18"),
            visit("A", "play", "2026-11-07"),
            visit("B", "exhibition", "2026-11-05"),
            visit("C", "experience", "2026-11-04"),
        )
        val insight = experienceInsight(visits, today)
        assertEquals("자연 체험이 3주째 없어요", insight.title)
        assertEquals("공원 코스 보기", insight.action)
        assertTrue("공원" in insight.body && "초록" in insight.body)
    }

    @Test
    fun 한_번도_안_간_분야와_첫_방문_전_안내() {
        val today = LocalDate.of(2026, 11, 9)
        assertEquals("첫 나들이를 기록해 보세요", experienceInsight(emptyList(), today).title)
        val onlyPlay = experienceInsight(listOf(visit("A", "play", "2026-11-07")), today)
        assertEquals("자연 체험이 아직 없어요", onlyPlay.title)
    }

    @Test
    fun 모든_분야를_최근에_다녔으면_칭찬만_한다() {
        val today = LocalDate.of(2026, 11, 9)
        val visits = listOf("play", "exhibition", "experience", "nature").mapIndexed { i, k -> visit("장소$i", k, "2026-11-0${i + 5}") }
        val insight = experienceInsight(visits, today)
        assertEquals("골고루 다니고 있어요", insight.title)
        assertNull(insight.action)
    }

    @Test
    fun 요약은_개월수와_장소수_다녀온_분야수() {
        val today = LocalDate.of(2026, 11, 9)
        val data = buildUniverse(figmaVisits() + visit("옛 장소", "play", "2026-08-01"))
        assertEquals("지난 3개월 · 10곳 · 3개 분야", summary(data, today))
        assertNotNull(summary(buildUniverse(emptyList()), today))
        assertEquals("아직 비어 있어요 · 첫 나들이로 시작해요", summary(buildUniverse(emptyList()), today))
    }
}
