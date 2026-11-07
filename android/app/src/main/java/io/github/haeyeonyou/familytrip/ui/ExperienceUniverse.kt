package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.data.Visit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

internal val MONTH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("MM.dd")

/** 분야 노드 하나. 크기는 방문 횟수에 비례한다. */
internal class FieldNode(val key: String, val label: String, val color: Color, val count: Int, val last: LocalDate?)

/** 방문한 장소 노드. 같은 장소를 여러 번 가면 횟수만 늘어난다. */
internal class PlaceNode(val name: String, val field: String, val count: Int, val last: LocalDate, val latest: Boolean)

internal class UniverseData(val fields: List<FieldNode>, val places: List<PlaceNode>) {
    val isEmpty get() = places.isEmpty()
}

private val FIELD_COLORS = mapOf(
    "play" to TripColors.FieldShop,
    "exhibition" to TripColors.FieldExhibit,
    "experience" to TripColors.FieldExperience,
    "nature" to TripColors.FieldNature,
)

/** 분야 노드가 놓이는 자리 (캔버스 폭·높이의 비율). 가운데는 우리 아이. */
private val FIELD_ANCHORS = mapOf(
    "play" to Offset(0.24f, 0.30f),
    "exhibition" to Offset(0.74f, 0.30f),
    "experience" to Offset(0.72f, 0.74f),
    "nature" to Offset(0.26f, 0.74f),
)

/** 육아 편의·상황 태그 (피그마 HF04). 자리만 두고 장소와는 이어 두지 않는다. */
private val TAG_NODES = listOf(
    "수유실" to Offset(0.50f, 0.10f),
    "반려견 동반" to Offset(0.08f, 0.50f),
    "유모차" to Offset(0.93f, 0.50f),
    "비 오는 날" to Offset(0.50f, 0.86f),
)

internal fun buildUniverse(visits: List<Visit>): UniverseData {
    val outings = visits.filter { it.role == "outing" && it.theme in EXPERIENCE_FIELDS }.sortedBy { it.arrivedAt }
    val latestId = outings.lastOrNull()?.id
    val fields = EXPERIENCE_FIELDS.map { (key, label) ->
        val inField = outings.filter { it.theme == key }
        FieldNode(key, label, FIELD_COLORS.getValue(key), inField.size, inField.lastOrNull()?.date)
    }
    val places = outings.groupBy { it.theme.orEmpty() to it.placeName }.map { (key, list) ->
        PlaceNode(key.second, key.first, list.size, list.last().date, list.any { it.id == latestId })
    }.sortedByDescending { it.last }
    return UniverseData(fields, places)
}

internal class Insight(val title: String, val body: String, val action: String?)

/** 기기에 쌓인 방문으로 가장 오래 비어 있는 분야를 찾는다. */
internal fun experienceInsight(visits: List<Visit>, today: LocalDate = LocalDate.now()): Insight {
    val outings = visits.filter { it.role == "outing" && it.theme in EXPERIENCE_FIELDS }
    if (outings.isEmpty()) {
        return Insight("첫 나들이를 기록해 보세요", "나들이 장소에 도착하면 우주에 별이 하나씩 생겨요.", "코스 만들기")
    }
    val gaps = EXPERIENCE_FIELDS.map { (key, label) ->
        val last = outings.filter { it.theme == key }.maxOfOrNull { it.date }
        Triple(key, label, last?.let { ChronoUnit.DAYS.between(it, today) / 7 })
    }
    // 가장 오래 비어 있는 분야 (한 번도 안 간 곳이 먼저, 같으면 자연 먼저)
    val worst = gaps.maxWith(compareBy({ it.third ?: Long.MAX_VALUE }, { if (it.first == "nature") 1 else 0 }))
    val label = worst.second
    val weeks = worst.third
    return when {
        weeks == null -> Insight("$label 체험이 아직 없어요", "$label 나들이로 다음 영역을 넓혀 볼까요?", "코스 만들기")
        weeks >= 2 -> Insight("$label 체험이 ${weeks}주째 없어요", "$label 나들이로 비어 있는 영역을 넓혀 볼까요?", "코스 만들기")
        else -> Insight("골고루 다니고 있어요", "최근 2주 안에 모든 분야를 다녀왔어요.", null)
    }
}

private class Placed<T>(val item: T, val pos: Offset, val radius: Float)

private class UniverseLayout(
    val center: Offset,
    val centerRadius: Float,
    val fields: List<Placed<FieldNode>>,
    val places: List<Placed<PlaceNode>>,
    val tags: List<Pair<String, Offset>>,
)

