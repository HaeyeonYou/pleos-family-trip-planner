package io.github.haeyeonyou.familytrip.navi

/** 내비 `TrafficCongestionType`을 앱에서 쓰는 모양으로 옮긴 것 (SDK 클래스에 매이지 않게). */
enum class Congestion { UNKNOWN, FAST, MODERATE, SLOW, HEAVY }

enum class TrafficEventKind { ACCIDENT, DISASTER, REPAIRING, ROAD_DAMAGE, EVENT, ETC }

/** 경로 위 정체 구간 하나 (`TrafficCongestionInfo`). */
data class CongestionSection(
    val roadName: String,
    val type: Congestion,
    val lengthMeters: Int,
    val seconds: Int,
)

/** 경로 위 사고·공사 등 (`TrafficEventInfo`). [endMillis]는 끝나는 시각(epoch ms), 모르면 null. */
data class TrafficEventItem(
    val kind: TrafficEventKind,
    val description: String,
    val endMillis: Long?,
)

/** 내비 `RouteStateInfo.trafficStatus`를 옮긴 것. */
data class TrafficSnapshot(
    val sections: List<CongestionSection> = emptyList(),
    val events: List<TrafficEventItem> = emptyList(),
)

/**
 * 지연 알림에 쓰는 정체 이유.
 * [reason]은 "…(으)로 도착이 N분 늦어져요" 앞에 붙는 짧은 말, [detail]은 알림 아래에 붙는 자세한 말.
 */
data class DelayCause(val reason: String, val detail: String)

object TrafficCause {
    /** 이유를 알 수 없을 때 */
    val UNKNOWN = DelayCause("교통 상황", "내비가 알려 준 도착 시각이 처음보다 늦어졌어요")

    /**
     * 내비가 준 교통 정보에서 가장 큰 이유 하나를 고른다.
     * 사고·재난 > 공사 > 도로 파손 > 정체 > 행사 순으로 본다. 정체는 심한 구간(HEAVY)이 있으면 그것만,
     * 없으면 서행(SLOW)까지 보고, 도로 이름별로 정체 시간을 합쳐 가장 큰 도로를 이름으로 쓴다.
     */
    fun describe(snapshot: TrafficSnapshot?, nowMillis: Long): DelayCause {
        if (snapshot == null) return UNKNOWN

        val events = snapshot.events.filter { it.endMillis == null || it.endMillis > nowMillis }
        val priority = listOf(
            TrafficEventKind.ACCIDENT to "사고",
            TrafficEventKind.DISASTER to "재난 상황",
            TrafficEventKind.REPAIRING to "공사",
            TrafficEventKind.ROAD_DAMAGE to "도로 파손",
        )
        val congested = congestedSections(snapshot)
        val jam = jamText(congested)
        for ((kind, label) in priority) {
            val event = events.firstOrNull { it.kind == kind } ?: continue
            val detail = event.description.takeIf { it.isNotBlank() } ?: "앞쪽 경로에 ${label}이 있어요"
            return DelayCause(label, listOfNotNull(detail, jam).joinToString(" · "))
        }
        if (congested.isNotEmpty()) return DelayCause("정체", jam!!)
        val other = events.firstOrNull { it.kind == TrafficEventKind.EVENT }
        if (other != null) return DelayCause("행사", other.description.ifBlank { "앞쪽 경로에서 행사가 있어요" })
        return UNKNOWN
    }

    private fun congestedSections(snapshot: TrafficSnapshot): List<CongestionSection> {
        val heavy = snapshot.sections.filter { it.type == Congestion.HEAVY }
        if (heavy.isNotEmpty()) return heavy
        return snapshot.sections.filter { it.type == Congestion.SLOW }
    }

    /** "대왕판교로 정체 외 3곳 · 정체 구간 합계 1.7km" 꼴. 정체 구간이 없으면 null. */
    private fun jamText(sections: List<CongestionSection>): String? {
        if (sections.isEmpty()) return null
        val worstRoad = sections.groupBy { it.roadName }
            .maxByOrNull { (_, list) -> list.sumOf { it.seconds } }!!.key
        val roads = sections.map { it.roadName }.filter { it.isNotBlank() }.distinct()
        val name = worstRoad.ifBlank { "앞쪽" }
        val others = (roads.size - if (worstRoad.isBlank()) 0 else 1).coerceAtLeast(0)
        val first = if (others > 0) "$name 정체 외 ${others}곳" else "$name 구간 정체"
        val km = sections.sumOf { it.lengthMeters } / 1000.0
        return "$first · 정체 구간 합계 ${"%.1f".format(km)}km"
    }

    /** "정체" + "로" → "정체로", "도로 파손" + "로" → "도로 파손으로" (받침 ㄹ은 '로'). */
    fun withRo(word: String): String {
        val last = word.lastOrNull() ?: return word
        val hasFinal = last in '가'..'힣' && (last - '가') % 28 != 0
        val rieul = last in '가'..'힣' && (last - '가') % 28 == 8
        return if (hasFinal && !rieul) "${word}으로" else "${word}로"
    }
}
