package io.github.haeyeonyou.familytrip.vehicle

import io.github.haeyeonyou.familytrip.data.CourseResponse
import io.github.haeyeonyou.familytrip.data.FuelStation
import kotlin.math.roundToInt

/**
 * 주행 가능 거리 판정 (홈 H01 연료 타일, #215 · 지시서 1111-home-fuel).
 * 서버 `app/course/fuel.py`와 기준이 같아야 한다.
 */
object FuelRange {
    /**
     * QP2 "부족" 기준 [제안, 디자이너 답 대기 2026-11-11]: 코스 총 거리(귀가 포함) × 1.3보다 주행 가능 거리가 짧으면 부족.
     * 답이 오면 이 값(서버는 fuel.py의 FUEL_MARGIN)만 바꾼다.
     */
    const val FUEL_MARGIN = 1.3

    /** 코스가 없을 때: 이보다 짧으면 부족 (QP2 제안 50km) */
    const val NO_COURSE_LOW_METERS = 50_000
}

enum class FuelLevel { ENOUGH, LOW }

/** 코스 총 거리(m): 출발 → 경유지 → 귀가 구간을 모두 더한다. */
fun courseMeters(course: CourseResponse): Int = course.stops.sumOf { it.driveDistanceMeters }

/**
 * 충분/부족. 주행 가능 거리를 모르면 null(판정하지 않는다, 가짜 값 없음).
 * [courseMeters]가 null이면 코스가 없는 것으로 보고 [FuelRange.NO_COURSE_LOW_METERS]와 비교한다.
 */
fun fuelLevel(rangeMeters: Float?, courseMeters: Int?, margin: Double = FuelRange.FUEL_MARGIN): FuelLevel? {
    if (rangeMeters == null || rangeMeters < 0f || rangeMeters.isNaN()) return null
    val need = if (courseMeters != null && courseMeters > 0) courseMeters * margin else FuelRange.NO_COURSE_LOW_METERS.toDouble()
    return if (rangeMeters < need) FuelLevel.LOW else FuelLevel.ENOUGH
}

/** 서버에 보낼 주행 가능 거리(m). 모르거나 이상한 값이면 null. */
fun rangeForRequest(rangeMeters: Float?): Int? =
    rangeMeters?.takeIf { !it.isNaN() && it > 0f }?.roundToInt()

/**
 * 전기차인지. Vehicle SDK 2.3.2에는 연료 종류를 읽는 API가 없어(Fuel·CarInfo에 없음),
 * EV 배터리 잔량(stateOfChargePercent)을 받으면 전기차로 본다 (추정, 실차 미확인).
 */
fun VehicleState.isElectric(): Boolean = stateOfChargePercent != null

/** 게이지 원 안의 숫자: 내림한 km. 1km 미만이면 0. */
fun rangeKm(rangeMeters: Float): Int = (rangeMeters / 1000f).toInt().coerceAtLeast(0)

/** 시안의 "1.2km · 3분 · 경로 위" 줄. 다음 지점이 없으면 "근처". */
fun stationMeta(station: FuelStation): String {
    val km = station.distanceMeters / 1000.0
    val distance = if (station.distanceMeters < 1000) "${station.distanceMeters}m" else "%.1fkm".format(km)
    return "$distance · ${station.driveMinutes}분 · ${if (station.onRoute) "경로 위" else "근처"}"
}
