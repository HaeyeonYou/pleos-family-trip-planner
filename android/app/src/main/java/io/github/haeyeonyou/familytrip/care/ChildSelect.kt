package io.github.haeyeonyou.familytrip.care

import io.github.haeyeonyou.familytrip.data.Child
import io.github.haeyeonyou.familytrip.data.FeedingRoutine
import io.github.haeyeonyou.familytrip.data.ServerChild
import io.github.haeyeonyou.familytrip.data.clockLabel
import io.github.haeyeonyou.familytrip.data.durationLabel

/**
 * 아이 선택 드롭다운(C07)의 행 (순수 함수). 수유 타이머와 케어 모드 값(공조 프리셋)이 아이별이다 (QO5, #192 — D4 폐기).
 * 아이 키: 첫째(목록 맨 앞)는 null, 나머지는 서버 아이 id. 서버 id가 없는 아이(웹앱과 아직 안 맞춤)는 고를 수 없다.
 */
data class ChildOption(
    val key: String?,
    val name: String,
    val ageMonths: Int,
    /** 이름 옆 월령 배지 ([ageStageLabel]). */
    val stage: String,
    val line: String,
    /** 그 아이의 베이비케어 모드 값 한 줄 ("24°C · 약풍 · 음량 낮게"). 모르면 null. */
    val mode: String? = null,
    val selected: Boolean,
    val enabled: Boolean,
)

/** 아이 [index]의 키. */
fun childKey(children: List<Child>, index: Int): String? = if (index == 0) null else children[index].serverId

/** 고른 아이의 위치. 없으면(아이가 바뀌었으면) 첫째. */
fun selectedChildIndex(children: List<Child>, selected: String?): Int =
    if (selected == null) 0 else children.indexOfFirst { it.serverId == selected }.takeIf { it > 0 } ?: 0

/** C07 이름 옆 월령 배지: 12개월 이하([INFANT_MAX_MONTHS], 수유 표시 QD1과 같은 경계)는 "영아기", 넘으면 "유아기". */
fun ageStageLabel(child: Child, today: java.time.LocalDate = java.time.LocalDate.now()): String =
    if (isInfant(child, today)) STAGE_INFANT else STAGE_TODDLER

const val STAGE_INFANT = "영아기"
const val STAGE_TODDLER = "유아기"

/** 수유하지 않는 아이 (유아식). */
private fun noFeeding(type: String?) = type == "toddler"

fun childOptions(
    children: List<Child>,
    server: List<ServerChild>,
    selected: String?,
    modeOf: (Child) -> String? = { null },
    today: java.time.LocalDate = java.time.LocalDate.now(),
    routineOf: (String?) -> FeedingRoutine,
): List<ChildOption> {
    val selectedIndex = selectedChildIndex(children, selected)
    return children.mapIndexed { i, child ->
        val key = childKey(children, i)
        val info = server.firstOrNull { it.id == child.serverId } ?: server.getOrNull(i)?.takeIf { child.serverId == null && i == 0 }
        val routine = routineOf(key)
        val line = buildList {
            // 유아기(12개월 초과)는 이름 옆 배지로 보이고, 줄에는 수유 내용이 없다 (#217)
            if (!isInfant(child, today)) {
                Unit
            } else if (noFeeding(info?.feedingType)) {
                add("수유 없음")
            } else {
                add("${durationLabel(routine.intervalMinutes.toLong())} 간격")
                add(routine.nextAt?.let { "다음 수유 ${clockLabel(it)}" } ?: "수유 기록 없음")
            }
            info?.napCount?.let { add("낮잠 ${it}회") }
        }.joinToString(" · ")
        ChildOption(
            key = key,
            name = child.name.ifBlank { "아이 ${i + 1}" },
            ageMonths = child.ageMonths(),
            stage = ageStageLabel(child, today),
            line = if (i > 0 && child.serverId == null) "웹앱과 연결되면 고를 수 있어요" else line,
            mode = modeOf(child),
            selected = i == selectedIndex,
            enabled = i == 0 || child.serverId != null,
        )
    }
}

/** 칩 글자 "서아 · 14개월". 아이가 없으면 "아이". */
fun childChipLabel(children: List<Child>, selected: String?): String {
    if (children.isEmpty()) return "아이"
    val i = selectedChildIndex(children, selected)
    val c = children[i]
    return "${c.name.ifBlank { "아이 ${i + 1}" }} · ${c.ageMonths()}개월"
}

/**
 * 아이를 바꿀 때 새 아이 값으로 다시 넣을 모드 (공조를 쓰고 있는 것 하나). 수유 정차 > 수면 > 베이비케어 순서로,
 * 나중에 켠 것이 공조를 차지하고 있기 때문이다. 공조를 쓰는 모드가 없으면 null.
 */
fun activeModeSlot(babyCare: Boolean, sleepClimate: Boolean, feedingStopClimate: Boolean): String? = when {
    feedingStopClimate -> "feeding_stop"
    sleepClimate -> "nap"
    babyCare -> "boarding"
    else -> null
}

/** 안내 문구의 모드 이름 ("수유 정차 모드를 …"). */
fun modeNoticeName(slot: String): String = when (slot) {
    "feeding_stop" -> "수유 정차 모드"
    "nap" -> "수면 모드"
    else -> "베이비케어 모드"
}
