package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.haeyeonyou.familytrip.data.CoursePlace
import io.github.haeyeonyou.familytrip.data.CourseResponse
import io.github.haeyeonyou.familytrip.data.LatLng
import io.github.haeyeonyou.familytrip.data.WeatherInfo
import io.github.haeyeonyou.familytrip.guide.CourseEnd
import io.github.haeyeonyou.familytrip.guide.GuideStage
import io.github.haeyeonyou.familytrip.guide.GuideState
import io.github.haeyeonyou.familytrip.guide.ProgressPoint
import io.github.haeyeonyou.familytrip.guide.departLine
import io.github.haeyeonyou.familytrip.guide.departPlan
import io.github.haeyeonyou.familytrip.guide.extraCandidates
import io.github.haeyeonyou.familytrip.guide.finishedSummary
import io.github.haeyeonyou.familytrip.guide.progressPoints
import io.github.haeyeonyou.familytrip.guide.remainingStops
import io.github.haeyeonyou.familytrip.guide.stageOf
import io.github.haeyeonyou.familytrip.guide.visitedStops
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** H03·H03-1의 오른쪽 고르기 패널: 다음 장소 바꾸기(H02-1) 또는 한 곳 더 가기. 음성 "바꿔줘"·"한 곳 더"로도 연다. */
enum class GuidePicker { SWAP, MORE }

/** 코스 진행 화면의 동작 묶음. */
class GuideActions(
    val onBack: () -> Unit,
    val onShowMap: () -> Unit,
    val onMarkArrived: () -> Unit,
    val onNext: () -> Unit,
    val onSwap: (CoursePlace) -> Unit,
    val onAddExtra: (String, CoursePlace) -> Unit,
    val onGoHome: () -> Unit,
    val onEndHere: () -> Unit,
    val onShowJelly: () -> Unit,
    val onHome: () -> Unit,
    val onPicker: (GuidePicker?) -> Unit,
)

/**
 * 코스 진행 (Figma ⑥ `420:13226`, 결정 QC1 단독 화면): 가는 중 · H03 경유지 도착 `420:13227` · H03-1 마지막 장소 `420:13357` ·
 * H04 코스 종료 `420:13474`. 안내 중에는 내장 내비가 앞에 있고, 도착하면 헤드업 알림이 뜬다. 이 화면은 알림이나 앱으로 돌아왔을 때 보인다.
 * 주행 중에도 버튼은 그대로 둔다(동승자 조작, 사용자 결정). 계산은 guide/CourseProgress.kt.
 *
 * 앱 프레임 1256x886: 위 뒤로·코스 이름·진행 상태·날씨, 왼쪽 넓은 카드(800)는 도착 안내·진행 줄·다음 행동, 오른쪽(392)은 지도·남은 일정
 * (고르는 중이면 H02-1 후보 패널). [여기서 코스 끝내기]는 Figma에 자리가 없어 왼쪽 카드 오른쪽 아래 텍스트 버튼(QC2 제안).
 */
