package io.github.haeyeonyou.familytrip.care

import io.github.haeyeonyou.familytrip.data.ClimateSettings
import io.github.haeyeonyou.familytrip.data.PRESET_SLOTS

/**
 * 모드 편집 시트(C08)의 값 규칙 (순수 함수). 모드 3개는 서버 공조 프리셋 슬롯
 * (`boarding` 베이비케어 기본 · `nap` 낮잠·수면 · `feeding_stop` 수유 정차)과 같다. 웹앱과 같은 값을 쓴다.
 */

/** 탭 이름 (Figma C08). 순서는 [PRESET_SLOTS]. */
fun modeTabLabel(slot: String): String = when (slot) {
    "boarding" -> "베이비케어 기본"
    "nap" -> "낮잠 · 수면"
    "feeding_stop" -> "수유 정차"
    else -> slot
}

val MODE_SLOTS: List<String> = PRESET_SLOTS

const val MIN_TEMPERATURE = 16f
const val MAX_TEMPERATURE = 30f
const val TEMPERATURE_STEP = 0.5f

fun stepTemperature(current: Float, up: Boolean): Float =
    (current + if (up) TEMPERATURE_STEP else -TEMPERATURE_STEP).coerceIn(MIN_TEMPERATURE, MAX_TEMPERATURE)

/** 풍량 선택지 (라벨, 공조 단계). 카드 요약(`modeSummary`)의 미풍 1 · 약풍 2 · 중풍 3 · 강풍 4 이상과 맞춘다. */
val FAN_CHOICES: List<Pair<String, Int>> = listOf("미풍" to 1, "약" to 2, "중" to 3, "강" to 4)

/** 지금 단계가 고른 선택지 (4단 이상은 "강"). */
fun fanChoiceIndex(fanSpeed: Int): Int = FAN_CHOICES.indexOfLast { fanSpeed >= it.second }.coerceAtLeast(0)

/** 음량 빠른 선택 (Figma: 최소 10 · 낮게 30 · 보통 50, 그 밖의 값은 "직접 N%"). */
val VOLUME_CHOICES: List<Pair<String, Int>> = listOf("최소" to 10, "낮게" to 30, "보통" to 50)

const val VOLUME_STEP = 5

/** 슬라이더·± 버튼으로 바꾼 음량 (0~100, 5 단위). */
fun stepVolume(current: Int?, up: Boolean): Int = ((current ?: VOLUME_CHOICES[1].second) + if (up) VOLUME_STEP else -VOLUME_STEP).coerceIn(0, 100)

fun snapVolume(raw: Float): Int = (Math.round(raw / VOLUME_STEP) * VOLUME_STEP).coerceIn(0, 100)

/** 빠른 선택 중 지금 값과 같은 것의 위치. 없으면 null (= "직접"). */
fun volumeChoiceIndex(volume: Int?): Int? = volume?.let { v -> VOLUME_CHOICES.indexOfFirst { it.second == v }.takeIf { it >= 0 } }

/** 바뀐 모드만 (저장할 것). */
fun changedModes(original: Map<String, ClimateSettings>, drafts: Map<String, ClimateSettings>): Map<String, ClimateSettings> =
    drafts.filter { (slot, draft) -> original[slot] != draft }
