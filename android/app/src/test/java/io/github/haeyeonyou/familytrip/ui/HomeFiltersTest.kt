package io.github.haeyeonyou.familytrip.ui

import io.github.haeyeonyou.familytrip.data.CourseOption
import io.github.haeyeonyou.familytrip.data.CourseResponse
import io.github.haeyeonyou.familytrip.data.WeatherInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeFiltersTest {
    @Test
    fun `기본은 반나절 10km`() {
        val c = homeConditions(null, HomeFilters(), nowHour = 9)
        assertEquals(14, c.returnByHour)
        assertEquals(20, c.maxDriveMinutes)
    }

    @Test
    fun `하루 코스는 귀가 시각 제한이 없다`() {
        assertNull(homeConditions(null, HomeFilters(duration = HomeDuration.Full), nowHour = 9).returnByHour)
        assertNull(homeConditions(null, HomeFilters(duration = null), nowHour = 9).returnByHour)
    }

    @Test
    fun `반나절 귀가 시각은 23시를 넘지 않는다`() {
        assertEquals(23, homeConditions(null, HomeFilters(), nowHour = 21).returnByHour)
    }

    @Test
    fun `반경 단계는 5 10 20이고 이동 분으로 바뀐다`() {
        assertEquals(listOf(5, 10, 20), HOME_RADIUS_STEPS_KM)
        assertEquals(40, driveMinutesForRadius(20))
    }

    @Test
    fun `코스 카드 제목은 장소를 앞에 두고 '코스'를 뗀다`() {
        assertEquals("능골공원 자연 산책", homeCourseTitle("자연 산책 코스 · 능골공원"))
        assertEquals("판교박물관 박물관·전시", homeCourseTitle("박물관·전시 코스 · 판교박물관"))
        assertEquals("아차산 숲길 반나절", homeCourseTitle("아차산 숲길 반나절"))
        assertEquals("능골공원", homeCourseTitle("코스 · 능골공원"))
    }

    @Test
    fun `코스 카드 편의 태그는 경유지 전체에서 모은다`() {
        fun p(id: String, vararg a: Pair<String, String>) =
            io.github.haeyeonyou.familytrip.data.CoursePlace(name = id, latitude = 0.0, longitude = 0.0, source = "kakao", sourceId = id, amenities = a.toMap())
        fun s(role: String, place: io.github.haeyeonyou.familytrip.data.CoursePlace) =
            io.github.haeyeonyou.familytrip.data.CourseStop(role = role, place = place, arriveAt = "10:00", stayMinutes = 30, driveMinutes = 5, driveDistanceMeters = 1000)
        val course = CourseResponse(
            stops = listOf(
                s("meal", p("a", "nursing_room" to "available", "stroller" to "unknown")),
                s("outing", p("b", "pet" to "available")),
                s("home", p("h", "stroller" to "available")),
            ),
            totalDriveMinutes = 10, returnAt = "", routeSource = "",
        )
        assertEquals(listOf("수유실" to true, "유모차 확인" to false, "반려동물" to true), homeCourseTags(course))
    }

    @Test
    fun `탑승 카드 문장`() {
        val option = CourseOption("아차산 숲길 반나절", "a → b", 180, 22, "k:1", false,
            CourseResponse(stops = emptyList(), totalDriveMinutes = 0, returnAt = "", routeSource = ""))
        assertEquals("맑고 14°예요.\n아차산 숲길 반나절 어때요?", proactiveSentence(WeatherInfo(14.0, "맑음"), option))
        assertEquals("아차산 숲길 반나절 어때요?", proactiveSentence(null, option))
    }
}
