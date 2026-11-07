package io.github.haeyeonyou.familytrip.voice

import io.github.haeyeonyou.familytrip.care.CareKind
import io.github.haeyeonyou.familytrip.data.VoiceInterpretation
import io.github.haeyeonyou.familytrip.vehicle.ClimateChange
import io.github.haeyeonyou.familytrip.vehicle.ClimateReading
import kotlin.math.roundToInt

/** 앱이 음성으로 할 수 있는 동작. 서버의 동작 목록(app/voice.py)과 이름이 같다. */
sealed interface Step {
    data class Situation(val situation: CareSituation) : Step
    data class Sleep(val on: Boolean) : Step
    data class BabyCare(val on: Boolean) : Step
    data class Climate(val request: ClimateRequest) : Step
    data object FeedLog : Step
    data class FeedInterval(val minutes: Int) : Step
    data class Find(val kind: CareKind) : Step
    data class AddWaypoint(val kind: CareKind) : Step
    data object RemoveWaypoint : Step
    data class GoTab(val tab: VoiceTab) : Step
    data class SetFilter(val filter: VoiceFilter, val on: Boolean) : Step
    data object AnotherCourses : Step
    data class OpenCourse(val number: Int) : Step
    data object StartCourse : Step
    data class Choose(val number: Int) : Step
    data object Confirm : Step
    data object Cancel : Step
    data object KeepGoing : Step
    data class Query(val topic: VoiceTopic) : Step

    /** 해석은 됐지만 앱에서 할 수 없는 요청 */
    data object Unsupported : Step
}

enum class VoiceTab(val api: String, val label: String) { HOME("home", "홈"), CARE("care", "케어"), GROW("grow", "그로우"), SETTINGS("settings", "설정") }

/** 홈의 코스 추천 조건 칩 (서버 filter 값 → 앱 칩) */
enum class VoiceFilter(val api: String, val label: String) {
    PET("pet", "반려견 동반"),
    INDOOR("indoor", "실내"),
    NURSING_ROOM("nursing_room", "수유실"),
    STROLLER("stroller", "유아차"),
    KIDS_MENU("kids_menu", "키즈메뉴"),
    PLAYROOM("playroom", "놀이방"),
    PARKING("parking", "주차"),
}

enum class VoiceTopic(val api: String) { ETA("eta"), NEXT_FEED("next_feed"), LAST_FEED("last_feed"), WEATHER("weather"), CLIMATE("climate") }

/** 공조 요청: 절대 온도 또는 지금보다 올리고 내릴 폭, 바람, 에어컨, 열선 */
data class ClimateRequest(
    val temperature: Float? = null,
    val delta: Float? = null,
    val fanSpeed: Int? = null,
    val ac: Boolean? = null,
    val driverSeatHeat: Int? = null,
    val passengerSeatHeat: Int? = null,
    val steeringWheelHeat: Int? = null,
)

/** 서버가 해석한 결과를 앱 동작으로 바꾼다. 알 수 없는 값은 null (되묻는다). */
fun toStep(i: VoiceInterpretation): Step? {
    if (i.source != "llm") return null
    val target = i.target
    return when (i.action) {
        "care_situation" -> when (target) {
            "sleep" -> CareSituation.SLEEP
            "diaper" -> CareSituation.DIAPER
            "feed" -> CareSituation.FEED
            "soothe" -> CareSituation.SOOTHE
            else -> null
        }?.let(Step::Situation)
        "sleep_mode" -> i.on?.let(Step::Sleep)
        "baby_care" -> i.on?.let(Step::BabyCare)
        "climate" -> Step.Climate(
            ClimateRequest(
                temperature = i.temperature?.toFloat(),
                delta = i.temperatureDelta?.toFloat(),
                fanSpeed = i.number,
                ac = i.ac,
                driverSeatHeat = i.driverSeatHeat,
                passengerSeatHeat = i.passengerSeatHeat,
                steeringWheelHeat = i.steeringHeat,
            ),
        ).takeIf { with(it.request) { listOfNotNull(temperature, delta, fanSpeed, ac, driverSeatHeat, passengerSeatHeat, steeringWheelHeat).isNotEmpty() } }
        "feed_log" -> Step.FeedLog
        "feed_interval" -> i.number?.takeIf { it in FEED_INTERVALS }?.let(Step::FeedInterval)
        "find_place" -> placeKind(target)?.let(Step::Find)
        "add_waypoint" -> placeKind(target)?.let(Step::AddWaypoint)
        "remove_waypoint" -> Step.RemoveWaypoint
        "go_tab" -> VoiceTab.entries.firstOrNull { it.api == target }?.let(Step::GoTab)
        "set_filter" -> VoiceFilter.entries.firstOrNull { it.api == target }?.let { f -> i.on?.let { Step.SetFilter(f, it) } }
        "another_courses" -> Step.AnotherCourses
        "open_course" -> i.number?.takeIf { it in 1..3 }?.let(Step::OpenCourse)
        "start_course" -> Step.StartCourse
        "choose" -> i.number?.takeIf { it in 1..3 }?.let(Step::Choose)
        "confirm" -> Step.Confirm
        "cancel" -> Step.Cancel
        "keep_going" -> Step.KeepGoing
        "query" -> VoiceTopic.entries.firstOrNull { it.api == target }?.let(Step::Query)
        else -> null
    }
}

