package io.github.haeyeonyou.familytrip.ui

import io.github.haeyeonyou.familytrip.data.CourseStop
import io.github.haeyeonyou.familytrip.data.StopParking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingInfoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun line_with_all_fields() {
        val lot = StopParking(name = "맛고을공영주차장", kind = "공영", capacity = 120, feeSummary = "30분 400원", walkMinutes = 2)
        assertEquals("주차 공영 120면 · 30분 400원 · 도보 약 2분", parkingLine(lot))
    }

    @Test
    fun line_skips_unknown_parts() {
        val lot = StopParking(name = "모란1", kind = null, capacity = null, feeSummary = "", walkMinutes = 1)
        assertEquals("주차 모란1 · 도보 약 1분", parkingLine(lot))
    }

    @Test
    fun note_warns_only_when_known_closed() {
        val closed = StopParking(name = "A", hours = "평일 09:00~18:00", openAtArrival = false)
        assertTrue(parkingNote(closed)!!.contains("운영 안 함"))
        assertEquals("B", parkingNote(StopParking(name = "B", openAtArrival = null)))
    }

    @Test
    fun old_saved_json_without_parking_still_reads() {
        val old = """{"role":"outing","place":{"name":"공원","latitude":37.5,"longitude":127.0,"source":"tour","source_id":"1"},
            "arrive_at":"11:00","drive_minutes":10,"drive_distance_m":5000}"""
        val stop = json.decodeFromString<CourseStop>(old)
        assertTrue(stop.parking.isEmpty())
    }

    @Test
    fun server_parking_json_reads() {
        val s = """{"role":"outing","place":{"name":"공원","latitude":37.5,"longitude":127.0,"source":"tour","source_id":"1"},
            "parking":[{"name":"맛고을","kind":"공영","distance_m":223,"walk_minutes":3,"capacity":208,"fee_summary":"30분 400원","free":false,
            "hours":"평일 24시간","open_at_arrival":null,"latitude":37.4,"longitude":127.1,"extra":1}],
            "arrive_at":"11:00","drive_minutes":10,"drive_distance_m":5000}"""
        val lot = json.decodeFromString<CourseStop>(s).parking.single()
        assertEquals(3, lot.walkMinutes)
        assertNull(lot.openAtArrival)
    }
}