private fun layoutUniverse(data: UniverseData, size: Size, dp: Float): UniverseLayout {
    val center = Offset(size.width * 0.5f, size.height * 0.5f)
    val fields = data.fields.map { f ->
        val a = FIELD_ANCHORS.getValue(f.key)
        val r = if (f.count == 0) 18f * dp else (16f + min(f.count, 8) * 4.5f) * dp
        Placed(f, Offset(size.width * a.x, size.height * a.y), r)
    }
    val places = fields.flatMap { field ->
        val mine = data.places.filter { it.field == field.item.key }.take(MAX_PLACES_PER_FIELD)
        val away = atan2(field.pos.y - center.y, field.pos.x - center.x)
        mine.mapIndexed { i, p ->
            val angle = away + (i - (mine.size - 1) / 2f) * 0.85f
            val dist = field.radius + (84f + (i % 2) * 22f) * dp
            val raw = field.pos + Offset(cos(angle) * dist, sin(angle) * dist)
            // 크기를 아직 모를 때(0)도 범위가 뒤집히지 않게 한다.
            val lo = 24f * dp
            val pos = Offset(raw.x.coerceIn(lo, max(lo, size.width - lo)), raw.y.coerceIn(lo, max(lo, size.height - lo)))
            Placed(p, pos, (8f + min(p.count, 4) * 1.5f) * dp)
        }
    }
    val tags = TAG_NODES.map { (label, a) -> label to Offset(size.width * a.x, size.height * a.y) }
    return UniverseLayout(center, 42f * dp, fields, places, tags)
}

private const val MAX_PLACES_PER_FIELD = 6
private const val MIN_SCALE = 0.6f
private const val MAX_SCALE = 3f

/**
 * 별 배경 위의 경험 우주. 핀치로 확대·축소하고 끌어서 옮기며, 장소 노드를 누르면 [onPlaceClick]을 부른다.
 * 화면에 그리는 위치 = 캔버스 위치 × scale + offset.
 */
@Composable
internal fun UniverseCanvas(
    data: UniverseData,
    centerLabel: String,
    selected: String?,
    onPlaceClick: (PlaceNode?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dp = LocalDensity.current.density
    val textMeasurer = rememberTextMeasurer()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val layout = remember(data, size) { layoutUniverse(data, Size(size.width.toFloat(), size.height.toFloat()), dp) }
    val stars = remember {
        val rnd = Random(7)
        List(120) { Triple(Offset(rnd.nextFloat(), rnd.nextFloat()), 0.8f + rnd.nextFloat() * 1.6f, 0.25f + rnd.nextFloat() * 0.6f) }
    }

    fun zoomAt(factor: Float, at: Offset) {
        val next = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        offset = at - (at - offset) * (next / scale)
        scale = next
    }
    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    Box(modifier = modifier.clipToBounds().background(TripColors.Universe)) {
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
                .pointerInput(layout) {
                    detectTapGestures { tap ->
                        val hit = layout.places.minByOrNull { hypot(tap.x - (it.pos.x * scale + offset.x), tap.y - (it.pos.y * scale + offset.y)) }
                            ?.takeIf { hypot(tap.x - (it.pos.x * scale + offset.x), tap.y - (it.pos.y * scale + offset.y)) <= max(it.radius * scale, 30f * dp) }
                        onPlaceClick(hit?.item)
                    }
                },
        ) {
            drawUniverse(layout, stars, textMeasurer, centerLabel, selected, scale, offset, dp)
        }

        UniverseLegend(data, Modifier.align(Alignment.TopStart).padding(20.dp))
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF161B2E)),
        ) {
            ZoomButton("+") { zoomAt(1.4f, Offset(size.width / 2f, size.height / 2f)) }
            ZoomButton("−") { zoomAt(1f / 1.4f, Offset(size.width / 2f, size.height / 2f)) }
            ZoomButton("⤢") { reset() }
        }
        Text(
            "핀치로 확대 · 노드를 누르면 그곳의 코스를 다시 볼 수 있어요",
            fontSize = 18.sp,
            color = Color(0xFF9AA3BD),
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
        )
    }
}

@Composable
private fun ZoomButton(text: String, onClick: () -> Unit) {
    Box(modifier = Modifier.size(64.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 28.sp, color = Color.White)
    }
}

@Composable
private fun UniverseLegend(data: UniverseData, modifier: Modifier) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFF161B2E)).padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        data.fields.forEach { f ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(f.color))
                Text(f.label, fontSize = 18.sp, color = Color(0xFFD5DAEA))
            }
        }
    }
}

