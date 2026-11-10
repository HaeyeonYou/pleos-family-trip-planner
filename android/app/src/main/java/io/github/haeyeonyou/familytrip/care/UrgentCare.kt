package io.github.haeyeonyou.familytrip.care

import io.github.haeyeonyou.familytrip.data.CarePlace
import io.github.haeyeonyou.familytrip.data.FeedingRoutine
import io.github.haeyeonyou.familytrip.data.clockLabel

/**
 * 급한 케어 시트(C09~C11)와 경유지 추가 완료(C12)의 상태·문구 (순수 함수).
 * 화면(`ui/CareUrgentSheet.kt`)은 여기서 만든 값만 그린다.
 */

/** 시트에 한 번에 보이는 후보 수 (C09·C11 "3곳"). */
const val URGENT_LIMIT = 3

/** 시트의 모양. */
enum class UrgentSheetMode {
    /** 찾는 중 */
    Loading,
    /** 찾은 곳 목록 (C09 수유실, C11 정차 공간, 그 밖의 종류도 같은 모양) */
    Found,
    /** 수유실·기저귀 교환대가 없어 정차 공간을 대신 보인다 (C10) */
    Fallback,
    /** 서버에 닿지 않아 자주 가는 케어 장소를 대신 보인다 (C10 아래 문구의 "오프라인일 때") */
    Offline,
    /** 정차 공간까지 없을 때 */
    Empty,
}

data class UrgentSheet(
    val kind: CareKind,
    val mode: UrgentSheetMode = UrgentSheetMode.Loading,
    val places: List<CarePlace> = emptyList(),
    /** 가는 곳(목적지)이 있어 경로 기준으로 골랐는지 */
    val routeBased: Boolean = false,
)

/** 결과가 없을 때 차 안에서 대신 해결할 수 있는 종류인지 (정차 공간을 대신 보인다). */
fun CareKind.fallsBackToParking(): Boolean = this == CareKind.NURSING || this == CareKind.DIAPER

/**
 * 찾기 결과로 시트 모양을 정한다. [found]는 [kind] 결과(실패면 null), [fallback]은 정차 공간 결과(찾지 않았거나 실패면 null),
 * [offline]은 서버에 닿지 않았을 때 보일 자주 가는 케어 장소.
 */
fun urgentSheetOf(
    kind: CareKind,
    found: List<CarePlace>?,
    fallback: List<CarePlace>?,
    offline: List<CarePlace>,
    routeBased: Boolean,
): UrgentSheet = when {
    !found.isNullOrEmpty() -> UrgentSheet(kind, UrgentSheetMode.Found, found.take(URGENT_LIMIT), routeBased)
    kind.fallsBackToParking() && !fallback.isNullOrEmpty() -> UrgentSheet(kind, UrgentSheetMode.Fallback, fallback.take(URGENT_LIMIT - 1), routeBased)
    found == null && offline.isNotEmpty() -> UrgentSheet(kind, UrgentSheetMode.Offline, offline.take(URGENT_LIMIT), routeBased)
    else -> UrgentSheet(kind, UrgentSheetMode.Empty, emptyList(), routeBased)
}

/** 시트 제목. "가까운 수유실 3곳" · "근처에 수유실이 없어요" */
fun urgentTitle(sheet: UrgentSheet): String = when (sheet.mode) {
    UrgentSheetMode.Loading -> "가까운 ${sheet.kind.label} 찾는 중"
    UrgentSheetMode.Found -> "가까운 ${sheet.kind.label} ${sheet.places.size}곳"
    UrgentSheetMode.Fallback, UrgentSheetMode.Empty -> "근처에 ${sheet.kind.label}${subjectParticle(sheet.kind.label)} 없어요"
    UrgentSheetMode.Offline -> "${sheet.kind.label} 정보를 불러오지 못했어요"
}

/** 제목 아래 기준 설명. 서버가 쓰는 자료(카카오 장소 검색)를 그대로 밝힌다. */
fun urgentSubtitle(sheet: UrgentSheet): String {
    val basis = if (sheet.routeBased) "경로 기준 · 돌아가는 시간순" else "현재 위치 기준 · 거리순"
    return when (sheet.mode) {
        UrgentSheetMode.Loading -> basis
        UrgentSheetMode.Found -> when (sheet.kind) {
            CareKind.REST -> "$basis · 주차장·공원 · 갓길 제외"
            else -> "$basis · 카카오 장소 검색 · 자주 가는 곳 표시"
        }
        UrgentSheetMode.Fallback, UrgentSheetMode.Empty -> "반경 10km 안에서 찾지 못했거나 정보를 불러오지 못했어요"
        UrgentSheetMode.Offline -> "서버에 연결하지 못해 자주 가는 케어 장소로 대신 보여 드려요"
    }
}

