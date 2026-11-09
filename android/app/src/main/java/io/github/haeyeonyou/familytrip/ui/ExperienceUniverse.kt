package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import kotlin.math.max

internal val MONTH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("MM.dd")

// Figma HF 04 'Graph · space' 색
private val SpaceBg = Color(0xFF0B0F1B)
private val EdgeCenter = Color(0xFF9FB3D9)
private val SatelliteColor = Color(0xFFC9D4EA)
private val TagDot = Color(0xFFE8EEFC)
private val OnSpace = Color(0xFF0B1220)

private fun fieldColor(key: String) = Color(FIELD_ARGB.getValue(key))

private const val MIN_SCALE = 0.6f
private const val MAX_SCALE = 3f

/**
 * 별 배경 위의 경험 우주 (Figma HF 04 'Graph · space'). 핀치로 확대·축소하고 끌어서 옮기며,
 * 장소 노드를 누르면 [onPlaceClick]을 부른다.
 * 그리는 위치 = (설계 좌표 × 창 맞춤 배율 + 여백) × scale + offset.
 */
@Composable
internal fun UniverseCanvas(
    data: UniverseData,
    centerLabel: String,
    selected: String?,
    onPlaceClick: (PlaceNode?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val geometry = remember(data) { computeGeometry(data) }
    val fit = remember(size) { fitDesign(size.width.toFloat(), size.height.toFloat()) }
    val glows = remember { HashMap<Long, FloatArray>() }
    val u = fit.unit

    fun zoomAt(factor: Float, at: Offset) {
        val next = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        offset = at - (at - offset) * (next / scale)
        scale = next
    }
    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }
    fun toScreen(x: Float, y: Float) = Pt((fit.left + x * u) * scale + offset.x, (fit.top + y * u) * scale + offset.y)

    // 확대·축소 버튼 등 겹쳐 놓는 요소의 크기 (설계 픽셀 → dp)
    fun px(v: Float): Dp = with(density) { (v * u).toDp() }
    fun fs(v: Float) = with(density) { max(v * u, minOf(v, 13f)).toSp() }

    Box(modifier = modifier.clipToBounds().background(SpaceBg)) {
        Canvas(
            modifier = Modifier.fillMaxSize()
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    // 두 손가락(핀치)이거나 이미 확대했을 때만 제스처를 가져간다. 아니면 페이지 스크롤에 넘긴다.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val multi = event.changes.count { it.pressed } > 1
                            if (multi || scale > 1.01f) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                if (zoom != 1f || pan != Offset.Zero) {
                                    zoomAt(zoom, event.calculateCentroid(useCurrent = false))
                                    offset += pan
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(geometry, fit) {
                    detectTapGestures { tap ->
                        val hit = hitPlace(geometry.places, tap.x, tap.y, ::toScreen, u * scale, 30f * density.density)
                        onPlaceClick(hit?.let { geometry.places[it].item })
                    }
                },
        ) {
            drawUniverse(geometry, data.isEmpty, textMeasurer, glows, centerLabel, selected, fit, scale, offset)
        }

        // 범례 칩 (Figma 24,22)
        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(start = px(24f), top = px(22f))
                .clip(RoundedCornerShape(percent = 50)).background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = px(16f), vertical = px(10f)),
            horizontalArrangement = Arrangement.spacedBy(px(16f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FIELD_TITLES.forEach { (key, title) ->
                Row(horizontalArrangement = Arrangement.spacedBy(px(6f)), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(px(10f)).clip(CircleShape).background(fieldColor(key)))
                    Text(title, fontSize = fs(14f), fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }

        // 확대·축소·맞춤 (Figma 오른쪽 아래, 가장자리에서 24)
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = px(24f), bottom = px(24f))
                .clip(RoundedCornerShape(px(14f))).background(Color.White.copy(alpha = 0.08f)),
        ) {
            ZoomButton("＋", max(px(56f).value, 52f).dp, fs(22f)) { zoomAt(1.4f, Offset(size.width / 2f, size.height / 2f)) }
            ZoomButton("－", max(px(56f).value, 52f).dp, fs(22f)) { zoomAt(1f / 1.4f, Offset(size.width / 2f, size.height / 2f)) }
            ZoomButton("⤢", max(px(56f).value, 52f).dp, fs(22f)) { reset() }
        }
        Text(
            "핀치로 확대 · 노드를 누르면 그곳의 코스를 다시 볼 수 있어요",
            fontSize = fs(14f),
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = px(24f), bottom = px(23f)),
        )
    }
}

