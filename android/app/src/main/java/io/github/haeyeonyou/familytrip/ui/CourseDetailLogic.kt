package io.github.haeyeonyou.familytrip.ui

import io.github.haeyeonyou.familytrip.care.FrequentCarePlaces
import io.github.haeyeonyou.familytrip.data.CoursePlace
import io.github.haeyeonyou.familytrip.data.CourseResponse
import io.github.haeyeonyou.familytrip.data.CourseStop
import io.github.haeyeonyou.familytrip.data.LatLng
import java.time.Duration
import java.time.LocalTime

/*
 * 코스 상세 H02·대체 장소 H02-1 (11/10 디자인)의 계산 (순수 함수). 화면은 CourseOverview.kt.
 * 서버는 코스·교체 후보·재계산(`/course`, `/course/schedule`)만 주고, 요약 태그·수유 지점·후보 우회 시간은 앱이 계산한다.
 */

/** 요약 태그 한 개. [available]: true 체크, false 빼기, null이면 아이콘만. */
data class CourseTag(val label: String, val kind: Kind, val available: Boolean? = null) {
    enum class Kind { TIME, NURSING, STROLLER, PARKING, PET }
}

private fun CourseStop.isVisit() = role != "home"
private fun CoursePlace.has(key: String) = amenities[key] == "available"

/** "총 5시간 30분" (출발~귀가). 시각을 못 읽으면 null. */
internal fun totalLabel(course: CourseResponse): String? = runCatching {
    var minutes = Duration.between(LocalTime.parse(course.departureAt), LocalTime.parse(course.returnAt)).toMinutes()
    if (minutes < 0) minutes += 24 * 60
    if (minutes % 60 == 0L) "총 ${minutes / 60}시간" else "총 ${minutes / 60}시간 ${minutes % 60}분"
}.getOrNull()

/**
 * H02 요약 태그 5종: 총 시간 · 수유실 N곳 · 유모차 · 주차 · 반려동물.
 * 편의 정보가 '정보 없음'(unknown)이면 없다고 단정하지 않고 "확인 필요"로 쓴다.
 */
internal fun courseTags(course: CourseResponse): List<CourseTag> {
    val visits = course.stops.filter { it.isVisit() }
    val places = visits.map { it.place }
    return buildList {
        totalLabel(course)?.let { add(CourseTag(it, CourseTag.Kind.TIME)) }
        val nursing = places.count { it.has("nursing_room") }
        add(CourseTag(if (nursing > 0) "수유실 ${nursing}곳" else "수유실 확인 필요", CourseTag.Kind.NURSING, nursing > 0))
        val stroller = places.count { it.has("stroller") }
        add(
            CourseTag(
                when {
                    places.isNotEmpty() && stroller == places.size -> "유모차 OK"
                    stroller > 0 -> "유모차 ${stroller}곳 가능"
                    else -> "유모차 확인 필요"
                },
                CourseTag.Kind.STROLLER,
                stroller > 0,
            ),
        )
        val lots = visits.sumOf { it.parking.size }
        add(CourseTag(if (lots > 0) "주차 ${lots}곳 확인" else "주차 정보 없음", CourseTag.Kind.PARKING, lots > 0))
        val pet = places.count { it.has("pet") }
        add(
            CourseTag(
                when {
                    places.isNotEmpty() && pet == places.size -> "반려동물 OK"
                    pet > 0 -> "반려동물 일부만 가능"
                    else -> "반려동물 확인 필요"
                },
                CourseTag.Kind.PET,
                if (pet > 0) true else null,
            ),
        )
    }
}

/** 타임라인에 끼워 넣을 수유·케어 지점. [afterStop] 번째 경유지 행 바로 아래에 둔다. */
data class FeedingPoint(val afterStop: Int, val time: String, val title: String, val detail: String)

