package io.github.haeyeonyou.familytrip.care

import io.github.haeyeonyou.familytrip.data.Child
import io.github.haeyeonyou.familytrip.data.FeedingRoutine
import io.github.haeyeonyou.familytrip.data.ServerChild
import io.github.haeyeonyou.familytrip.data.clockLabel
import io.github.haeyeonyou.familytrip.data.durationLabel

/**
 * 아이 선택 드롭다운(C07)의 행 (순수 함수). D4: 수유 타이머만 아이별, 모드 값은 가족 공통.
 * 아이 키: 첫째(목록 맨 앞)는 null, 나머지는 서버 아이 id. 서버 id가 없는 아이(웹앱과 아직 안 맞춤)는 고를 수 없다.
 */
data class ChildOption(
    val key: String?,
    val name: String,
    val ageMonths: Int,
    val line: String,
    val selected: Boolean,
    val enabled: Boolean,
)

/** 아이 [index]의 키. */
fun childKey(children: List<Child>, index: Int): String? = if (index == 0) null else children[index].serverId

/** 고른 아이의 위치. 없으면(아이가 바뀌었으면) 첫째. */
fun selectedChildIndex(children: List<Child>, selected: String?): Int =
    if (selected == null) 0 else children.indexOfFirst { it.serverId == selected }.takeIf { it > 0 } ?: 0

/** 수유하지 않는 아이 (유아식). */
private fun noFeeding(type: String?) = type == "toddler"

fun childOptions(
    children: List<Child>,
    server: List<ServerChild>,
    selected: String?,
    routineOf: (String?) -> FeedingRoutine,
): List<ChildOption> {
    val selectedIndex = selectedChildIndex(children, selected)
    return children.mapIndexed { i, child ->
        val key = childKey(children, i)
        val info = server.firstOrNull { it.id == child.serverId } ?: server.getOrNull(i)?.takeIf { child.serverId == null && i == 0 }
        val routine = routineOf(key)
        val line = buildList {
            if (noFeeding(info?.feedingType)) {
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
            line = if (i > 0 && child.serverId == null) "웹앱과 연결되면 고를 수 있어요" else line,
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
