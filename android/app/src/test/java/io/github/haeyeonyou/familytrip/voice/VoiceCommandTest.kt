package io.github.haeyeonyou.familytrip.voice

import io.github.haeyeonyou.familytrip.care.CareKind
import io.github.haeyeonyou.familytrip.data.VoiceInterpretation
import io.github.haeyeonyou.familytrip.data.WeatherInfo
import io.github.haeyeonyou.familytrip.guide.GuideState
import io.github.haeyeonyou.familytrip.navi.NaviState
import io.github.haeyeonyou.familytrip.vehicle.ClimateReading
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandTest {
    private fun interp(
        action: String,
        target: String? = null,
        on: Boolean? = null,
        number: Int? = null,
        temperature: Double? = null,
        delta: Double? = null,
        ac: Boolean? = null,
        steeringHeat: Int? = null,
        source: String = "llm",
    ) = VoiceInterpretation(
        action = action, target = target, on = on, number = number, temperature = temperature,
        temperatureDelta = delta, ac = ac, steeringHeat = steeringHeat, confidence = 0.9, source = source,
    )

    // 동작 이름 → 앱 동작 연결

    @Test
    fun serverActionsMapToAppSteps() {
        assertEquals(Step.Situation(CareSituation.DIAPER), toStep(interp("care_situation", "diaper")))
        assertEquals(Step.Sleep(true), toStep(interp("sleep_mode", on = true)))
        assertEquals(Step.BabyCare(false), toStep(interp("baby_care", on = false)))
        assertEquals(Step.FeedLog, toStep(interp("feed_log")))
        assertEquals(Step.FeedInterval(150), toStep(interp("feed_interval", number = 150)))
        assertEquals(Step.Find(CareKind.RESTROOM), toStep(interp("find_place", "restroom")))
        assertEquals(Step.AddWaypoint(CareKind.NURSING), toStep(interp("add_waypoint", "nursing")))
        assertEquals(Step.RemoveWaypoint, toStep(interp("remove_waypoint")))
        assertEquals(Step.GoTab(VoiceTab.HOME), toStep(interp("go_tab", "home")))
        assertEquals(Step.SetFilter(VoiceFilter.PET, false), toStep(interp("set_filter", "pet", on = false)))
        assertEquals(Step.AnotherCourses, toStep(interp("another_courses")))
        assertEquals(Step.OpenCourse(2), toStep(interp("open_course", number = 2)))
        assertEquals(Step.StartCourse, toStep(interp("start_course")))
        assertEquals(Step.Choose(1), toStep(interp("choose", number = 1)))
        assertEquals(Step.Query(VoiceTopic.ETA), toStep(interp("query", "eta")))
        assertEquals(Step.Confirm, toStep(interp("confirm")))
    }

    @Test
    fun unknownOrBrokenResultsAreNotExecuted() {
        assertNull(toStep(interp("unknown")))
        assertNull(toStep(interp("launch_missiles")))
        assertNull(toStep(interp("go_tab", "nowhere")))
        assertNull(toStep(interp("set_filter", "pet")))  // on 없음
        assertNull(toStep(interp("feed_interval", number = 200)))  // 허용 간격이 아님
        assertNull(toStep(interp("choose", number = 9)))
        assertNull(toStep(interp("climate")))  // 바꿀 항목이 없음
        assertNull(toStep(interp("sleep_mode", on = true, source = "fallback")))  // 서버가 해석하지 못함
    }

    // 확인 문구 흐름

    @Test
    fun drivingAsksBeforeVehicleChangesButParkedDoesNot() {
        val climate = Step.Climate(ClimateRequest(delta = -2f))
        assertTrue(confirmPrompt(climate, driving = true)!!.contains("응"))
        assertNull(confirmPrompt(climate, driving = false))
        assertTrue(confirmPrompt(Step.BabyCare(true), driving = true) != null)
        assertNull(confirmPrompt(Step.BabyCare(false), driving = true))
        assertTrue(confirmPrompt(Step.RemoveWaypoint, driving = true) != null)
        assertTrue(confirmPrompt(Step.StartCourse, driving = true) != null)
        // 화면 이동·기록·찾기는 되묻지 않는다
        assertNull(confirmPrompt(Step.GoTab(VoiceTab.CARE), driving = true))
        assertNull(confirmPrompt(Step.FeedLog, driving = true))
        assertNull(confirmPrompt(Step.Find(CareKind.NURSING), driving = true))
    }

    @Test
    fun confirmWordsAreRecognized() {
        assertEquals(Utterance.Confirm, parseUtterance("응"))
        assertEquals(Utterance.Confirm, parseUtterance("그래 해줘".replace(" 해줘", "")))
        assertEquals(Utterance.Cancel, parseUtterance("아니야"))
    }

    // 공조 요청 계산

    private val on = ClimateReading(supported = true, power = true, temperature = 24f, fanSpeed = 3)
    private val off = ClimateReading(supported = true, power = false)

    @Test
    fun tooHotLowersTemperatureFromCurrentAndTurnsAcOn() {
        val c = resolveClimate(ClimateRequest(delta = -2f), on)
        assertEquals(22f, c.temperature)
        assertEquals(true, c.ac)
        assertNull(c.power)  // 이미 켜져 있다
        assertNull(c.driverSeatHeat)  // 말하지 않은 열선은 건드리지 않는다
    }

    @Test
    fun relativeRequestWhenHvacIsOffStartsFromBaseAndPowersOn() {
        val c = resolveClimate(ClimateRequest(delta = -2f), off)
        assertEquals(CLIMATE_BASE - 2f, c.temperature)
        assertEquals(true, c.power)
    }

    @Test
    fun absoluteTemperatureAndRangesAreClamped() {
        assertEquals(CLIMATE_MAX, resolveClimate(ClimateRequest(temperature = 99f), on).temperature)
        assertEquals(CLIMATE_MIN, resolveClimate(ClimateRequest(delta = -30f), on).temperature)
        assertEquals(8, resolveClimate(ClimateRequest(fanSpeed = 20), on).fanSpeed)
        assertEquals(3, resolveClimate(ClimateRequest(steeringWheelHeat = 9), on).steeringWheelHeat)
        assertEquals(0, resolveClimate(ClimateRequest(steeringWheelHeat = -1), on).steeringWheelHeat)
    }

    @Test
    fun temperatureSnapsToHalfDegrees() {
        assertEquals(23.5f, resolveClimate(ClimateRequest(temperature = 23.6f), on).temperature)
    }

    @Test
    fun heatOnlyRequestDoesNotTouchTemperatureOrPower() {
        val c = resolveClimate(ClimateRequest(steeringWheelHeat = 2), off)
        assertNull(c.temperature)
        assertNull(c.power)
        assertEquals(2, c.steeringWheelHeat)
    }

    // 앱 정보 답변

    @Test
    fun etaUsesNaviWhenRoutingAndSaysSoWhenThereIsNoRoute() {
        val navi = NaviState(routing = true, remainingSeconds = 600)
        assertEquals("목적지에 약 10분 뒤, 10시 40분쯤 도착해요.", etaText(navi, GuideState(), LocalTime.of(10, 30)))
        assertEquals("안내 중인 경로가 없어요.", etaText(NaviState(), GuideState(), LocalTime.of(10, 30)))
    }

    @Test
    fun weatherAnswerIsHonestWhenMissing() {
        assertEquals("날씨 정보를 아직 받지 못했어요.", weatherText(null))
        assertTrue(weatherText(WeatherInfo(temperature = 12.0, condition = "맑음", region = "성남시")).contains("맑음 12도"))
        assertTrue(weatherText(WeatherInfo(temperature = 12.0, condition = "비", rainy = true)).contains("비가 와요"))
    }
}
