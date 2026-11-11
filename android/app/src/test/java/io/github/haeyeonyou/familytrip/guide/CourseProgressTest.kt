package io.github.haeyeonyou.familytrip.guide

import io.github.haeyeonyou.familytrip.data.CoursePlace
import io.github.haeyeonyou.familytrip.data.CourseResponse
import io.github.haeyeonyou.familytrip.data.CourseStop
import io.github.haeyeonyou.familytrip.data.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** 코스 진행 H03·H03-1·H04 계산 (#218). Figma 예시 코스(덕수궁 나들이 코스)를 그대로 쓴다. */
class CourseProgressTest {
    private fun place(name: String, lat: Double, lng: Double, id: String = name) =
        CoursePlace(name = name, latitude = lat, longitude = lng, source = "kakao", sourceId = id)

    private fun stop(role: String, p: CoursePlace, arrive: String, depart: String?, stay: Int, drive: Int) =
        CourseStop(role = role, place = p, arriveAt = arrive, departAt = depart, stayMinutes = stay, driveMinutes = drive, driveDistanceMeters = drive * 500)

    private val brunch = place("올데이 브런치", 37.5650, 126.9750)
    private val palace = place("덕수궁", 37.5658, 126.9751)
    private val waffle = place("벨지안 와플", 37.5670, 126.9770)
    private val home = place("우리 집", 37.5500, 126.9500)

    private val stops = listOf(
        stop("meal", brunch, "10:40", "11:40", 60, 40),
        stop("outing", palace, "12:00", "13:00", 60, 12),
        stop("cafe", waffle, "13:20", "14:20", 60, 20),
        stop("home", home, "14:40", null, 0, 20),
    )

    private fun guide(index: Int, arrived: Boolean = true, visited: Set<Int> = (0..index).toSet(), endedBy: CourseEnd? = null) = GuideState(
        stops = stops, index = index, arrived = arrived, visited = if (arrived) visited else visited - index,
        endedBy = endedBy, departureAt = "10:00", title = courseTitle(stops),
    )

    @Test
    fun `단계는 가는 중 · 가운데 도착 · 마지막 도착 · 종료로 나뉜다`() {
        assertEquals(GuideStage.HEADING, stageOf(guide(0, arrived = false)))
        assertEquals(GuideStage.ARRIVED, stageOf(guide(0)))
        assertEquals(GuideStage.ARRIVED, stageOf(guide(1)))
        assertEquals(GuideStage.LAST_STOP, stageOf(guide(2)))
        assertEquals(GuideStage.FINISHED, stageOf(guide(3, arrived = false, visited = setOf(0, 1, 2), endedBy = CourseEnd.HOME)))
        assertEquals(GuideStage.FINISHED, stageOf(guide(1, endedBy = CourseEnd.HERE)))
        assertEquals("덕수궁 나들이 코스", courseTitle(stops))
    }

    @Test
    fun `제때 도착하면 다음 일정 그대로 · 늦으면 늦어지는 분을 알린다`() {
        val onTime = departPlan(guide(0), LocalTime.of(10, 40))!!
        assertEquals(LocalTime.of(11, 40), onTime.departAt)
        assertEquals(0, onTime.delayMinutes)
        assertEquals("60분 머문 뒤 11:40에 출발하면 다음 일정 그대로예요", departLine(onTime, last = false))

        val early = departPlan(guide(0), LocalTime.of(10, 30))!!
        assertEquals(0, early.delayMinutes)

        val late = departPlan(guide(0), LocalTime.of(10, 55))!!
        assertEquals(15, late.delayMinutes)
        assertEquals("60분 머문 뒤 11:55에 출발하면 다음 일정이 15분 늦어져요", departLine(late, last = false))
    }

    @Test
    fun `마지막 장소는 귀가 시각을 알린다`() {
        val plan = departPlan(guide(2), LocalTime.of(13, 20))!!
        assertEquals(LocalTime.of(14, 40), plan.homeAt)
        assertEquals("오늘 코스의 마지막 장소예요 · 60분 머문 뒤 14:20에 출발하면 14:40 귀가", departLine(plan, last = true))
    }

    @Test
    fun `자정 근처 계산도 지연을 음수로 만들지 않는다`() {
        val night = guide(0).copy(stops = listOf(stop("meal", brunch, "23:50", "00:20", 30, 10), stops.last()))
        assertEquals(0, departPlan(night, LocalTime.of(23, 40))!!.delayMinutes)
        assertEquals(10, departPlan(night, LocalTime.of(0, 0))!!.delayMinutes)
    }

    @Test
    fun `진행 줄은 출발 · 장소 · 귀가이고 지금 장소를 강조한다`() {
        val points = progressPoints(guide(0))
        assertEquals(listOf("출발", "올데이 브런치", "덕수궁", "벨지안 와플", "귀가"), points.map { it.label })
        assertEquals(listOf("10:00", "10:40", "12:00", "13:20", "14:40"), points.map { it.time })
        assertEquals(
            listOf(ProgressPoint.Status.DONE, ProgressPoint.Status.CURRENT, ProgressPoint.Status.UPCOMING, ProgressPoint.Status.UPCOMING, ProgressPoint.Status.UPCOMING),
            points.map { it.status },
        )
        assertEquals(ProgressPoint.Status.CURRENT, progressPoints(guide(2))[3].status)
        assertEquals(ProgressPoint.Status.DONE, progressPoints(guide(2))[2].status)
    }

    @Test
    fun `남은 일정은 지금 장소 다음부터 귀가까지`() {
        assertEquals(
            listOf(RemainingStop("12:00", "덕수궁", 60), RemainingStop("13:20", "벨지안 와플", 60), RemainingStop("14:40", "귀가", 0)),
            remainingStops(guide(0)),
        )
        assertEquals(listOf(RemainingStop("14:40", "귀가", 0)), remainingStops(guide(2)))
    }

    @Test
    fun `다녀온 곳만 기록 대상 · 건너뛴 곳은 skipped로 올린다`() {
        // 브런치 다녀온 뒤 덕수궁으로 가다가 여기서 끝냄 → 덕수궁·와플은 기록하지 않는다
        val ended = guide(1, arrived = false, visited = setOf(0), endedBy = CourseEnd.HERE)
        assertEquals(listOf("올데이 브런치"), visitedStops(ended).map { it.place.name })
        assertEquals("덕수궁 나들이 코스 · 10:00 – 11:40 · 1곳", finishedSummary(ended))
        val snap = tripSnapshot(ended, LocalDate.of(2026, 11, 11))!!
        assertEquals("finished", snap.status)
        assertEquals("here", snap.endedBy)
        assertEquals(listOf("visited", "skipped", "skipped", "skipped"), snap.stops.map { it.state })
        assertNull(ended.current) // 여기서 끝내면 갈 곳이 없다 (지연 알림·케어 경로가 쓰지 않게)

        // 세 곳 다 다녀오고 귀가 선택 → 귀가는 지금 가는 곳
        val homeward = guide(3, arrived = false, visited = setOf(0, 1, 2), endedBy = CourseEnd.HOME)
        assertEquals("덕수궁 나들이 코스 · 10:00 – 14:40 · 3곳", finishedSummary(homeward))
        assertEquals(listOf("visited", "visited", "visited", "current"), tripSnapshot(homeward, LocalDate.of(2026, 11, 11))!!.stops.map { it.state })
        assertEquals("home", homeward.current?.role)

        // 진행 중
        val running = tripSnapshot(guide(1), LocalDate.of(2026, 11, 11))!!
        assertEquals("active", running.status)
        assertEquals(listOf("visited", "visited", "upcoming", "upcoming"), running.stops.map { it.state })
        assertEquals("2026-11-11", running.startedOn)
        assertNull(tripSnapshot(GuideState(), LocalDate.of(2026, 11, 11)))
    }

    @Test
    fun `다시 계산한 코스는 지나온 장소가 같을 때만 받는다`() {
        val museum = place("서울시립미술관", 37.5640, 126.9738)
        val swapped = stops.toMutableList().apply { this[1] = stop("outing", museum, "12:05", "13:05", 60, 10) }
        val updated = withRescheduled(guide(0), swapped)!!
        assertEquals("서울시립미술관", updated.nextVisit?.place?.name)
        assertEquals(setOf(0), updated.visited)
        assertTrue(updated.arrived)
        // 이미 다녀온 브런치가 바뀐 코스는 받지 않는다
        val other = swapped.toMutableList().apply { this[0] = stop("meal", museum, "10:40", "11:40", 60, 40) }
        assertNull(withRescheduled(guide(0), other))
    }

    @Test
    fun `한 곳 더 후보는 코스에 없는 곳을 덜 돌아가는 순으로 3곳까지`() {
        val near = place("근처 카페", 37.5672, 126.9772)
        val far = place("먼 공원", 37.6200, 127.0500)
        val onWay = place("길목 식당", 37.5600, 126.9650)
        val extra = place("또 다른 곳", 37.5900, 127.0000)
        val course = CourseResponse(
            departureAt = "10:00", stops = stops, totalDriveMinutes = 92, returnAt = "14:40", routeSource = "kakao",
            alternatives = mapOf("cafe" to listOf(near, waffle), "outing" to listOf(far, extra), "meal" to listOf(onWay)),
        )
        val picks = extraCandidates(course, stops[2], LatLng(home.latitude, home.longitude))
        assertEquals(3, picks.size)
        assertTrue(picks.none { it.second.place.name == "벨지안 와플" }) // 이미 코스에 있음
        assertEquals(setOf("길목 식당", "근처 카페"), picks.take(2).map { it.second.place.name }.toSet())
        assertTrue(picks.none { it.second.place.name == "먼 공원" })
        assertEquals("meal", picks.first { it.second.place.name == "길목 식당" }.first)
    }

    @Test
    fun `도착 질문의 음성 답을 단계에 맞게 가른다`() {
        assertEquals(ArrivalChoice.NEXT, classifyArrivalReply("다음 장소 가자", GuideStage.ARRIVED))
        assertEquals(ArrivalChoice.SWAP, classifyArrivalReply("바꿔줘", GuideStage.ARRIVED))
        assertEquals(ArrivalChoice.HOME, classifyArrivalReply("집에 가자", GuideStage.ARRIVED))
        assertEquals(ArrivalChoice.HOME, classifyArrivalReply("집에 가자", GuideStage.LAST_STOP))
        assertEquals(ArrivalChoice.MORE, classifyArrivalReply("한 곳 더", GuideStage.LAST_STOP))
        assertEquals(ArrivalChoice.END_HERE, classifyArrivalReply("여기서 코스 끝내자", GuideStage.ARRIVED))
        // 그 단계에 없는 답 · 관계없는 말 · 도착 화면이 아님
        assertNull(classifyArrivalReply("다음 장소 가자", GuideStage.LAST_STOP))
        assertNull(classifyArrivalReply("한 곳 더", GuideStage.ARRIVED))
        assertNull(classifyArrivalReply("에어컨 켜줘", GuideStage.ARRIVED))
        assertNull(classifyArrivalReply("집에 가자", GuideStage.HEADING))
        assertNull(classifyArrivalReply("집에 가자", GuideStage.FINISHED))
    }

    @Test
    fun `주행 중 질문은 화면과 같은 선택지를 말한다`() {
        assertTrue(arrivalQuestion(guide(0))!!.contains("\"바꿔줘\""))
        assertTrue(arrivalQuestion(guide(2))!!.contains("\"한 곳 더\""))
        assertNull(arrivalQuestion(guide(0, arrived = false)))
    }

    private val seoul = java.time.ZoneId.of("Asia/Seoul")
    private fun at(text: String) = java.time.LocalDateTime.parse(text).atZone(seoul).toInstant().toEpochMilli()

    @Test
    fun `다시 뜬 앱은 오늘 시작한 코스만 이어 간다`() {
        val started = guide(0).copy(startedAtMillis = at("2026-11-11T10:00"))
        assertTrue(resumableToday(started, at("2026-11-11T23:59"), seoul))
        // 자정을 넘기면 어제 코스
        assertTrue(!resumableToday(started.copy(startedAtMillis = at("2026-11-11T23:30")), at("2026-11-12T00:10"), seoul))
        assertTrue(!resumableToday(started, at("2026-11-12T09:00"), seoul))
        // 시작 시각이 없는 옛 저장 파일은 이어 가지 않는다
        assertTrue(!resumableToday(started.copy(startedAtMillis = null), at("2026-11-11T10:05"), seoul))
    }

    @Test
    fun `지난 코스는 시작한 날짜로 여기서 끝냄을 올린다`() {
        val stale = guide(1, arrived = false, visited = setOf(0)).copy(startedAtMillis = at("2026-11-10T10:00"))
        val snap = expiredSnapshot(stale, LocalDate.of(2026, 11, 11), seoul)!!
        assertEquals("2026-11-10", snap.startedOn)
        assertEquals("finished", snap.status)
        assertEquals("here", snap.endedBy)
        assertEquals(listOf("visited", "skipped", "skipped", "skipped"), snap.stops.map { it.state })
        assertEquals("2026-11-11", expiredSnapshot(stale.copy(startedAtMillis = null), LocalDate.of(2026, 11, 11), seoul)!!.startedOn)
    }

    @Test
    fun `저장 파일은 안내 상태와 코스를 그대로 되살린다`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val state = guide(1).copy(startedAtMillis = 1L, arrivedAtMillis = 2L)
        val back = json.decodeFromString(SavedGuide.serializer(), json.encodeToString(SavedGuide.serializer(), SavedGuide(state)))
        assertEquals(state, back.state)
        // 옛 파일(시작 시각 없음)도 읽힌다
        val old = json.encodeToString(SavedGuide.serializer(), SavedGuide(state.copy(startedAtMillis = null))).replace(",\"startedAtMillis\":null", "")
        assertNull(json.decodeFromString(SavedGuide.serializer(), old).state.startedAtMillis)
    }
}
