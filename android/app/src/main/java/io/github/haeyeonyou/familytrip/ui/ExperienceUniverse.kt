package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.grow.JellyKind
import java.time.LocalDate
import java.time.YearMonth

/** 우주를 거르는 칩 (G01 "분야 칩으로 거르기"). 분류·달은 함께 걸 수 있다(음성 "지난달 물놀이 간 곳", #185). 둘 다 없으면 전체. */
internal data class GrowFilter(val kind: JellyKind? = null, val month: YearMonth? = null) {
    companion object {
        val All = GrowFilter()
    }
}

private val StarGray = Color(0xFFC9D2DC)

/**
 * G01 라이트 경험 우주 (752x540 설계 좌표를 상자에 맞춰 그린다).
 * 가운데 아이 → 다녀온 분류의 젤리 → 그 분류의 최근 장소. 안 간 분류는 흐린 빈 별, 추천 분류는 점선 원.
 */
@Composable
internal fun JellyUniverseCanvas(
    geometry: JellyGeometry,
    centerLabel: String,
    filter: GrowFilter,
    selectedPlace: String?,
    today: LocalDate,
    onHit: (UniverseHit?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = SitterSurface.current
    val tm = rememberTextMeasurer()
    val month = YearMonth.from(today)
    val images = rememberJellyImages(geometry.kinds.filter { it.state.count > 0 }.map { it.state.kind to it.state.stage }.toSet())
    Canvas(
        modifier.pointerInput(geometry) {
            detectTapGestures { pos ->
                val fit = fitDesign(size.width.toFloat(), size.height.toFloat())
                val x = (pos.x - fit.left) / fit.unit
                val y = (pos.y - fit.top) / fit.unit
                onHit(hitUniverse(geometry, x, y, minHit = 24f))
            }
        },
    ) {
        val fit = fitDesign(size.width, size.height)
        translate(fit.left, fit.top) {
            scale(fit.unit, fit.unit, pivot = Offset.Zero) {
                drawUniverse(geometry, images, centerLabel, filter, selectedPlace, month, tm, s)
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
private fun kindVisible(filter: GrowFilter, node: KindNode, month: YearMonth, places: List<PlaceDot>): Boolean =
    (filter.kind == null || node.state.kind == filter.kind) &&
        (filter.month == null || places.any { it.place.kind == node.state.kind && filter.month in it.place.months })

@Suppress("UNUSED_PARAMETER")
private fun placeVisible(filter: GrowFilter, p: GrowPlace, month: YearMonth): Boolean =
    (filter.kind == null || p.kind == filter.kind) && (filter.month == null || filter.month in p.months)

private fun DrawScope.drawUniverse(
    g: JellyGeometry,
    images: Map<Pair<JellyKind, io.github.haeyeonyou.familytrip.grow.JellyStage>, androidx.compose.ui.graphics.ImageBitmap>,
    centerLabel: String,
    filter: GrowFilter,
    selectedPlace: String?,
    month: YearMonth,
    tm: TextMeasurer,
    s: SitterSurface,
) {
    // 배경 별과 가는 궤도선
    STARS.forEach { drawCircle(StarGray, it.r * 1.4f, Offset(it.x, it.y), alpha = it.alpha) }
    drawOval(
        StarGray.copy(alpha = 0.55f),
        Offset(CENTER.x - RING_RX, CENTER.y - RING_RY),
        Size(RING_RX * 2, RING_RY * 2),
        style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 6f))),
    )
    val center = Offset(CENTER.x, CENTER.y)
    val dim = 0.22f

    // 가운데 → 분류 선
    g.kinds.forEach { k ->
        val visible = kindVisible(filter, k, month, g.places)
        val color = Color(k.state.kind.argb)
        when {
            k.state.count > 0 -> drawLine(color.copy(alpha = 0.5f), center, Offset(k.x, k.y), strokeWidth = 2.5f, alpha = if (visible) 1f else dim)
            k.suggested -> drawLine(
                s.primary.copy(alpha = 0.6f), center, Offset(k.x, k.y), strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
        }
    }
    // 분류 → 장소 선
    g.places.forEach { p ->
        val k = g.kinds[p.kindIndex]
        val a = if (placeVisible(filter, p.place, month)) 1f else dim
        drawLine(Color(k.state.kind.argb).copy(alpha = 0.45f), Offset(k.x, k.y), Offset(p.x, p.y), strokeWidth = 1.5f, alpha = a)
    }

    // 가운데 아이
    drawCircle(s.primary.copy(alpha = 0.10f), CENTER_RADIUS + 22f, center)
    drawCircle(Brush.linearGradient(colorStops = SitterPalette.GradientStops, start = center - Offset(CENTER_RADIUS, CENTER_RADIUS), end = center + Offset(CENTER_RADIUS, CENTER_RADIUS)), CENTER_RADIUS, center)
    label(tm, centerLabel, center, 15f, Color.White, bold = true, maxWidth = CENTER_RADIUS * 2 - 6f, centerY = true)

    // 분류 노드
    g.kinds.forEach { k ->
        val kind = k.state.kind
        val color = Color(kind.argb)
        val visible = kindVisible(filter, k, month, g.places)
        val alpha = if (visible) 1f else dim
        val at = Offset(k.x, k.y)
        when {
            k.state.count > 0 -> {
                drawCircle(color.copy(alpha = 0.14f * alpha), k.r * 1.6f, at)
                if (alpha < 1f) {
                    drawCircle(color.copy(alpha = 0.35f), k.r * 0.8f, at)
                } else {
                    images[kind to k.state.stage]?.let { drawJelly(it, at) }
                }
                label(tm, "${kind.chip} · ${k.state.count}", Offset(k.x, k.y + maxOf(k.r, 27f) + 6f), 13f, lerp(color, Color.Black, 0.25f).copy(alpha = alpha), bold = true)
            }
            k.suggested -> {
                drawCircle(s.primarySoft, k.r, at)
                drawCircle(s.primary, k.r, at, style = Stroke(1.6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))))
                label(tm, kind.label, Offset(k.x, k.y + k.r + 6f), 13f, s.primary, bold = true)
                label(tm, "다음에 넓어질 영역", Offset(k.x, k.y + k.r + 24f), 12f, s.primary)
            }
            else -> {
                drawCircle(StarGray, k.r, at, alpha = if (filter == GrowFilter.All) 1f else 0.5f)
                label(tm, kind.chip, Offset(k.x, k.y + k.r + 4f), 11f, s.inkMute.copy(alpha = 0.8f))
            }
        }
    }

    // 장소 노드
    g.places.forEach { p ->
        val color = Color(p.place.kind.argb)
        val visible = placeVisible(filter, p.place, month)
        val alpha = if (visible) 1f else dim
        val at = Offset(p.x, p.y)
        if (p.place.name == selectedPlace) drawCircle(s.primary, p.r + 6f, at, style = Stroke(2f))
        drawCircle(color, p.r, at, alpha = alpha)
        drawCircle(Color.White, p.r, at, style = Stroke(1.5f), alpha = alpha)
        label(tm, p.place.name, Offset(p.x, p.y + p.r + 4f), 12f, s.inkSoft.copy(alpha = alpha), maxWidth = 104f)
    }

    // 방금 기록 말풍선
    g.places.firstOrNull { it.place.latest }?.let { p ->
        drawCircle(s.primary, p.r + 4f, Offset(p.x, p.y), style = Stroke(2f))
        val text = "방금 기록 · ${p.place.last.monthValue.toString().padStart(2, '0')}.${p.place.last.dayOfMonth.toString().padStart(2, '0')}"
        val layout = tm.measure(text, TextStyle(fontSize = (12f / density / fontScale).sp, color = Color.White, fontFamily = Pretendard))
        val w = layout.size.width + 16f
        val h = layout.size.height + 8f
        val left = (p.x - w / 2).coerceIn(2f, DESIGN_W - w - 2f)
        val top = (p.y - p.r - h - 8f).coerceAtLeast(2f)
        drawRoundRect(Color(0xFF191F28), Offset(left, top), Size(w, h), androidx.compose.ui.geometry.CornerRadius(8f))
        drawText(layout, topLeft = Offset(left + 8f, top + 4f))
    }
}

/** 가운데 정렬 글자. [at]은 글자 위 가운데(centerY면 정가운데). 설계 좌표는 dp 단위가 아니라 px로 그리므로 sp를 그대로 쓴다. */
private fun DrawScope.label(
    tm: TextMeasurer,
    text: String,
    at: Offset,
    size: Float,
    color: Color,
    bold: Boolean = false,
    maxWidth: Float = 140f,
    centerY: Boolean = false,
) {
    val style = TextStyle(
        fontSize = (size / density / fontScale).sp,
        color = color,
        fontFamily = Pretendard,
        fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium,
        textAlign = TextAlign.Center,
    )
    val layout = tm.measure(text, style, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(1)))
    val left = (at.x - layout.size.width / 2f).coerceIn(0f, DESIGN_W - layout.size.width)
    val top = if (centerY) at.y - layout.size.height / 2f else at.y
    drawText(layout, topLeft = Offset(left, top))
}