val FEED_INTERVALS = listOf(120, 150, 180, 240)

private fun placeKind(target: String?): CareKind? = CareKind.entries.firstOrNull { it.api == target }

/**
 * 운전 중 위험해질 수 있는 동작은 바로 하지 않고 되묻는다("응"/"아니야").
 * 주차 중이면 바로 한다. 되물을 필요가 없으면 null.
 */
fun confirmPrompt(step: Step, driving: Boolean): String? {
    if (!driving) return null
    return when (step) {
        is Step.Climate -> "주행 중이에요. 공조를 바꿀까요? (${describe(step.request)}) \"응\" 또는 \"아니야\"로 말씀해 주세요."
        is Step.BabyCare -> if (step.on) "주행 중이에요. 베이비케어 공조를 켤까요? \"응\" 또는 \"아니야\"로 말씀해 주세요." else null
        Step.RemoveWaypoint -> "경유지를 모두 뺄까요? \"응\" 또는 \"아니야\"로 말씀해 주세요."
        Step.StartCourse -> "코스 안내를 시작할까요? \"응\" 또는 \"아니야\"로 말씀해 주세요."
        else -> null
    }
}

/** 공조 요청을 사람이 읽는 말로 (예: "온도 2도 낮춤, 에어컨 켬") */
fun describe(r: ClimateRequest): String = buildList {
    r.temperature?.let { add("온도 ${trimTemp(it)}도") }
    r.delta?.let { add(if (it < 0) "온도 ${trimTemp(-it)}도 낮춤" else "온도 ${trimTemp(it)}도 올림") }
    r.fanSpeed?.let { add("풍량 ${it}단") }
    r.ac?.let { add(if (it) "에어컨 켬" else "에어컨 끔") }
    r.driverSeatHeat?.let { add(heat("운전석", it)) }
    r.passengerSeatHeat?.let { add(heat("조수석", it)) }
    r.steeringWheelHeat?.let { add(heat("핸들", it)) }
}.joinToString(", ")

private fun heat(name: String, level: Int) = if (level == 0) "$name 열선 끔" else "$name 열선 ${level}단"

private fun trimTemp(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v)

/** 안전 범위 (서버와 같다). 차가 지원하는 구역 범위로 한 번 더 맞춰진다. */
const val CLIMATE_MIN = 16f
const val CLIMATE_MAX = 30f
const val CLIMATE_BASE = 23f

/**
 * 요청을 차에 보낼 변경 항목으로 바꾼다. 상대 요청(덥다 -2)은 지금 설정 온도를 기준으로 계산하고,
 * 설정 온도를 읽지 못하면(공조가 꺼진 상태) [CLIMATE_BASE]에서 출발해 공조를 함께 켠다.
 * 더워서 온도를 내리면 에어컨을 함께 켠다.
 */
fun resolveClimate(r: ClimateRequest, reading: ClimateReading): ClimateChange {
    val relative = r.delta != null && r.temperature == null
    val temperature = when {
        r.temperature != null -> r.temperature
        relative -> ((reading.temperature ?: CLIMATE_BASE) + r.delta!!)
        else -> null
    }?.coerceIn(CLIMATE_MIN, CLIMATE_MAX)?.let { (it * 2).roundToInt() / 2f }  // 0.5도 단위
    val needsPower = reading.power != true && (temperature != null || r.fanSpeed != null || r.ac == true)
    return ClimateChange(
        power = true.takeIf { needsPower },
        temperature = temperature,
        fanSpeed = r.fanSpeed?.coerceIn(1, 8),
        ac = r.ac ?: (true.takeIf { relative && r.delta!! < 0 }),
        driverSeatHeat = r.driverSeatHeat?.coerceIn(0, 3),
        passengerSeatHeat = r.passengerSeatHeat?.coerceIn(0, 3),
        steeringWheelHeat = r.steeringWheelHeat?.coerceIn(0, 3),
    )
}
