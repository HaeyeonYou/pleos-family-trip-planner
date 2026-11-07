package io.github.haeyeonyou.familytrip.care

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * 케어 상황별 화면 톤 (피그마 VX00: 수면=다크, 기저귀=레드 틴트, 수유=앰버, 달래기=블루).
 * 톤은 앱 패널 안의 색만 바꾼다. 지도·회전 안내·주행 정보는 다른 앱이라 영향이 없다.
 */
enum class CareTone(
    val panel: Color,
    val card: Color,
    val tile: Color,
    val ink: Color,
    val inkSoft: Color,
    val line: Color,
    val dark: Boolean,
    /** 톤을 대표하는 강조색 (선택된 카드 테두리·번호) */
    val accent: Color,
) {
    NONE(Color(0xFFF7F8FA), Color.White, Color(0xFFEDEFF2), Color(0xFF1F2530), Color(0xFF5D6675), Color(0xFFDDE1E6), false, Color(0xFF0E8A5F)),
    SLEEP(Color(0xFF12161D), Color(0xFF1D232D), Color(0xFF28303C), Color(0xFFE8ECF2), Color(0xFF9AA4B2), Color(0xFF323B48), true, Color(0xFF8FA8FF)),
    DIAPER(Color(0xFFFDE8E9), Color(0xFFFFF4F4), Color(0xFFF8D6D8), Color(0xFF2A1A1C), Color(0xFF7A5558), Color(0xFFF0B8BC), false, Color(0xFFE5484D)),
    FEED(Color(0xFFFFF1D6), Color(0xFFFFF9EB), Color(0xFFFBE2B0), Color(0xFF2B2110), Color(0xFF7A6232), Color(0xFFF0CF88), false, Color(0xFFD97706)),
    SOOTHE(Color(0xFFE6EBFF), Color(0xFFF3F5FF), Color(0xFFD3DCFF), Color(0xFF151B33), Color(0xFF4F5A85), Color(0xFFB4C2FF), false, Color(0xFF3050F0)),
}

/**
 * 지금 화면에 입힐 톤. 음성 상황이 있으면 그 톤, 없고 수면 모드가 켜져 있으면 다크, 아니면 기본.
 * 색을 읽는 화면이 Compose 상태를 통해 바로 다시 그려진다.
 */
object ToneState {
    var voice: CareTone? by mutableStateOf(null)
    var sleep: Boolean by mutableStateOf(false)
    val current: CareTone get() = voice ?: if (sleep) CareTone.SLEEP else CareTone.NONE
}
