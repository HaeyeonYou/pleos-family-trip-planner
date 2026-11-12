package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.data.CourseStop
import io.github.haeyeonyou.familytrip.data.LatLng
import kotlin.math.roundToInt

/** 지도 모양 두 가지 (Figma 11/10 확정안). */
enum class CourseMapStyle {
    /** 홈 H01 "추천 코스 미리보기": 공원 원·물길 띠 장식, 핀 옆 장소 이름 알약. */
    Preview,

    /** 코스 상세 H02(진행 H03도 같음): 도로 격자만, 번호 핀만. */
    Plain,
}

/**
 * 코스 경로 지도 (#231·#233·#239). 실제 지도 타일 없이 시터노트 토큰으로 그린 가상 배경 위에
 * 출발 지점 → 장소 ①②③을 순서대로 잇는 부드러운 곡선을 그린다 (Figma H01·H02와 같게, 실제 도로 경로·귀가 선은 그리지 않음).
 * 핀 위치는 위경도 비율을 대략 따르되 겹치지 않게 펼친다 ([CourseMapGeometry]). [path]는 출발 위치(첫 점)를 알아내는 데만 쓴다.
 *
 * [contentPadding]은 핀을 놓을 때 비워 둘 가장자리다. 지도 위에 배지·카드가 덮이는 곳을 비워 경로가 가려지지 않게 한다.
 */
@Composable
fun CourseMap(
    stops: List<CourseStop>,
    path: List<LatLng>,
    modifier: Modifier = Modifier,
    style: CourseMapStyle = CourseMapStyle.Plain,
    contentPadding: PaddingValues = PaddingValues(horizontal = 56.dp, vertical = 44.dp),
) {
    val s = SitterSurface.current
    val anchors = remember(stops, path) { CourseMapGeometry.anchors(stops, path) }
    BoxWithConstraints(modifier.background(s.tile)) {
        val density = LocalDensity.current
        val dir = LocalLayoutDirection.current
        val points = with(density) {
            val placed = CourseMapGeometry.place(
                points = anchors.map { it.at },
                width = constraints.maxWidth.toFloat(),
                height = constraints.maxHeight.toFloat(),
                padLeft = contentPadding.calculateLeftPadding(dir).toPx(),
                padTop = contentPadding.calculateTopPadding().toPx(),
                padRight = contentPadding.calculateRightPadding(dir).toPx(),
                padBottom = contentPadding.calculateBottomPadding().toPx(),
            )
            // 순위 섞기로도 가까운 핀은 조금 더 벌린다.
            CourseMapGeometry.spread(placed, minGap = 40.dp.toPx())
        }
        val curve = CourseMapGeometry.smoothCurve(points)

        Canvas(Modifier.matchParentSize()) {
            if (style == CourseMapStyle.Preview) drawPreviewDecor(s.dark)
            drawRoadGrid(s)
            drawRoute(curve, s)
            anchors.forEachIndexed { i, a -> if (a.number == null) drawOriginRing(points[i], s) }
        }

        // 번호 핀(과 홈의 이름 알약)은 글자가 있어 Compose 요소로 얹는다.
        MarkerLayer(
            markers = anchors.mapIndexedNotNull { i, a -> a.number?.let { Marker(points[i], it, a.label) } },
            showLabels = style == CourseMapStyle.Preview,
            modifier = Modifier.matchParentSize(),
        )
    }
}

private data class Marker(val at: MapPoint, val number: Int, val label: String)

private val PIN_SIZE = 28.dp

/**
 * 번호 핀(primary 원 + 흰 번호 + 흰 테두리)은 지점 한가운데에 둔다.
 * [showLabels]면 장소 이름 알약을 핀 오른쪽 → 왼쪽 → 위 → 아래 중 다른 핀·이름과 겹치지 않는 자리에 두고, 없으면 생략한다.
 */
