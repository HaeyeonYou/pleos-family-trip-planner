package io.github.haeyeonyou.familytrip.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.R

/**
 * Pretendard (SIL OFL 1.1, 번들: `res/font/pretendard_*.otf`, 라이선스 원문: `docs/licenses/Pretendard-OFL-1.1.txt`).
 * Bold·Medium·Regular 세 굵기만 쓴다. 다른 굵기(SemiBold 등)는 가장 가까운 굵기로 그려진다.
 */
val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

private fun level(size: Int, weight: FontWeight) = TextStyle(
    fontFamily = Pretendard,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * 1.25f).sp, // 줄 높이 125%
    letterSpacing = (-0.015f).em, // 자간 -1.5%
)

/**
 * 타입 위계 10단 (Figma 타입 프레임 357:1733). 크기는 Figma px = dp(sp).
 * 색은 넣지 않았다. 쓰는 곳에서 `color = SitterSurface.current.ink` 등으로 정한다
 * (글자 3단계: ink #191F28 / inkSoft #4E5968 / inkMute #8B95A1).
 */
object SitterText {
    val Display = level(56, FontWeight.Bold)
    val Number = level(44, FontWeight.Bold)
    val Title1 = level(34, FontWeight.Bold)
    val Title2 = level(26, FontWeight.Bold)
    val Title3 = level(22, FontWeight.Bold)
    val Button = level(20, FontWeight.Bold)
    val Body1 = level(20, FontWeight.Medium)
    val Body2 = level(18, FontWeight.Normal)
    val Label = level(16, FontWeight.Medium)
    val Caption = level(15, FontWeight.Normal)
}

/**
 * 기존 화면이 `Text(fontSize = …)`로 크기를 직접 정하므로, Material 기본 스타일에는 글꼴·굵기·자간만 싣고 줄 높이는 비운다
 * (줄 높이를 고정하면 큰 글자가 잘린다). 새 화면은 [SitterText]를 직접 쓰면 줄 높이 125%까지 적용된다.
 * 아무 스타일 없이 쓴 `Text`는 bodyLarge를 따르므로 Regular(Body2)로 둔다.
 */
private fun TextStyle.forMaterial() = copy(lineHeight = TextUnit.Unspecified)

val SitterTypography = Typography(
    displayLarge = SitterText.Display.forMaterial(),
    displayMedium = SitterText.Number.forMaterial(),
    displaySmall = SitterText.Title1.forMaterial(),
    headlineLarge = SitterText.Title1.forMaterial(),
    headlineMedium = SitterText.Title2.forMaterial(),
    headlineSmall = SitterText.Title3.forMaterial(),
    titleLarge = SitterText.Title3.forMaterial(),
    titleMedium = SitterText.Body1.forMaterial(),
    titleSmall = SitterText.Label.forMaterial(),
    bodyLarge = SitterText.Body2.forMaterial(),
    bodyMedium = SitterText.Body2.forMaterial(),
    bodySmall = SitterText.Caption.forMaterial(),
    labelLarge = SitterText.Button.forMaterial(),
    labelMedium = SitterText.Label.forMaterial(),
    labelSmall = SitterText.Caption.forMaterial(),
)
