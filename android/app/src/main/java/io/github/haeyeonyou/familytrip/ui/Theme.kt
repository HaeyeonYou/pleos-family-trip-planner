package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.haeyeonyou.familytrip.care.CareTone
import io.github.haeyeonyou.familytrip.care.ToneState

/**
 * 11/10 확정 디자인(Figma 351:2611 "Pleos 공통 UI + 지수ver")의 시터노트 팔레트 (프레임 358:5057, 7색 + 그라데이션).
 * 값은 Figma에서 직접 읽은 것이며 팔레트 프레임의 라벨과 일치한다. 화면 파일에 hex를 직접 쓰지 말고 이 값이나 [SitterSurface]를 쓴다.
 */
object SitterPalette {
    val Primary = Color(0xFF4D55F0)
    val Blue = Color(0xFF1F80FF)
    val Violet = Color(0xFF8A1EE6)
    val Soft = Color(0xFFEEEFFE)
    val Pink = Color(0xFFE9A6F7)
    val Cyan = Color(0xFF22C3EE)
    val Periwinkle = Color(0xFF7E9BF5)

    /** Cue Gradient: #1F80FF 0% > #5B4BF5 55% > #8A1EE6 100%. 왼쪽 위(파랑) → 오른쪽 아래(보라) 대각선. */
    val GradientStops: Array<Pair<Float, Color>> = arrayOf(
        0f to Blue,
        0.55f to Color(0xFF5B4BF5),
        1f to Violet,
    )
    val Gradient: Brush get() = Brush.linearGradient(colorStops = GradientStops)

    /** 같은 그라데이션을 가로로 (하단 활성 표시줄 등, 0도). */
    val GradientHorizontal: Brush get() = Brush.horizontalGradient(colorStops = GradientStops)

    /** 주 버튼·음성 버튼 아래 그림자 (#5B4BF5 35%, y4 blur12). */
    val GradientShadow = Color(0xFF5B4BF5).copy(alpha = 0.35f)

    // 경고 (C05 임박 / C06 지남)
    val WarnFill = Color(0xFFFFF3E0)
    val Warn = Color(0xFFE07A00)
    val DangerFill = Color(0xFFFDECEC)
    val Danger = Color(0xFFE5484D)

    // 하단 구분선·스크림
    val Scrim = Color(0xFF191F28)
}

/**
 * 한 화면 팔레트(Light 또는 Dark)에서 역할별 색. 값 출처는 `docs/design/pleos-1110/01-inventory-and-tokens.md` 4절.
 * Dark의 [inkSoft]·[tile]은 Figma에서 값이 직접 확인되지 않아 같은 화면의 인접 값에서 고른 추정치다 (PR 본문에 적음).
 */
data class SitterSurface(
    val dark: Boolean,
    /** 앱 프레임 바탕 (Light #EEF1F4 / Dark #121418) */
    val app: Color,
    /** 카드 (Light #FFFFFF / Dark #1E2228) */
    val card: Color,
    /** 타일·비활성 칩·보조 버튼 (Light #F2F4F6) */
    val tile: Color,
    val line: Color,
    /** 글자 3단계: 진함 / 보조 / 약함 */
    val ink: Color,
    val inkSoft: Color,
    val inkMute: Color,
    val primary: Color,
    /** 선택 상태·정차 모드 카드 바탕 (Light #EEEFFE / Dark 수면 카드 #262B45) */
    val primarySoft: Color,
    val onPrimary: Color,
    /** OS가 그리는 앱 노출 영역 바탕 (Light #F2F4F6 / Dark #0B0D10). 앱 프레임 바깥 여백에 쓴다. */
    val shellArea: Color,
) {
    companion object {
        val Light = SitterSurface(
            dark = false,
            app = Color(0xFFEEF1F4),
            card = Color.White,
            tile = Color(0xFFF2F4F6),
            line = Color(0xFFE5E8EB),
            ink = Color(0xFF191F28),
            inkSoft = Color(0xFF4E5968),
            inkMute = Color(0xFF8B95A1),
            primary = SitterPalette.Primary,
            primarySoft = SitterPalette.Soft,
            onPrimary = Color.White,
            shellArea = Color(0xFFF2F4F6),
        )

        val Dark = SitterSurface(
            dark = true,
            app = Color(0xFF121418),
            card = Color(0xFF1E2228),
            tile = Color(0xFF2A2F37),
            line = Color(0xFF2C313A),
            ink = Color(0xFFF2F4F6),
            inkSoft = Color(0xFFB0B8C1),
            inkMute = Color(0xFF8B95A1),
            primary = Color(0xFF8C9BFF),
            primarySoft = Color(0xFF262B45),
            onPrimary = Color(0xFF101216),
            shellArea = Color(0xFF0B0D10),
        )

        /**
         * 지금 화면에 입힐 팔레트. 수면 모드(`CareTone.SLEEP`)는 Dark, 기본(`NONE`)은 Light.
         * 음성 케어 상황 톤(기저귀·수유·달래기)은 11/10 디자인에 없어 옛 톤 색을 그대로 쓴다 (케어 세션이 정리할 때까지 호환용).
         */
        fun of(tone: CareTone): SitterSurface = when (tone) {
            CareTone.NONE -> Light
            CareTone.SLEEP -> Dark
            else -> Light.copy(
                app = tone.panel,
                card = tone.card,
                tile = tone.tile,
                line = tone.line,
                ink = tone.ink,
                inkSoft = tone.inkSoft,
                inkMute = tone.inkSoft,
                primarySoft = tone.tile,
            )
        }

        /** 지금 팔레트. Compose 상태를 읽으므로 읽은 화면은 톤이 바뀌면 다시 그려진다. */
        val current: SitterSurface get() = of(ToneState.current)
    }
}

