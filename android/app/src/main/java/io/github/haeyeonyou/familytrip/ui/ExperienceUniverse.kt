package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.grow.JellyKind
import io.github.haeyeonyou.familytrip.grow.JellyStage
import java.time.YearMonth

/** 젤리를 거르는 칩 (G01 칩 줄). 분류·달은 함께 걸 수 있다(음성 "지난달 물놀이 간 곳", #185). 둘 다 없으면 전체. */
internal data class GrowFilter(val kind: JellyKind? = null, val month: YearMonth? = null) {
    companion object {
        val All = GrowFilter()
    }
}

private val OrbitLine = Color(0xFFC9CFD6)
private val BubbleInk = Color(0xFF191F28)

/**
 * G01 경험 젤리 궤도 (800x560 설계 좌표를 상자에 맞춰 그린다, #216).
 * [visibleKinds]에 없는 분야는 흐리게(칩 거르기). [bubble]은 최근 탄생 말풍선을 붙일 분야와 글자.
 */
@Composable
internal fun JellyOrbitCanvas(
    orbit: JellyOrbit,
    visibleKinds: Set<JellyKind>?,
    bubble: Pair<JellyKind, String>?,
    onHit: (JellyKind?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = SitterSurface.current
    val tm = rememberTextMeasurer()
    val images = rememberJellyImages(orbit.nodes.filter { !it.empty }.map { it.kind to it.state.stage }.toSet())
    Canvas(
        modifier.pointerInput(orbit) {
            detectTapGestures { pos ->
                val fit = fitDesign(size.width.toFloat(), size.height.toFloat())
                onHit(hitOrbit(orbit, (pos.x - fit.left) / fit.unit, (pos.y - fit.top) / fit.unit, minHit = 26f))
            }
        },
    ) {
        val fit = fitDesign(size.width, size.height)
        translate(fit.left, fit.top) {
            scale(fit.unit, fit.unit, pivot = Offset.Zero) {
                drawOrbit(orbit, images, visibleKinds, bubble, tm, s)
            }
        }
    }
}

private fun DrawScope.drawOrbit(
    o: JellyOrbit,
    images: Map<Pair<JellyKind, JellyStage>, ImageBitmap>,
    visible: Set<JellyKind>?,
    bubble: Pair<JellyKind, String>?,
    tm: TextMeasurer,
    s: SitterSurface,
) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(5f, 6f))
    listOf(INNER_RX to INNER_RY, OUTER_RX to OUTER_RY).forEach { (rx, ry) ->
        drawOval(OrbitLine, Offset(CENTER.x - rx, CENTER.y - ry), Size(rx * 2, ry * 2), style = Stroke(1.4f, pathEffect = dash))
    }
    // 가운데 젤리가 위에 오도록 궤도 → 가운데 순
    (o.nodes.filter { !it.center } + o.nodes.filter { it.center }).forEach { n ->
        val alpha = if (visible == null || n.kind in visible) 1f else 0.25f
        val at = Offset(n.x, n.y)
        if (n.empty) {
            drawCircle(s.card, EMPTY_RADIUS, at, alpha = alpha)
            drawCircle(OrbitLine, EMPTY_RADIUS, at, style = Stroke(1.6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))), alpha = alpha)
            label(tm, orbitLabel(n.state), Offset(n.x, n.labelY), 14f, s.inkMute.copy(alpha = alpha))
        } else {
            if (n.center) drawCircle(Color(n.kind.argb).copy(alpha = 0.10f * alpha), n.frame * 0.42f, at)
            images[n.kind to n.state.stage]?.let { drawJellyFramed(it, at, n.frame, alpha) }
            label(tm, orbitLabel(n.state), Offset(n.x, n.labelY), if (n.center) 16f else 14f, s.inkSoft.copy(alpha = alpha), bold = n.center)
        }
    }
    bubble?.let { (kind, text) ->
        val n = o.nodes.firstOrNull { it.kind == kind } ?: return@let
        val layout = tm.measure(text, TextStyle(fontSize = (14f / density / fontScale).sp, color = Color.White, fontFamily = Pretendard, fontWeight = FontWeight.Medium))
        val w = layout.size.width + 24f
        val h = layout.size.height + 12f
        // 젤리 오른쪽 위 (위쪽 궤도 젤리 이름을 가리지 않게)
        val left = (n.x + n.frame * 0.22f).coerceIn(2f, DESIGN_W - w - 2f)
        val top = (n.y - n.frame * 0.30f - h).coerceAtLeast(2f)
        drawRoundRect(BubbleInk, Offset(left, top), Size(w, h), CornerRadius(10f))
        drawText(layout, topLeft = Offset(left + 12f, top + 6f))
    }
}

/** 가운데 정렬 글자. [at]은 글자 위 가운데. 설계 좌표를 px로 그리므로 sp를 그대로 쓴다. */
private fun DrawScope.label(tm: TextMeasurer, text: String, at: Offset, size: Float, color: Color, bold: Boolean = false, maxWidth: Float = 160f) {
    val style = TextStyle(
        fontSize = (size / density / fontScale).sp,
        color = color,
        fontFamily = Pretendard,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        textAlign = TextAlign.Center,
    )
    val layout = tm.measure(text, style, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = maxWidth.toInt()))
    val left = (at.x - layout.size.width / 2f).coerceIn(0f, DESIGN_W - layout.size.width)
    drawText(layout, topLeft = Offset(left, at.y.coerceAtMost(DESIGN_H - layout.size.height)))
}
