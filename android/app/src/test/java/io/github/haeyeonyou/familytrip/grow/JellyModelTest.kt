package io.github.haeyeonyou.familytrip.grow

import io.github.haeyeonyou.familytrip.data.Visit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class JellyModelTest {
    private var nextId = 1L
    private val now = LocalDateTime.parse("2026-11-10T12:00:00")
    private val today: LocalDate = now.toLocalDate()

    private fun visit(
        name: String,
        date: String,
        theme: String? = null,
        role: String = "outing",
        stay: Long? = 60,
        lat: Double? = null,
        amenities: List<String> = emptyList(),
    ): Visit {
        val arrived = LocalDateTime.parse("${date}T10:00:00")
        return Visit(
            id = nextId++, placeName = name, role = role, theme = theme, arrivedAt = arrived.toString(),
            departedAt = stay?.let { arrived.plusMinutes(it).toString() },
            latitude = lat, longitude = lat?.let { 127.0 }, amenities = amenities,
        )
    }

    @Test
    fun 십분_넘게_머문_나들이_식사_카페만_경험으로_센다() {
        assertTrue(isExperience(visit("숲", "2026-11-01", stay = 10), now))
        assertFalse(isExperience(visit("숲", "2026-11-01", stay = 9), now))
        assertFalse(isExperience(visit("집", "2026-11-01", role = "home"), now))
        assertTrue(isExperience(visit("식당", "2026-11-01", role = "meal"), now))
        // 떠난 기록이 없으면 지금까지 머문 시간으로 본다 (웹에서 받은 지난 기록, 지금 머무는 곳)
        assertTrue(isExperience(visit("숲", "2026-11-01", stay = null), now))
        val justArrived = Visit(1, "숲", "outing", arrivedAt = now.minusMinutes(5).toString())
        assertFalse(isExperience(justArrived, now))
    }

    @Test
    fun 장소_이름과_역할로_12분류를_정한다() {
        assertEquals(JellyKind.ART, classify("국립현대미술관", "outing", "exhibition"))
        assertEquals(JellyKind.SCIENCE, classify("◇◇ 키즈 박물관", "outing", "exhibition"))
        assertEquals(JellyKind.NATURE, classify("아차산 생태공원", "outing", "nature"))
        assertEquals(JellyKind.ANIMAL, classify("서울대공원 동물원", "outing", "nature"))
        assertEquals(JellyKind.WATER, classify("키즈 워터파크", "outing", "play"))
        assertEquals(JellyKind.MOVE, classify("△△ 실내 놀이터", "outing", "play"))
        assertEquals(JellyKind.MUSIC, classify("어린이 인형극 극장", "outing", "experience"))
        assertEquals(JellyKind.BOOK, classify("구리시립 어린이도서관", "outing", null))
        assertEquals(JellyKind.BOOK, classify("그림책 북카페", "cafe", null))
        assertEquals(JellyKind.FOOD, classify("숲속 카페", "cafe", null))
        assertEquals(JellyKind.FOOD, classify("숲속 카페", "outing", "nature")) // 서버에서 받은 기록
        assertEquals(JellyKind.MOVE, classify("□□ 키즈카페", "outing", "play"))
        assertEquals(JellyKind.FRIEND, classify("구리시 육아종합지원센터", "outing", "experience"))
        assertEquals(JellyKind.MAKE, classify("베이비 쿠킹 클래스", "outing", "experience"))
        assertEquals(JellyKind.TRAVEL, classify("남산 전망대", "outing", null))
        // 이름으로 못 정하면 서버 분야, 그것도 없으면 여행
        assertEquals(JellyKind.MOVE, classify("◎◎ 백화점", "outing", "play"))
        assertEquals(JellyKind.ANIMAL, classify("ㅁㅁ", "outing", "pet"))
        assertEquals(JellyKind.TRAVEL, classify("ㅁㅁ", "outing", null))
    }

    @Test
    fun 성장_단계는_누적_경험_수로_방울_몽글_탄생() {
        assertEquals(JellyStage.NONE, JellyStage.of(0))
        assertEquals(JellyStage.DROP, JellyStage.of(1))
        assertEquals(JellyStage.DROP, JellyStage.of(3))
        assertEquals(JellyStage.PUFF, JellyStage.of(4))
        assertEquals(JellyStage.PUFF, JellyStage.of(9))
        assertEquals(JellyStage.BORN, JellyStage.of(10))
        assertEquals("유니콘 방울", stageName(JellyKind.ART, JellyStage.DROP))
        assertEquals("유니콘 몽글", stageName(JellyKind.ART, JellyStage.PUFF))
        assertEquals("상상의 유니콘형", stageName(JellyKind.ART, JellyStage.BORN))
    }

    @Test
    fun 유형은_최근_6개월_1위와_2위() {
        val visits = List(3) { visit("미술관", "2026-10-0${it + 1}") } +
            List(2) { visit("생태공원", "2026-10-1${it + 1}") } +
            // 6개월보다 오래된 과학관 5번은 유형에서 빠지지만 성장(누적)에는 남는다
            List(5) { visit("과학관", "2026-03-0${it + 1}") }
        val states = jellyStates(experiences(visits, now), today)
        assertEquals(5, states.first { it.kind == JellyKind.SCIENCE }.count)
        assertEquals(0, states.first { it.kind == JellyKind.SCIENCE }.recent)
        val type = jellyType(states)!!
        assertEquals(JellyKind.ART, type.main)
        assertEquals(JellyKind.NATURE, type.spoon)
        assertEquals("상상의 유니콘형 + 숲 곰 한 스푼", type.line)
        assertNull(jellyType(jellyStates(emptyList(), today)))
    }

    @Test
    fun 비어_있는_별과_절반_넘는_분류() {
        val visits = List(4) { visit("실내 놀이터", "2026-11-0${it + 1}") } + visit("생태공원", "2026-11-06")
        val states = jellyStates(experiences(visits, now), today)
        assertEquals(JellyKind.SCIENCE, suggestEmpty(states)) // 자연은 가 봤으니 다음 순서
        assertEquals(JellyKind.MOVE, dominant(states))
        assertEquals("몸놀이 경험이 절반을 넘었어요 · 비어 있는 과학·박물관 별도 키워 볼까요?", universeHint(states))
        assertNull(dominant(jellyStates(experiences(visits.take(3), now), today))) // 4개 미만은 판단 안 함
        assertEquals("나들이 장소에 10분 이상 머물면 첫 별이 생겨요", universeHint(jellyStates(emptyList(), today)))
    }

    @Test
    fun 이번_달_타일과_새로_해본_분야() {
        val visits = listOf(
            visit("미술관", "2026-10-03"), visit("생태공원", "2026-10-20"),
            visit("미술관", "2026-11-02"), visit("미술관", "2026-11-04"), visit("키즈 워터파크", "2026-11-05"), visit("도서관", "2026-11-06"),
        )
        val list = experiences(visits, now)
        val tile = monthTile(list, today)
        assertEquals(3, tile.places) // 미술관(같은 곳 2번은 1) + 워터파크 + 도서관
        assertEquals(1, tile.delta)
        assertEquals("지난달보다 +1곳", monthDeltaText(1))
        assertEquals("지난달보다 -2곳", monthDeltaText(-2))
        assertEquals("지난달과 같아요", monthDeltaText(0))
        val fresh = newKindsThisMonth(jellyStates(list, today), today)
        assertEquals(listOf(JellyKind.WATER, JellyKind.BOOK), fresh) // 미술은 지난달에 처음 해 봄
    }

    @Test
    fun 다시_가볼_곳은_일주일_지난_곳을_많이_간_순() {
        val visits = listOf(
            visit("아차산 생태공원", "2026-10-20", lat = 37.5), visit("아차산 생태공원", "2026-10-13"),
            visit("숲속 카페", "2026-10-12", role = "cafe", amenities = listOf("nursing_room")),
            visit("키즈풀 실내놀이터", "2026-10-19"),
            visit("어제 간 곳", "2026-11-08"),
        )
        val rows = revisits(experiences(visits, now), today)
        assertEquals(listOf("아차산 생태공원", "키즈풀 실내놀이터", "숲속 카페"), rows.map { it.placeName })
        assertEquals(37.5, rows[0].latitude!!, 0.0)
        assertNull(rows[1].latitude)
        assertEquals("3주 전 · 자연·숲", revisitCaption(rows[0], today))
        assertEquals("4주 전 · 수유실 있음", revisitCaption(rows[2], today))
        assertEquals("10.01", agoText(LocalDate.parse("2026-10-01"), today))
        assertEquals("3일 전", agoText(LocalDate.parse("2026-11-07"), today))
        assertEquals("오늘", agoText(today, today))
    }

    @Test
    fun 탄생은_열_번째_경험에서_한_번만() {
        val nine = List(9) { visit("미술관", "2026-10-${10 + it}") }
        assertTrue(birthsIn(nine, emptySet(), now).isEmpty())
        val ten = nine + visit("갤러리", "2026-11-01")
        assertEquals(listOf(JellyKind.ART), birthsIn(ten, emptySet(), now))
        assertTrue(birthsIn(ten, setOf("ART"), now).isEmpty())
        assertEquals("서아의 유니콘 캐릭터가 태어났어요", birthSpeech("서아", listOf(JellyKind.ART)))
        assertEquals("우리 아이의 유니콘, 공룡 캐릭터가 태어났어요", birthSpeech("", listOf(JellyKind.ART, JellyKind.SCIENCE)))
    }
}