/**
 * 다음 수유 시각([nextFeeding])이 코스 안(출발~귀가)에 있으면 그 시각에 머무는(또는 막 떠난) 경유지에 수유 지점을 둔다 (Q29).
 * 그 장소에 수유실이 있으면 "OO 수유실", 없으면 "OO 근처 수유실 확인". 코스 밖이면 null.
 */
internal fun feedingPoint(course: CourseResponse, nextFeeding: LocalTime?): FeedingPoint? {
    nextFeeding ?: return null
    val start = runCatching { LocalTime.parse(course.departureAt) }.getOrNull() ?: return null
    // 출발부터 몇 분 뒤인지로 비교한다 (밤에 출발해 자정을 넘는 코스)
    fun offset(t: LocalTime) = Math.floorMod(Duration.between(start, t).toMinutes(), 24 * 60L)
    val end = runCatching { offset(LocalTime.parse(course.returnAt)) }.getOrNull() ?: return null
    val next = offset(nextFeeding)
    if (next >= end) return null
    // 다음 수유 시각 이전에 도착한 마지막 경유지
    val index = course.stops.indices.lastOrNull { i ->
        val stop = course.stops[i]
        stop.isVisit() && runCatching { offset(LocalTime.parse(stop.arriveAt)) <= next }.getOrDefault(false)
    } ?: return null
    val place = course.stops[index].place
    val time = "%02d:%02d".format(nextFeeding.hour, nextFeeding.minute)
    return if (place.has("nursing_room")) {
        FeedingPoint(index, time, "수유 · 케어 지점", "${place.name} 수유실 · 다음 수유 ${time}에 맞춤")
    } else {
        FeedingPoint(index, time, "수유 · 케어 지점", "${place.name} 근처 수유실 확인 · 다음 수유 ${time}")
    }
}

/** H02-1 후보 한 곳. [detourMinutes]는 앞뒤 지점 사이에서 더 걸리는 시간(서버 값, 없으면 직선거리 어림). */
data class Replacement(val place: CoursePlace, val distanceM: Int, val detourMinutes: Int, val keepsNeeds: Boolean)

const val REPLACEMENT_COUNT = 3

/**
 * [stopIndex] 경유지의 대체 후보 최대 3곳. 서버 `alternatives[role]`에서 고르고,
 * 원래 장소에 있던 수유실·유모차 조건을 지키는 곳 → 경로에서 덜 돌아가는 곳 순서 (디자인 Note "같은 시간대 · 경로에서 가까운 순 · 조건 유지").
 * 우회 시간·거리는 서버가 준 `detour_minutes`·`distance_m`(#160)을 쓰고, 없으면(그 역할이 코스에 없었거나 경로가 바뀌어 지운 값)
 * 직선거리 × 도로 계수로 어림한다. 바꾼 뒤 `/course/schedule` 재계산이 진짜 값이다.
 */
internal fun replacements(course: CourseResponse, stopIndex: Int, origin: LatLng?): List<Replacement> {
    val stop = course.stops.getOrNull(stopIndex)?.takeIf { it.isVisit() } ?: return emptyList()
    val prev = course.stops.getOrNull(stopIndex - 1)?.place?.let { LatLng(it.latitude, it.longitude) } ?: origin
    val next = course.stops.getOrNull(stopIndex + 1)?.place?.let { LatLng(it.latitude, it.longitude) }
    val needs = listOf("nursing_room", "stroller").filter { stop.place.has(it) }
    val taken = course.stops.map { it.place.sourceId }.toSet()
    fun leg(a: LatLng?, p: CoursePlace): Int = a?.let { FrequentCarePlaces.distanceMeters(it, p.latitude, p.longitude) } ?: 0
    val base = leg(prev, stop.place) + leg(next, stop.place)
    return course.alternatives[stop.role].orEmpty()
        .filter { it.sourceId !in taken }
        .map { p ->
            // 서버 값(#160: 실제 경로 시간 비율로 보정한 추정)이 있으면 그것, 없으면 직선거리 어림
            val extra = (leg(prev, p) + leg(next, p) - base).coerceAtLeast(0)
            Replacement(
                place = p,
                distanceM = p.distanceM ?: FrequentCarePlaces.distanceMeters(LatLng(stop.place.latitude, stop.place.longitude), p.latitude, p.longitude),
                detourMinutes = p.detourMinutes?.coerceAtLeast(0) ?: if (extra == 0) 0 else FrequentCarePlaces.minutes(extra),
                keepsNeeds = needs.all { p.has(it) },
            )
        }
        .sortedWith(compareByDescending<Replacement> { it.keepsNeeds }.thenBy { it.detourMinutes }.thenBy { it.distanceM })
        .take(REPLACEMENT_COUNT)
}

