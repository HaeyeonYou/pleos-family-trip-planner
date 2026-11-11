package io.github.haeyeonyou.familytrip.guide

import io.github.haeyeonyou.familytrip.care.FrequentCarePlaces
import io.github.haeyeonyou.familytrip.data.CoursePlace
import io.github.haeyeonyou.familytrip.data.CourseResponse
import io.github.haeyeonyou.familytrip.data.CourseStop
import io.github.haeyeonyou.familytrip.data.LatLng
import io.github.haeyeonyou.familytrip.data.TripSnapshot
import io.github.haeyeonyou.familytrip.data.TripSnapshotStop
import io.github.haeyeonyou.familytrip.ui.REPLACEMENT_COUNT
import io.github.haeyeonyou.familytrip.ui.Replacement
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * 코스 진행 화면(Figma ⑥ H03 `420:13227`·H03-1 `420:13357`·H04 `420:13474`)의 계산. 화면·세션과 떼어 단위 테스트한다.
 * 정책(Figma Note): 장소를 하나씩 목적지로 보낸다. 코스는 [귀가]나 [여기서 코스 끝내기]를 고른 순간 끝난다. 다녀온 곳만 기록한다.
 */

/** 코스를 끝낸 방법. [HOME] 귀가를 골라 집 안내 중, [HERE] 여기서 코스 끝내기(안내 없음). */
enum class CourseEnd { HOME, HERE }

/** 지금 화면 단계. */
enum class GuideStage {
    /** 장소로 가는 중 (내비가 앞에 있다) */
    HEADING,

    /** H03 경유지 도착: 다음 장소가 남았다 */
    ARRIVED,

    /** H03-1 마지막 장소 도착: 다음이 귀가뿐 */
    LAST_STOP,

    /** H04 코스 종료 */
    FINISHED,
}

fun stageOf(state: GuideState): GuideStage = when {
    state.endedBy != null -> GuideStage.FINISHED
    !state.arrived -> GuideStage.HEADING
    state.nextVisit == null -> GuideStage.LAST_STOP
    else -> GuideStage.ARRIVED
}