@Composable
private fun ZoomButton(text: String, size: Dp, fontSize: androidx.compose.ui.unit.TextUnit, onClick: () -> Unit) {
    Box(modifier = Modifier.size(size).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, fontSize = fontSize, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.85f))
    }
}

private fun DrawScope.drawUniverse(
    g: UniverseGeometry,
    empty: Boolean,
    tm: TextMeasurer,
    glows: HashMap<Long, FloatArray>,
    centerLabel: String,
    selected: String?,
    fit: Fit,
    scale: Float,
    offset: Offset,
) {
    val us = fit.unit * scale // 설계 1픽셀이 화면에서 차지하는 픽셀
    fun at(x: Float, y: Float) = Offset((fit.left + x * fit.unit) * scale + offset.x, (fit.top + y * fit.unit) * scale + offset.y)
    fun w(v: Float) = max(v * us, 0.8f)
    fun dash(on: Float, off: Float) = PathEffect.dashPathEffect(floatArrayOf(on * us, off * us))
    fun style(px: Float, weight: FontWeight, color: Color) = TextStyle(color = color, fontSize = max(px * us, minOf(px, 13f)).toSp(), fontWeight = weight)
    val center = at(CENTER.x, CENTER.y)

    // 별
    STARS.forEach { drawCircle(Color.White.copy(alpha = it.alpha), max(it.r * us, 0.4f), at(it.x, it.y)) }

    // 가운데 → 분야 (아직 안 간 분야는 분야 색의 점선)
    g.fields.forEach { f ->
        val key = f.item.key
        if (f.item.count == 0) {
            drawLine(fieldColor(key).copy(alpha = 0.5f), center, at(f.x, f.y), w(1.5f), pathEffect = dash(5f, 7f))
        } else {
            drawLine(EdgeCenter.copy(alpha = 0.45f), center, at(f.x, f.y), w(2f))
        }
    }
    // 분야 → 장소 → 위성 점
    g.places.forEach { p ->
        val f = g.fields.first { it.item.key == p.item.field }
        drawLine(fieldColor(p.item.field).copy(alpha = 0.45f), at(f.x, f.y), at(p.x, p.y), w(1.4f))
    }
    g.satellites.forEach { s ->
        val p = g.places[s.placeIndex]
        drawLine(SatelliteColor.copy(alpha = 0.25f), at(p.x, p.y), at(s.x, s.y), w(1f))
    }
    // 태그 ↔ 장소: 아주 희미한 점선
    g.tagLinks.forEach { l ->
        val t = g.tags[l.tag]
        val p = g.places[l.place]
        drawLine(Color.White.copy(alpha = 0.12f), at(t.x, t.y), at(p.x, p.y), w(1f), pathEffect = dash(2f, 5f))
    }

    g.satellites.forEach { drawCircle(SatelliteColor, 2.5f * us, at(it.x, it.y)) }

    // 태그
    g.tags.forEach { t ->
        glow(glows, at(t.x, t.y), 6f, TAG_GLOW_SIGMA, TagDot, us)
        drawCircle(TagDot, 6f * us, at(t.x, t.y))
        centered(tm, t.label, at(t.x, t.y + 10f), style(14f, FontWeight.Normal, Color.White.copy(alpha = 0.55f)), 160f * us)
    }

    // 장소
    g.places.forEach { p ->
        val c = at(p.x, p.y)
        val color = fieldColor(p.item.field)
        glow(glows, c, p.r, placeGlowSigma(p.r), color, us)
        drawCircle(color, p.r * us, c)
        if (p.item.latest) drawCircle(Color.White, (p.r + 8.25f) * us, c, style = Stroke(w(1.5f)))
        if (p.item.name == selected) drawCircle(Color.White, (p.r + 12f) * us, c, style = Stroke(w(1.5f), pathEffect = dash(4f, 4f)))
        val labelColor = if (p.item.latest) Color.White else Color.White.copy(alpha = 0.7f)
        centered(tm, p.item.name, at(p.x, p.y + p.r + 4f), style(14f, FontWeight.Normal, labelColor), 150f * us)
    }
    // 가장 최근 방문의 말풍선
    g.places.firstOrNull { it.item.latest }?.let { p -> chip(tm, "방금 기록 · ${p.item.last.format(MONTH_DAY)}", at(p.x, p.y - p.r - 16f), us) }

    // 분야
    g.fields.forEach { f ->
        val c = at(f.x, f.y)
        val color = fieldColor(f.item.key)
        if (f.item.count == 0) {
            drawCircle(color, f.r * us, c, style = Stroke(w(2f), pathEffect = dash(9f, 5.1f)))
            centered(tm, f.item.title, at(f.x, f.y + f.r + 6f), style(17f, FontWeight.SemiBold, color), 200f * us)
            centered(tm, "다음에 넓어질 영역", at(f.x, f.y + f.r + 32f), style(13f, FontWeight.Normal, color.copy(alpha = 0.75f)), 200f * us)
        } else {
            glow(glows, c, f.r, fieldGlowSigma(f.r), color, us)
            drawCircle(color, f.r * us, c)
            centered(tm, "${f.item.title} · ${f.item.count}", at(f.x, f.y + f.r + 6f), style(17f, FontWeight.SemiBold, color), 200f * us)
        }
    }

    // 가운데 우리 아이
    glow(glows, center, CENTER_RADIUS, CENTER_GLOW_SIGMA, Color.White, us)
    drawCircle(Color.White, CENTER_RADIUS * us, center)
    if (centerLabel == "우리 아이") {
        centered(tm, centerLabel, at(CENTER.x, CENTER.y - 10f), style(18f, FontWeight.Bold, OnSpace), 66f * us)
    } else {
        centered(tm, centerLabel, at(CENTER.x, CENTER.y - 19f), style(16f, FontWeight.Bold, OnSpace), 62f * us)
        centered(tm, "우리 아이", at(CENTER.x, CENTER.y + 3f), style(11f, FontWeight.Normal, OnSpace.copy(alpha = 0.6f)), 62f * us)
    }

    if (empty) {
        centered(tm, "나들이 장소에 도착하면", at(CENTER.x, CENTER.y + 84f), style(17f, FontWeight.Normal, Color.White.copy(alpha = 0.6f)), 300f * us)
        centered(tm, "우주에 별이 하나씩 생겨요", at(CENTER.x, CENTER.y + 108f), style(17f, FontWeight.Normal, Color.White.copy(alpha = 0.6f)), 300f * us)
    }
}

