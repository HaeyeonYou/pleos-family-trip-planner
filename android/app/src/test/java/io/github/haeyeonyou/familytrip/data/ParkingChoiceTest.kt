package io.github.haeyeonyou.familytrip.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParkingChoiceTest {
    private val palace = CoursePlace(name = "덕수궁", latitude = 37.5658, longitude = 126.9752, source = "tour", sourceId = "126508")
    private val sejong = StopParking(name = "세종로 공영주차장", kind = "공영", latitude = 37.5725, longitude = 126.9769)
    private val jeongdong = StopParking(name = "정동 공영주차장", kind = "공영", latitude = 37.5660, longitude = 126.9710)

    private fun stop(role: String = "outing", place: CoursePlace = palace, parking: List<StopParking> = listOf(sejong, jeongdong)) =
        CourseStop(role = role, place = place, parking = parking, arriveAt = "12:00", driveMinutes = 10, driveDistanceMeters = 3000)

    private fun course(vararg stops: CourseStop) =
        CourseResponse(departureAt = "10:00", stops = stops.toList(), totalDriveMinutes = 30, returnAt = "15:00", routeSource = "kakao")

    @Test
    fun `고르지 않으면 추천 주차장이 도착지, 주차 정보가 없으면 장소`() {
        assertEquals(LatLng(sejong.latitude, sejong.longitude), stop().arrivalPoint())
        assertEquals(LatLng(palace.latitude, palace.longitude), stop(parking = emptyList()).arrivalPoint())
        assertEquals(LatLng(palace.latitude, palace.longitude), stop(role = "home").arrivalPoint())
        assertEquals(LatLng(palace.latitude, palace.longitude), stop(role = "fuel").arrivalPoint())
    }

    @Test
    fun `고른 주차장이 도착지가 되고 추천을 다시 고르면 기본으로 돌아간다`() {
        val picked = course(stop()).withParkingPick(0, jeongdong)
        assertEquals(jeongdong.name, picked.stops[0].chosenParking()?.name)
        assertEquals(LatLng(jeongdong.latitude, jeongdong.longitude), picked.stops[0].arrivalPoint())
        assertNull(picked.withParkingPick(0, sejong).stops[0].parkingPick)
    }

    @Test
    fun `재계산 뒤에도 같은 장소면 고른 주차장을 지키고 바뀐 장소는 버린다`() {
        val old = course(stop()).withParkingPick(0, jeongdong)
        val fresh = course(stop(parking = listOf(sejong, jeongdong.copy(walkMinutes = 5))))
        assertEquals(jeongdong.name, keepParkingPicks(old, fresh).stops[0].chosenParking()?.name)
        val other = course(stop(place = palace.copy(sourceId = "other")))
        assertNull(keepParkingPicks(old, other).stops[0].parkingPick)
    }
}