@Composable
fun GuideScreen(
    state: GuideState,
    course: CourseResponse?,
    actions: GuideActions,
    picker: GuidePicker?,
    loading: Boolean = false,
    error: String? = null,
    weather: WeatherInfo? = null,
    origin: LatLng? = null,
    homeRemainingSeconds: Int? = null,
) {
    if (!state.active) return
    val s = SitterSurface.current
    val stage = stageOf(state)
    // 안내 중인 코스와 화면 코스가 같을 때만 후보·경로를 쓴다 (다른 코스를 열어 둔 경우 대비).
    val sameCourse = course?.takeIf { c -> c.stops.map { it.place.sourceId } == state.stops.map { it.place.sourceId } }
    val places = state.stops.count { it.role != "home" }
    val number = (state.index ?: 0) + 1
    val home = state.stops.lastOrNull { it.role == "home" }?.place?.let { LatLng(it.latitude, it.longitude) }

    SitterCanvas(ownsDock = true) {
        Row(Modifier.padding(start = 24.dp, top = 22.dp, end = 24.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(60.dp).clip(CircleShape).background(s.card).clickable(onClick = actions.onBack),
                contentAlignment = Alignment.Center,
            ) { SitterIcon(SitterIcons.ChevronLeft, s.ink, size = SitterIconSize.Medium) }
            Column(Modifier.padding(start = 20.dp).weight(1f)) {
                Text(state.title, style = SitterText.Title1, color = s.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = when {
                    stage != GuideStage.FINISHED -> "코스 진행 중 · ${number.coerceAtMost(places)}/$places 장소"
                    state.endedBy == CourseEnd.HOME -> "코스 종료 · 귀가 선택"
                    else -> "코스 종료 · 여기서 끝냄"
                }
                Text(sub, style = SitterText.Caption, color = s.inkMute, maxLines = 1)
            }
            weather?.let { w ->
                SitterWeather(
                    w.temperature?.let { "${Math.round(it)}°" } ?: "--",
                    listOf(w.condition, w.region).filter { it.isNotBlank() }.joinToString(" · "),
                )
            }
        }
        Row(
            Modifier.padding(start = SitterGrid.MARGIN.dp, top = 120.dp, end = SitterGrid.MARGIN.dp, bottom = SitterGrid.MARGIN.dp).fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(SitterGrid.GAP.dp),
        ) {
            if (stage == GuideStage.FINISHED) {
                FinishedCard(state, Modifier.width(SitterGrid.DOUBLE_W.dp).fillMaxHeight())
                Column(Modifier.width(SitterGrid.COLUMN_W.dp).fillMaxHeight()) { FinishedSide(state, homeRemainingSeconds, actions) }
            } else {
                SitterCard(Modifier.width(SitterGrid.DOUBLE_W.dp).fillMaxHeight(), contentPadding = PaddingValues(start = 32.dp, top = 28.dp, end = 32.dp, bottom = 20.dp)) {
                    if (stage == GuideStage.HEADING) Heading(state, actions) else Arrived(state, stage, sameCourse, picker, loading, actions)
                }
                Column(Modifier.width(SitterGrid.COLUMN_W.dp).fillMaxHeight()) {
                    val current = state.current
                    when {
                        picker == GuidePicker.SWAP && sameCourse != null && state.nextVisit != null -> {
                            val target = (state.index ?: 0) + 1
                            val candidates = remember(sameCourse, target) { replacements(sameCourse, target, origin).map { ROLE_KEEP to it } }
                            PickerPanel("${state.nextVisit?.place?.name} 대체 장소", "같은 시간대 · 경로에서 가까운 순 · 수유실·유모차 조건 유지", candidates, "이걸로 바꾸기", loading, error, actions) { _, p -> actions.onSwap(p) }
                        }
                        picker == GuidePicker.MORE && sameCourse != null && current != null -> {
                            val candidates = remember(sameCourse, current) { extraCandidates(sameCourse, current, home) }
                            PickerPanel("근처 추천 장소", "지금 장소에서 가깝고 귀가 길에서 덜 돌아가는 순 · 귀가 전에 들어가요", candidates, "여기 들르기", loading, error, actions, actions.onAddExtra)
                        }
                        else -> MapAndRemaining(state, sameCourse, error)
                    }
                }
            }
        }
    }
}

private const val ROLE_KEEP = ""

// ───────────── 가는 중 ─────────────

@Composable
private fun ColumnScope.Heading(state: GuideState, actions: GuideActions) {
    val s = SitterSurface.current
    val stop = state.current ?: return
    val home = stop.role == "home"
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(if (home) "귀가 중" else "${(state.index ?: 0) + 1}번째 장소로 가는 중", strong = true)
        Chip("Pleos 내비로 안내 중이에요")
    }
    Text("${stop.place.name}(으)로 가는 중", style = SitterText.Title1, color = s.ink, modifier = Modifier.padding(top = 18.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text("${stop.arriveAt} 도착 예정 · 이동 ${stop.driveMinutes}분", style = SitterText.Body2, color = s.inkSoft, modifier = Modifier.padding(top = 12.dp))
    ProgressLine(progressPoints(state), Modifier.padding(top = 28.dp))
    Spacer(Modifier.weight(1f))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        SitterButton("지도 보기", actions.onShowMap, Modifier.weight(1f).height(72.dp), icon = SitterIcons.Nav)
        // 근접 판정이 안 될 때를 위한 수동 도착
        SitterButton("도착했어요", actions.onMarkArrived, Modifier.weight(1f).height(72.dp), kind = SitterButtonKind.Line, icon = SitterIcons.Check)
    }
    BottomRow(hint = null, actions)
}

// ───────────── H03 · H03-1 도착 ─────────────

@Composable
private fun ColumnScope.Arrived(
    state: GuideState,
    stage: GuideStage,
    course: CourseResponse?,
    picker: GuidePicker?,
    loading: Boolean,
    actions: GuideActions,
) {
    val s = SitterSurface.current
    val stop = state.current ?: return
    val last = stage == GuideStage.LAST_STOP
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(if (last) "마지막 장소 도착" else "${(state.index ?: 0) + 1}번째 장소 도착", strong = true)
        Chip("내비 안내가 끝나 시터노트로 돌아왔어요")
    }
    Text("${stop.place.name}에 도착했어요", style = SitterText.Title1, color = s.ink, modifier = Modifier.padding(top = 18.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    val arrivedAt = state.arrivedAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime() } ?: LocalTime.now()
    departPlan(state, arrivedAt)?.let { plan ->
        Text(departLine(plan, last), style = SitterText.Body2, color = if (plan.delayMinutes > 0 && !last) SitterPalette.Danger else s.inkSoft, modifier = Modifier.padding(top = 12.dp))
    }
    ProgressLine(progressPoints(state), Modifier.padding(top = 28.dp))
    Spacer(Modifier.weight(1f))
    Text("다음엔 어떻게 할까요?", style = SitterText.Title3, color = s.ink, modifier = Modifier.padding(bottom = 14.dp))
    val home = state.stops.lastOrNull()?.takeIf { it.role == "home" }
    val homeLine = "우리 집 · ${home?.driveMinutes ?: "--"}분 · 코스 끝"
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!last) {
            val next = state.nextVisit
            ChoiceCard(SitterIcons.Nav, "다음 장소로", "${next?.place?.name} · ${next?.driveMinutes}분 거리", primary = picker == null, enabled = !loading, onClick = actions.onNext)
            ChoiceCard(
                SitterIcons.Pencil, "장소 바꾸기", "${next?.place?.name} 대신 다른 곳",
                primary = picker == GuidePicker.SWAP, enabled = !loading && course != null && !course.alternatives[next?.role].isNullOrEmpty(),
            ) { actions.onPicker(if (picker == GuidePicker.SWAP) null else GuidePicker.SWAP) }
            ChoiceCard(SitterIcons.Home, "귀가", homeLine, primary = false, enabled = !loading, onClick = actions.onGoHome)
        } else {
            ChoiceCard(SitterIcons.Home, "귀가", homeLine, primary = picker == null, enabled = !loading, onClick = actions.onGoHome)
            ChoiceCard(
                SitterIcons.Plus, "한 곳 더 가기", "근처 추천 장소 고르기",
                primary = picker == GuidePicker.MORE, enabled = !loading && course != null && addCandidates(course).isNotEmpty(),
            ) { actions.onPicker(if (picker == GuidePicker.MORE) null else GuidePicker.MORE) }
        }
    }
    BottomRow(hint = if (last) "“집에 가자” “한 곳 더”로 답해도 돼요" else "“다음 장소 가자” “바꿔줘” “집에 가자”로 답해도 돼요", actions)
}

