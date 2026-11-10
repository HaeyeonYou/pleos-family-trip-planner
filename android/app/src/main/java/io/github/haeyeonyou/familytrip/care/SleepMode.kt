package io.github.haeyeonyou.familytrip.care

import io.github.haeyeonyou.familytrip.data.FeedingRoutine

/**
 * 낮잠·수면 모드(C04)의 규칙 (순수 함수). Q09 추천안:
 * - 안내 음성 크기는 낮잠 프리셋의 음량(웹에서 바꾼 값)을 쓰고, 없으면 10%.
 * - 다크는 앱 화면만 (화면 밝기·시스템 야간 테마는 SDK로 바꿀 수 없다).
 * - 켠 시각을 저장해 "잠든 지 N분"을 보인다.
 */

/** 낮잠 프리셋 음량이 없을 때의 안내 음성 크기 (Figma C04 "안내 음성 10%"). */
const val SLEEP_DEFAULT_VOICE_PERCENT = 10

/** 수면 모드의 안내 음성 크기(0~1). [presetVolume]은 낮잠 프리셋의 미디어 음량(%). */
fun sleepVoiceVolume(presetVolume: Int?): Float = ((presetVolume ?: SLEEP_DEFAULT_VOICE_PERCENT).coerceIn(0, 100)) / 100f

fun sleepVoicePercent(presetVolume: Int?): Int = (presetVolume ?: SLEEP_DEFAULT_VOICE_PERCENT).coerceIn(0, 100)

/** "잠든 지 42분" · "잠든 지 1시간 5분". 시작 시각을 모르면 null. */
fun sleepingLabel(startedAt: Long?, now: Long): String? {
    startedAt ?: return null
    val minutes = Math.floorDiv(now - startedAt, FeedingRoutine.MS_PER_MINUTE).coerceAtLeast(0)
    return when {
        minutes < 60 -> "잠든 지 ${minutes}분"
        minutes % 60 == 0L -> "잠든 지 ${minutes / 60}시간"
        else -> "잠든 지 ${minutes / 60}시간 ${minutes % 60}분"
    }
}