/** Figma 그림자 효과(같은 색, σ 번짐, 0.75)를 방사형 그라디언트로 그린다. */
private fun DrawScope.glow(cache: HashMap<Long, FloatArray>, c: Offset, r: Float, sigma: Float, color: Color, us: Float, strength: Float = 1f) {
    val key = (r * 100).toLong() * 1_000_000L + (sigma * 100).toLong()
    val profile = cache.getOrPut(key) { glowProfile(r, sigma) }
    val reach = glowReach(r, sigma) * us
    val last = profile.size - 1
    val stops = Array(profile.size) { k -> k.toFloat() / last to color.copy(alpha = GLOW_ALPHA * strength * profile[k]) }
    drawCircle(Brush.radialGradient(colorStops = stops, center = c, radius = reach), radius = reach, center = c)
}

/** 가로 가운데 맞춤, [top]은 글자 윗변 위치. */
private fun DrawScope.centered(tm: TextMeasurer, text: String, top: Offset, style: TextStyle, maxWidth: Float) {
    val r = tm.measure(text, style, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(1)))
    drawText(r, topLeft = Offset(top.x - r.size.width / 2f, top.y))
}

/** 흰 알약 말풍선 (px 12, py 6, 14 semibold). [center]는 말풍선 아래 가운데. */
private fun DrawScope.chip(tm: TextMeasurer, text: String, bottomCenter: Offset, us: Float) {
    val r = tm.measure(text, TextStyle(color = OnSpace, fontSize = max(14f * us, 13f).toSp(), fontWeight = FontWeight.SemiBold), maxLines = 1)
    val padX = 12f * us
    val padY = 6f * us
    val w = r.size.width + padX * 2
    val h = r.size.height + padY * 2
    val topLeft = Offset(bottomCenter.x - w / 2f, bottomCenter.y - h)
    drawRoundRect(Color.White, topLeft, Size(w, h), CornerRadius(h / 2f))
    drawText(r, topLeft = Offset(topLeft.x + padX, topLeft.y + padY))
}

