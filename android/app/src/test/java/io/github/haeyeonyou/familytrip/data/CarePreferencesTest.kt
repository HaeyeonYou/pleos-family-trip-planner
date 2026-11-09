package io.github.haeyeonyou.familytrip.data

import io.github.haeyeonyou.familytrip.ui.HomeFilters
import io.github.haeyeonyou.familytrip.ui.homeConditions
import io.github.haeyeonyou.familytrip.ui.shouldPreferIndoor
import io.github.haeyeonyou.familytrip.ui.withPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class CarePreferencesTest {
    private val now = YearMonth.of(2026, 11)
    private val infantFamily = FamilyProfile(children = listOf(Child(2026, 7, "서아")), petAlong = false)
    private val originPoint = LatLng(37.5, 127.0)

    @Test
    fun 선호_조건은_코스_요청의_필수_시설_조건으로_들어간다() {
        val prefs = CarePreferences(nursingRoomOnly = true, strollerOk = true, indoorOnRain = false, petOnly = false)
        val filters = HomeFilters().withPreferences(prefs)
        val request = CourseRequest(conditions = homeConditions(infantFamily, filters), origin = originPoint)
        val json = Json.encodeToString(request)
        assertEquals(listOf("nursing_room", "stroller"), request.conditions.needs)
        assertTrue(json.contains("\"needs\":[\"nursing_room\",\"stroller\"]"))
    }

    @Test
    fun 반려견_동반만을_켜면_요청의_with_pet이_참이다() {
        val filters = HomeFilters().withPreferences(CarePreferences(petOnly = true))
        val json = Json.encodeToString(CourseRequest(homeConditions(null, filters), originPoint))
        assertTrue(json.contains("\"with_pet\":true"))
    }

    @Test
    fun 모두_끄면_필수_시설_조건이_비어_있다() {
        val filters = HomeFilters().withPreferences(CarePreferences(indoorOnRain = false))
        val c = homeConditions(infantFamily, filters)
        assertTrue(c.needs.isEmpty())
        assertFalse(c.withPet)
    }

    @Test
    fun 홈_칩을_그날만_끄면_설정과_달리_그_요청에서만_빠진다() {
        val prefs = CarePreferences(nursingRoomOnly = true, strollerOk = true)
        val chips = HomeFilters().withPreferences(prefs).copy(nursingRoom = false)
        assertEquals(listOf("stroller"), homeConditions(infantFamily, chips).needs)
        assertEquals(listOf("nursing_room", "stroller"), prefs.needs())
    }

    @Test
    fun 문장으로_요청한_조건에도_선호_조건이_더해지고_중복은_없다() {
        val prefs = CarePreferences(nursingRoomOnly = true, strollerOk = true, petOnly = true)
        val parsed = TripConditions(needs = listOf("kids_chair", "stroller"), themes = listOf("nature"))
        val c = prefs.applyTo(parsed)
        assertEquals(listOf("kids_chair", "stroller", "nursing_room"), c.needs)
        assertTrue(c.withPet)
        assertEquals(listOf("nature"), c.themes)
    }

    @Test
    fun 선호_조건이_꺼져_있으면_문장_조건을_그대로_둔다() {
        val parsed = TripConditions(needs = listOf("kids_chair"), withPet = true)
        assertEquals(parsed, CarePreferences(indoorOnRain = false).applyTo(parsed))
    }

    @Test
    fun 만지지_않은_토글은_가족_정보로_정한_기본값을_따른다() {
        val d = CarePreferences.defaultsFor(infantFamily, now)
        assertTrue(d.nursingRoomOnly && d.strollerOk && d.indoorOnRain)
        assertFalse(d.petOnly)
        val older = FamilyProfile(children = listOf(Child(2020, 1)), petAlong = true)
        val o = CarePreferences.defaultsFor(older, now)
        assertFalse(o.nursingRoomOnly || o.strollerOk)
        assertTrue(o.petOnly)
    }

    @Test
    fun 사용자가_정한_값이_기본값보다_우선한다() {
        val r = CarePreferences.resolve(mapOf(Preference.NURSING_ROOM to false, Preference.PET to true), infantFamily, now)
        assertFalse(r.nursingRoomOnly)
        assertTrue(r.strollerOk)  // 안 만짐: 아이가 영아라서 기본 켜짐
        assertTrue(r.petOnly)
    }

    @Test
    fun 비_눈_실내_우선은_설정과_칩_상태를_따른다() {
        val rain = WeatherInfo(rainy = true)
        val on = CarePreferences(indoorOnRain = true)
        assertTrue(shouldPreferIndoor(on, rain, indoorTouched = false, filters = HomeFilters()))
        assertTrue(shouldPreferIndoor(on, WeatherInfo(rainLater = true), false, HomeFilters()))
        assertFalse(shouldPreferIndoor(on.copy(indoorOnRain = false), rain, false, HomeFilters()))
        assertFalse(shouldPreferIndoor(on, rain, indoorTouched = true, filters = HomeFilters()))
        assertFalse(shouldPreferIndoor(on, WeatherInfo(), false, HomeFilters()))
    }

    @Test
    fun 수유_간격_스테퍼는_30분씩_60에서_360분_안에서만_움직인다() {
        assertEquals(210, IntervalStepper.step(180, +1))
        assertEquals(150, IntervalStepper.step(180, -1))
        assertEquals(60, IntervalStepper.step(60, -1))
        assertEquals(360, IntervalStepper.step(360, +1))
    }

    @Test
    fun 낮잠_시간은_30분씩_옮기고_구간이_뒤집히지_않는다() {
        val nap = NapWindow()
        assertEquals("13:00 – 15:00", nap.label())
        assertEquals("13:30 – 15:00", nap.shiftStart(30).label())
        assertEquals("13:00 – 15:30", nap.shiftEnd(30).label())
        assertEquals("14:30 – 15:00", nap.shiftStart(300).label())
        assertEquals("13:00 – 13:30", nap.shiftEnd(-300).label())
        assertEquals("00:00 – 15:00", nap.shiftStart(-1000).label())
    }
}
