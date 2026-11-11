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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
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

    /**
     * 좁은 창 기준 (분할 화면). 캔버스를 전체 화면과 같은 배율(창 높이 기준)로 그렸을 때 폭이 이보다 작으면
     * 3열을 통째로 줄이지 않고 1~2열로 쌓아 세로로 스크롤한다. 전체 화면은 폭이 1256이라 해당하지 않는다.
     */
    const val STACK_BELOW_W = 1100

    /** 1열만 쌓을 때 필요한 최소 폭 (열 하나 + 좌우 여백) */
    const val ONE_COLUMN_MIN_W = COLUMN_W + MARGIN * 2

    /** 2열이 들어가는 최소 폭 (392 * 2 + 16 + 좌우 여백) */
    const val TWO_COLUMN_MIN_W = DOUBLE_W + MARGIN * 2

    /** 캔버스 폭(Figma px) → 열 수. 3이면 원래 3열 캔버스, 1·2면 쌓기. */
    fun columnsFor(canvasWidth: Float): Int = when {
        canvasWidth >= STACK_BELOW_W -> COLUMN_COUNT
        canvasWidth >= TWO_COLUMN_MIN_W -> 2
        else -> 1
    }

    /**
     * 캔버스 영역(dp) → 배율·캔버스 크기(Figma px)·열 수. 넓으면 지금처럼 1256x886 전체가 들어가는 배율.
     * 좁으면 전체 화면과 같은 높이 기준 배율을 그대로 써서 글자·터치 크기를 유지하고(열 하나가 안 들어갈 만큼 좁을 때만 더 줄인다),
     * 폭은 창에 맞춘다.
     */
    fun layoutFor(widthDp: Float, heightDp: Float): CanvasLayout {
        val heightScale = heightDp / FRAME_H
        if (columnsFor(widthDp / heightScale) == COLUMN_COUNT) {
            return CanvasLayout(min(widthDp / FRAME_W, heightScale), FRAME_W.toFloat(), FRAME_H.toFloat(), COLUMN_COUNT)
        }
        val scale = min(heightScale, widthDp / ONE_COLUMN_MIN_W)
        val w = widthDp / scale
        return CanvasLayout(scale, w, heightDp / scale, columnsFor(w))
    }
}

/** [SitterGrid.layoutFor] 결과. [width]·[height]는 캔버스 크기(Figma px = 캔버스 dp). */
data class CanvasLayout(val scale: Float, val width: Float, val height: Float, val columns: Int) {
    val stacked: Boolean get() = columns < SitterGrid.COLUMN_COUNT
}

/** 지금 캔버스의 열 수 (3 = 원래 3열, 1·2 = 좁은 창에서 쌓기). [SitterCanvas] 밖에서는 3. */
val LocalSitterColumns = staticCompositionLocalOf { SitterGrid.COLUMN_COUNT }

/** 좁은 창이라 열을 쌓고 있는지. 화면별로 폭이 고정된 요소(모달 등)를 창에 맞출 때 쓴다. */
@Composable
fun sitterStacked(): Boolean = LocalSitterColumns.current < SitterGrid.COLUMN_COUNT

/**
 * 새 디자인으로 옮긴 화면의 캔버스. 1256x886 디자인 공간을 앞 창 프레임에 맞게 같은 비율로 키워 그린다
 * (`LocalDensity`를 곱하므로 안의 dp·sp는 모두 Figma px 그대로 쓴다). 창이 좁으면 들어가는 만큼 줄어든다.
 *
 * 이 캔버스를 쓰는 화면은 3열째에 [ShortcutDock]을 직접 둔다 (코스 상세 제외). 그래서 셸의 임시 독을 숨긴다.
 * 좁은 창 판정(`isNarrowWindow`)은 화면 설정 폭을 읽으므로 확대와 무관하게 실제 창 폭 기준이다.
 *
 * 분할 화면처럼 좁으면([SitterGrid.layoutFor]) 캔버스 폭을 창에 맞추고 배율은 전체 화면과 같게 둔다.
 * 그때 [SitterThreeColumns]·[SitterWideAndColumn]·[SitterVoiceBar]가 [LocalSitterColumns]를 보고 열을 쌓는다.
 * 좁은 배치를 아직 맞추지 않은 화면(온보딩 등)은 [stackOnNarrow] = false로 예전처럼 통째로 줄인다.
 */
