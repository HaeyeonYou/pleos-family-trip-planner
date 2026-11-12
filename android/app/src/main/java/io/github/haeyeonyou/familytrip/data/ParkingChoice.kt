package io.github.haeyeonyou.familytrip.data

/*
 * 장소마다 고른 주차장 (Figma H02-2 `428:16045`, #250).
 * "고른 주차장을 이 장소의 도착지로 길안내해요": 내비 목적지·도착 판정 좌표는 고른 주차장이고, 방문 기록·화면 이름은 장소 그대로다.
 * 디자인처럼 아무것도 고르지 않았으면 추천(서버 순서 첫 번째) 주차장을 고른 것으로 본다 → [PARKING_DEFAULT_TO_RECOMMENDED].
 */

/** 고르지 않았을 때 추천 주차장을 도착지로 쓸지. false면 고르기 전까지 장소 좌표로 안내한다. */
const val PARKING_DEFAULT_TO_RECOMMENDED = true

/** 같은 주차장인지 (이름과 좌표). 서버가 재계산 때 새 목록을 줘도 같은 곳을 알아본다. */
fun StopParking.sameLot(other: StopParking): Boolean =
    name == other.name && Math.abs(latitude - other.latitude) < 1e-5 && Math.abs(longitude - other.longitude) < 1e-5

/** 이 장소에서 고른 주차장. 고르지 않았으면 추천 주차장(설정에 따라), 주차 정보가 없으면 null. 귀가·주유는 늘 null. */
fun CourseStop.chosenParking(): StopParking? {
    if (role == "home" || role == "fuel") return null
    parkingPick?.let { pick -> return parking.firstOrNull { it.sameLot(pick) } ?: pick }
    return if (PARKING_DEFAULT_TO_RECOMMENDED) parking.firstOrNull() else null
}

/** 내비 목적지·도착 판정 좌표: 고른 주차장, 없으면 장소. */
fun CourseStop.arrivalPoint(): LatLng =
    chosenParking()?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }?.let { LatLng(it.latitude, it.longitude) }
        ?: LatLng(place.latitude, place.longitude)

/** [stopIndex] 경유지에서 [lot]을 고른다. 추천 주차장을 다시 고르면 고른 값을 지운다(기본으로 돌아감). */
fun CourseResponse.withParkingPick(stopIndex: Int, lot: StopParking): CourseResponse {
    val stop = stops.getOrNull(stopIndex) ?: return this
    val pick = lot.takeUnless { PARKING_DEFAULT_TO_RECOMMENDED && stop.parking.firstOrNull()?.sameLot(it) == true }
    return copy(stops = stops.toMutableList().apply { this[stopIndex] = stop.copy(parkingPick = pick) })
}

/**
 * 재계산(`/course/schedule`)으로 새 코스가 와도, 같은 장소(출처·id)에 고른 주차장은 그대로 둔다.
 * 장소가 바뀌었거나 빠졌으면 그 고른 값은 버린다.
 */
fun keepParkingPicks(old: CourseResponse, fresh: CourseResponse): CourseResponse {
    val picks = old.stops.mapNotNull { s -> s.parkingPick?.let { "${s.place.source}:${s.place.sourceId}" to it } }.toMap()
    if (picks.isEmpty()) return fresh
    return fresh.copy(stops = fresh.stops.map { s -> picks["${s.place.source}:${s.place.sourceId}"]?.let { s.copy(parkingPick = it) } ?: s })
}