@Composable
private fun MarkerLayer(markers: List<Marker>, showLabels: Boolean, modifier: Modifier) {
    val s = SitterSurface.current
    Layout(
        modifier = modifier,
        content = {
            markers.forEach { m ->
                Box(
                    Modifier.size(PIN_SIZE).clip(CircleShape).background(s.primary).border(2.dp, s.card, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("${m.number}", style = SitterText.Label.copy(fontSize = 14.sp), color = s.onPrimary) }
                if (showLabels) {
                    Text(
                        m.label,
                        style = SitterText.Caption,
                        color = s.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(SitterShapes.Pill)
                            .background(s.card)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val per = if (showLabels) 2 else 1
        val maxLabel = (200.dp.roundToPx()).coerceAtMost(constraints.maxWidth)
        val placeables = measurables.mapIndexed { i, m ->
            m.measure(if (i % per == 1) Constraints(maxWidth = maxLabel) else Constraints())
        }
        val gap = 8.dp.roundToPx()
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val pinRects = markers.mapIndexed { i, m ->
            val pin = placeables[i * per]
            val x = m.at.x.roundToInt() - pin.width / 2
            val y = m.at.y.roundToInt() - pin.height / 2
            IntRect(x, y, x + pin.width, y + pin.height)
        }
        val labelPos = arrayOfNulls<IntOffset>(markers.size)
        if (showLabels) {
            val taken = mutableListOf<IntRect>()
            markers.indices.forEach { i ->
                val label = placeables[i * 2 + 1]
                val pin = pinRects[i]
                val cx = pin.center.x
                val cy = pin.center.y
                val spot = listOf(
                    IntOffset(pin.right + gap, cy - label.height / 2),
                    IntOffset(pin.left - gap - label.width, cy - label.height / 2),
                    IntOffset(cx - label.width / 2, pin.top - gap - label.height),
                    IntOffset(cx - label.width / 2, pin.bottom + gap),
                ).map { IntOffset(it.x.coerceIn(0, (w - label.width).coerceAtLeast(0)), it.y.coerceIn(0, (h - label.height).coerceAtLeast(0))) }
                    .firstOrNull { o ->
                        val r = IntRect(o.x, o.y, o.x + label.width, o.y + label.height)
                        taken.none { it.overlaps(r) } && pinRects.none { it.overlaps(r) }
                    }
                if (spot != null) {
                    labelPos[i] = spot
                    taken += IntRect(spot.x, spot.y, spot.x + label.width, spot.y + label.height)
                }
            }
        }
        layout(w, h) {
            // 이름표를 먼저 깔고 핀을 위에 얹어 번호가 가려지지 않게 한다.
            markers.indices.forEach { i -> labelPos[i]?.let { placeables[i * 2 + 1].place(it.x, it.y) } }
            markers.indices.forEach { i -> placeables[i * per].place(pinRects[i].left, pinRects[i].top) }
        }
    }
}

/** 홈 H01 장식: 연초록 공원 원 두 개와 가로지르는 연파랑 물길 띠. 카드 크기 비율로 놓는다. */
private fun DrawScope.drawPreviewDecor(dark: Boolean) {
    val w = size.width
    val h = size.height
    // 수면(다크)에서는 연초록·연파랑이 어두운 바탕 위에 밝게 튀지 않게 어두운 같은 계열로 (Figma 다크 지도 없음, 추정)
    val park = if (dark) Color(0xFF22352A) else SitterPalette.MapPark
    val water = if (dark) Color(0xFF1E2E40) else SitterPalette.MapWater
    drawCircle(park, radius = w * 0.24f, center = Offset(w * 0.20f, h * 0.20f))
    drawCircle(park, radius = w * 0.17f, center = Offset(w * 0.84f, h * 0.30f))
    val river = Path().apply {
        moveTo(0f, h * 0.52f)
        cubicTo(w * 0.35f, h * 0.46f, w * 0.65f, h * 0.50f, w, h * 0.42f)
        lineTo(w, h * 0.56f)
        cubicTo(w * 0.65f, h * 0.64f, w * 0.35f, h * 0.60f, 0f, h * 0.66f)
        close()
    }
    drawPath(river, water)
}

/** 흰 굵은 도로 격자: 세로 2줄·가로 2줄, 살짝 기울임 (Figma H01·H02). */
private fun DrawScope.drawRoadGrid(s: SitterSurface) {
    val w = size.width
    val h = size.height
    val road = s.card
    val width = 14.dp.toPx()
    drawLine(road, Offset(w * 0.30f, 0f), Offset(w * 0.32f, h), strokeWidth = width)
    drawLine(road, Offset(w * 0.70f, 0f), Offset(w * 0.68f, h), strokeWidth = width)
    drawLine(road, Offset(0f, h * 0.24f), Offset(w, h * 0.21f), strokeWidth = width)
    drawLine(road, Offset(0f, h * 0.68f), Offset(w, h * 0.72f), strokeWidth = width)
}

/** 경로선: 순서대로 잇는 부드러운 곡선 하나 (primary). */
private fun DrawScope.drawRoute(curve: List<CurveSegment>, s: SitterSurface) {
    if (curve.isEmpty()) return
    val path = Path().apply {
        moveTo(curve.first().from.x, curve.first().from.y)
        curve.forEach { cubicTo(it.c1.x, it.c1.y, it.c2.x, it.c2.y, it.to.x, it.to.y) }
    }
    drawPath(path, s.primary, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** 출발 지점: 흰 속을 채운 primary 고리 (글자 없음, Figma H02). */
private fun DrawScope.drawOriginRing(p: MapPoint, s: SitterSurface) {
    val c = Offset(p.x, p.y)
    drawCircle(s.card, radius = 10.dp.toPx(), center = c)
    drawCircle(s.primary, radius = 10.dp.toPx(), center = c, style = Stroke(width = 4.dp.toPx()))
}