@Composable
fun SitterCanvas(
    modifier: Modifier = Modifier,
    ownsDock: Boolean = true,
    stackOnNarrow: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    if (ownsDock) OwnsShortcutDock()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val layout = SitterGrid.layoutFor(maxWidth.value, maxHeight.value).let {
            if (stackOnNarrow || !it.stacked) it
            else CanvasLayout(min(maxWidth.value / SitterGrid.FRAME_W, maxHeight.value / SitterGrid.FRAME_H), SitterGrid.FRAME_W.toFloat(), SitterGrid.FRAME_H.toFloat(), SitterGrid.COLUMN_COUNT)
        }
        CompositionLocalProvider(
            LocalDensity provides Density(density.density * layout.scale, density.fontScale),
            LocalSitterColumns provides layout.columns,
        ) {
            Box(Modifier.align(Alignment.Center).size(layout.width.dp, layout.height.dp), content = content)
        }
    }
}

/**
 * 3열 본문 (y=168부터 694 높이, 열 392). 열 안은 위에서 아래로 쌓는다.
 * 좁은 창에서는 Figma 열 순서대로 1~2열로 쌓고 본문만 세로로 스크롤한다 ([StackedBody]).
 */
@Composable
fun SitterThreeColumns(
    first: @Composable ColumnScope.() -> Unit,
    second: @Composable ColumnScope.() -> Unit,
    third: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val columns = LocalSitterColumns.current
    if (columns < SitterGrid.COLUMN_COUNT) {
        StackedBody(columns, listOf(StackSlot(1, first), StackSlot(1, second), StackSlot(1, third)), modifier)
        return
    }
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

/**
 * 1·2열을 합친 넓은 열(800) + 3열 (코스 타임라인·경험 젤리·코스 진행 화면). [top]은 본문 시작 y.
 * 좁은 창에서는 넓은 열 → 3열 순서로 쌓는다. [focusThird]가 참이 되면(대체 후보 패널을 열었을 때 등) 3열까지 스크롤한다.
 * [thirdWrapsWhenStacked]: 3열이 빈 공간 + 아래 버튼뿐이면, 쌓을 때 본문 높이 대신 내용 높이로 줄여 버튼이 바로 이어지게 한다.
 */
@Composable
fun SitterWideAndColumn(
    wide: @Composable ColumnScope.() -> Unit,
    third: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    top: Int = SitterGrid.BODY_TOP,
    focusThird: Boolean = false,
    thirdWrapsWhenStacked: Boolean = false,
) {
    val columns = LocalSitterColumns.current
    if (columns < SitterGrid.COLUMN_COUNT) {
        StackedBody(columns, listOf(StackSlot(2, wide), StackSlot(1, third, fixedHeight = !thirdWrapsWhenStacked)), modifier, top, focusSlot = if (focusThird) 1 else null)
        return
    }
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(start = SitterGrid.MARGIN.dp, top = top.dp, end = SitterGrid.MARGIN.dp, bottom = SitterGrid.MARGIN.dp),
        horizontalArrangement = Arrangement.spacedBy(SitterGrid.GAP.dp),
    ) {
        Column(modifier = Modifier.width(SitterGrid.DOUBLE_W.dp).fillMaxHeight(), content = wide)
        Column(modifier = Modifier.width(SitterGrid.COLUMN_W.dp).fillMaxHeight(), content = third)
    }
}