/** 아래 줄: 음성 안내 · 오른쪽 [여기서 코스 끝내기] 텍스트 버튼 (QC2). */
@Composable
private fun BottomRow(hint: String?, actions: GuideActions) {
    val s = SitterSurface.current
    Row(Modifier.padding(top = 14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (hint != null) {
            SitterIcon(SitterIcons.Mic, s.inkMute, size = SitterIconSize.Small)
            Text(hint, style = SitterText.Caption, color = s.inkMute, modifier = Modifier.padding(start = 8.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Spacer(Modifier.weight(1f))
        }
        Text(
            "여기서 코스 끝내기",
            style = SitterText.Label,
            color = s.inkSoft,
            modifier = Modifier.clip(SitterShapes.Pill).clickable(onClick = actions.onEndHere).padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun Chip(text: String, strong: Boolean = false) {
    val s = SitterSurface.current
    Text(
        text,
        style = SitterText.Label,
        color = if (strong) s.primary else s.inkSoft,
        modifier = Modifier.clip(SitterShapes.Pill).background(if (strong) s.primarySoft else s.tile).padding(horizontal = 12.dp, vertical = 6.dp),
        maxLines = 1,
    )
}

@Composable
private fun RowScope.ChoiceCard(icon: ImageVector, title: String, sub: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val s = SitterSurface.current
    Column(
        Modifier
            .weight(1f)
            .height(180.dp)
            .clip(SitterShapes.Row)
            .background(if (primary) s.primarySoft else s.card)
            .border(if (primary) 2.dp else 1.5.dp, if (primary) s.primary else s.line, SitterShapes.Row)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(24.dp),
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(if (primary) s.primary else s.tile),
            contentAlignment = Alignment.Center,
        ) { SitterIcon(icon, if (primary) Color.White else if (enabled) s.inkSoft else s.inkMute, size = SitterIconSize.Medium) }
        Spacer(Modifier.weight(1f))
        Text(title, style = SitterText.Title2, color = if (!enabled) s.inkMute else if (primary) s.primary else s.ink)
        Text(sub, style = SitterText.Body2, color = if (primary) s.primary else s.inkSoft, modifier = Modifier.padding(top = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 진행 줄: 출발 — 장소 — 귀가 (점 · 이름 · 시각), 지금 위치 강조. */
@Composable
private fun ProgressLine(points: List<ProgressPoint>, modifier: Modifier = Modifier) {
    val s = SitterSurface.current
    Row(
        modifier.fillMaxWidth().clip(SitterShapes.Row).background(s.tile).padding(vertical = 22.dp, horizontal = 8.dp),
    ) {
        points.forEachIndexed { i, p ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val filled = p.status != ProgressPoint.Status.UPCOMING
                Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    // 앞 점과 잇는 선 (앞 점까지 왔으면 진하게)
                    Box(Modifier.weight(1f).height(3.dp).background(if (i == 0) Color.Transparent else if (filled) s.primary else s.line))
                    Box(
                        Modifier
                            .size(if (p.status == ProgressPoint.Status.CURRENT) 22.dp else 20.dp)
                            .clip(CircleShape)
                            .background(if (filled) s.primary else s.card)
                            .border(2.dp, if (filled) s.primary else s.line, CircleShape),
                    )
                    val nextFilled = points.getOrNull(i + 1)?.status?.let { it != ProgressPoint.Status.UPCOMING } ?: false
                    Box(Modifier.weight(1f).height(3.dp).background(if (i == points.lastIndex) Color.Transparent else if (nextFilled) s.primary else s.line))
                }
                val current = p.status == ProgressPoint.Status.CURRENT
                Text(p.label, style = SitterText.Label, color = if (current) s.primary else s.ink, modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(p.time, style = SitterText.Caption, color = s.inkMute, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

// ───────────── 오른쪽: 지도 · 남은 일정 / 후보 ─────────────

@Composable
private fun ColumnScope.MapAndRemaining(state: GuideState, course: CourseResponse?, error: String?) {
    val s = SitterSurface.current
    CourseMap(stops = state.stops, path = course?.path.orEmpty(), modifier = Modifier.fillMaxWidth().height(330.dp).clip(SitterShapes.Card))
    Spacer(Modifier.height(16.dp))
    SitterCard(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(28.dp)) {
        Text("남은 일정", style = SitterText.Title3, color = s.ink)
        Column(Modifier.padding(top = 18.dp).weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            remainingStops(state).forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.time, style = SitterText.Body2, color = s.inkMute, modifier = Modifier.width(64.dp))
                    Text(r.name, style = SitterText.Body1, color = s.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (r.stayMinutes > 0) Text("${r.stayMinutes}분", style = SitterText.Body2, color = s.inkMute)
                }
            }
        }
        error?.let { Text(it, style = SitterText.Caption, color = SitterPalette.Danger, maxLines = 2, modifier = Modifier.padding(bottom = 6.dp)) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SitterIcon(SitterIcons.Info, s.inkMute, size = SitterIconSize.Small)
            Text("바꾼 일정은 웹앱에도 바로 반영돼요", style = SitterText.Caption, color = s.inkMute)
        }
    }
}

@Composable
private fun ColumnScope.PickerPanel(
    title: String,
    caption: String,
    candidates: List<Pair<String, Replacement>>,
    confirm: String,
    loading: Boolean,
    error: String?,
    actions: GuideActions,
    onPick: (String, CoursePlace) -> Unit,
) {
    val s = SitterSurface.current
    var chosen by remember(candidates) { mutableStateOf(candidates.firstOrNull()) }
    SitterCard(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(18.dp)) {
        Text(title, style = SitterText.Title3, color = s.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(caption, style = SitterText.Caption, color = s.inkMute, modifier = Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Column(Modifier.padding(top = 12.dp).weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (candidates.isEmpty()) Text("지금은 고를 후보가 없어요", style = SitterText.Body2, color = s.inkSoft)
            candidates.forEach { c -> CandidateCard(c.second, selected = chosen?.second?.place == c.second.place) { chosen = c } }
        }
        if (loading) CircularProgressIndicator(Modifier.padding(bottom = 8.dp).size(28.dp))
        error?.let { Text(it, style = SitterText.Caption, color = SitterPalette.Danger, maxLines = 2, modifier = Modifier.padding(bottom = 6.dp)) }
        Text("고르면 뒤 일정을 다시 계산하고 그곳으로 안내해요", style = SitterText.Caption, color = s.inkMute)
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SitterButton("취소", { actions.onPicker(null) }, Modifier.weight(1f), kind = SitterButtonKind.Line)
        SitterButton(confirm, { chosen?.let { onPick(it.first, it.second.place) } }, Modifier.weight(1f), enabled = chosen != null && !loading)
    }
}

// ───────────── H04 코스 종료 ─────────────

@Composable
private fun FinishedCard(state: GuideState, modifier: Modifier) {
    val s = SitterSurface.current
    val visited = visitedStops(state)
    SitterCard(modifier, contentPadding = PaddingValues(start = 32.dp, top = 32.dp, end = 32.dp, bottom = 32.dp)) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(SitterPalette.Gradient), contentAlignment = Alignment.Center) {
            SitterIcon(SitterIcons.Check, Color.White, size = SitterIconSize.Large)
        }
        Text("오늘 코스를 마쳤어요", style = SitterText.Title1, color = s.ink, modifier = Modifier.padding(top = 24.dp))
        Text(finishedSummary(state), style = SitterText.Body1, color = s.inkSoft, modifier = Modifier.padding(top = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Column(
            Modifier.padding(top = 24.dp).fillMaxWidth().clip(SitterShapes.Row).background(s.tile).padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            if (visited.isEmpty()) Text("다녀온 장소가 없어요", style = SitterText.Body2, color = s.inkSoft, modifier = Modifier.padding(vertical = 14.dp))
            visited.forEach { stop ->
                Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(s.primary), contentAlignment = Alignment.Center) {
                        SitterIcon(SitterIcons.Check, Color.White, size = SitterIconSize.Small)
                    }
                    Text(stop.arriveAt, style = SitterText.Body2, color = s.inkMute, modifier = Modifier.padding(start = 14.dp).width(64.dp))
                    Text(stop.place.name, style = SitterText.Body1, color = s.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (stop.stayMinutes > 0) Text("${stop.stayMinutes}분", style = SitterText.Body2, color = s.inkMute)
                }
            }
        }
        if (state.endedBy == CourseEnd.HERE) {
            Text("‘여기서 코스 끝내기’를 골라 귀가 안내 없이 여기서 마쳤어요", style = SitterText.Caption, color = s.inkMute, modifier = Modifier.padding(top = 12.dp))
        }
        Spacer(Modifier.weight(1f))
        // 경험 젤리 요약 (F-6: '경험 나무' 대신 '경험 젤리')
        Row(
            Modifier.fillMaxWidth().clip(SitterShapes.Row).border(1.5.dp, s.line, SitterShapes.Row).padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(s.primarySoft), contentAlignment = Alignment.Center) {
                SitterIcon(SitterIcons.Sprout, s.primary, size = SitterIconSize.Medium)
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text(
                    if (visited.isEmpty()) "이번에는 쌓인 경험 젤리가 없어요" else "경험 젤리 ${visited.size}개가 자랐어요",
                    style = SitterText.Title3, color = s.ink,
                )
                Text("그로우에서 오늘 다녀온 곳을 다시 볼 수 있어요", style = SitterText.Caption, color = s.inkMute, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun ColumnScope.FinishedSide(state: GuideState, homeRemainingSeconds: Int?, actions: GuideActions) {
    val s = SitterSurface.current
    if (state.endedBy == CourseEnd.HOME) {
        val home = state.stops.lastOrNull { it.role == "home" }
        val arrivedHome = state.arrived && state.current?.role == "home"
        val minutes = homeRemainingSeconds?.let { (it + 59) / 60 } ?: home?.driveMinutes
        val eta = minutes?.let { rememberNowTime().plusMinutes(it.toLong()) }
        Column(
            Modifier.fillMaxWidth().clip(SitterShapes.Card).background(SitterPalette.Gradient).clickable(onClick = actions.onShowMap).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SitterIcon(if (arrivedHome) SitterIcons.Home else SitterIcons.Nav, Color.White, size = SitterIconSize.Medium)
                Text(if (arrivedHome) "집에 도착했어요" else "우리 집으로 안내 중", style = SitterText.Title2, color = Color.White)
            }
            if (!arrivedHome) {
                Text(listOfNotNull(minutes?.let { "${it}분" }, eta?.let { "%02d:%02d 도착 예정".format(it.hour, it.minute) }).joinToString(" · "), style = SitterText.Body1, color = Color.White)
                Text("Pleos 내비로 안내를 넘겼어요", style = SitterText.Caption, color = Color.White)
            } else {
                Text("수고하셨어요", style = SitterText.Body1, color = Color.White)
            }
        }
    }
    Spacer(Modifier.weight(1f))
    SitterButton("경험 젤리 보기", actions.onShowJelly, Modifier.fillMaxWidth().height(68.dp), kind = SitterButtonKind.Line)
    Spacer(Modifier.height(12.dp))
    SitterButton("홈으로", actions.onHome, Modifier.fillMaxWidth().height(68.dp))
}

@Composable
private fun rememberNowTime(): LocalTime {
    val now = rememberNow()
    return Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalTime()
}