private fun parseTime(text: String?): LocalTime? = text?.takeIf { it.isNotBlank() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

private fun LocalTime.label() = "%02d:%02d".format(hour, minute)

/** 자정을 넘어도 맞게 [from]에서 [to]까지 분 (−12시간 ~ +12시간). */
private fun minutesBetween(from: LocalTime, to: LocalTime): Long {
    val raw = Math.floorMod(Duration.between(from, to).toMinutes(), 24 * 60L)
    return if (raw > 12 * 60) raw - 24 * 60 else raw
}

/**
 * 도착한 장소의 출발 계획. [departAt] = 실제 도착 시각 + 머무는 시간, [delayMinutes] = 계획한 출발보다 늦는 분(빠르면 0),
 * [homeAt] = 마지막 장소면 그 뒤 귀가 도착 시각.
 */
data class DepartPlan(val stayMinutes: Int, val departAt: LocalTime, val delayMinutes: Int, val homeAt: LocalTime?)

fun departPlan(state: GuideState, arrivedAt: LocalTime): DepartPlan? {
    val stop = state.current ?: return null
    val departAt = arrivedAt.plusMinutes(stop.stayMinutes.toLong())
    val planned = parseTime(stop.departAt) ?: parseTime(stop.arriveAt)?.plusMinutes(stop.stayMinutes.toLong()) ?: departAt
    val delay = minutesBetween(planned, departAt).coerceAtLeast(0).toInt()
    val home = state.next?.takeIf { it.role == "home" && state.nextVisit == null }
    return DepartPlan(stop.stayMinutes, departAt, delay, home?.let { departAt.plusMinutes(it.driveMinutes.toLong()) })
}

/**
 * 카드 보조 줄.
 * H03 "60분 머문 뒤 11:40에 출발하면 다음 일정 그대로예요" / 늦으면 "… 출발하면 다음 일정이 15분 늦어져요".
 * H03-1 "오늘 코스의 마지막 장소예요 · 60분 머문 뒤 14:20에 출발하면 14:40 귀가".
 */
fun departLine(plan: DepartPlan, last: Boolean): String {
    val stay = if (plan.stayMinutes > 0) "${plan.stayMinutes}분 머문 뒤 ${plan.departAt.label()}에 출발하면" else "지금 출발하면"
    return if (last) {
        "오늘 코스의 마지막 장소예요 · $stay ${plan.homeAt?.label() ?: "--:--"} 귀가"
    } else if (plan.delayMinutes == 0) {
        "$stay 다음 일정 그대로예요"
    } else {
        "$stay 다음 일정이 ${plan.delayMinutes}분 늦어져요"
    }
}

/** 진행 줄의 점 한 개 (출발·장소·귀가). */
data class ProgressPoint(val label: String, val time: String, val status: Status) {
    enum class Status { DONE, CURRENT, UPCOMING }
}

/** 출발 → 장소들 → 귀가. 지금 장소(도착했거나 가는 중)가 CURRENT, 그 앞은 DONE. */
fun progressPoints(state: GuideState): List<ProgressPoint> {
    val index = state.index ?: return emptyList()
    val start = ProgressPoint("출발", state.departureAt.ifBlank { "--:--" }, ProgressPoint.Status.DONE)
    val finished = state.endedBy != null
    return listOf(start) + state.stops.mapIndexed { i, stop ->
        val status = when {
            finished && stop.role != "home" -> if (i in state.visited) ProgressPoint.Status.DONE else ProgressPoint.Status.UPCOMING
            i == index -> ProgressPoint.Status.CURRENT
            i < index || i in state.visited -> ProgressPoint.Status.DONE
            else -> ProgressPoint.Status.UPCOMING
        }
        ProgressPoint(if (stop.role == "home") "귀가" else stop.place.name, stop.arriveAt, status)
    }
}

/** 오른쪽 "남은 일정": 지금 장소 다음부터 귀가까지 (시각·이름·머무는 시간). */
data class RemainingStop(val time: String, val name: String, val stayMinutes: Int)

fun remainingStops(state: GuideState): List<RemainingStop> {
    val index = state.index ?: return emptyList()
    return state.stops.drop(index + 1).map { RemainingStop(it.arriveAt, if (it.role == "home") "귀가" else it.place.name, if (it.role == "home") 0 else it.stayMinutes) }
}

/** H04 다녀온 장소 (도착한 곳만, 코스 순서). */
fun visitedStops(state: GuideState): List<CourseStop> =
    state.stops.filterIndexed { i, stop -> i in state.visited && stop.role != "home" }

/** H04 "덕수궁 나들이 코스 · 10:00 – 14:40 · 3곳". 귀가를 고르지 않았으면 끝은 마지막으로 다녀온 곳을 떠난 시각. */
fun finishedSummary(state: GuideState): String {
    val visited = visitedStops(state)
    val home = state.stops.lastOrNull()?.takeIf { it.role == "home" }
    val end = when {
        state.endedBy == CourseEnd.HOME && home != null -> home.arriveAt
        else -> visited.lastOrNull()?.let { it.departAt ?: parseTime(it.arriveAt)?.plusMinutes(it.stayMinutes.toLong())?.label() } ?: "--:--"
    }
    return "${state.title} · ${state.departureAt.ifBlank { "--:--" }} – $end · ${visited.size}곳"
}

/** 코스 이름: "덕수궁 나들이 코스" (H02 머리와 같은 규칙). */
fun courseTitle(stops: List<CourseStop>): String {
    val outing = stops.firstOrNull { it.role == "outing" } ?: stops.firstOrNull { it.role != "home" } ?: stops.firstOrNull()
    return "${outing?.place?.name ?: "오늘"} 나들이 코스"
}

/**
 * 장소를 바꾸거나 붙여 서버가 다시 계산한 코스를 안내에 반영한다. 이미 지나온 장소(지금 장소까지)가 그대로일 때만 받는다.
 * 지금 위치·도착 여부·다녀온 곳은 유지한다. 맞지 않으면 null (다른 코스가 들어온 것).
 */
fun withRescheduled(state: GuideState, stops: List<CourseStop>): GuideState? {
    val index = state.index ?: return null
    val keep = state.stops.take(index + 1).map { it.place.sourceId }
    if (stops.take(index + 1).map { it.place.sourceId } != keep) return null
    return state.copy(stops = stops)
}

/**
 * H03-1 [한 곳 더 가기] 후보 (H02-1 형식, 최대 3곳): 코스 후보(`alternatives`) 중 아직 코스에 없는 식사·나들이·카페를
 * 지금 장소에서 가깝고 귀가 길에서 덜 돌아가는 순서로. 우회 분은 직선거리 어림(붙인 뒤 `/course/schedule`이 진짜 값을 준다).
 */
fun extraCandidates(course: CourseResponse, current: CourseStop, home: LatLng?): List<Pair<String, Replacement>> {
    val taken = course.stops.map { it.place.sourceId }.toSet()
    val here = LatLng(current.place.latitude, current.place.longitude)
    fun dist(a: LatLng, p: CoursePlace) = FrequentCarePlaces.distanceMeters(a, p.latitude, p.longitude)
    val direct = home?.let { FrequentCarePlaces.distanceMeters(here, it.latitude, it.longitude) } ?: 0
    return listOf("meal", "outing", "cafe")
        .flatMap { role -> course.alternatives[role].orEmpty().map { role to it } }
        .filter { (_, p) -> p.sourceId !in taken }
        .distinctBy { (_, p) -> p.sourceId }
        .map { (role, p) ->
            val toPlace = dist(here, p)
            val extra = (toPlace + (home?.let { dist(it, p) } ?: 0) - direct).coerceAtLeast(0)
            role to Replacement(p, toPlace, if (extra == 0) 0 else FrequentCarePlaces.minutes(extra), keepsNeeds = true)
        }
        .sortedWith(compareBy<Pair<String, Replacement>> { it.second.detourMinutes }.thenBy { it.second.distanceM })
        .take(REPLACEMENT_COUNT)
}

/** 서버 `PUT /me/trip`에 올릴 지금 코스 (좌표 없음). 안내 전이면 null. */
fun tripSnapshot(state: GuideState, today: LocalDate): TripSnapshot? {
    val index = state.index ?: return null
    if (state.stops.isEmpty()) return null
    val finished = state.endedBy != null
    return TripSnapshot(
        title = state.title.take(60),
        startedOn = today.toString(),
        departureAt = parseTime(state.departureAt)?.label() ?: state.stops.first().arriveAt,
        status = if (finished) "finished" else "active",
        endedBy = state.endedBy?.name?.lowercase(),
        stops = state.stops.mapIndexed { i, stop ->
            val home = stop.role == "home"
            val st = when {
                i in state.visited -> "visited"
                finished && !home -> "skipped"
                finished && state.endedBy == CourseEnd.HOME -> "current"
                finished -> "skipped"
                i == index -> "current"
                else -> "upcoming"
            }
            TripSnapshotStop(stop.place.name.take(60).ifBlank { "장소" }, stop.role, parseTime(stop.arriveAt)?.label() ?: "00:00", stop.stayMinutes.coerceIn(0, 600), st)
        }.take(12),
    )
}

// ───────────── 다시 뜬 앱이 안내를 이어 갈지 ─────────────

/**
 * 저장해 둔 안내를 이어 갈지: 기기 시간대 기준으로 오늘 시작한 코스만 (자정을 넘기면 어제 코스다).
 * 시작 시각이 없는 옛 저장 파일은 언제 시작했는지 모르므로 이어 가지 않는다.
 */
fun resumableToday(state: GuideState, nowMillis: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Boolean {
    val started = state.startedAtMillis ?: return false
    fun day(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    return day(started) == day(nowMillis)
}

/** 지난 날 코스를 끝낸 것으로 서버에 올릴 스냅숏 (여기서 끝냄, 시작한 날짜 기준). 시작 시각을 모르면 [fallbackDay]. */
fun expiredSnapshot(state: GuideState, fallbackDay: LocalDate, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): TripSnapshot? {
    val day = state.startedAtMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: fallbackDay
    return tripSnapshot(state.copy(endedBy = CourseEnd.HERE, arrived = false), day)
}

// ───────────── 음성 답 ─────────────

/** 도착 화면에서 고를 수 있는 답. */
enum class ArrivalChoice { NEXT, SWAP, HOME, MORE, END_HERE }

/**
 * 도착 질문에 대한 한마디를 가른다 (H03 "다음 장소 가자"·"바꿔줘"·"집에 가자", H03-1 "집에 가자"·"한 곳 더").
 * 그 단계에 없는 답이면 null (예: 마지막 장소에서 "다음 장소"). 띄어쓰기는 무시한다.
 */
fun classifyArrivalReply(text: String, stage: GuideStage): ArrivalChoice? {
    val c = text.filter { it.isLetterOrDigit() }
    if (c.isEmpty()) return null
    val choice = when {
        listOf("코스끝", "여기서끝", "그만할", "끝내").any { c.contains(it) } -> ArrivalChoice.END_HERE
        listOf("집에", "집으로", "귀가", "집가", "우리집").any { c.contains(it) } -> ArrivalChoice.HOME
        listOf("한곳더", "한군데더", "하나더", "더가", "한곳만더").any { c.contains(it) } -> ArrivalChoice.MORE
        listOf("바꿔", "바꾸", "다른곳", "다른데", "딴데").any { c.contains(it) } -> ArrivalChoice.SWAP
        listOf("다음장소", "다음으로", "다음곳", "다음가", "다음").any { c.contains(it) } -> ArrivalChoice.NEXT
        else -> null
    } ?: return null
    return when (stage) {
        GuideStage.ARRIVED -> choice.takeIf { it != ArrivalChoice.MORE }
        GuideStage.LAST_STOP -> choice.takeIf { it == ArrivalChoice.HOME || it == ArrivalChoice.MORE || it == ArrivalChoice.END_HERE }
        else -> null
    }
}

/** 주행 중 도착하면 음성으로만 묻는 말 (화면 문구와 같은 선택지). */
fun arrivalQuestion(state: GuideState): String? {
    val name = state.current?.place?.name ?: return null
    return when (stageOf(state)) {
        GuideStage.ARRIVED -> "${name}에 도착했어요. 다음 장소로 갈까요? \"다음 장소 가자\", \"바꿔줘\", \"집에 가자\"로 답해 주세요."
        GuideStage.LAST_STOP -> "${name}에 도착했어요. 오늘 코스의 마지막 장소예요. \"집에 가자\"나 \"한 곳 더\"로 답해 주세요."
        else -> null
    }
}