/** Figma 아이콘(24/26/20 격자, 둥근 선 2)을 같은 경로 데이터로 그린다. */
@Composable
internal fun SvgIcon(paths: List<String>, color: Color, size: Dp, viewBox: Float, modifier: Modifier = Modifier) {
    val parsed = remember(paths) { paths.map { PathParser().parsePathString(it).toPath() } }
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        val k = this.size.width / viewBox
        withTransform({ scale(k, k, Offset.Zero) }) {
            parsed.forEach { drawPath(it, color, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
        }
    }
}

internal val ICON_NAV = listOf("M3.25 11.9167L23.8333 2.16667L14.0833 22.75L11.9167 14.0833L3.25 11.9167Z")
internal val ICON_CHEV = listOf("M7.5 15L12.5 10L7.5 5")
internal val ICON_SPROUT = listOf(
    "M7.58333 21.6667H18.4167",
    "M10.8333 21.6667C16.7917 18.9583 11.7 14.7333 14.0833 10.8333",
    "M10.2917 10.1833C11.4833 11.05 12.2417 12.5667 12.7833 14.1917C10.6167 14.625 8.99167 14.625 7.58333 13.8667C6.28333 13.2167 5.09167 11.8083 4.33333 9.31667C7.36667 8.775 9.1 9.31667 10.2917 10.1833Z",
    "M15.275 6.5C14.4491 7.79085 14.0336 9.30166 14.0833 10.8333C16.1417 10.725 17.6583 10.1833 18.7417 9.31667C19.825 8.23333 20.475 6.825 20.5833 4.33333C17.6583 4.44167 16.25 5.41667 15.275 6.5Z",
)
internal val ICON_QR = listOf(
    "M4 3H7C7.55228 3 8 3.44772 8 4V7C8 7.55228 7.55228 8 7 8H4C3.44772 8 3 7.55228 3 7V4C3 3.44772 3.44772 3 4 3Z",
    "M17 3H20C20.5523 3 21 3.44772 21 4V7C21 7.55228 20.5523 8 20 8H17C16.4477 8 16 7.55228 16 7V4C16 3.44772 16.4477 3 17 3Z",
    "M4 16H7C7.55228 16 8 16.4477 8 17V20C8 20.5523 7.55228 21 7 21H4C3.44772 21 3 20.5523 3 20V17C3 16.4477 3.44772 16 4 16Z",
    "M21 16H18C17.4696 16 16.9609 16.2107 16.5858 16.5858C16.2107 16.9609 16 17.4696 16 18V21",
    "M21 21V21.01", "M12 7V10C12 10.5304 11.7893 11.0391 11.4142 11.4142C11.0391 11.7893 10.5304 12 10 12H7",
    "M3 12H3.01", "M12 3H12.01", "M12 16V16.01", "M16 12H17", "M21 12V12.01", "M12 21V20",
)

/** 눌러 둔 장소 카드. 저장한 코스에 있으면 다시 볼 수 있다. */
@Composable
internal fun SelectedPlaceCard(
    place: PlaceNode,
    fieldLabel: String,
    hasCourse: Boolean,
    onOpenCourse: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF1B2140)).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(place.name, fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "$fieldLabel · ${place.count}번 · 마지막 ${place.last.format(MONTH_DAY)}",
                fontSize = 18.sp,
                color = Color(0xFFB9C0D8),
            )
            if (!hasCourse) Text("저장한 코스에는 없는 장소예요", fontSize = 18.sp, color = Color(0xFF9AA3BD))
        }
        if (hasCourse) {
            Button(
                onClick = onOpenCourse,
                modifier = Modifier.heightIn(min = 64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TripColors.Green),
            ) { Text("코스 다시 보기", fontSize = 22.sp) }
        }
        Box(modifier = Modifier.size(56.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Text("✕", fontSize = 22.sp, color = Color(0xFF9AA3BD))
        }
    }
}
