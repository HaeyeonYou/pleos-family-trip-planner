package io.github.haeyeonyou.familytrip.care

import io.github.haeyeonyou.familytrip.data.ClimateSettings
import io.github.haeyeonyou.familytrip.data.FeedingRoutine
import io.github.haeyeonyou.familytrip.data.LatLng
import io.github.haeyeonyou.familytrip.data.clockLabel

/**
 * 수유 정차 모드(C03)의 규칙 (순수 함수). 확정·추천 결정:
 * - 정차 = P단 (D10). 정차 중에만 켤 수 있고, 출발(P단에서 벗어남)하면 자동으로 끝난다.
 * - "수유 중"은 차 앱 안의 진행 상태이고, 끝나면 **시작 시각**을 마지막 수유로 기록한다 (D3, Q06 ①).
 * - 자동 기록은 수유 중 타이머가 돌던 정차가 끝날 때만 (Q07 ③). 너무 짧으면(켜자마자 출발) 기록하지 않는다.
 * - 시작은 카드·음성 "수유할게" + 케어에서 넣은 경유지에 도착해 정차하면 묻기 (Q08 ①+②). 몰래 자동 적용은 하지 않는다.
 */

/**
 * 진행 중인 수유 정차. [climateApplied]는 차 공조에 모드 값을 넣었는지.
 * [feeding]이 false면 12개월이 넘은 아이의 '정차 모드'(Figma C05-T1 "쉬었다 갈게")라 같은 공조 값만 쓰고 수유로 기록하지 않는다 (#217).
 */
data class FeedingStopSession(val startedAt: Long, val climateApplied: Boolean = false, val feeding: Boolean = true)

/** 출발로 자동 종료될 때 이보다 짧으면 수유로 기록하지 않는다 (켜자마자 출발한 경우). */
const val FEEDING_STOP_MIN_RECORD_MINUTES = 3L

/** 케어에서 넣은 경유지에서 이 거리 안에 정차하면 "도착"으로 보고 수유 정차 모드를 묻는다. */
const val FEEDING_STOP_ARRIVAL_RADIUS_M = 300

/** 서버 프리셋이 없을 때의 수유 정차 모드 (Figma C03 "24°C · 송풍"). 송풍 = 가장 약한 바람, 냉방 끔. */
val FEEDING_STOP_DEFAULT = ClimateSettings(
    name = "수유 정차",
    power = true,
    temperature = 24f,
    fanSpeed = 1,
    ac = false,
    airCleaner = false,
    driverSeatHeat = 0,
    passengerSeatHeat = 0,
    steeringWheelHeat = 0,
)

/** "12분째" 의 12. */
fun feedingStopMinutes(session: FeedingStopSession, now: Long): Long =
    Math.floorDiv(now - session.startedAt, FeedingRoutine.MS_PER_MINUTE).coerceAtLeast(0)

/** 진행 막대: 30분을 한 바퀴로 본다 (Figma C03 12분째 ≈ 30%). */
const val FEEDING_STOP_BAR_MINUTES = 30f

fun feedingStopProgress(session: FeedingStopSession, now: Long): Float =
    (feedingStopMinutes(session, now) / FEEDING_STOP_BAR_MINUTES).coerceIn(0f, 1f)

/** 끝날 때 수유로 기록할지. 손으로 끝내면 항상, 출발로 끝나면 [FEEDING_STOP_MIN_RECORD_MINUTES] 이상일 때만. */
fun shouldRecordFeeding(session: FeedingStopSession, now: Long, auto: Boolean): Boolean =
    session.feeding && (!auto || feedingStopMinutes(session, now) >= FEEDING_STOP_MIN_RECORD_MINUTES)

/** 끝난 뒤 한 줄 안내. */
fun feedingStopEndMessage(session: FeedingStopSession, recorded: Boolean, auto: Boolean): String {
    if (!session.feeding) return if (auto) "출발해서 정차 모드를 끝냈어요" else "정차 모드를 끝냈어요"
    val head = if (auto) "출발해서 수유 정차 모드를 끝냈어요" else "수유 정차 모드를 끝냈어요"
    return if (recorded) "$head · ${clockLabel(session.startedAt)} 수유로 기록했어요" else "$head · 짧아서 수유로 기록하지 않았어요"
}

/**
 * 케어에서 넣은 경유지에 도착해 정차했는지 (수유 정차 모드를 물을 때). [here]가 없으면 판단하지 않는다.
 */
fun arrivedAtWaypoint(parked: Boolean, added: AddedWaypoint?, here: LatLng?): Boolean {
    if (!parked || added == null || here == null) return false
    return FrequentCarePlaces.distanceMeters(here, added.place.latitude, added.place.longitude) <= FEEDING_STOP_ARRIVAL_RADIUS_M
}
