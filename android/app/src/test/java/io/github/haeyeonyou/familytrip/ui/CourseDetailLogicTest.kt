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
        assertEquals(listOf("총 5시간 30분", "수유실 2곳", "유모차 OK", "주차 3곳 · 대체 1곳", "반려동물 일부만 가능"), tags)
    }

    @Test
    fun `수유를 보이지 않는 코스는 수유실 태그가 없다 (H02-2)`() {
        assertEquals(listOf("총 5시간 30분", "유모차 OK", "주차 3곳 · 대체 1곳", "반려동물 일부만 가능"), courseTags(course(), showNursing = false).map { it.label })
    }

    @Test
    fun `주차 줄은 고른 곳의 요금과 도보, 나머지 수를 쓰고 모르는 요금은 정보 없음`() {
        val lots = listOf(
            StopParking(name = "세종로 공영주차장", kind = "공영", distanceMeters = 400, walkMinutes = 6, feeSummary = "5분 400원·일 최대 6,000원"),
            StopParking(name = "정동 공영주차장", kind = "공영", distanceMeters = 270, walkMinutes = 4, feeSummary = "10분 500원"),
            StopParking(name = "건물 지하 주차장", distanceMeters = 600, walkMinutes = 9),
        )
        val s = stop("outing", palace, "12:00").copy(parking = lots)
        val lot = parkingRowLot(s)!!
        assertEquals("세종로 공영주차장", lot.name)
        assertEquals("5분 400원·일 최대 6,000원 · 도보 6분", parkingRowDetail(lot))
        assertEquals("공영 · 5분 400원·일 최대 6,000원 · 도보 6분", parkingCardDetail(lot))
        assertEquals("대체 2곳", parkingOthersLabel(s))
        assertEquals(listOf("정동 공영주차장", "세종로 공영주차장", "건물 지하 주차장"), parkingChoices(s).map { it.name })
        assertEquals("도보 6분 · 5분 400원", parkingChoiceDetail(lots[0]))
        assertEquals("도보 9분 · 요금 정보 없음", parkingChoiceDetail(lots[2]))
        assertEquals("요금 정보 없음 · 도보 9분", parkingRowDetail(lots[2]))
        assertNull(parkingRowLot(stop("home", home, "15:30")))
        assertNull(parkingRowLot(stop("fuel", waffle, "10:10").copy(parking = lots)))
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

    @Test
    fun `장소 추가 후보는 코스에 없는 곳만 역할 순서로`() {
        val extra = place("국밥집", 37.5, 126.9)
        val cafe2 = place("카페2", 37.5, 126.9)
        val groups = addCandidates(course(mapOf("cafe" to listOf(waffle, cafe2), "meal" to listOf(brunch, extra), "outing" to listOf(palace))))
        assertEquals(listOf("meal" to listOf(extra), "cafe" to listOf(cafe2)), groups)
    }

    @Test
    fun `서버가 준 우회 분과 거리를 어림값보다 먼저 쓴다`() {
        val far = place("먼카페", 37.60, 127.05).copy(distanceM = 640, detourMinutes = 7)
        val shorter = place("지름길카페", 37.60, 127.05).copy(distanceM = 1100, detourMinutes = -3)
        val r = replacements(course(mapOf("cafe" to listOf(far, shorter))), 2, null)
        assertEquals(listOf("지름길카페", "먼카페"), r.map { it.place.name })
        assertEquals(0, r[0].detourMinutes)
        assertEquals(1100, r[0].distanceM)
        assertEquals(7, r[1].detourMinutes)
        assertEquals(640, r[1].distanceM)
    }

    @Test
    fun `경로가 바뀐 재계산이면 이전 후보의 서버 우회 값을 지우고 코스에 들어간 곳은 뺀다`() {
        val c = course(mapOf("cafe" to listOf(place("카페2", 37.5, 126.9).copy(distanceM = 300, detourMinutes = 4), waffle)))
        fun req(vararg ps: CoursePlace) = ps.map { io.github.haeyeonyou.familytrip.data.ScheduleStop("x", it) }
        val same = mergeAlternatives(c, req(brunch, palace, waffle), emptyMap())
        assertEquals(4, same.getValue("cafe").single().detourMinutes)
        val moved = mergeAlternatives(c, req(palace, brunch, waffle), emptyMap())
        assertNull(moved.getValue("cafe").single().detourMinutes)
        assertNull(moved.getValue("cafe").single().distanceM)
        val fresh = mergeAlternatives(c, req(palace, brunch), mapOf("outing" to listOf(home)))
        assertEquals(listOf(home), fresh["outing"])
        assertEquals(2, fresh.getValue("cafe").size)
    }
}
