package io.github.haeyeonyou.familytrip.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.haeyeonyou.familytrip.care.CareTone
import io.github.haeyeonyou.familytrip.care.ToneState

/**
 * 피그마 지수 페이지 PX·컴포넌트 기준 색 (밝은 패널, 초록 강조, 짙은 남색 주 버튼).
 * 디자인 토큰이 정리되면 값만 바꾼다 (#14).
 */
object TripColors {
    val Green = Color(0xFF0E8A5F)
    val GreenSoft = Color(0xFFE3F3EC)

    // 케어 톤(수면·기저귀·수유·달래기)에 따라 바뀌는 색. 읽는 곳은 자동으로 다시 그려진다.
    val Ink get() = ToneState.current.ink
    val InkSoft get() = ToneState.current.inkSoft
    val Panel get() = ToneState.current.panel
    val Tile get() = ToneState.current.tile
    val Line get() = ToneState.current.line
    /** 카드 배경 (기본 흰색) */
    val Card get() = ToneState.current.card
    val Amber = Color(0xFFF5A524)
    val AmberSoft = Color(0xFFFFF4E0)
    val Route = Color(0xFF4F5BF2)

    // 경험 우주 (HF04): 별 배경 패널과 분야별 노드 색
    val Universe = Color(0xFF0B0F1D)
    val UniverseLine = Color(0xFF3A4160)
    val FieldShop = Color(0xFF7C8CFF)
    val FieldExhibit = Color(0xFFB382FF)
    val FieldExperience = Color(0xFFFFA94D)
    val FieldNature = Color(0xFF3DDC97)

    // 지도 배경 (우리 창 안의 지도 레이어)
    val MapGround = Color(0xFFECEFF2)
    val MapRoad = Color(0xFFFFFFFF)
    val MapPark = Color(0xFFD5EBD9)
    val MapWater = Color(0xFFCFE0F2)
}

/** 톤에 맞는 색 구성. 수면은 어두운 구성, 나머지는 밝은 구성에 패널 색만 바꾼다. */
private fun colorsFor(tone: CareTone) = if (tone.dark) {
    darkColorScheme(
        primary = tone.accent,
        onPrimary = Color(0xFF0B1020),
        primaryContainer = Color(0xFF26324F),
        onPrimaryContainer = tone.ink,
        secondary = tone.ink,
        onSecondary = tone.panel,
        tertiary = Color(0xFFF0B45A),
        tertiaryContainer = Color(0xFF3A2E16),
        onTertiaryContainer = Color(0xFFFFE2A8),
        background = tone.panel,
        surface = tone.panel,
        onBackground = tone.ink,
        onSurface = tone.ink,
        surfaceVariant = tone.tile,
        onSurfaceVariant = tone.inkSoft,
        outline = tone.line,
        outlineVariant = tone.line,
    )
} else {
    lightColorScheme(
        primary = TripColors.Green,
        onPrimary = Color.White,
        primaryContainer = TripColors.GreenSoft,
        onPrimaryContainer = Color(0xFF0A5A3E),
        secondary = tone.ink,
        onSecondary = Color.White,
        tertiary = Color(0xFFB45309),
        tertiaryContainer = TripColors.AmberSoft,
        onTertiaryContainer = Color(0xFF7A3E00),
        background = tone.panel,
        surface = tone.panel,
        onBackground = tone.ink,
        onSurface = tone.ink,
        surfaceVariant = tone.tile,
        onSurfaceVariant = tone.inkSoft,
        outline = tone.line,
        outlineVariant = tone.line,
    )
}

@Composable
fun FamilyTripTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = colorsFor(ToneState.current), content = content)
