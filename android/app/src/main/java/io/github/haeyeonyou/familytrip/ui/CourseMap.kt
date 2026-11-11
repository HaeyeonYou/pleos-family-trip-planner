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
import io.github.haeyeonyou.familytrip.data.CourseStop
import io.github.haeyeonyou.familytrip.data.LatLng
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * 코스 경로 지도 (#231). 실제 지도 타일 없이 시터노트 토큰으로 그린 가상 배경 위에
 * 서버가 준 실제 도로 경로 [path]를 비율대로 그리고(비면 장소 순서대로 직선), 출발지와 장소 번호 핀을 꽂는다.
 * 홈 H01 미리보기, 코스 상세 H02, 코스 진행 H03이 같이 쓴다.
 *
 * [contentPadding]은 경로를 맞출 때 비워 둘 가장자리다. 지도 위에 배지·카드가 덮이는 곳을 비워 경로가 가려지지 않게 한다.
 */
@Composable
fun CourseMap(
    stops: List<CourseStop>,
    path: List<LatLng>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 56.dp, vertical = 44.dp),
) {
    val s = SitterSurface.current
    val shape = remember(stops, path) { CourseMapGeometry.shape(stops, path) }
    BoxWithConstraints(modifier.background(s.tile)) {
        val density = LocalDensity.current
        val dir = LocalLayoutDirection.current
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val projection = with(density) {
            CourseMapGeometry.fit(
                points = shape.line + shape.visits.map { LatLng(it.place.latitude, it.place.longitude) },
                width = w,
                height = h,
                padLeft = contentPadding.calculateLeftPadding(dir).toPx(),
                padTop = contentPadding.calculateTopPadding().toPx(),
                padRight = contentPadding.calculateRightPadding(dir).toPx(),
                padBottom = contentPadding.calculateBottomPadding().toPx(),
            )
        }
        val line = shape.line.map(projection::toScreen)
        val origin = shape.origin?.let(projection::toScreen)
        val home = shape.home?.let(projection::toScreen)
        val ringPx = with(density) { 40.dp.toPx() }
        // 귀가 지점이 출발지와 겹치면(대개 집 → 집) 표시를 하나로 합친다.
        val homeSeparate = home != null && (origin == null || hypot(home.x - origin.x, home.y - origin.y) > ringPx)

        Canvas(Modifier.matchParentSize()) {
            drawGround(s)
            drawRoute(line, s)
            origin?.let { drawRing(it, s) }
            if (homeSeparate) home?.let { drawRing(it, s) }
        }

        // 출발지·귀가 이름표와 번호 핀은 글자가 있어 Compose 요소로 얹는다.
        // 이름표 자리가 모자라면 앞쪽(장소 → 출발 → 귀가)부터 차지한다.
        val markers = buildList {
            shape.visits.forEachIndexed { i, stop ->
                add(Marker(projection.toScreen(LatLng(stop.place.latitude, stop.place.longitude)), number = i + 1, label = stop.place.name))
            }
            origin?.let { add(Marker(it, number = null, label = if (home != null && !homeSeparate) "출발·귀가" else "출발")) }
            if (homeSeparate) home?.let { add(Marker(it, number = null, label = "귀가")) }
        }
        MarkerLayer(markers, Modifier.matchParentSize())
    }
}

private data class Marker(val at: MapPoint, val number: Int?, val label: String)

