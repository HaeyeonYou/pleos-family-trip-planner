package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.data.CourseStop

/**
 * 코스 경로 미리보기 (HF02 경로 카드). 실제 지도 SDK(카카오맵, #40)를 붙이기 전까지는 도로 격자 위에
 * 코스 장소를 위경도 비율대로 놓고 경로를 잇는 간이 지도다. 키가 생기면 이 자리를 실제 지도로 바꾼다.
 */
@Composable
fun CourseMapLayer(stops: List<CourseStop>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val heightPx = with(density) { maxHeight.toPx() }
        val stripPx = with(density) { maxWidth.toPx() }
        val places = stops.filter { it.role != "home" }
        val points = project(places, stripPx, heightPx, density.density)

        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(TripColors.MapGround)
            // 공원·강 (고정 배치, 장식)
            drawOval(TripColors.MapPark, topLeft = Offset(size.width * 0.04f, size.height * 0.08f), size = Size(size.width * 0.12f, size.height * 0.18f))
            drawOval(TripColors.MapPark, topLeft = Offset(size.width * 0.14f, size.height * 0.74f), size = Size(size.width * 0.1f, size.height * 0.14f))
            val river = Path().apply {
                moveTo(0f, size.height * 0.56f)
                cubicTo(size.width * 0.3f, size.height * 0.5f, size.width * 0.6f, size.height * 0.66f, size.width, size.height * 0.58f)
                lineTo(size.width, size.height * 0.64f)
                cubicTo(size.width * 0.6f, size.height * 0.72f, size.width * 0.3f, size.height * 0.56f, 0f, size.height * 0.62f)
                close()
            }
            drawPath(river, TripColors.MapWater)
            // 도로 격자
            val step = 120.dp.toPx()
            var x = step * 0.4f
            while (x < size.width) {
                drawLine(TripColors.MapRoad, Offset(x, 0f), Offset(x + 30f, size.height), strokeWidth = 14f)
                x += step
            }
            var y = step * 0.6f
            while (y < size.height) {
                drawLine(TripColors.MapRoad, Offset(0f, y), Offset(size.width, y - 20f), strokeWidth = 14f)
                y += step
            }
            // 코스 경로
            if (points.size >= 2) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.zipWithNext().forEach { (a, b) ->
                        val midY = (a.y + b.y) / 2
                        cubicTo(a.x, midY, b.x, midY, b.x, b.y)
                    }
                }
                drawPath(path, TripColors.Route, style = Stroke(width = 14f, cap = StrokeCap.Round))
            }
            points.firstOrNull()?.let {
                drawCircle(Color.White, radius = 22f, center = it)
                drawCircle(TripColors.Route, radius = 14f, center = it)
            }
        }
        // 장소 번호 핀과 이름표
        places.zip(points).forEachIndexed { i, (stop, p) ->
            val (dx, dy) = with(density) { p.x.toDp() to p.y.toDp() }
            Row(
                modifier = Modifier.offset(x = dx - 22.dp, y = dy - 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(TripColors.Green),
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Text(
                    stop.place.name.take(10),
                    fontSize = 16.sp,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** 위경도를 왼쪽 띠 안의 화면 좌표로 옮긴다 (북쪽이 위). 장소가 없으면 빈 목록. */
private fun project(stops: List<CourseStop>, stripPx: Float, heightPx: Float, density: Float): List<Offset> {
    if (stops.isEmpty()) return emptyList()
    val lats = stops.map { it.place.latitude }
    val lngs = stops.map { it.place.longitude }
    val margin = 70f * density
    val left = margin * 0.6f
    val right = stripPx - margin * 2.2f
    val top = heightPx * 0.15f
    val bottom = heightPx * 0.8f
    val latSpan = (lats.max() - lats.min()).takeIf { it > 1e-6 } ?: 1.0
    val lngSpan = (lngs.max() - lngs.min()).takeIf { it > 1e-6 } ?: 1.0
    return stops.map {
        val fx = ((it.place.longitude - lngs.min()) / lngSpan).toFloat()
        val fy = ((lats.max() - it.place.latitude) / latSpan).toFloat()
        Offset(left + fx * (right - left), top + fy * (bottom - top))
    }
}