/**
 * 쌓기 배치의 한 칸: [span]은 전체 화면에서 차지하던 열 수 (넓은 열 = 2).
 * [fixedHeight]가 거짓이면 내용 높이만 쓴다 (안의 weight는 0이 되고, 안쪽 세로 스크롤은 둘 수 없다).
 */
class StackSlot(val span: Int, val content: @Composable ColumnScope.() -> Unit, val fixedHeight: Boolean = true)

/** 칸들을 [columns]열 줄로 나눈다 (순서 유지, 줄을 넘는 칸은 다음 줄로). 결과는 줄마다 칸 번호와 차지하는 열 수. */
internal fun stackRows(spans: List<Int>, columns: Int): List<List<Pair<Int, Int>>> {
    val rows = mutableListOf<MutableList<Pair<Int, Int>>>()
    var used = columns
    spans.forEachIndexed { i, span ->
        val w = span.coerceIn(1, columns)
        if (used + w > columns) {
            rows += mutableListOf<Pair<Int, Int>>()
            used = 0
        }
        rows.last() += i to w
        used += w
    }
    return rows
}

/**
 * 좁은 창 본문: 칸마다 전체 화면 때와 같은 높이(본문 높이)를 주고 [columns]열로 쌓아 세로 스크롤한다.
 * 칸 안의 weight·안쪽 스크롤이 전체 화면과 똑같이 동작하도록 높이를 고정한다. 폭은 창에 맞춰 늘거나 준다.
 */
@Composable
private fun StackedBody(
    columns: Int,
    slots: List<StackSlot>,
    modifier: Modifier = Modifier,
    top: Int = SitterGrid.BODY_TOP,
    focusSlot: Int? = null,
) {
    val slotH = SitterGrid.FRAME_H - top - SitterGrid.MARGIN
    val rows = stackRows(slots.map { it.span }, columns)
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(focusSlot) {
        val row = focusSlot?.let { f -> rows.indexOfFirst { r -> r.any { it.first == f } } } ?: return@LaunchedEffect
        scroll.animateScrollTo(with(density) { (row * (slotH + SitterGrid.GAP)).dp.roundToPx() })
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = top.dp)
            .verticalScroll(scroll)
            .padding(start = SitterGrid.MARGIN.dp, end = SitterGrid.MARGIN.dp, bottom = SitterGrid.MARGIN.dp),
        verticalArrangement = Arrangement.spacedBy(SitterGrid.GAP.dp),
    ) {
        rows.forEach { row ->
            val fixed = row.any { (index, _) -> slots[index].fixedHeight }
            Row(Modifier.fillMaxWidth().then(if (fixed) Modifier.height(slotH.dp) else Modifier), horizontalArrangement = Arrangement.spacedBy(SitterGrid.GAP.dp)) {
                row.forEach { (index, span) ->
                    Column(Modifier.weight(span.toFloat()).then(if (fixed) Modifier.fillMaxHeight() else Modifier), content = slots[index].content)
                }
                val rest = columns - row.sumOf { it.second }
                if (rest > 0) Spacer(Modifier.weight(rest.toFloat()))
            }
        }
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
    // 좁은 창: 검색바가 남는 폭을 쓰고 날씨는 오른쪽에 붙는다
    val stacked = sitterStacked()
    Row(
        modifier = modifier
            .then(if (stacked) Modifier.fillMaxWidth() else Modifier)
            .padding(start = SitterGrid.MARGIN.dp, top = SitterGrid.SEARCH_Y.dp)
            .height(SitterGrid.SEARCH_H.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .then(if (stacked) Modifier.weight(1f) else Modifier.width(SitterGrid.SEARCH_W.dp))
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
        Box(
            if (stacked) Modifier.padding(start = 12.dp, end = 24.dp) else Modifier.weight(1f).padding(end = 24.dp),
            contentAlignment = Alignment.CenterEnd,
        ) { weather?.invoke() }
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