/** 둥근 모서리 (Figma 반지름 집계: 카드 24 · 행 20 · 코스 항목 18 · 태그 10 · 시트·앱 프레임 32 · 알약 999). */
object SitterShapes {
    val Frame = RoundedCornerShape(32.dp)
    val Sheet = RoundedCornerShape(32.dp)
    val Card = RoundedCornerShape(24.dp)
    val Row = RoundedCornerShape(20.dp)
    val Item = RoundedCornerShape(18.dp)
    val Tag = RoundedCornerShape(10.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

/**
 * 옛 이름 호환 (#14). 값은 11/10 팔레트로 바뀌었고 화면 파일은 그대로 읽는다.
 * 새 코드는 [SitterPalette]·[SitterSurface]를 쓴다. 이 별칭은 모든 화면이 옮겨간 뒤 지운다.
 */
object TripColors {
    /** 옛 초록 강조 → 인디고 Primary. 다크(수면)에서는 밝은 인디고. */
    val Green get() = SitterSurface.current.primary
    val GreenSoft get() = SitterSurface.current.primarySoft

    // 케어 톤(수면·기저귀·수유·달래기)에 따라 바뀌는 색. 읽는 곳은 자동으로 다시 그려진다.
    val Ink get() = SitterSurface.current.ink
    val InkSoft get() = SitterSurface.current.inkSoft
    val Panel get() = SitterSurface.current.app
    val Tile get() = SitterSurface.current.tile
    val Line get() = SitterSurface.current.line
    /** 카드 배경 (기본 흰색) */
    val Card get() = SitterSurface.current.card
    val Amber = Color(0xFFF5A524)
    val AmberSoft = Color(0xFFFFF4E0)
    val Route = SitterPalette.Primary

    // 경험 우주 (HF04): 별 배경 패널과 분야별 노드 색 (그로우 세션이 정리)
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

private fun colorsFor(s: SitterSurface) = if (s.dark) {
    darkColorScheme(
        primary = s.primary,
        onPrimary = s.onPrimary,
        primaryContainer = s.primarySoft,
        onPrimaryContainer = s.ink,
        secondary = s.ink,
        onSecondary = s.app,
        tertiary = Color(0xFFF0B45A),
        tertiaryContainer = Color(0xFF3A2E16),
        onTertiaryContainer = Color(0xFFFFE2A8),
        background = s.app,
        surface = s.app,
        onBackground = s.ink,
        onSurface = s.ink,
        surfaceVariant = s.tile,
        onSurfaceVariant = s.inkSoft,
        outline = s.line,
        outlineVariant = s.line,
    )
} else {
    lightColorScheme(
        primary = s.primary,
        onPrimary = s.onPrimary,
        primaryContainer = s.primarySoft,
        onPrimaryContainer = SitterPalette.Primary,
        secondary = s.ink,
        onSecondary = Color.White,
        tertiary = SitterPalette.Warn,
        tertiaryContainer = SitterPalette.WarnFill,
        onTertiaryContainer = Color(0xFF7A3E00),
        background = s.app,
        surface = s.app,
        onBackground = s.ink,
        onSurface = s.ink,
        surfaceVariant = s.tile,
        onSurfaceVariant = s.inkSoft,
        outline = s.line,
        outlineVariant = s.line,
    )
}

@Composable
fun FamilyTripTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = colorsFor(SitterSurface.current), typography = SitterTypography, content = content)
