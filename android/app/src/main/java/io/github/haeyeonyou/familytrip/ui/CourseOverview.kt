package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.haeyeonyou.familytrip.data.CourseResponse
import io.github.haeyeonyou.familytrip.data.CourseStop
import io.github.haeyeonyou.familytrip.data.FeedingRoutine
import io.github.haeyeonyou.familytrip.data.StopParking
import io.github.haeyeonyou.familytrip.data.WeatherInfo
import java.time.Instant
import java.time.ZoneId

/**
 * 코스 상세 (11/10 디자인 H02 `353:2745`)와 대체 장소 고르기 (H02-1 `353:3066`).
 * 앱 프레임 1256x886: 위에 뒤로·제목·날씨, 요약 태그 5종, 아래 왼쪽 1·2열(800)은 코스 타임라인, 3열(392)은
 * H02면 지도·도착·대안 안내·버튼 3개, ↻ 교체를 누르면(H02-1) 그 자리에 대체 후보 3곳과 취소·이걸로 바꾸기.
 * 바로가기 독은 없다(셸 규칙: 코스 상세 제외). 계산은 CourseDetailLogic.kt.
 *
 * 경유지 편집 (11/10 디자인에 따로 화면이 없어 사용자 결정 2026-11-10): `경유지 편집`을 누르면 H02-1로 바뀌어
 * 첫 경유지의 대체 후보가 열리고, 타임라인에서 장소를 누르면 그 장소의 대체 후보로 바뀐다. ✕ 삭제·≡ 순서 변경은 행 안에서.
 * 옛 편집 화면에 있던 머무름 ±30분과 장소 상세는 H02-1 패널 머리에, 장소 추가는 편집 중 타임라인 끝의 `+ 장소 추가` 줄에 둔다.
 */