/** C10 목록 위 안내. */
fun fallbackLead(kind: CareKind): String = when (kind) {
    CareKind.DIAPER -> "대신 차 안에서 기저귀를 갈 수 있는 정차 공간이에요"
    else -> "대신 차 안에서 수유할 수 있는 정차 공간이에요"
}

/** 행 아래 한 줄: "1.2km · 4분 · 경로에서 +3분 · 자주 가는 곳" */
fun urgentPlaceLine(place: CarePlace, frequent: Boolean): String = buildList {
    add("${distanceText(place.distanceM)} · ${place.driveMinutes}분")
    when {
        place.onRoute -> add("경로 위")
        place.detourMinutes != null -> add("경로에서 +${place.detourMinutes}분")
    }
    if (frequent) add("자주 가는 곳")
}.joinToString(" · ")

/** 행 오른쪽 태그. 서버가 확인한 시설 종류만 보인다 (수유실 유무는 "확인 필요"라 체크 태그로 쓰지 않는다). */
fun urgentTags(place: CarePlace): List<String> = buildList {
    if (place.category.isNotBlank()) add(place.category)
    if (place.onRoute) add("경로 위")
}.distinct()

/** 이름이 자주 가는 케어 장소와 같은지 (방문 기록 표시). */
fun isFrequent(place: CarePlace, frequent: List<FrequentCarePlace>): Boolean = frequent.any { it.name == place.name }

private fun distanceText(meters: Int): String = if (meters < 1000) "${meters}m" else "%.1fkm".format(meters / 1000.0)

// ───────────── C12 경유지 추가 완료 ─────────────

/** 케어에서 넣은 경유지 (C12 토스트·"다음 경유지" 슬롯). [asWaypoint]가 false면 안내 중이 아니어서 목적지로 안내를 시작했다. */
data class AddedWaypoint(
    val place: CarePlace,
    val asWaypoint: Boolean,
    val addedAt: Long,
)

/** 토스트를 보이는 시간 (되돌리기를 누를 수 있는 시간). */
const val WAYPOINT_TOAST_MS = 6_000L

/** "OO몰 2F 수유실을 경유지로 추가했어요 · +3분" */
fun waypointToastText(w: AddedWaypoint): String {
    val name = w.place.name
    val head = if (w.asWaypoint) "$name${objectParticle(name)} 경유지로 추가했어요" else "$name${directionParticle(name)} 안내를 시작했어요"
    val extra = when {
        !w.asWaypoint -> null
        w.place.onRoute -> "경로 위"
        w.place.detourMinutes != null && w.place.detourMinutes > 0 -> "+${w.place.detourMinutes}분"
        else -> null
    }
    return listOfNotNull(head, extra).joinToString(" · ")
}

/** 경유지 도착 예상 시각(epoch ms). 서버의 차로 걸리는 시간 추정으로 계산한다. */
fun waypointArrivalAt(w: AddedWaypoint): Long = w.addedAt + w.place.driveMinutes * FeedingRoutine.MS_PER_MINUTE

/** "10:52 도착 · 수유 12:30 전에 여유 있게" / "10:52 도착 · 수유 시각보다 8분 늦어요" */
fun waypointSlotLine(w: AddedWaypoint, feeding: FeedingRoutine): String {
    val arrival = waypointArrivalAt(w)
    val head = "${clockLabel(arrival)} 도착"
    val next = feeding.nextAt ?: return head
    val slack = Math.floorDiv(next - arrival, FeedingRoutine.MS_PER_MINUTE)
    return if (slack >= 0) "$head · 수유 ${clockLabel(next)} 전에 여유 있게" else "$head · 수유 시각보다 ${-slack}분 늦어요"
}

// ───────────── 조사 ─────────────

private fun hasFinalConsonant(word: String): Boolean? {
    val c = word.trimEnd().lastOrNull() ?: return null
    if (c in '가'..'힣') return (c - '가') % 28 != 0
    if (c.isDigit()) return c in "013678"
    return null
}

/** 이/가 */
fun subjectParticle(word: String): String = if (hasFinalConsonant(word) == false) "가" else "이"

/** 을/를 */
fun objectParticle(word: String): String = when (hasFinalConsonant(word)) {
    true -> "을"
    false -> "를"
    null -> "을(를)"
}

/** 으로/로 (ㄹ 받침은 "로") */
fun directionParticle(word: String): String {
    val c = word.trimEnd().lastOrNull() ?: return "(으)로"
    if (c !in '가'..'힣') return "(으)로"
    val jong = (c - '가') % 28
    return if (jong == 0 || jong == 8) "로" else "으로"
}
