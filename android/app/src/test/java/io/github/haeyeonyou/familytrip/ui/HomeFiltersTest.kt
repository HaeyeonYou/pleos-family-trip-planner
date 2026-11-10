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
    fun `반경은 5 10 20으로 순환하고 이동 분으로 바뀐다`() {
        assertEquals(10, nextRadiusKm(5))
        assertEquals(20, nextRadiusKm(10))
        assertEquals(5, nextRadiusKm(20))
        assertEquals(40, driveMinutesForRadius(20))
    }

    @Test
    fun `탑승 카드 문장`() {
        val option = CourseOption("아차산 숲길 반나절", "a → b", 180, 22, "k:1", false,
            CourseResponse(stops = emptyList(), totalDriveMinutes = 0, returnAt = "", routeSource = ""))
        assertEquals("맑고 14°예요.\n아차산 숲길 반나절 어때요?", proactiveSentence(WeatherInfo(14.0, "맑음"), option))
        assertEquals("아차산 숲길 반나절 어때요?", proactiveSentence(null, option))
    }
}
