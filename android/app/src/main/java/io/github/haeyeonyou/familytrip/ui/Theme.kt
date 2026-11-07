package io.github.haeyeonyou.familytrip.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 피그마 지수 페이지 PX·컴포넌트 기준 색 (밝은 패널, 초록 강조, 짙은 남색 주 버튼).
 * 디자인 토큰이 정리되면 값만 바꾼다 (#14).
 */
object TripColors {
    val Green = Color(0xFF0E8A5F)
    val GreenSoft = Color(0xFFE3F3EC)
    val Ink = Color(0xFF1F2530)
    val InkSoft = Color(0xFF5D6675)
    val Panel = Color(0xFFF7F8FA)
    val Tile = Color(0xFFEDEFF2)
    val Line = Color(0xFFDDE1E6)
    val Amber = Color(0xFFF5A524)
    val AmberSoft = Color(0xFFFFF4E0)
    val Route = Color(0xFF4F5BF2)

    // 지도 배경 (우리 창 안의 지도 레이어)
    val MapGround = Color(0xFFECEFF2)
    val MapRoad = Color(0xFFFFFFFF)
    val MapPark = Color(0xFFD5EBD9)
    val MapWater = Color(0xFFCFE0F2)
}

private val colors = lightColorScheme(
    primary = TripColors.Green,
    onPrimary = Color.White,
    primaryContainer = TripColors.GreenSoft,
    onPrimaryContainer = Color(0xFF0A5A3E),
    secondary = TripColors.Ink,
    onSecondary = Color.White,
    tertiary = Color(0xFFB45309),
    tertiaryContainer = TripColors.AmberSoft,
    onTertiaryContainer = Color(0xFF7A3E00),
    background = TripColors.Panel,
    surface = TripColors.Panel,
    onSurface = TripColors.Ink,
    surfaceVariant = TripColors.Tile,
    onSurfaceVariant = TripColors.InkSoft,
    outline = TripColors.Line,
    outlineVariant = TripColors.Line,
)

@Composable
fun FamilyTripTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = colors, content = content)
