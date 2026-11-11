package io.github.haeyeonyou.familytrip.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.haeyeonyou.familytrip.data.Child
import io.github.haeyeonyou.familytrip.data.SavedCourse
import io.github.haeyeonyou.familytrip.data.Visit
import io.github.haeyeonyou.familytrip.data.WeatherInfo
import io.github.haeyeonyou.familytrip.grow.GrowVoiceBus
import io.github.haeyeonyou.familytrip.grow.JellyKind
import io.github.haeyeonyou.familytrip.grow.JellyStage
import io.github.haeyeonyou.familytrip.grow.JellyState
import io.github.haeyeonyou.familytrip.grow.MakeJellyRow
import io.github.haeyeonyou.familytrip.grow.MakeTag
import io.github.haeyeonyou.familytrip.grow.Revisit
import io.github.haeyeonyou.familytrip.grow.UnvisitedPick
import io.github.haeyeonyou.familytrip.grow.agoText
import io.github.haeyeonyou.familytrip.grow.birthBubble
import io.github.haeyeonyou.familytrip.grow.experiences
import io.github.haeyeonyou.familytrip.grow.jellySubtitle
import io.github.haeyeonyou.familytrip.grow.jellyStates
import io.github.haeyeonyou.familytrip.grow.kindPlaces
import io.github.haeyeonyou.familytrip.grow.kindsInMonth
import io.github.haeyeonyou.familytrip.grow.latestBirth
import io.github.haeyeonyou.familytrip.grow.makeJellyRows
import io.github.haeyeonyou.familytrip.grow.monthDeltaText
import io.github.haeyeonyou.familytrip.grow.monthTile
import io.github.haeyeonyou.familytrip.grow.pendingBirths
import io.github.haeyeonyou.familytrip.grow.stageName
import io.github.haeyeonyou.familytrip.grow.unvisitedTargets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * 그로우 G01 "경험 젤리"(354:3033, #216): 다녀온 곳(10분 이상 머문 방문)을 12분류 젤리로 모아 방울 → 몽글 → 캐릭터로 키운다.
 * 가장 많이 간 분야를 가운데 크게, 나머지는 궤도 위에. 오른쪽 위 "월별 보기"는 G03 월별 캘린더(`ExperienceCalendar.kt`).
 * 계산은 `grow/JellyModel.kt`·`grow/GrowJelly.kt`, 배치는 `ExperienceLayout.kt`.
 * 캐릭터가 태어나면 정차 중에는 축하 시트, 주행 중에는 음성 1회(MainActivity)로 알린다.
 */
@Composable
fun ExperienceScreen(
    visits: List<Visit>,
    courses: List<SavedCourse>,
    children: List<Child>,
    weather: WeatherInfo?,
    celebrated: Set<String>,
    onCelebrated: (List<JellyKind>) -> Unit,
    onOpenCourse: (SavedCourse) -> Unit,
    onRoute: (name: String, latitude: Double, longitude: Double) -> Unit,
    onPlan: () -> Unit,
    /** 안가봄 줄: 빈 분야 근처 장소 검색 1회 (QF1 ③). 실패하면 null. */
    searchUnvisited: suspend (targets: List<JellyKind>, visitedNames: Set<String>) -> UnvisitedPick? = { _, _ -> null },
) = SitterCanvas {
    val today = remember(visits) { LocalDate.now() }
    val list = remember(visits) { experiences(visits, LocalDateTime.now()) }
    val states = remember(list) { jellyStates(list, today) }
    val orbit = remember(states) { computeJellyOrbit(states) }
    val child = children.firstOrNull()
    val name = child?.name?.takeIf { it.isNotBlank() }
    val thisMonth = remember(today) { YearMonth.from(today) }
    var filter by remember { mutableStateOf(GrowFilter.All) }
    var sheetKind by remember { mutableStateOf<JellyKind?>(null) }
    var sheetPlace by remember { mutableStateOf<String?>(null) }
    var calendar by remember { mutableStateOf<CalendarView?>(null) }
    val driving = LocalDriveMode.current.isDriving
    val births = remember(states, celebrated) { pendingBirths(states, celebrated) }

    // 안가봄 줄: 빈 분야가 바뀔 때만 1번 찾는다
    val targets = remember(states) { unvisitedTargets(states) }
    var pick by remember { mutableStateOf<UnvisitedPick?>(null) }
    LaunchedEffect(targets) {
        pick = if (targets.isEmpty()) null else searchUnvisited(targets, list.map { it.visit.placeName }.toSet())
    }

    // 음성 명령(#185, #216): "지난달 젤리 보여줘" → G03 그 달, "유니콘 젤리 보여줘" → 분야 카드, "○○ 다시 가자" → 그 장소 분야 카드
    val voiceView by GrowVoiceBus.view.collectAsState()
    LaunchedEffect(voiceView) {
        val v = GrowVoiceBus.consume() ?: return@LaunchedEffect
        if (v.opensCalendar) {
            calendar = CalendarView.Month(v.period!!.month(today))
            return@LaunchedEffect
        }
        calendar = null
        filter = GrowFilter(kind = v.kind, month = v.period?.month(today))
        val placeKind = v.place?.let { p -> list.lastOrNull { it.visit.placeName == p }?.kind }
        sheetKind = placeKind ?: v.kind?.takeIf { v.period == null }
        sheetPlace = v.place
    }

    fun courseWith(placeName: String) = courses.firstOrNull { c -> c.course.stops.any { it.place.name == placeName } }

    val weatherSlot: @Composable () -> Unit = {
        weather?.let { SitterWeather(it.temperature?.let { t -> "${Math.round(t)}°" } ?: "--", listOf(it.condition, it.region).filter(String::isNotBlank).joinToString(" · ")) }
    }

    calendar?.let { view ->
        BackHandler { calendar = null }
        ExperienceCalendar(
            list = list,
            today = today,
            view = view,
            avatarLabel = name?.take(1),
            weather = weatherSlot,
            onView = { calendar = it },
            onBack = { calendar = null },
            onRoute = onRoute,
        )
        births.firstOrNull()?.takeIf { !driving }?.let { kind ->
            BirthSheet(kind, name ?: "우리 아이", onDone = { onCelebrated(listOf(kind)) })
        }
        return@SitterCanvas
    }

    SitterVoiceBar(hint = "무엇을 볼까요?  “유니콘 젤리 보여줘”", avatarLabel = name?.take(1), weather = weatherSlot)
    SitterChipRow {
        SitterChip("전체", filter == GrowFilter.All, onClick = { filter = GrowFilter.All })
        // 분야 칩은 개수 순 (음성으로 고른 분야가 밖이어도 보인다). 아이 칩은 넣지 않는다 (아이는 음성 줄 아바타).
        val top = rankByCount(states).take(5).map { it.kind }
        (top + listOfNotNull(filter.kind?.takeIf { it !in top })).forEach { kind ->
            val on = filter.kind == kind
            val count = states.first { it.kind == kind }.count
            SitterChip("${kind.label} $count", on, onClick = { filter = filter.copy(kind = if (on) null else kind) })
        }
        SitterChip("이번 달", filter.month == thisMonth, onClick = {
            filter = filter.copy(month = if (filter.month == thisMonth) null else thisMonth)
        })
        // 지난달은 음성으로 골랐을 때만 칩이 생긴다 (누르면 해제)
        filter.month?.takeIf { it != thisMonth }?.let { m ->
            SitterChip(if (m == thisMonth.minusMonths(1)) "지난달" else "${m.monthValue}월", true, onClick = { filter = filter.copy(month = null) })
        }
    }
    // 칩 거르기: 고른 분야·달에 경험이 있는 분야만 또렷하게
    val visible = remember(filter, list) {
        when {
            filter == GrowFilter.All -> null
            filter.month == null -> setOfNotNull(filter.kind)
            else -> list.filter { YearMonth.from(it.date) == filter.month && (filter.kind == null || it.kind == filter.kind) }.map { it.kind }.toSet()
        }
    }
    SitterWideAndColumn(
        wide = {
            val bubble = remember(list) { latestBirth(list)?.let { (k, d) -> k to birthBubble(k, d) } }
            SitterCard(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp)) {
                Text(if (name == null) "우리 아이 경험 젤리" else "${name}의 경험 젤리", style = SitterText.Title2, color = SitterSurface.current.ink, maxLines = 1)
                Text(
                    jellySubtitle(list.size, states),
                    style = SitterText.Caption,
                    color = SitterSurface.current.inkMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp)) {
                    JellyOrbitCanvas(
                        orbit = orbit,
                        visibleKinds = visible,
                        bubble = bubble,
                        onHit = { kind ->
                            sheetKind = kind
                            sheetPlace = null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(
                    if (list.isEmpty()) "나들이 장소에 10분 이상 머물면 그 분야 젤리가 생겨요" else "젤리를 누르면 그 분야 카드와 다녀온 코스를 볼 수 있어요",
                    style = SitterText.Caption,
                    color = SitterSurface.current.inkMute,
                    maxLines = 1,
                )
            }
        },
        third = {
            MonthCard(
                list = list,
                today = today,
                onCalendar = { calendar = CalendarView.Month(thisMonth) },
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
            Spacer(Modifier.height(16.dp))
            MakeJellyCard(
                rows = remember(list, states, pick) { makeJellyRows(list, states, pick) },
                onRoute = onRoute,
                onPlan = onPlan,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            ShortcutDock()
        },
    )

    sheetKind?.let { k ->
        BackHandler { sheetKind = null }
        KindSheet(
            state = states.first { it.kind == k },
            places = remember(list, k) { kindPlaces(list, k) },
            highlight = sheetPlace,
            today = today,
            courseFor = ::courseWith,
            onRoute = onRoute,
            onOpenCourse = onOpenCourse,
            onPlan = onPlan,
            onClose = {
                sheetKind = null
                sheetPlace = null
            },
        )
    }
    births.firstOrNull()?.takeIf { !driving }?.let { kind ->
        BirthSheet(kind, name ?: "우리 아이", onDone = { onCelebrated(listOf(kind)) })
    }
}

/** 오른쪽 위 (G-3): 이번 달 N곳 · 지난달 대비 · 월별 보기 › · 이번 달 만난 젤리. */
@Composable
private fun MonthCard(list: List<io.github.haeyeonyou.familytrip.grow.Experience>, today: LocalDate, onCalendar: () -> Unit, modifier: Modifier) {
    val s = SitterSurface.current
    val month = remember(list) { monthTile(list, today) }
    val met = remember(list) { kindsInMonth(list, YearMonth.from(today)) }
    val states = remember(list) { jellyStates(list, today).associateBy { it.kind } }
    SitterCard(modifier, contentPadding = PaddingValues(20.dp)) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text("이번 달", style = SitterText.Caption, color = s.inkSoft)
                Text("${month.places}곳", style = SitterText.Number, color = s.ink)
                Text(monthDeltaText(month.delta), style = SitterText.Caption, color = s.primary, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.height(44.dp).clip(SitterShapes.Pill).background(s.tile).clickable(role = Role.Button, onClick = onCalendar)
                        .padding(start = 16.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("월별 보기", style = SitterText.Label, color = s.ink)
                    SitterIcon(SitterIcons.ChevronRight, s.ink, size = SitterIconSize.Small)
                }
            }
            Column(
                Modifier.width(176.dp).fillMaxHeight().clip(SitterShapes.Row).background(s.primarySoft).clickable(role = Role.Button, onClick = onCalendar),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (met.isEmpty()) {
                        Text("이번 달은\n아직 없어요", style = SitterText.Caption, color = s.inkMute, textAlign = TextAlign.Center)
                    } else {
                        // 가장 많이 만난 젤리를 가운데 크게
                        val order = listOfNotNull(met.getOrNull(1), met.getOrNull(0), met.getOrNull(2))
                        Row(verticalAlignment = Alignment.Bottom) {
                            order.forEach { k ->
                                val big = k == met[0]
                                JellyIcon(k, states[k]?.stage?.atLeastDrop() ?: JellyStage.DROP, if (big) 84.dp else 56.dp, Modifier.offset(y = if (big) 0.dp else (-4).dp))
                            }
                        }
                    }
                }
                Text("이번 달 만난 젤리", style = SitterText.Caption, color = s.inkSoft, modifier = Modifier.padding(bottom = 10.dp))
            }
        }
    }
}

internal fun JellyStage.atLeastDrop() = if (this == JellyStage.NONE) JellyStage.DROP else this

/** 오른쪽 아래 (G-4): 경험젤리 만들러 갈까요? 많이 감·적게 감·안가봄. */
@Composable
private fun MakeJellyCard(rows: List<MakeJellyRow>, onRoute: (String, Double, Double) -> Unit, onPlan: () -> Unit, modifier: Modifier) {
    val s = SitterSurface.current
    SitterCard(modifier, contentPadding = PaddingValues(start = 12.dp, top = 20.dp, end = 12.dp, bottom = 12.dp)) {
        Text("경험젤리 만들러 갈까요?", style = SitterText.Title3, color = s.ink, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
        if (rows.isEmpty()) {
            Text("나들이를 다녀오면 젤리를 키울 곳을 골라 드려요", style = SitterText.Body2, color = s.inkMute, modifier = Modifier.padding(start = 8.dp))
            return@SitterCard
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { r -> MakeRow(r, onRoute, onPlan) }
        }
    }
}

@Composable
private fun MakeRow(r: MakeJellyRow, onRoute: (String, Double, Double) -> Unit, onPlan: () -> Unit) {
    val s = SitterSurface.current
    val lat = r.latitude
    val lon = r.longitude
    val name = r.placeName
    val go: () -> Unit = if (name != null && lat != null && lon != null) ({ onRoute(name, lat, lon) }) else onPlan
    val (tagFill, tagInk) = when (r.tag) {
        MakeTag.MANY -> Color(0xFF7CB342) to Color.White
        MakeTag.FEW -> s.primarySoft to s.primary
        MakeTag.NEW -> s.card to s.inkSoft
    }
    Row(
        Modifier.fillMaxWidth().height(84.dp).clip(SitterShapes.Row)
            .background(if (r.tag == MakeTag.MANY) Color(0xFFEFF7E3) else s.tile)
            .clickable(role = Role.Button, onClick = go)
            .padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            if (r.tag == MakeTag.NEW) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(Color(r.kind.argb).copy(alpha = 0.35f)))
            } else {
                JellyIcon(r.kind, r.stage.atLeastDrop(), 52.dp)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    r.tag.label,
                    style = SitterText.Caption,
                    color = tagInk,
                    maxLines = 1,
                    modifier = Modifier.clip(SitterShapes.Pill).background(tagFill)
                        .then(if (r.tag == MakeTag.NEW) Modifier.border(1.dp, s.line, SitterShapes.Pill) else Modifier)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
                Text(
                    name ?: r.kind.label,
                    style = SitterText.Body1,
                    color = s.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(r.caption, style = SitterText.Caption, color = s.inkMute, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
        GradientCircleButton(size = 52.dp, onClick = go) { SitterIcon(SitterIcons.Turn, Color.White) }
    }
}

/** 분야 카드 (G-6): 캐릭터·단계·역량 문구 + 다녀온 장소 목록. 빈 분야는 홈 추천으로. */
@Composable
private fun BoxScope.KindSheet(
    state: JellyState,
    places: List<Revisit>,
    highlight: String?,
    today: LocalDate,
    courseFor: (String) -> SavedCourse?,
    onRoute: (String, Double, Double) -> Unit,
    onOpenCourse: (SavedCourse) -> Unit,
    onPlan: () -> Unit,
    onClose: () -> Unit,
) {
    val s = SitterSurface.current
    val kind = state.kind
    SitterScrim(onClick = onClose)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.width(1000.dp).height(560.dp).clip(SitterShapes.Sheet).background(s.card).pointerInput(Unit) { detectTapGestures { } }.padding(28.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(
                Modifier.width(360.dp).fillMaxHeight().clip(SitterShapes.Card).background(Color(kind.argb).copy(alpha = 0.10f)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                    if (state.count > 0) {
                        JellyIcon(kind, state.stage, 200.dp)
                    } else {
                        Box(
                            Modifier.size(90.dp).clip(CircleShape).border(2.dp, s.line, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text("?", style = SitterText.Title1, color = s.inkMute) }
                    }
                }
                Text(kind.label, style = SitterText.Label, color = s.inkSoft, modifier = Modifier.padding(top = 8.dp))
                Text(if (state.count > 0) stageName(kind, state.stage) else "${kind.character} 젤리", style = SitterText.Title2, color = s.ink)
                val n = state.count.coerceAtMost(JellyStage.BORN.min)
                Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(10.dp).clip(SitterShapes.Pill).background(s.line)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(n / JellyStage.BORN.min.toFloat()).clip(SitterShapes.Pill).background(Color(kind.argb)))
                }
                Text(
                    if (state.stage == JellyStage.BORN) "${state.count}번 다녀왔어요" else "$n/${JellyStage.BORN.min} · ${JellyStage.BORN.min - n}번 더 가면 캐릭터가 태어나요",
                    style = SitterText.Caption,
                    color = s.inkMute,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(kind.power, style = SitterText.Body2, color = s.inkSoft, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (places.isEmpty()) "아직 다녀온 곳이 없어요" else "다녀온 곳 ${places.size}곳",
                        style = SitterText.Title3,
                        color = s.ink,
                        modifier = Modifier.weight(1f),
                    )
                    SitterButton("닫기", onClick = onClose, kind = SitterButtonKind.Soft)
                }
                Spacer(Modifier.height(14.dp))
                if (places.isEmpty()) {
                    Text("${kind.label} 나들이를 하면 ${kind.character} 젤리가 생겨요", style = SitterText.Body2, color = s.inkMute)
                    Spacer(Modifier.height(20.dp))
                    SitterButton("오늘 코스 찾기", onClick = { onClose(); onPlan() })
                    return@Column
                }
                val ordered = places.sortedByDescending { it.placeName == highlight }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ordered.forEach { p ->
                        val course = courseFor(p.placeName)
                        SitterRow(selected = p.placeName == highlight, outlined = true, contentPadding = PaddingValues(start = 18.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(p.placeName, style = SitterText.Body1, color = s.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${p.count}번 · 마지막 ${agoText(p.last, today)}" + if (p.nursingRoom) " · 수유실 있음" else "", style = SitterText.Caption, color = s.inkMute, maxLines = 1)
                            }
                            val lat = p.latitude
                            val lon = p.longitude
                            if (course != null) {
                                SitterButton("코스 보기", onClick = { onOpenCourse(course) }, kind = SitterButtonKind.Soft, modifier = Modifier.padding(end = 8.dp))
                            }
                            if (lat != null && lon != null) {
                                SitterButton("다시 안내", onClick = { onRoute(p.placeName, lat, lon) }, icon = SitterIcons.Turn)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 캐릭터 탄생 시트 (B안 374:7543, 정차 중에만). */
@Composable
internal fun BirthSheet(kind: JellyKind, childName: String, onDone: () -> Unit) {
    val s = SitterSurface.current
    SitterScrim()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(560.dp).clip(SitterShapes.Sheet).background(s.card).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("${JellyStage.BORN.min}번째 ${kind.label} 경험이에요!", style = SitterText.Label, color = s.primary)
            JellyIcon(kind, JellyStage.BORN, 180.dp, Modifier.padding(vertical = 12.dp))
            Text("${childName}의 ${kind.character} 캐릭터가 태어났어요", style = SitterText.Title2, color = s.ink, textAlign = TextAlign.Center)
            Text(kind.typeName, style = SitterText.Title3, color = Color(kind.argb), modifier = Modifier.padding(top = 8.dp))
            Text(kind.power, style = SitterText.Body2, color = s.inkSoft, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            SitterButton("좋아요", onClick = onDone, size = SitterButtonSize.Large, modifier = Modifier.padding(top = 24.dp).fillMaxWidth())
        }
    }
}
