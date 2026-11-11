package io.github.haeyeonyou.familytrip.care

import io.github.haeyeonyou.familytrip.data.PRESET_SLOTS
import io.github.haeyeonyou.familytrip.data.PresetState
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

// 케어 모드 적용 시점 (C08, #168). 서버 `apply_at`과 같은 값이고, 아이별 프리셋마다 저장한다.

const val APPLY_BOARDING = "boarding"
const val APPLY_DEPARTURE = "departure"
const val APPLY_MANUAL = "manual"

/** C08 적용 시점 선택지 (값, 이름). 순서는 디자인 그대로. */
val APPLY_AT_CHOICES = listOf(APPLY_BOARDING to "탑승하면 바로", APPLY_DEPARTURE to "출발 시각", APPLY_MANUAL to "직접 실행")

/** 슬롯 기본값: 예전 고정 문구("탑승 시" · "수면 모드와 함께" · "정차 시")와 같다. */
fun defaultApplyAt(slot: String): String = if (slot == "boarding") APPLY_BOARDING else APPLY_MANUAL

/** 이 모드의 적용 시점. 편집해 둔 값 → 서버 값 → 슬롯 기본값. 모르는 값은 슬롯 기본값으로 본다. */
fun applyAtOf(preset: PresetState): String =
    (preset.settings.applyAt ?: preset.applyAt)?.takeIf { v -> APPLY_AT_CHOICES.any { it.first == v } } ?: defaultApplyAt(preset.slot)

/** C08 적용 시점 칸 번호. */
fun applyAtIndex(applyAt: String): Int = APPLY_AT_CHOICES.indexOfFirst { it.first == applyAt }.coerceAtLeast(0)

/** [trigger](탑승·출발 시각)에 켤 모드들. 슬롯 순서(베이비케어 → 수면 → 수유 정차)라 공조는 뒤의 것이 이긴다 (수유 정차 > 수면 > 베이비케어). */
fun autoSlots(presets: List<PresetState>, trigger: String): List<String> =
    PRESET_SLOTS.filter { slot -> presets.firstOrNull { it.slot == slot }?.let { applyAtOf(it) == trigger } == true }

/** 코스 출발 시각("HH:MM")의 오늘 시각(ms). 비었거나 읽을 수 없으면 null. */
fun departureMillis(departureAt: String?, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
    val time = departureAt?.takeIf { it.isNotBlank() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return null
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return today.atTime(time).atZone(zone).toInstant().toEpochMilli()
}

/**
 * 출발 시각에 켤 때까지 기다릴 시간(ms). 이미 지난 출발 시각이면 null: 그 코스는 탑승 때 이미 켰거나, 지나서 고른 것이라 자동으로 켜지 않는다.
 * 차 앱이 켜져 있을 때만 켠다 (원격 공조 없음).
 */
fun departureDelay(departureAt: String?, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? =
    departureMillis(departureAt, now, zone)?.let { it - now }?.takeIf { it > 0 }

/** C08 '출발 시각'을 고르면 보이는 짧은 안내. */
fun departureHint(departureAt: String?): String =
    departureAt?.takeIf { it.isNotBlank() }?.let { "고른 코스 출발 시각 ${it}에 켜져요 · 차 앱이 켜져 있을 때만" } ?: "코스를 고르면 출발 시각에 켜져요"

/** 자동으로 켠 뒤 케어 화면 맨 위 안내. */
fun autoApplyNotice(trigger: String, slots: List<String>): String {
    val names = slots.joinToString(" · ") { modeNoticeName(it) }
    return if (trigger == APPLY_DEPARTURE) "출발 시각이라 ${names}를 켰어요" else "탑승해서 ${names}를 켰어요"
}
