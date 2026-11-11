package io.github.haeyeonyou.familytrip.vehicle

import io.github.haeyeonyou.familytrip.data.CoursePlace
import io.github.haeyeonyou.familytrip.data.CourseResponse
import io.github.haeyeonyou.familytrip.data.CourseStop
import io.github.haeyeonyou.familytrip.data.FuelStation
import io.github.haeyeonyou.familytrip.ui.fuelToward
import io.github.haeyeonyou.familytrip.ui.stopLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 홈 연료 타일 판정 (#215). 서버 tests/test_fuel.py와 같은 기준. */
class FuelRangeTest {
    private fun place(name: String, lat: Double = 37.5, lng: Double = 127.0) =
        CoursePlace(name = name, latitude = lat, longitude = lng, source = "kakao", sourceId = name)

    private fun stop(role: String, meters: Int, name: String = role, stay: Int = 60) =
        CourseStop(role = role, place = place(name), arriveAt = "10:00", stayMinutes = stay, driveMinutes = 10, driveDistanceMeters = meters)

    private fun course(vararg stops: CourseStop) =
        CourseResponse(stops = stops.toList(), totalDriveMinutes = 0, returnAt = "18:00", routeSource = "kakao")

    @Test
    fun `코스 거리는 귀가 구간까지 더한다`() {
        assertEquals(40_000, courseMeters(course(stop("meal", 10_000), stop("outing", 5_000), stop("home", 25_000))))
    }

    @Test
    fun `긴 코스는 코스 × 1_3보다 짧으면 부족`() {
        assertEquals(FuelLevel.LOW, fuelLevel(120_000f, 100_000)) // 130km 필요
        assertEquals(FuelLevel.ENOUGH, fuelLevel(131_000f, 100_000))
        assertEquals(FuelLevel.ENOUGH, fuelLevel(130_000f, 100_000)) // 같으면 충분
    }

    @Test
    fun `기준값 하나만 바꾸면 판정이 바뀐다`() {
        assertEquals(FuelLevel.LOW, fuelLevel(115_000f, 100_000, margin = 1.2))
        assertEquals(FuelLevel.ENOUGH, fuelLevel(115_000f, 100_000, margin = 1.1))
        assertEquals(1.3, FuelRange.FUEL_MARGIN, 0.0)
        assertEquals(80_000, FuelRange.LOW_RANGE_METERS)
    }

    @Test
    fun `80km 미만이면 코스와 상관없이 부족 (QP2)`() {
        assertEquals(FuelLevel.LOW, fuelLevel(79_999f, null))
        assertEquals(FuelLevel.ENOUGH, fuelLevel(80_000f, null))
        assertEquals(FuelLevel.LOW, fuelLevel(79_999f, 10_000)) // 짧은 코스라도
        assertEquals(FuelLevel.ENOUGH, fuelLevel(80_000f, 40_000))
        assertEquals(FuelLevel.LOW, fuelLevel(10_000f, 0))
    }

    @Test
    fun `값이 없으면 판정하지 않는다 (가짜 숫자 없음)`() {
        assertNull(fuelLevel(null, 40_000))
        assertNull(fuelLevel(Float.NaN, 40_000))
        assertNull(rangeForRequest(null))
        assertNull(rangeForRequest(0f))
        assertEquals(65_432, rangeForRequest(65_432.4f))
    }

    @Test
    fun `게이지 숫자는 내림한 km`() {
        assertEquals(65, rangeKm(65_900f))
        assertEquals(0, rangeKm(800f))
    }

    @Test
    fun `EV 배터리 값이 오면 전기차`() {
        assertTrue(VehicleState(stateOfChargePercent = 70).isElectric())
        assertFalse(VehicleState(rangeRemainingMeters = 400_000f).isElectric())
    }

    @Test
    fun `주유소 줄은 거리·분·경로 위`() {
        val st = FuelStation(place = place("△△ 휴게소"), distanceMeters = 1_234, driveMinutes = 3, onRoute = true)
        assertEquals("1.2km · 3분 · 경로 위", stationMeta(st))
        assertEquals("800m · 2분 · 근처", stationMeta(st.copy(distanceMeters = 800, driveMinutes = 2, onRoute = false)))
    }

    @Test
    fun `주유소를 찾을 방향은 주유 경유지를 뺀 첫 방문지`() {
        val c = course(stop("fuel", 1_000, "주유소"), stop("meal", 5_000, "식당").copy(place = place("식당", 37.6, 127.1)), stop("home", 9_000))
        val toward = fuelToward(c)!!
        assertEquals(37.6, toward.latitude, 0.0)
        assertNull(fuelToward(course(stop("home", 1_000))))
    }

    @Test
    fun `타임라인 주유 줄은 주유 · N분`() {
        assertEquals("주유 · 10분", stopLine(stop("fuel", 1_000, "서광주유소", stay = 10)))
        assertEquals("충전 · 30분", stopLine(stop("fuel", 1_000, "아라타워 전기차충전소", stay = 30)))
        assertEquals("식사 · 60분 머무름", stopLine(stop("meal", 1_000)))
    }
}