private fun DrawScope.drawUniverse(
    layout: UniverseLayout,
    stars: List<Triple<Offset, Float, Float>>,
    tm: TextMeasurer,
    centerLabel: String,
    selected: String?,
    scale: Float,
    offset: Offset,
    dp: Float,
) {
    fun at(p: Offset) = p * scale + offset
    val dashed = PathEffect.dashPathEffect(floatArrayOf(10f * dp, 8f * dp))
    val labelStyle = TextStyle(color = Color(0xFFD5DAEA), fontSize = 18.sp)

    stars.forEach { (p, r, a) -> drawCircle(Color.White.copy(alpha = a), r * dp * 0.8f, Offset(p.x * size.width, p.y * size.height)) }

    // 이음선: 우리 아이 → 분야 → 장소, 태그는 희미하게
    layout.tags.forEach { (_, p) -> drawLine(TripColors.UniverseLine.copy(alpha = 0.5f), at(layout.center), at(p), 1f * dp, pathEffect = dashed) }
    layout.fields.forEach { f ->
        if (f.item.count == 0) {
            drawLine(TripColors.FieldNature.copy(alpha = 0.55f), at(layout.center), at(f.pos), 1.5f * dp, pathEffect = dashed)
        } else {
            drawLine(f.item.color.copy(alpha = 0.45f), at(layout.center), at(f.pos), 1.5f * dp)
        }
    }
    layout.places.forEach { p ->
        val field = layout.fields.first { it.item.key == p.item.field }
        drawLine(field.item.color.copy(alpha = 0.6f), at(field.pos), at(p.pos), 1.2f * dp)
    }

    // 태그
    layout.tags.forEach { (label, p) ->
        drawCircle(Color.White, 5f * dp, at(p))
        centered(tm, label, at(p) + Offset(0f, 12f * dp), labelStyle.copy(fontSize = 17.sp, color = Color(0xFFB9C0D8)))
    }

    // 분야 노드
    layout.fields.forEach { f ->
        val c = at(f.pos)
        val r = f.radius * scale
        if (f.item.count == 0) {
            drawCircle(f.item.color, r, c, style = Stroke(2f * dp, pathEffect = dashed))
            centered(tm, f.item.label, c + Offset(0f, r + 6f * dp), labelStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium))
            centered(tm, "다음에 넓어질 영역", c + Offset(0f, r + 32f * dp), labelStyle.copy(fontSize = 16.sp, color = f.item.color))
        } else {
            drawCircle(f.item.color.copy(alpha = 0.18f), r * 1.9f, c)
            drawCircle(f.item.color, r, c)
            centered(tm, "${f.item.label} · ${f.item.count}", c + Offset(0f, r + 8f * dp), labelStyle.copy(fontSize = 20.sp, color = f.item.color, fontWeight = FontWeight.Medium))
        }
    }

    // 장소 노드 (가장 최근 방문은 흰 테두리와 '방금 기록' 칩)
    layout.places.forEach { p ->
        val field = layout.fields.first { it.item.key == p.item.field }
        val c = at(p.pos)
        val r = p.radius * scale
        drawCircle(field.item.color, r, c)
        if (p.item.latest) drawCircle(Color.White, r + 6f * dp, c, style = Stroke(2.5f * dp))
        if (p.item.name == selected) drawCircle(Color.White, r + 12f * dp, c, style = Stroke(1.5f * dp, pathEffect = dashed))
        val name = if (p.item.name.length > 9) p.item.name.take(9) + "…" else p.item.name
        centered(tm, name, c + Offset(0f, r + 8f * dp), labelStyle)
        if (p.item.latest) chip(tm, "방금 기록 · ${p.item.last.format(MONTH_DAY)}", c - Offset(0f, r + 40f * dp))
    }

    // 가운데 우리 아이
    val cc = at(layout.center)
    val cr = layout.centerRadius * scale
    drawCircle(Color.White.copy(alpha = 0.15f), cr * 1.6f, cc)
    drawCircle(Color.White, cr, cc)
    val ink = TextStyle(color = TripColors.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    if (centerLabel == "우리 아이") {
        centered(tm, centerLabel, cc - Offset(0f, 13f * dp), ink)
    } else {
        centered(tm, centerLabel, cc - Offset(0f, 24f * dp), ink)
        centered(tm, "우리 아이", cc + Offset(0f, 2f * dp), ink.copy(fontSize = 15.sp, fontWeight = FontWeight.Normal, color = TripColors.InkSoft))
    }
}

/** 가로 가운데 맞춤, [top]은 글자 윗변 위치. */
private fun DrawScope.centered(tm: TextMeasurer, text: String, top: Offset, style: TextStyle) {
    val r = tm.measure(text, style)
    drawText(r, topLeft = Offset(top.x - r.size.width / 2f, top.y))
}

private fun DrawScope.chip(tm: TextMeasurer, text: String, center: Offset) {
    val r = tm.measure(text, TextStyle(color = TripColors.Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium))
    val pad = 12f * density
    val w = r.size.width + pad * 2
    val h = r.size.height + pad * 0.8f
    val topLeft = Offset(center.x - w / 2f, center.y - h / 2f)
    drawRoundRect(Color.White, topLeft, Size(w, h), CornerRadius(h / 2f))
    drawText(r, topLeft = Offset(topLeft.x + pad, topLeft.y + pad * 0.4f))
}

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
