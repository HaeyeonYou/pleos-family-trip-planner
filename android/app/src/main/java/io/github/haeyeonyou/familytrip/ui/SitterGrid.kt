package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * 앱 프레임 안 그리드 (Figma 352:2749 등, 좌표는 프레임 기준 Figma px = 캔버스 dp).
 * 바깥 여백 24, 3열 x=24/432/840 · 열 폭 392 · 열 간격 16, 본문 y=168~862(높이 694), 아래 여백 24.
 */
object SitterGrid {
    const val FRAME_W = 1256
    const val FRAME_H = 886
    const val MARGIN = 24
    const val COLUMN_W = 392
    const val GAP = 16
    const val COLUMN_COUNT = 3

    /** 1·2열을 합친 폭 (코스 타임라인·경험 젤리). 392 * 2 + 16 */
    const val DOUBLE_W = COLUMN_W * 2 + GAP

    const val SEARCH_Y = 24
    const val SEARCH_W = 940
    const val SEARCH_H = 64
    const val CHIP_Y = 104
    const val CHIP_H = 53
    const val BODY_TOP = 168
    const val BODY_H = 694

    /** 3열째 맨 아래 바로가기 독 (840,770 392x92) */
    const val DOCK_H = 92

    /** [index]열(0부터)의 왼쪽 x */
    fun columnX(index: Int) = MARGIN + index * (COLUMN_W + GAP)
}

/**
 * 새 디자인으로 옮긴 화면의 캔버스. 1256x886 디자인 공간을 앞 창 프레임에 맞게 같은 비율로 키워 그린다
 * (`LocalDensity`를 곱하므로 안의 dp·sp는 모두 Figma px 그대로 쓴다). 창이 좁으면 들어가는 만큼 줄어든다.
 *
 * 이 캔버스를 쓰는 화면은 3열째에 [ShortcutDock]을 직접 둔다 (코스 상세 제외). 그래서 셸의 임시 독을 숨긴다.
 * 좁은 창 판정(`isNarrowWindow`)은 화면 설정 폭을 읽으므로 확대와 무관하게 실제 창 폭 기준이다.
 */
@Composable
fun SitterCanvas(modifier: Modifier = Modifier, ownsDock: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    if (ownsDock) OwnsShortcutDock()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val scale = min(
            constraints.maxWidth / (SitterGrid.FRAME_W * density.density),
            constraints.maxHeight / (SitterGrid.FRAME_H * density.density),
        )
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            Box(Modifier.align(Alignment.Center).size(SitterGrid.FRAME_W.dp, SitterGrid.FRAME_H.dp), content = content)
        }
    }
}

/** 3열 본문 (y=168부터 694 높이, 열 392). 열 안은 위에서 아래로 쌓는다. */
@Composable
fun SitterThreeColumns(
    first: @Composable ColumnScope.() -> Unit,
    second: @Composable ColumnScope.() -> Unit,
    third: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(start = SitterGrid.MARGIN.dp, top = SitterGrid.BODY_TOP.dp, end = SitterGrid.MARGIN.dp, bottom = SitterGrid.MARGIN.dp),
        horizontalArrangement = Arrangement.spacedBy(SitterGrid.GAP.dp),
    ) {
        listOf(first, second, third).forEach { slot ->
            Column(modifier = Modifier.width(SitterGrid.COLUMN_W.dp).fillMaxHeight(), content = slot)
        }
    }
}

/** 1·2열을 합친 넓은 열(800) + 3열 (코스 타임라인·경험 젤리 화면). */
@Composable
fun SitterWideAndColumn(
    wide: @Composable ColumnScope.() -> Unit,
    third: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(start = SitterGrid.MARGIN.dp, top = SitterGrid.BODY_TOP.dp, end = SitterGrid.MARGIN.dp, bottom = SitterGrid.MARGIN.dp),
        horizontalArrangement = Arrangement.spacedBy(SitterGrid.GAP.dp),
    ) {
        Column(modifier = Modifier.width(SitterGrid.DOUBLE_W.dp).fillMaxHeight(), content = wide)
        Column(modifier = Modifier.width(SitterGrid.COLUMN_W.dp).fillMaxHeight(), content = third)
    }
}

