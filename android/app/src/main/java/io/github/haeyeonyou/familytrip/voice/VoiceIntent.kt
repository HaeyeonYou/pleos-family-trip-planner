package io.github.haeyeonyou.familytrip.voice

import io.github.haeyeonyou.familytrip.care.CareTone

/** 음성으로 알리는 케어 상황 네 가지 (피그마 VX00). [tone]은 그때 앱 패널에 입힐 톤. */
enum class CareSituation(val label: String, val tone: CareTone) {
    SLEEP("수면", CareTone.SLEEP),
    DIAPER("기저귀", CareTone.DIAPER),
    FEED("수유", CareTone.FEED),
    SOOTHE("달래기", CareTone.SOOTHE),
}

/** 한 마디를 해석한 결과. */
sealed interface Utterance {
    data class Situation(val situation: CareSituation) : Utterance

    /** "1번으로 가줘" — [number]은 1부터 */
    data class Choose(val number: Int) : Utterance

    /** "아니야" 같은 취소 */
    data object Cancel : Utterance

    /** "그대로 가기" — 아무것도 바꾸지 않고 닫기 */
    data object KeepGoing : Utterance

    data object Unknown : Utterance
}

private val CHOOSE = Regex("(1|2|3|일|이|삼|하나|둘|셋|첫|두)\\s?(번째|번)")
private val CANCEL = listOf("아니야", "아니에요", "아냐", "취소", "그만", "됐어", "틀렸어")
private val KEEP_GOING = listOf("그대로", "그냥가", "그냥 가", "괜찮아", "닫아")

// 겹치는 말은 급한 쪽이 먼저: "애 쌌다 배고프다"면 기저귀를 먼저 챙긴다.
private val KEYWORDS: List<Pair<CareSituation, List<String>>> = listOf(
    CareSituation.DIAPER to listOf("쌌", "똥", "응가", "기저귀", "오줌", "쉬했", "젖었"),
    CareSituation.FEED to listOf("배고", "밥", "수유", "분유", "젖", "먹고싶", "먹을"),
    CareSituation.SOOTHE to listOf("운다", "울어", "울고", "울음", "보챈", "보채", "칭얼", "달래"),
    CareSituation.SLEEP to listOf("잔다", "자요", "자고", "자는", "재웠", "재운", "잠들", "잠든", "잠이"),
)

/**
 * 인식한 문장을 간단한 키워드 규칙으로 해석한다 (AI 없이, 같은 문장은 늘 같은 결과).
 * 띄어쓰기는 무시하고 비교한다.
 */
fun parseUtterance(text: String): Utterance {
    val raw = text.trim()
    if (raw.isEmpty()) return Utterance.Unknown
    val compact = raw.replace(" ", "")
    if (CANCEL.any { compact.contains(it.replace(" ", "")) }) return Utterance.Cancel
    CHOOSE.find(raw.replace("  ", " "))?.let { m ->
        val n = when (m.groupValues[1]) {
            "1", "일", "하나", "첫" -> 1
            "2", "이", "둘", "두" -> 2
            else -> 3
        }
        return Utterance.Choose(n)
    }
    if (KEEP_GOING.any { compact.contains(it.replace(" ", "")) }) return Utterance.KeepGoing
    for ((situation, words) in KEYWORDS) {
        if (words.any { compact.contains(it) }) return Utterance.Situation(situation)
    }
    return Utterance.Unknown
}

/** 화면에 보여 줄 문장 버튼 (운전 중에도 누를 수 있다). */
val VOICE_SAMPLES = listOf("애 잔다", "애 쌌다", "애 배고프다", "애 운다")
