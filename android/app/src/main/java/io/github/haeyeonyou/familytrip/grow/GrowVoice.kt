package io.github.haeyeonyou.familytrip.grow

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.YearMonth

/*
 * 그로우 음성 명령의 순수 계산 (#185). 서버 app/voice.py의 grow_show·grow_revisit·grow_go를 받아
 * 화면에 보낼 보기(거르기·선택)와 읽어 줄 문장을 만든다. 분류는 [experiences]·[classify]가 정한 것만 쓴다.
 */

enum class GrowPeriod(val api: String, val label: String) {
    THIS_MONTH("this_month", "이번 달"),
    LAST_MONTH("last_month", "지난달"),
    ;

    fun month(today: LocalDate): YearMonth = YearMonth.from(today).let { if (this == LAST_MONTH) it.minusMonths(1) else it }

    companion object {
        fun of(api: String?): GrowPeriod? = entries.firstOrNull { it.api == api }
    }
}

/** 서버 분류 값(소문자 이름) → 젤리 분류 */
fun jellyKindOf(api: String?): JellyKind? = JellyKind.entries.firstOrNull { it.name.lowercase() == api }

/** 음성으로 연 그로우 보기. 모두 null이면 전체. [place]가 있으면 그 장소 팝업을 띄운다. */
data class GrowView(val kind: JellyKind? = null, val period: GrowPeriod? = null, val place: String? = null)

/**
 * 음성 → 그로우 화면 통로. 음성 컨트롤러가 [show]하고, 그로우 화면이 [consume]해서 거르기·선택에 반영한다.
 * 화면이 아직 없으면(다른 탭) 그로우로 옮겨 간 뒤 화면이 처음 그릴 때 받는다.
 */
object GrowVoiceBus {
    private val _view = MutableStateFlow<GrowView?>(null)
    val view: StateFlow<GrowView?> = _view.asStateFlow()

    fun show(view: GrowView) {
        _view.value = view
    }

    fun consume(): GrowView? = _view.value.also { _view.value = null }
}

/** 서버에 보내는 다녀온 곳 이름 (최근 순, 같은 곳 1개). "○○ 다시 가자"는 이 안에서만 고른다. */
fun voicePlaceNames(list: List<Experience>, limit: Int = 30): List<String> =
    list.sortedByDescending { it.visit.arrivedAt }.map { it.visit.placeName.take(60) }.filter(String::isNotBlank).distinct().take(limit)

/** [view]에 맞는 경험 (기간·분류). */
fun experiencesIn(list: List<Experience>, view: GrowView, today: LocalDate): List<Experience> {
    val month = view.period?.month(today)
    return list.filter { (view.kind == null || it.kind == view.kind) && (month == null || YearMonth.from(it.date) == month) }
}

/** 읽어 줄 장소 이름 (최근 순 [limit]곳). "서울숲, 별마당 도서관 외 2곳" */
internal fun placeList(names: List<String>, limit: Int = 3): String =
    names.take(limit).joinToString(", ") + if (names.size > limit) " 외 ${names.size - limit}곳" else ""

/**
 * grow_show의 답. 예:
 * - "지난달에는 3곳 다녀왔어요. 서울숲, 별마당 도서관, 키즈카페 점프킹."
 * - "과학·박물관 경험 5개, 공룡 몽글이에요. 탄생까지 5개 남았어요. 국립중앙박물관 등 2곳."
 * - "지난달 물놀이 다녀온 곳은 없어요."
 */
fun growShowAnswer(list: List<Experience>, view: GrowView, today: LocalDate): String {
    val mine = experiencesIn(list, view, today)
    val names = mine.sortedByDescending { it.visit.arrivedAt }.map { it.visit.placeName }.distinct()
    val kind = view.kind
    val period = view.period
    if (kind == null) {
        val whenText = period?.let { "${it.label}에는" } ?: "지금까지"
        if (names.isEmpty()) return "${period?.label ?: "아직"} 다녀온 곳이 없어요."
        return "$whenText ${names.size}곳 다녀왔어요. ${placeList(names)}."
    }
    val state = jellyStates(list, today).first { it.kind == kind }
    val growth = when (state.stage) {
        JellyStage.NONE -> "아직 비어 있는 별이에요. ${kind.label} 나들이를 하면 별이 생겨요."
        JellyStage.DROP -> "${kind.label} 경험 ${state.count}개, ${stageName(kind, state.stage)}이에요. 몽글까지 ${JellyStage.PUFF.min - state.count}개 남았어요."
        JellyStage.PUFF -> "${kind.label} 경험 ${state.count}개, ${stageName(kind, state.stage)}이에요. 탄생까지 ${JellyStage.BORN.min - state.count}개 남았어요."
        JellyStage.BORN -> "${kind.label} 경험 ${state.count}개, ${kind.typeName} 캐릭터가 태어났어요."
    }
    if (period == null) return if (names.isEmpty()) growth else "$growth ${placeList(names)}."
    if (names.isEmpty()) return "${period.label} ${kind.chip} 다녀온 곳은 없어요."
    return "${period.label} ${kind.chip} ${names.size}곳 다녀왔어요. ${placeList(names)}."
}

/** 다시 가볼 곳 중 길 안내가 되는 곳 (좌표가 있는 곳). 음성 후보는 2곳까지. */
fun voiceRevisits(list: List<Experience>, today: LocalDate, limit: Int = 2): List<Revisit> =
    revisits(list, today, limit = 10).filter { it.latitude != null && it.longitude != null }.take(limit)

/** 다녀온 곳 하나 (grow_go). 같은 이름의 가장 최근 방문으로 만든다. 없으면 null. */
fun revisitOf(list: List<Experience>, placeName: String): Revisit? {
    val xs = list.filter { it.visit.placeName == placeName }.ifEmpty { return null }
    val last = xs.maxBy { it.visit.arrivedAt }
    val located = xs.lastOrNull { it.visit.latitude != null && it.visit.longitude != null }?.visit
    return Revisit(
        placeName = placeName,
        kind = last.kind,
        count = xs.size,
        last = last.date,
        latitude = located?.latitude,
        longitude = located?.longitude,
        nursingRoom = xs.any { "nursing_room" in it.visit.amenities },
    )
}

/** 음성 후보·D01 카드에 보이는 한 줄: "3주 전 · 자연·숲 · 4번 방문" */
fun revisitVoiceNote(r: Revisit, today: LocalDate): String =
    listOf(agoText(r.last, today), r.kind.label, "${r.count}번 방문").joinToString(" · ")

/** 정차 중 "다시 가볼 곳" 답. */
fun revisitAnswer(rows: List<Revisit>, today: LocalDate): String = when (rows.size) {
    0 -> "아직 다시 가 볼 곳이 없어요. 일주일 넘게 지난 나들이가 생기면 골라 드릴게요."
    1 -> "${rows[0].placeName}, ${agoWhen(rows[0].last, today)} 갔던 곳이에요. \"1번으로 가줘\"라고 하면 안내할게요."
    else -> "다시 가 볼 곳 ${rows.size}곳이에요. 1번은 ${rows[0].placeName}, 2번은 ${rows[1].placeName}. 번호로 말씀해 주세요."
}

/** 주행 중 D01 1곳 제안 문장. */
fun revisitProposalMessage(r: Revisit, today: LocalDate): String =
    "${agoWhen(r.last, today)} 간 ${r.placeName}이에요. 다시 안내할까요?"

/** "오늘" / "3주 전에" / "10.19에" */
internal fun agoWhen(date: LocalDate, today: LocalDate): String = agoText(date, today).let { if (it == "오늘") it else "${it}에" }
