package io.github.haeyeonyou.familytrip.ui

import io.github.haeyeonyou.familytrip.data.CoursePlace
import io.github.haeyeonyou.familytrip.data.CourseResponse
import io.github.haeyeonyou.familytrip.data.CourseStop
import io.github.haeyeonyou.familytrip.data.StopParking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class CourseDetailLogicTest {
    private fun place(name: String, lat: Double, lng: Double, vararg amenities: String, id: String = name) = CoursePlace(
        name = name, latitude = lat, longitude = lng, source = "kakao", sourceId = id,
        amenities = amenities.associateWith { "available" },
    )

    private fun stop(role: String, p: CoursePlace, arrive: String, stay: Int = 60, parking: Int = 0) = CourseStop(
        role = role, place = p, arriveAt = arrive, stayMinutes = stay, driveMinutes = 10, driveDistanceMeters = 3000,
        parking = List(parking) { StopParking(name = "주차$it", kind = "공영", walkMinutes = 3) },
    )

    private val brunch = place("브런치", 37.565, 126.975, "nursing_room", "stroller")
    private val palace = place("덕수궁", 37.566, 126.975, "nursing_room", "stroller", "pet")
    private val waffle = place("와플", 37.567, 126.977, "stroller")
    private val home = place("집", 37.55, 126.95)

    private fun course(alternatives: Map<String, List<CoursePlace>> = emptyMap()) = CourseResponse(
        departureAt = "10:00",
        stops = listOf(
            stop("meal", brunch, "10:40", parking = 1),
            stop("outing", palace, "12:00", stay = 90, parking = 2),
            stop("cafe", waffle, "14:30", stay = 40, parking = 1),
            stop("home", home, "15:30", stay = 0),
        ),
        totalDriveMinutes = 70,
        returnAt = "15:30",
        routeSource = "kakao",
        alternatives = alternatives,
    )

    @Test
    fun `요약 태그 5종을 코스 편의 정보로 계산한다`() {
        val tags = courseTags(course()).map { it.label }
        assertEquals(listOf("총 5시간 30분", "수유실 2곳", "유모차 OK", "주차 4곳 확인", "반려동물 일부만 가능"), tags)
    }

    @Test
    fun `정보가 없으면 없다고 단정하지 않는다`() {
        val bare = course().copy(stops = course().stops.map { it.copy(place = it.place.copy(amenities = emptyMap()), parking = emptyList()) })
        val tags = courseTags(bare).map { it.label }
        assertEquals(listOf("총 5시간 30분", "수유실 확인 필요", "유모차 확인 필요", "주차 정보 없음", "반려동물 확인 필요"), tags)
    }

    @Test
    fun `다음 수유 시각에 머무는 경유지 아래에 수유 지점을 둔다`() {
        val p = feedingPoint(course(), LocalTime.of(12, 30))!!
        assertEquals(1, p.afterStop)
        assertEquals("12:30", p.time)
        assertTrue(p.detail.startsWith("덕수궁 수유실"))
    }

    @Test
    fun `수유실이 없는 곳이면 근처 수유실 확인으로 쓴다`() {
        val p = feedingPoint(course(), LocalTime.of(14, 50))!!
        assertEquals(2, p.afterStop)
        assertTrue(p.detail.contains("근처 수유실 확인"))
    }

    @Test
    fun `코스 밖 시각이나 기록이 없으면 수유 지점이 없다`() {
        assertNull(feedingPoint(course(), LocalTime.of(9, 0)))
        assertNull(feedingPoint(course(), LocalTime.of(16, 0)))
        assertNull(feedingPoint(course(), null))
    }

    @Test
    fun `자정을 넘는 코스에서도 수유 지점을 찾는다`() {
        val night = CourseResponse(
            departureAt = "23:00",
            stops = listOf(stop("meal", brunch, "23:07"), stop("outing", palace, "00:13", stay = 120), stop("home", home, "02:19", stay = 0)),
            totalDriveMinutes = 20, returnAt = "02:19", routeSource = "kakao",
        )
        assertEquals(0, feedingPoint(night, LocalTime.of(0, 5))!!.afterStop)
        assertEquals(1, feedingPoint(night, LocalTime.of(0, 56))!!.afterStop)
        assertNull(feedingPoint(night, LocalTime.of(2, 30)))
        assertNull(feedingPoint(night, LocalTime.of(22, 0)))
    }

    @Test
    fun `대체 후보는 조건 유지 먼저, 그다음 덜 돌아가는 순으로 3곳`() {
        val near = place("국밥집", 37.5655, 126.9752, "stroller", "nursing_room")
        val far = place("파스타집", 37.58, 126.99, "nursing_room", "stroller")
        val noNeeds = place("분식집", 37.5651, 126.9751)
        val middle = place("순두부집", 37.57, 126.98, "nursing_room", "stroller")
        val list = replacements(course(mapOf("meal" to listOf(far, noNeeds, middle, near))), 0, origin = null)
        assertEquals(listOf("국밥집", "순두부집", "파스타집"), list.map { it.place.name })
        assertTrue(list.all { it.keepsNeeds })
        assertTrue(list[0].detourMinutes <= list[1].detourMinutes)
    }

    @Test
    fun `이미 코스에 있는 곳과 귀가는 후보가 아니다`() {
        assertEquals(emptyList<Replacement>(), replacements(course(mapOf("meal" to listOf(palace))), 0, null))
        assertEquals(emptyList<Replacement>(), replacements(course(), 3, null))
    }

    @Test
    fun `후보 메타 문구`() {
        assertEquals("600m · 경로에서 +2분", replacementMeta(Replacement(brunch, 600, 2, true)))
        assertEquals("1.1km · 추가 이동 없음", replacementMeta(Replacement(brunch, 1100, 0, true)))
    }
}