/** 핀(또는 출발 고리 자리)은 지점 한가운데, 이름표는 그 아래 가운데에 두되 카드 밖으로 나가지 않게 당긴다. */
@Composable
private fun MarkerLayer(markers: List<Marker>, modifier: Modifier) {
    val s = SitterSurface.current
    Layout(
        modifier = modifier,
        content = {
            markers.forEach { m ->
                Box(Modifier.size(if (m.number != null) 34.dp else 0.dp), contentAlignment = Alignment.Center) {
                    if (m.number != null) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(s.primary).border(2.dp, s.card, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text("${m.number}", style = SitterText.Label, color = s.onPrimary) }
                    }
                }
                Text(
                    m.label,
                    style = SitterText.Caption,
                    color = s.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(SitterShapes.Pill)
                        .background(s.card.copy(alpha = 0.92f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        },
    ) { measurables, constraints ->
        val maxLabel = (160.dp.roundToPx()).coerceAtMost(constraints.maxWidth)
        val placeables = measurables.mapIndexed { i, m ->
            m.measure(if (i % 2 == 1) Constraints(maxWidth = maxLabel) else Constraints())
        }
        val gap = 6.dp.roundToPx()
        val ringHalf = 14.dp.roundToPx()
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        // 핀(고리) 자리. 아주 가까운 장소끼리는 번호가 겹치지 않게 핀을 조금씩 벌린다.
        val centers = CourseMapGeometry.spread(markers.map { it.at }, minGap = 30.dp.toPx())
        val pinRects = markers.indices.map { i ->
            val half = maxOf(placeables[i * 2].width / 2, ringHalf)
            val c = centers[i]
            IntRect(c.x.roundToInt() - half, c.y.roundToInt() - half, c.x.roundToInt() + half, c.y.roundToInt() + half)
        }
        val labelPos = arrayOfNulls<IntOffset>(markers.size)
        val taken = mutableListOf<IntRect>()
        markers.forEachIndexed { i, _ ->
            val label = placeables[i * 2 + 1]
            val pin = pinRects[i]
            val cx = pin.center.x
            val cy = pin.center.y
            // 아래 → 위 → 오른쪽 → 왼쪽 순서로 겹치지 않는 자리를 찾고, 없으면 이름표를 생략한다 (번호는 남는다).
            val candidates = listOf(
                IntOffset(cx - label.width / 2, pin.bottom + gap),
                IntOffset(cx - label.width / 2, pin.top - gap - label.height),
                IntOffset(pin.right + gap, cy - label.height / 2),
                IntOffset(pin.left - gap - label.width, cy - label.height / 2),
            ).map { IntOffset(it.x.coerceIn(0, (w - label.width).coerceAtLeast(0)), it.y.coerceIn(0, (h - label.height).coerceAtLeast(0))) }
            val spot = candidates.firstOrNull { o ->
                val r = IntRect(o.x, o.y, o.x + label.width, o.y + label.height)
                taken.none { it.overlaps(r) } && pinRects.none { it.overlaps(r) }
            }
            if (spot != null) {
                labelPos[i] = spot
                taken += IntRect(spot.x, spot.y, spot.x + label.width, spot.y + label.height)
            }
        }
        layout(w, h) {
            // 이름표를 먼저 깔고 핀을 위에 얹어 번호가 가려지지 않게 한다.
            markers.indices.forEach { i -> labelPos[i]?.let { placeables[i * 2 + 1].place(it.x, it.y) } }
            markers.indices.forEach { i ->
                val pin = placeables[i * 2]
                pin.place(pinRects[i].center.x - pin.width / 2, pinRects[i].center.y - pin.height / 2)
            }
        }
    }
}

/** 가상 배경: 타일 바탕 위에 공원·물길 한 군데와 넓은 길 몇 줄. 카드 크기 비율로 놓아 어느 화면에서도 비슷하게 보인다. */
private fun DrawScope.drawGround(s: SitterSurface) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        s.primarySoft,
        topLeft = Offset(w * 0.06f, h * 0.10f),
        size = androidx.compose.ui.geometry.Size(w * 0.22f, h * 0.18f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
    )
    val river = Path().apply {
        moveTo(0f, h * 0.60f)
        cubicTo(w * 0.30f, h * 0.52f, w * 0.62f, h * 0.70f, w, h * 0.60f)
    }
    drawPath(river, SitterPalette.Cyan.copy(alpha = if (s.dark) 0.16f else 0.18f), style = Stroke(width = 26.dp.toPx(), cap = StrokeCap.Round))
    val road = s.card
    val wide = 16.dp.toPx()
    val thin = 9.dp.toPx()
    // 거의 세로인 길 3줄, 거의 가로인 길 3줄 (디자인 H02처럼 살짝 기울임)
    drawLine(road, Offset(w * 0.30f, 0f), Offset(w * 0.33f, h), strokeWidth = wide)
    drawLine(road, Offset(w * 0.66f, 0f), Offset(w * 0.63f, h), strokeWidth = thin)
    drawLine(road, Offset(w * 0.90f, 0f), Offset(w * 0.88f, h), strokeWidth = thin)
    drawLine(road, Offset(0f, h * 0.24f), Offset(w, h * 0.20f), strokeWidth = thin)
    drawLine(road, Offset(0f, h * 0.44f), Offset(w, h * 0.50f), strokeWidth = wide)
    drawLine(road, Offset(0f, h * 0.80f), Offset(w, h * 0.76f), strokeWidth = thin)
}

/** 경로선: 바깥 테두리(카드색) 위에 주 색 선. */
private fun DrawScope.drawRoute(points: List<MapPoint>, s: SitterSurface) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, s.card, style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, s.primary, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** 출발지·귀가 지점: 속이 빈 고리 (디자인 H02의 출발 표시). */
private fun DrawScope.drawRing(p: MapPoint, s: SitterSurface) {
    val c = Offset(p.x, p.y)
    drawCircle(s.card, radius = 13.dp.toPx(), center = c)
    drawCircle(s.primary, radius = 10.dp.toPx(), center = c, style = Stroke(width = 4.dp.toPx()))
}
