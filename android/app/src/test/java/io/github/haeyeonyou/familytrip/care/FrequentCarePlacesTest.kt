package io.github.haeyeonyou.familytrip.care

import io.github.haeyeonyou.familytrip.data.CarePlace
import io.github.haeyeonyou.familytrip.data.LatLng
import io.github.haeyeonyou.familytrip.data.Visit
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FrequentCarePlacesTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val here = LatLng(37.4979, 127.0276)

    private var nextId = 1L

    private fun visit(
        name: String,
        date: String,
        key: String? = "tour:$name",
        amenities: List<String> = emptyList(),
        lat: Double? = 37.5079,
        lng: Double? = 127.0276,
        role: String = "outing",
    ) = Visit(
        id = nextId++, placeName = name, role = role, placeKey = key, arrivedAt = "${date}T10:00:00",
        latitude = lat, longitude = lng, amenities = amenities,
    )

    private fun care(name: String, lat: Double = 37.5079, lng: Double = 127.0276) =
        CarePlace(name = name, latitude = lat, longitude = lng, distanceM = 1200, driveMinutes = 3)

    private fun repeat(name: String, times: Int, amenities: List<String> = listOf("nursing_room"), lat: Double? = 37.5079, key: String? = "tour:$name") =
        (1..times).map { visit(name, "2026-11-0$it", key, amenities, lat) }

    @Test
    fun 수유실이_확인된_곳을_많이_간_순으로_고른다() {
        val visits = repeat("구리시 육아종합지원센터", 3) + repeat("스타필드", 5) + repeat("공원", 4, amenities = emptyList())
        val result = FrequentCarePlaces.from(visits, here, emptyList(), today)
        assertEquals(listOf("스타필드", "구리시 육아종합지원센터"), result.map { it.name })
        assertEquals(5, result[0].visitCount)
    }

    @Test
    fun 한_번만_간_곳은_자주_가는_곳이_아니다() {
        assertTrue(FrequentCarePlaces.from(repeat("서울숲", 1), here, emptyList(), today).isEmpty())
    }

    @Test
    fun 최대_두_곳까지만_보인다() {
        val visits = repeat("가", 4) + repeat("나", 3) + repeat("다", 2)
        assertEquals(listOf("가", "나"), FrequentCarePlaces.from(visits, here, emptyList(), today).map { it.name })
    }

    @Test
    fun 같은_횟수면_최근에_간_곳이_앞이다() {
        val visits = listOf(visit("옛곳", "2026-10-01"), visit("옛곳", "2026-10-02", amenities = listOf("nursing_room"))) +
            listOf(visit("새곳", "2026-11-05", amenities = listOf("nursing_room")), visit("새곳", "2026-11-09", amenities = listOf("nursing_room")))
        assertEquals(listOf("새곳", "옛곳"), FrequentCarePlaces.from(visits, here, emptyList(), today).map { it.name })
    }

    @Test
    fun 석_달_넘게_지난_방문은_세지_않는다() {
        val visits = listOf(
            visit("옛곳", "2026-07-01", amenities = listOf("nursing_room")),
            visit("옛곳", "2026-07-02", amenities = listOf("nursing_room")),
            visit("옛곳", "2026-11-01", amenities = listOf("nursing_room")),
        )
        assertTrue(FrequentCarePlaces.from(visits, here, emptyList(), today).isEmpty())
    }

    @Test
    fun 수유실_확인이_없어도_케어_후보와_이름이_같으면_케어_장소다() {
        val visits = repeat("구리시 육아종합지원센터", 2, amenities = emptyList())
        val result = FrequentCarePlaces.from(visits, here, listOf(care("구리시 육아종합지원센터")), today)
        assertEquals(1, result.size)
        assertTrue("확인된 시설이 없으면 칩을 그리지 않는다", result[0].amenities.isEmpty())
    }

    @Test
    fun 좌표를_모르는_옛_기록은_케어_후보_좌표를_쓰고_없으면_뺀다() {
        val visits = repeat("옛 센터", 2, amenities = emptyList(), lat = null)
        assertTrue(FrequentCarePlaces.from(visits, here, emptyList(), today).isEmpty())
        val result = FrequentCarePlaces.from(visits, here, listOf(care("옛 센터", 37.51, 127.03)), today)
        assertEquals(37.51, result.single().latitude, 0.0)
    }

    @Test
    fun 집과_너무_먼_곳은_뺀다() {
        val far = repeat("부산 센터", 3, lat = 35.1)
        val farVisits = far.map { it.copy(longitude = 129.0) }
        assertTrue(FrequentCarePlaces.from(farVisits, here, emptyList(), today).isEmpty())
    }

    @Test
    fun 거리와_시간은_서버와_같은_방식으로_어림한다() {
        val result = FrequentCarePlaces.from(repeat("가까운 센터", 3), here, emptyList(), today).single()
        // 위도 0.01도 ≈ 1.11km, 서버 방식은 직선거리 × 1.3 ÷ 시속 30km
        assertEquals(1112.0, result.distanceM.toDouble(), 5.0)
        assertEquals(3, result.driveMinutes)
    }

    @Test
    fun 시설_칩은_방문에서_확인된_것만_모은다() {
        val visits = listOf(
            visit("센터", "2026-11-01", amenities = listOf("nursing_room")),
            visit("센터", "2026-11-02", amenities = listOf("nursing_room", "stroller")),
            visit("센터", "2026-11-03", amenities = listOf("pet")),
        )
        assertEquals(listOf("nursing_room", "stroller", "pet"), FrequentCarePlaces.from(visits, here, emptyList(), today).single().amenities)
    }

    @Test
    fun 집_귀가_방문은_세지_않는다() {
        val visits = (1..3).map { visit("집", "2026-11-0$it", amenities = listOf("nursing_room"), role = "home") }
        assertTrue(FrequentCarePlaces.from(visits, here, emptyList(), today).isEmpty())
    }

    @Test
    fun 경유지_추가가_받는_모양으로_바꾼다() {
        val place = FrequentCarePlaces.from(repeat("센터", 2), here, emptyList(), today).single().toCarePlace()
        assertEquals("센터", place.name)
        assertEquals(37.5079, place.latitude, 0.0)
    }

    @Test
    fun 예전_방문_기록은_새_필드_없이도_읽는다() {
        val json = Json { ignoreUnknownKeys = true }
        val old = """{"id":1,"placeName":"서울숲","role":"outing","arrivedAt":"2026-11-05T10:00:00"}"""
        val v = json.decodeFromString<Visit>(old)
        assertEquals(null, v.latitude)
        assertTrue(v.amenities.isEmpty())
        // 새 필드가 있는 기록도 그대로 저장·복원된다
        val back = json.decodeFromString<Visit>(json.encodeToString(visit("센터", "2026-11-05", amenities = listOf("pet"))))
        assertEquals(listOf("pet"), back.amenities)
        assertEquals(37.5079, back.latitude!!, 0.0)
    }
}