/**
 * 상단 음성 입력줄 줄 (H01·G01·C01~C12·D01 공통): 흰 알약 검색바 940x64(안: 음성 버튼 + 안내 문구 + 아이 아바타) ·
 * 그라데이션 원 64(길 안내) · 오른쪽 날씨. 안내 문구는 화면마다 다르다. 코스 상세(H02)는 이 줄 대신 제목 헤더를 쓴다.
 */
@Composable
fun SitterVoiceBar(
    hint: String,
    modifier: Modifier = Modifier,
    avatarLabel: String? = null,
    weather: (@Composable () -> Unit)? = null,
    onMic: () -> Unit = LocalShell.current?.onMic ?: {},
    onGo: (() -> Unit)? = null,
) {
    val s = SitterSurface.current
    Row(
        modifier = modifier.padding(start = SitterGrid.MARGIN.dp, top = SitterGrid.SEARCH_Y.dp).height(SitterGrid.SEARCH_H.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .width(SitterGrid.SEARCH_W.dp)
                .height(SitterGrid.SEARCH_H.dp)
                .clip(SitterShapes.Pill)
                .background(s.card)
                .border(1.5.dp, s.line, SitterShapes.Pill)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VoiceButton(onClick = onMic)
            Text(hint, style = SitterText.Body1, color = s.inkMute, modifier = Modifier.weight(1f).padding(start = 16.dp), maxLines = 1)
            avatarLabel?.let {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFFDE68A)), contentAlignment = Alignment.Center) {
                    Text(it, style = SitterText.Label, color = Color(0xFF191F28))
                }
            }
        }
        GradientCircleButton(size = 64.dp, modifier = Modifier.padding(start = 12.dp), onClick = onGo ?: {}) {
            SitterIcon(SitterIcons.Turn, Color.White)
        }
        Box(Modifier.weight(1f).padding(end = 24.dp), contentAlignment = Alignment.CenterEnd) { weather?.invoke() }
    }
}

/** 날씨 블록: 오른쪽에 아이콘 34, 온도 34 Bold, 아래에 `맑음 · 구리시` 15 Regular (Figma 1100,24 / 1060,66). */
@Composable
fun SitterWeather(temperature: String, description: String, modifier: Modifier = Modifier) {
    val s = SitterSurface.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(horizontalAlignment = Alignment.End) {
            Text(temperature, style = SitterText.Title1, color = s.ink)
            Text(description, style = SitterText.Caption, color = s.inkMute, textAlign = TextAlign.End)
        }
        WeatherIcon(weatherKindOf(description))
    }
}

/** 날씨 아이콘 34. 해는 노랑, 구름은 회색, 비는 파랑, 눈은 하늘색. */
@Composable
private fun WeatherIcon(kind: WeatherKind) {
    val s = SitterSurface.current
    val sun = Color(0xFFF59E0B)
    when (kind) {
        WeatherKind.SUN -> SitterIcon(SitterIcons.Sun, sun)
        WeatherKind.PARTLY_CLOUDY -> SitterIcon(SitterIcons.CloudSun, s.inkSoft, size = 34.dp)
        WeatherKind.CLOUD -> SitterIcon(SitterIcons.Cloud, s.inkSoft, size = 34.dp)
        WeatherKind.RAIN -> SitterIcon(SitterIcons.CloudRain, SitterPalette.Blue, size = 34.dp)
        WeatherKind.SNOW -> SitterIcon(SitterIcons.CloudSnow, SitterPalette.Cyan, size = 34.dp)
    }
}

/** 칩 줄 (y=104, 높이 53, 간격 12). 칩이 넘치면 옆으로 민다. */
@Composable
fun SitterChipRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .padding(start = SitterGrid.MARGIN.dp, top = SitterGrid.CHIP_Y.dp)
            .height(SitterGrid.CHIP_H.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** 앱 프레임만 덮는 어두운 막 (시트·드롭다운·토스트 아래). 차량 패널·상태바·독은 OS가 그리므로 건드리지 않는다. */
@Composable
fun SitterScrim(alpha: Float = 0.45f, onClick: (() -> Unit)? = null) {
    Box(
        Modifier
            .fillMaxSize()
            .background(SitterPalette.Scrim.copy(alpha = alpha))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    )
}