/** "0.6km · 경로에서 +2분" / "1.1km · 추가 이동 없음" (지금 장소보다 덜 돌아가면) */
internal fun replacementMeta(r: Replacement): String =
    distanceLabel(r.distanceM) + " · " + if (r.detourMinutes == 0) "추가 이동 없음" else "경로에서 +${r.detourMinutes}분"

/** 타임라인 행 보조 줄: "식사 · 60분 머무름 · 주차 공영 도보 약 3분". 주유·충전(#215)은 "주유 · 10분"·"충전 · 30분" */
internal fun stopLine(stop: CourseStop): String = if (stop.role == "fuel") {
    val label = if ("전기차" in stop.place.category || "충전소" in stop.place.name) "충전" else ROLE_LABELS[stop.role]
    listOfNotNull(label, stop.stayMinutes.takeIf { it > 0 }?.let { "${it}분" }).joinToString(" · ")
} else listOfNotNull(
    ROLE_LABELS[stop.role],
    stop.stayMinutes.takeIf { it > 0 }?.let { "${it}분 머무름" },
    stop.parking.firstOrNull()?.let { "주차 " + listOfNotNull(it.kind, it.name.takeIf { _ -> it.kind == null }).joinToString(" ") + " 도보 약 ${it.walkMinutes}분" },
).joinToString(" · ")

/** 장소 추가 후보: 서버 `alternatives`의 식사·나들이·카페 중 코스에 아직 없는 곳 (역할 순서 그대로, 빈 역할은 뺀다). */
internal fun addCandidates(course: CourseResponse): List<Pair<String, List<CoursePlace>>> {
    val taken = course.stops.map { it.place.sourceId }.toSet()
    return listOf("meal", "outing", "cafe").mapNotNull { role ->
        course.alternatives[role].orEmpty().filter { it.sourceId !in taken }.takeIf { it.isNotEmpty() }?.let { role to it }
    }
}

/**
 * 재계산 뒤 쓸 대체 후보. 서버가 새로 준 역할은 새 값으로 바꾸고, 안 준 역할은 이전 후보를 그대로 둔다.
 * 이전 후보의 우회 분·거리(#160)는 그때 경로 기준이라, 경유지 순서·장소가 바뀌었으면 지워서 앱 어림값을 쓰게 한다
 * (머무름만 바꾼 재계산이면 경로가 같아 그대로 둔다).
 */
internal fun mergeAlternatives(
    course: CourseResponse,
    requested: List<io.github.haeyeonyou.familytrip.data.ScheduleStop>,
    fresh: Map<String, List<CoursePlace>>,
): Map<String, List<CoursePlace>> {
    val route = requested.map { it.place.sourceId }
    val sameRoute = route == course.stops.filter { it.isVisit() }.map { it.place.sourceId }
    // 코스에 들어간 곳은 후보에서 뺀다 (값을 지운 사본은 호출 쪽의 `목록 - 고른 장소`와 같지 않아 여기서 sourceId로 뺀다)
    val old = course.alternatives.mapValues { (_, ps) ->
        ps.filter { it.sourceId !in route }.map { if (sameRoute) it else it.copy(distanceM = null, detourMinutes = null) }
    }
    return old + fresh
}