@Composable
fun CourseOverview(
    course: CourseResponse,
    loading: Boolean,
    error: String?,
    message: String?,
    feeding: FeedingRoutine,
    edit: CourseEditActions,
    onStartGuide: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    weather: WeatherInfo? = null,
    going: Int = 0,
    origin: io.github.haeyeonyou.familytrip.data.LatLng? = null,
    /** 12개월 이하 아이가 있을 때만 수유 지점·수유 여유를 보인다 (#217). */
    showFeeding: Boolean = true,
) {
    val s = SitterSurface.current
    val outing = course.stops.firstOrNull { it.role == "outing" } ?: course.stops.first()
    val now = rememberNow()
    val nextFeeding = feeding.nextAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime() }
    val point = if (showFeeding) feedingPoint(course, nextFeeding) else null
    val visits = course.stops.count { it.role != "home" }
    // 오른쪽 패널: null이면 H02 상세, 아니면 H02-1(대체 후보) 또는 장소 추가. 머무름을 바꿔 코스가 새로 와도 패널은 그대로 둔다.
    var panelState by remember { mutableStateOf<CoursePanel?>(null) }
    val panel = panelState?.takeIf { it !is CoursePanel.Replace || it.stopIndex < visits }
    val swapping = (panel as? CoursePanel.Replace)?.stopIndex
    var reordering by remember(course) { mutableStateOf<Int?>(null) }
    val outingIndex = course.stops.indexOfFirst { it.role == "outing" }.takeIf { it >= 0 } ?: 0

    // 독 자리를 이 화면이 맡는다고 알려(셸의 옛 독 줄을 끄고) 아무것도 두지 않는다: H02·H02-1은 독 대신 뒤로 버튼
    SitterCanvas(ownsDock = true) {
        // 머리: 뒤로 · 제목 · 부제 · 날씨
        Row(Modifier.padding(start = 24.dp, top = 22.dp, end = 24.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(60.dp).clip(CircleShape).background(s.card).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { SitterIcon(SitterIcons.ChevronLeft, s.ink, size = SitterIconSize.Medium) }
            Column(Modifier.padding(start = 20.dp).weight(1f)) {
                Text("${outing.place.name} 나들이 코스", style = SitterText.Title1, color = s.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle(course, going), style = SitterText.Caption, color = s.inkMute, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            weather?.let { w ->
                SitterWeather(
                    w.temperature?.let { "${Math.round(it)}°" } ?: "--",
                    listOf(w.condition, w.region).filter { it.isNotBlank() }.joinToString(" · "),
                )
            }
        }
        // 요약 태그 5종 (y 118)
        Row(Modifier.padding(start = 24.dp, top = 112.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            courseTags(course).forEach { SummaryTag(it) }
        }
        // 본문 (y 168): 왼쪽 타임라인 800, 오른쪽 392
        Row(
            Modifier.padding(start = SitterGrid.MARGIN.dp, top = SitterGrid.BODY_TOP.dp, end = SitterGrid.MARGIN.dp, bottom = SitterGrid.MARGIN.dp).fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(SitterGrid.GAP.dp),
        ) {
            SitterCard(Modifier.width(SitterGrid.DOUBLE_W.dp).fillMaxHeight(), contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("코스 타임라인", style = SitterText.Title3, color = s.ink, modifier = Modifier.weight(1f))
                    Text("↻ 교체 · ✕ 삭제 · ≡ 순서 변경", style = SitterText.Caption, color = s.inkMute)
                }
                Column(Modifier.padding(top = 12.dp).verticalScroll(rememberScrollState())) {
                    TimelineRow(course.departureAt.ifBlank { "--:--" }, "출발", hollow = true)
                    course.stops.forEachIndexed { index, stop ->
                        val home = stop.role == "home"
                        TimelineRow(
                            time = stop.arriveAt,
                            title = if (home) "귀가" else stop.place.name,
                            hollow = home,
                            selected = swapping == index,
                            // 장소를 누르면 그 장소의 대체 후보(H02-1). ↻도 같다.
                            onClick = if (!home) ({ panelState = CoursePanel.Replace(index); reordering = null }) else null,
                            onSwap = if (!home && !course.alternatives[stop.role].isNullOrEmpty()) ({ panelState = CoursePanel.Replace(index); reordering = null }) else null,
                            onRemove = if (!home && visits > 1 && !loading) ({
                                if (panel != null) panelState = CoursePanel.Replace(0)
                                edit.remove(index)
                            }) else null,
                            onReorder = if (!home && visits > 1 && !loading) ({ reordering = if (reordering == index) null else index }) else null,
                            reorderOpen = reordering == index,
                            onMove = { delta ->
                                reordering = null
                                // 고르고 있던 장소가 옮겨지면 패널도 따라간다
                                if (swapping == index) panelState = CoursePanel.Replace(index + delta)
                                edit.move(index, delta)
                            },
                            canMoveUp = index > 0,
                            canMoveDown = index < visits - 1,
                        ) {
                            if (home) {
                                Text("오늘 경험이 경험 젤리에 기록돼요", style = SitterText.Caption, color = s.inkMute)
                            } else {
                                Text(stopLine(stop), style = SitterText.Caption, color = s.inkMute, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                AmenityTags(stop)
                            }
                        }
                        if (point?.afterStop == index) {
                            TimelineRow(point.time, point.title, hollow = false, highlight = true) {
                                Text(point.detail, style = SitterText.Caption, color = s.inkMute, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    // 편집 중에만: 장소 추가 (옛 편집 화면의 '장소 추가'. 코스 끝(귀가 전)에 들어간다)
                    if (panel != null && addCandidates(course).isNotEmpty()) {
                        AddRow(selected = panel == CoursePanel.Add, enabled = !loading) { panelState = CoursePanel.Add; reordering = null }
                    }
                }
            }
            Column(Modifier.width(SitterGrid.COLUMN_W.dp).fillMaxHeight()) {
                when (panel) {
                    is CoursePanel.Replace -> {
                        val target = panel.stopIndex
                        ReplacePanel(
                            course, target, origin, loading, error, message,
                            onStay = { delta -> edit.changeStay(target, delta) },
                            onCancel = { panelState = null },
                            onPick = { place ->
                                panelState = null
                                edit.swap(target, place)
                            },
                        )
                    }
                    CoursePanel.Add -> AddPanel(course, loading, error, message, onAdd = edit.add, onClose = { panelState = null })
                    null -> DetailPanel(
                        course, outing, feeding.takeIf { showFeeding }, now, loading, error, message, onStartGuide,
                        onEdit = { panelState = CoursePanel.Replace(0) },
                        onAlternative = { panelState = CoursePanel.Replace(outingIndex) },
                        onSave = onSave,
                    )
                }
            }
        }
    }
}

/** "오늘의 추천 코스 · 첫 이동 40분 · 지금 13대 가는 중" */
private fun subtitle(course: CourseResponse, going: Int): String = listOfNotNull(
    "오늘의 추천 코스",
    course.stops.firstOrNull()?.let { "첫 이동 ${it.driveMinutes}분" },
    going.takeIf { it > 0 }?.let { "지금 ${it}대 가는 중" },
    "이동 추정".takeIf { course.routeSource == "estimate" },
).joinToString(" · ")

@Composable
private fun SummaryTag(tag: CourseTag) {
    val s = SitterSurface.current
    val icon: ImageVector = when (tag.kind) {
        CourseTag.Kind.TIME -> SitterIcons.Clock
        CourseTag.Kind.NURSING -> SitterIcons.Milk
        CourseTag.Kind.STROLLER -> SitterIcons.Baby
        CourseTag.Kind.PARKING -> SitterIcons.Parking
        CourseTag.Kind.PET -> SitterIcons.Info
    }
    Row(
        Modifier.height(44.dp).clip(SitterShapes.Pill).background(s.card).border(1.dp, s.line, SitterShapes.Pill).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SitterIcon(icon, if (tag.available == false) s.inkMute else s.ink, size = SitterIconSize.Small)
        Text(tag.label, style = SitterText.Body1, color = if (tag.available == false) s.inkMute else s.ink, maxLines = 1)
    }
}

@Composable
private fun TimelineRow(
    time: String,
    title: String,
    hollow: Boolean,
    highlight: Boolean = false,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onSwap: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onReorder: (() -> Unit)? = null,
    reorderOpen: Boolean = false,
    onMove: (Int) -> Unit = {},
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    body: @Composable ColumnScope.() -> Unit = {},
) {
    val s = SitterSurface.current
    Row(
        Modifier
            .padding(vertical = 3.dp)
            .fillMaxWidth()
            .clip(SitterShapes.Row)
            .background(if (highlight || selected) s.primarySoft else Color.Transparent)
            .then(if (selected) Modifier.border(2.dp, s.primary, SitterShapes.Row) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(time, style = SitterText.Body1, color = if (highlight) s.primary else s.inkSoft, modifier = Modifier.width(64.dp))
        Box(
            Modifier.padding(top = 4.dp, end = 14.dp).size(16.dp).clip(CircleShape)
                .background(if (hollow) s.card else s.primary).border(2.dp, s.primary, CircleShape),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = SitterText.Title3, color = s.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                onSwap?.let { swap ->
                    Box(Modifier.padding(start = 6.dp).size(36.dp).clip(CircleShape).clickable(onClick = swap), contentAlignment = Alignment.Center) {
                        SitterIcon(SitterIcons.Refresh, s.inkSoft, size = SitterIconSize.Small)
                    }
                }
            }
            body()
        }
        if (reorderOpen) {
            RowAction(SitterIcons.ChevronLeft, enabled = canMoveUp, rotate = 90f) { onMove(-1) }
            RowAction(SitterIcons.ChevronLeft, enabled = canMoveDown, rotate = -90f) { onMove(1) }
        }
        onRemove?.let { RowAction(SitterIcons.X, enabled = true, onClick = it) }
        onReorder?.let { RowAction(SitterIcons.Grip, enabled = true, onClick = it) }
    }
}

@Composable
private fun RowAction(icon: ImageVector, enabled: Boolean, rotate: Float = 0f, onClick: () -> Unit) {
    val s = SitterSurface.current
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SitterIcon(icon, if (enabled) s.inkSoft else s.line, modifier = Modifier.graphicsRotate(rotate), size = SitterIconSize.Medium)
    }
}

private fun Modifier.graphicsRotate(degrees: Float): Modifier = if (degrees == 0f) this else rotate(degrees)

/** 편의 태그: 있으면 체크, '정보 없음'은 빼기 표시 (없다는 뜻은 아님). 반려견은 있을 때만. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmenityTags(stop: CourseStop) {
    val a = stop.place.amenities
    val keys = listOf("nursing_room", "stroller", "kids_chair", "pet").filter { it in a }
    if (keys.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.forEach { key ->
            val ok = a[key] == "available"
            if (key == "pet" && !ok) return@forEach
            SitterTag(tagLabel(key), available = ok)
        }
    }
}

private fun tagLabel(key: String) = when (key) {
    "nursing_room" -> "수유실"
    "stroller" -> "유모차"
    "kids_chair" -> "유아의자"
    "pet" -> "반려동물"
    else -> AMENITY_LABELS[key] ?: key
}

// ───────────── 오른쪽: H02 상세 ─────────────

@Composable
private fun ColumnScope.DetailPanel(
    course: CourseResponse,
    outing: CourseStop,
    /** null이면 수유를 보이지 않는다 (12개월 넘은 아이만, #217) */
    feeding: FeedingRoutine?,
    now: Long,
    loading: Boolean,
    error: String?,
    message: String?,
    onStartGuide: () -> Unit,
    onEdit: () -> Unit,
    onAlternative: () -> Unit,
    onSave: () -> Unit,
) {
    val s = SitterSurface.current
    CourseMap(stops = course.stops, path = course.path, modifier = Modifier.fillMaxWidth().height(330.dp).clip(SitterShapes.Card))
    Spacer(Modifier.height(14.dp))
    // 도착·수유 여유
    // 서버가 넣은 주유·충전 경유지(#215)는 '도착'의 기준이 아니다
    val first = course.stops.firstOrNull { it.role != "home" && it.role != "fuel" } ?: outing
    Row(
        Modifier.fillMaxWidth().clip(SitterShapes.Card).background(s.primarySoft).padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SitterIcon(SitterIcons.Clock, s.primary, size = SitterIconSize.Medium)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val next = feeding?.nextAt?.let { " · 다음 수유 ${io.github.haeyeonyou.familytrip.data.clockLabel(it)}" } ?: ""
            Text("도착 ${first.arriveAt}$next", style = SitterText.Title3, color = s.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (feeding == null) {
                    "${first.place.name} · ${first.driveMinutes}분 이동"
                } else {
                    feedingSlackText(feeding, first.arriveAt, now) ?: "케어에서 수유를 기록하면 도착 때 여유를 알려 드려요"
                },
                style = SitterText.Caption,
                color = s.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    // 대안 안내: 나들이 장소의 대체 후보(H02-1)를 바로 연다
    Row(
        Modifier.fillMaxWidth().clip(SitterShapes.Card).background(s.card).clickable(onClick = onAlternative).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SitterIcon(SitterIcons.Info, s.inkSoft, size = SitterIconSize.Small)
        Text("휴무·마감이면 대안 장소로 바꿔요", style = SitterText.Body2, color = s.inkSoft, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        SitterIcon(SitterIcons.ChevronRight, s.inkMute, size = SitterIconSize.Small)
    }
    Spacer(Modifier.weight(1f))
    if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
    (error ?: message)?.let {
        Text(it, style = SitterText.Caption, color = if (error != null) SitterPalette.Danger else s.primary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 8.dp))
    }
    SitterButton("코스 시작", onStartGuide, Modifier.fillMaxWidth().height(72.dp), enabled = !loading, icon = SitterIcons.Nav)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SitterButton("경유지 편집", onEdit, Modifier.weight(1f), kind = SitterButtonKind.Line, icon = SitterIcons.Pencil)
        SitterButton("코스 저장", onSave, Modifier.weight(1f), kind = SitterButtonKind.Line, icon = SitterIcons.Bookmark)
    }
}

// ───────────── 오른쪽: H02-1 대체 장소 ─────────────

@Composable
private fun ColumnScope.ReplacePanel(
    course: CourseResponse,
    stopIndex: Int,
    origin: io.github.haeyeonyou.familytrip.data.LatLng?,
    loading: Boolean,
    error: String?,
    message: String?,
    onStay: (Int) -> Unit,
    onCancel: () -> Unit,
    onPick: (io.github.haeyeonyou.familytrip.data.CoursePlace) -> Unit,
) {
    val s = SitterSurface.current
    val stop = course.stops[stopIndex]
    val candidates = remember(course, stopIndex) { replacements(course, stopIndex, origin) }
    var chosen by remember(course, stopIndex) { mutableStateOf(candidates.firstOrNull()?.place) }
    // ⓘ: 대체 후보 대신 지금 장소의 상세(사진·주소·편의 정보 원문)
    var showDetail by remember(stopIndex) { mutableStateOf(false) }
    SitterCard(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (showDetail) stop.place.name else "${stop.place.name} 대체 장소",
                style = SitterText.Title3, color = s.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(if (showDetail) s.primarySoft else Color.Transparent).clickable { showDetail = !showDetail },
                contentAlignment = Alignment.Center,
            ) { SitterIcon(SitterIcons.Info, if (showDetail) s.primary else s.inkSoft, size = SitterIconSize.Small) }
        }
        StayStepper(stop.stayMinutes, enabled = !loading, onStay = onStay)
        if (showDetail) {
            Column(Modifier.padding(top = 12.dp).weight(1f).verticalScroll(rememberScrollState())) { PlaceDetail(stop, course.stops.getOrNull(stopIndex + 1)) }
        } else {
            Text("같은 시간대 · 경로에서 가까운 순 · 수유실·유모차 조건 유지", style = SitterText.Caption, color = s.inkMute, modifier = Modifier.padding(top = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (candidates.isEmpty()) {
                    Text("지금은 바꿀 후보가 없어요. 아래 '+ 장소 추가'로 다른 장소를 넣을 수 있어요.", style = SitterText.Body2, color = s.inkSoft)
                }
                candidates.forEach { r -> CandidateCard(r, selected = chosen == r.place) { chosen = r.place } }
            }
            Spacer(Modifier.weight(1f))
        }
        PanelStatus(loading, error, message)
        Text("바꾸면 뒤 일정과 수유 지점 시간이 다시 계산돼요", style = SitterText.Caption, color = s.inkMute)
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SitterButton("취소", onCancel, Modifier.weight(1f), kind = SitterButtonKind.Line)
        SitterButton("이걸로 바꾸기", { chosen?.let(onPick) }, Modifier.weight(1f), enabled = chosen != null && !loading && !showDetail)
    }
}

/** 오른쪽 패널 종류. [Replace]는 H02-1 대체 후보(경유지 번호), [Add]는 장소 추가. */
private sealed interface CoursePanel {
    data class Replace(val stopIndex: Int) : CoursePanel
    data object Add : CoursePanel
}

private const val STAY_STEP_MINUTES = 30

/** 머무름 −30 · N분 · +30 (옛 편집 화면의 머무름 조절). 바꾸면 서버가 뒤 일정을 다시 계산한다. */
@Composable
private fun StayStepper(stayMinutes: Int, enabled: Boolean, onStay: (Int) -> Unit) {
    val s = SitterSurface.current
    Row(
        Modifier.padding(top = 10.dp).fillMaxWidth().clip(SitterShapes.Row).background(s.tile).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("머무름", style = SitterText.Body2, color = s.inkSoft, modifier = Modifier.padding(start = 8.dp).weight(1f))
        RowAction(SitterIcons.Minus, enabled = enabled && stayMinutes > STAY_STEP_MINUTES) { onStay(-STAY_STEP_MINUTES) }
        Text("${stayMinutes}분", style = SitterText.Title3, color = s.ink, modifier = Modifier.width(72.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        RowAction(SitterIcons.Plus, enabled = enabled) { onStay(STAY_STEP_MINUTES) }
    }
}

@Composable
private fun PanelStatus(loading: Boolean, error: String?, message: String?) {
    val s = SitterSurface.current
    if (loading) CircularProgressIndicator(Modifier.padding(bottom = 8.dp).size(28.dp))
    (error ?: message)?.let {
        Text(it, style = SitterText.Caption, color = if (error != null) SitterPalette.Danger else s.primary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 6.dp))
    }
}

/** 장소 상세: 사진, 주소, 분류, 코스 안에서의 위치, 편의 정보 원문 (혜연 P04, 옛 편집 화면에서 옮김). */
@Composable
private fun PlaceDetail(stop: CourseStop, next: CourseStop?) {
    val s = SitterSurface.current
    val place = stop.place
    if (place.imageUrl.isNotBlank()) {
        coil.compose.AsyncImage(
            // TourAPI 이미지는 http로 오지만 같은 주소가 https도 지원한다 (평문 통신은 막혀 있음).
            model = place.imageUrl.replaceFirst("http://", "https://"),
            contentDescription = place.name,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(150.dp).clip(SitterShapes.Row),
        )
        Spacer(Modifier.height(12.dp))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (place.address.isNotBlank()) DetailLine("주소", place.address)
        place.shortCategory()?.let { DetailLine("분류", it) }
        val position = listOfNotNull(
            "${stop.arriveAt} 도착",
            stop.stayMinutes.takeIf { it > 0 }?.let { "${it}분 머무름" },
            next?.let { "다음까지 ${it.driveMinutes}분" },
        )
        DetailLine("코스에서", position.joinToString(" · "))
        place.amenityNotes.forEach { (key, note) -> DetailLine(tagLabel(key).takeIf { key != "infant_etc" } ?: "유아 기타", note) }
        if (place.amenities.values.any { it != "available" }) {
            Text("'정보 없음'은 시설이 없다는 뜻이 아니에요. 방문 전에 확인해 주세요.", style = SitterText.Caption, color = s.inkMute)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    val s = SitterSurface.current
    Row {
        Text(label, style = SitterText.Caption, color = s.inkMute, modifier = Modifier.width(72.dp))
        Text(value, style = SitterText.Body2, color = s.ink, modifier = Modifier.weight(1f))
    }
}

/** 카카오 분류는 "음식점 > 한식"처럼 오므로 마지막 단계만 쓴다. TourAPI 분류코드는 보여 주지 않는다. */
internal fun io.github.haeyeonyou.familytrip.data.CoursePlace.shortCategory(): String? =
    category.substringAfterLast(" > ").takeIf { it.isNotBlank() && source == "kakao" }

// ───────────── 장소 추가 ─────────────

/** 타임라인 끝 `+ 장소 추가` 줄 (편집 중에만) */
@Composable
private fun AddRow(selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val s = SitterSurface.current
    Row(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clip(SitterShapes.Row)
            .background(if (selected) s.primarySoft else s.tile)
            .then(if (selected) Modifier.border(2.dp, s.primary, SitterShapes.Row) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SitterIcon(SitterIcons.Plus, s.primary, size = SitterIconSize.Medium)
        Text("장소 추가", style = SitterText.Title3, color = s.primary)
        Text("식사·나들이·카페 후보를 귀가 전에 넣어요", style = SitterText.Caption, color = s.inkMute, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ColumnScope.AddPanel(
    course: CourseResponse,
    loading: Boolean,
    error: String?,
    message: String?,
    onAdd: (String, io.github.haeyeonyou.familytrip.data.CoursePlace) -> Unit,
    onClose: () -> Unit,
) {
    val s = SitterSurface.current
    val groups = addCandidates(course)
    SitterCard(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(18.dp)) {
        Text("장소 추가", style = SitterText.Title3, color = s.ink)
        Text("귀가 전에 들어가요 · ≡로 순서를 옮길 수 있어요", style = SitterText.Caption, color = s.inkMute, modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Column(Modifier.padding(top = 12.dp).weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (groups.isEmpty()) Text("더 넣을 후보가 없어요", style = SitterText.Body2, color = s.inkSoft)
            groups.forEach { (role, places) ->
                Text(ROLE_LABELS[role] ?: role, style = SitterText.Label, color = s.primary, modifier = Modifier.padding(top = 4.dp))
                places.forEach { place ->
                    Row(
                        Modifier.fillMaxWidth().clip(SitterShapes.Row).background(s.tile).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(place.name, style = SitterText.Body1, color = s.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val keys = listOf("nursing_room", "stroller", "kids_chair").filter { place.amenities[it] == "available" }
                            val meta = listOfNotNull(place.shortCategory()) + keys.map(::tagLabel)
                            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = SitterText.Caption, color = s.inkMute, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(
                            Modifier.padding(start = 8.dp).clip(SitterShapes.Pill).background(s.card).clickable(enabled = !loading) { onAdd(role, place) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) { Text("추가", style = SitterText.Label, color = if (loading) s.inkMute else s.ink) }
                    }
                }
            }
        }
        PanelStatus(loading, error, message)
    }
    Spacer(Modifier.height(12.dp))
    SitterButton("닫기", onClose, Modifier.fillMaxWidth(), kind = SitterButtonKind.Line)
}

@Composable
private fun CandidateCard(r: Replacement, selected: Boolean, onSelect: () -> Unit) {
    val s = SitterSurface.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(SitterShapes.Row)
            .background(if (selected) s.primarySoft else s.tile)
            .then(if (selected) Modifier.border(2.dp, s.primary, SitterShapes.Row) else Modifier)
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.place.name, style = SitterText.Title3, color = s.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(replacementMeta(r), style = SitterText.Caption, color = if (selected) s.primary else s.inkSoft, maxLines = 1)
            }
            if (selected) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(s.primary), contentAlignment = Alignment.Center) {
                    SitterIcon(SitterIcons.Check, Color.White, size = SitterIconSize.Small)
                }
            } else {
                Box(
                    Modifier.clip(SitterShapes.Pill).background(s.card).clickable(onClick = onSelect).padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("선택", style = SitterText.Label, color = s.ink) }
            }
        }
        val keys = listOf("nursing_room", "stroller", "kids_chair").filter { r.place.amenities[it] == "available" }
        if (keys.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { keys.forEach { SitterTag(tagLabel(it), available = true) } }
        }
    }
}

/** "주차 공영 120면 · 30분 400원 · 도보 약 2분" — 모르는 항목은 뺀다. (안내 화면 등에서 씀) */
internal fun parkingLine(lot: StopParking): String =
    listOfNotNull(
        "주차 " + listOfNotNull(lot.kind, lot.capacity?.let { "${it}면" }).joinToString(" ").ifBlank { lot.name },
        lot.feeSummary.takeIf { it.isNotBlank() },
        "도보 약 ${lot.walkMinutes}분",
    ).joinToString(" · ")

/** 이름과, 도착 시각에 운영하지 않을 때의 경고. 운영시간을 모르면 아무 말도 하지 않는다. */
internal fun parkingNote(lot: StopParking): String? =
    when (lot.openAtArrival) {
        false -> "${lot.name} · 도착 때 운영 안 함 (${lot.hours})"
        else -> lot.name
    }
