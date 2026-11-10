package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.haeyeonyou.familytrip.data.Child
import io.github.haeyeonyou.familytrip.data.SavedCourse
import io.github.haeyeonyou.familytrip.data.Visit
import io.github.haeyeonyou.familytrip.data.WeatherInfo
import io.github.haeyeonyou.familytrip.grow.JellyKind
import io.github.haeyeonyou.familytrip.grow.JellyStage
import io.github.haeyeonyou.familytrip.grow.JellyState
import io.github.haeyeonyou.familytrip.grow.Revisit
import io.github.haeyeonyou.familytrip.grow.agoText
import io.github.haeyeonyou.familytrip.grow.experiences
import io.github.haeyeonyou.familytrip.grow.jellyStates
import io.github.haeyeonyou.familytrip.grow.jellyType
import io.github.haeyeonyou.familytrip.grow.monthDeltaText
import io.github.haeyeonyou.familytrip.grow.monthTile
import io.github.haeyeonyou.familytrip.grow.newKindsThisMonth
import io.github.haeyeonyou.familytrip.grow.pendingBirths
import io.github.haeyeonyou.familytrip.grow.revisitCaption
import io.github.haeyeonyou.familytrip.grow.revisits
import io.github.haeyeonyou.familytrip.grow.stageName
import io.github.haeyeonyou.familytrip.grow.suggestEmpty
import io.github.haeyeonyou.familytrip.grow.universeHint
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 그로우 G01 (354:3033, #164): 아이 경험 우주 + 젤리 B안 성장 단계.
 * 다녀온 곳(10분 이상 머문 방문)을 12분류 젤리로 모아 방울 → 몽글 → 탄생으로 키운다. 계산은 `grow/JellyModel.kt`.
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
) = SitterCanvas {
    val today = remember(visits) { LocalDate.now() }
    val list = remember(visits) { experiences(visits, LocalDateTime.now()) }
    val states = remember(list) { jellyStates(list, today) }
    val places = remember(list) { growPlaces(list) }
    val suggested = remember(states) { if (states.any { it.count > 0 }) suggestEmpty(states) else null }
    val geometry = remember(states, places, suggested) { computeJellyGeometry(states, places, suggested) }
    val child = children.firstOrNull()
    val name = child?.name?.takeIf { it.isNotBlank() }
    var filter by remember { mutableStateOf<GrowFilter>(GrowFilter.All) }
    var selectedPlace by remember { mutableStateOf<String?>(null) }
    var selectedKind by remember { mutableStateOf<JellyKind?>(null) }
    var showKinds by remember { mutableStateOf(false) }
    val driving = LocalDriveMode.current.isDriving
    val births = remember(states, celebrated) { pendingBirths(states, celebrated) }

    fun courseWith(placeName: String) = courses.firstOrNull { c -> c.course.stops.any { it.place.name == placeName } }

    SitterVoiceBar(
        hint = "무엇을 볼까요?  “근처 실내 놀거리 찾아줘”",
        avatarLabel = name?.take(1),
        weather = { weather?.let { SitterWeather(it.temperature?.let { t -> "${Math.round(t)}°" } ?: "--", listOf(it.condition, it.region).filter(String::isNotBlank).joinToString(" · ")) } },
    )
    SitterChipRow {
        SitterChip("전체", filter == GrowFilter.All, onClick = { filter = GrowFilter.All })
        states.filter { it.count > 0 }.sortedByDescending { it.count }.take(4).forEach { st ->
            val on = filter == GrowFilter.Kind(st.kind)
            SitterChip(st.kind.chip, on, onClick = { filter = if (on) GrowFilter.All else GrowFilter.Kind(st.kind) })
        }
        SitterChip("이번 달", filter == GrowFilter.ThisMonth, onClick = {
            filter = if (filter == GrowFilter.ThisMonth) GrowFilter.All else GrowFilter.ThisMonth
        })
        child?.let { c ->
            Text(
                listOfNotNull(name, "${c.ageMonths()}개월").joinToString(" · "),
                style = SitterText.Body1,
                color = SitterSurface.current.inkSoft,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
    SitterWideAndColumn(
        wide = {
            UniverseCard(
                title = if (name == null) "우리 아이 경험 우주" else "${name}의 경험 우주",
                subtitle = "${list.size}개의 경험 · ${places.size}개 장소 · 다녀온 곳이 새 별로 추가돼요",
                typeLine = jellyType(states)?.line,
                hint = universeHint(states),
                modifier = Modifier.fillMaxSize(),
            ) {
                JellyUniverseCanvas(
                    geometry = geometry,
                    centerLabel = name ?: "우리 아이",
                    filter = filter,
                    selectedPlace = selectedPlace,
                    today = today,
                    onHit = { hit ->
                        when (hit) {
                            is UniverseHit.Place -> {
                                selectedPlace = geometry.places[hit.index].place.name
                                selectedKind = null
                            }
                            is UniverseHit.Kind -> {
                                val st = states.first { it.kind == hit.kind }
                                if (st.count == 0 && hit.kind == suggested) {
                                    onPlan()
                                } else {
                                    selectedKind = hit.kind
                                    selectedPlace = null
                                    if (st.count > 0) filter = GrowFilter.Kind(hit.kind)
                                }
                            }
                            null -> {
                                selectedPlace = null
                                selectedKind = null
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                val popup = Modifier.align(Alignment.BottomStart).padding(8.dp)
                places.firstOrNull { it.name == selectedPlace }?.let { p ->
                    PlacePopup(
                        place = p,
                        stage = states.first { it.kind == p.kind }.stage,
                        today = today,
                        course = courseWith(p.name),
                        onRoute = onRoute,
                        onOpenCourse = onOpenCourse,
                        onClose = { selectedPlace = null },
                        modifier = popup,
                    )
                }
                selectedKind?.let { k ->
                    KindPopup(states.first { it.kind == k }, onClose = { selectedKind = null }, modifier = popup)
                }
            }
        },
        third = {
            Row(Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val month = monthTile(list, today)
                val fresh = newKindsThisMonth(states, today)
                StatTile(
                    label = "이번 달",
                    value = "${month.places}곳",
                    caption = monthDeltaText(month.delta),
                    captionColor = SitterSurface.current.primary,
                    action = if (filter == GrowFilter.ThisMonth) "전체 보기" else "이번 달 보기",
                    onAction = { filter = if (filter == GrowFilter.ThisMonth) GrowFilter.All else GrowFilter.ThisMonth },
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "새로 해본 분야",
                    value = "${fresh.size}개",
                    caption = if (fresh.isEmpty()) "이번 달은 아직 없어요" else fresh.joinToString(" · ") { it.chip },
                    captionColor = SitterSurface.current.inkMute,
                    action = "분야별 보기",
                    onAction = { showKinds = true },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
            RevisitCard(
                rows = remember(list) { revisits(list, today) },
                states = states,
                today = today,
                courseFor = ::courseWith,
                onRoute = onRoute,
                onOpenCourse = onOpenCourse,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            ShortcutDock()
        },
    )

    if (showKinds) {
        androidx.activity.compose.BackHandler { showKinds = false }
        KindsSheet(states, jellyType(states)?.line, onClose = { showKinds = false })
    }
    births.firstOrNull()?.takeIf { !driving }?.let { kind ->
        BirthSheet(kind, name ?: "우리 아이", onDone = { onCelebrated(listOf(kind)) })
    }
}

@Composable
private fun UniverseCard(
    title: String,
    subtitle: String,
    typeLine: String?,
    hint: String,
    modifier: Modifier = Modifier,
    graph: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val s = SitterSurface.current
    SitterCard(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(title, style = SitterText.Title2, color = s.ink, maxLines = 1)
                Text(subtitle, style = SitterText.Caption, color = s.inkMute, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
            }
            typeLine?.let {
                Text(
                    it,
                    style = SitterText.Label,
                    color = s.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp).width(300.dp).clip(SitterShapes.Pill).background(s.primarySoft)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), content = graph)
        Text(hint, style = SitterText.Caption, color = s.inkMute, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    caption: String,
    captionColor: Color,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = SitterSurface.current
    SitterCard(modifier.fillMaxHeight(), contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        Text(label, style = SitterText.Caption, color = s.inkSoft)
        Text(value, style = SitterText.Number, color = s.ink, modifier = Modifier.padding(top = 2.dp))
        Text(caption, style = SitterText.Caption, color = captionColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.fillMaxWidth().height(44.dp).clip(SitterShapes.Pill).background(s.tile)
                .clickable(role = Role.Button, onClick = onAction),
            contentAlignment = Alignment.Center,
        ) { Text(action, style = SitterText.Label, color = s.ink) }
    }
}

@Composable
private fun RevisitCard(
    rows: List<Revisit>,
    states: List<JellyState>,
    today: LocalDate,
    courseFor: (String) -> SavedCourse?,
    onRoute: (String, Double, Double) -> Unit,
    onOpenCourse: (SavedCourse) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = SitterSurface.current
    SitterCard(modifier) {
        Text("다시 가볼까요?", style = SitterText.Title3, color = s.ink, modifier = Modifier.padding(bottom = 12.dp))
        if (rows.isEmpty()) {
            Text("일주일 넘게 지난 나들이가 생기면 다시 갈 곳을 골라 드려요", style = SitterText.Body2, color = s.inkMute)
            return@SitterCard
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEachIndexed { i, r ->
                val course = courseFor(r.placeName)
                val go: (() -> Unit)? = when {
                    r.latitude != null && r.longitude != null -> ({ onRoute(r.placeName, r.latitude, r.longitude) })
                    course != null -> ({ onOpenCourse(course) })
                    else -> null
                }
                SitterRow(selected = i == 0, contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, top = 10.dp, end = 10.dp, bottom = 10.dp)) {
                    JellyIcon(r.kind, states.first { it.kind == r.kind }.stage.coerceDrop(), 40.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(r.placeName, style = SitterText.Body1, color = s.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(revisitCaption(r, today), style = SitterText.Caption, color = s.inkMute, maxLines = 1)
                    }
                    go?.let {
                        GradientCircleButton(size = 52.dp, onClick = it) { SitterIcon(SitterIcons.Turn, Color.White) }
                    }
                }
            }
        }
    }
}

private fun JellyStage.coerceDrop() = if (this == JellyStage.NONE) JellyStage.DROP else this

@Composable
private fun PopupFrame(onClose: () -> Unit, modifier: Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val s = SitterSurface.current
    Row(
        modifier.width(460.dp).shadow(8.dp, SitterShapes.Row).clip(SitterShapes.Row).background(s.card)
            .border(1.dp, s.line, SitterShapes.Row).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Text("✕", style = SitterText.Body1, color = s.inkMute) }
    }
}

/** 장소 노드 팝업 (Q34 ②): 방문 이력 + 다시 안내 / 코스 보기. */
@Composable
private fun PlacePopup(
    place: GrowPlace,
    stage: JellyStage,
    today: LocalDate,
    course: SavedCourse?,
    onRoute: (String, Double, Double) -> Unit,
    onOpenCourse: (SavedCourse) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) = PopupFrame(onClose, modifier) {
    val s = SitterSurface.current
    JellyIcon(place.kind, stage.coerceDrop(), 48.dp)
    Column(Modifier.weight(1f)) {
        Text(place.name, style = SitterText.Body1, color = s.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${place.kind.label} · ${place.count}번 · 마지막 ${agoText(place.last, today)}", style = SitterText.Caption, color = s.inkMute, maxLines = 1)
    }
    val lat = place.latitude
    val lon = place.longitude
    when {
        lat != null && lon != null -> SitterButton("다시 안내", onClick = { onRoute(place.name, lat, lon) }, icon = SitterIcons.Turn)
        course != null -> SitterButton("코스 보기", onClick = { onOpenCourse(course) }, kind = SitterButtonKind.Soft)
    }
}

/** 분류 노드 팝업: 성장 단계와 다음 단계까지 남은 경험. */
@Composable
private fun KindPopup(state: JellyState, onClose: () -> Unit, modifier: Modifier) = PopupFrame(onClose, modifier) {
    val s = SitterSurface.current
    JellyIcon(state.kind, state.stage.coerceDrop(), 56.dp)
    Column(Modifier.weight(1f)) {
        Text(stageName(state.kind, state.stage), style = SitterText.Body1, color = s.ink, maxLines = 1)
        Text(growthCaption(state), style = SitterText.Caption, color = s.inkMute, maxLines = 2)
    }
}

/** "경험 5개 · 탄생까지 5개" / "탄생 · 보고, 생각하고 …" / "자연·숲 나들이를 하면 별이 생겨요" */
internal fun growthCaption(state: JellyState): String = when (state.stage) {
    JellyStage.NONE -> "${state.kind.label} 나들이를 하면 별이 생겨요"
    JellyStage.DROP -> "${state.kind.label} 경험 ${state.count}개 · 몽글까지 ${JellyStage.PUFF.min - state.count}개"
    JellyStage.PUFF -> "${state.kind.label} 경험 ${state.count}개 · 탄생까지 ${JellyStage.BORN.min - state.count}개"
    JellyStage.BORN -> "경험 ${state.count}개 · ${state.kind.power}"
}

/** 분야별 보기: 12분류 젤리를 한눈에 (6x2). */
@Composable
private fun KindsSheet(states: List<JellyState>, typeLine: String?, onClose: () -> Unit) {
    val s = SitterSurface.current
    SitterScrim(onClick = onClose)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(1040.dp).clip(SitterShapes.Sheet).background(s.card).pointerInput(Unit) { detectTapGestures { } }.padding(28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("12가지 경험 별", style = SitterText.Title2, color = s.ink)
                    Text(
                        typeLine ?: "나들이 장소에 10분 이상 머물면 그 분야 젤리가 자라요",
                        style = SitterText.Body2,
                        color = if (typeLine != null) s.primary else s.inkMute,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                SitterButton("닫기", onClick = onClose, kind = SitterButtonKind.Soft)
            }
            Spacer(Modifier.height(20.dp))
            states.chunked(6).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { st -> KindCell(st, Modifier.weight(1f)) }
                }
            }
            Text("방울 1~3회 · 몽글 4~9회 · 탄생 10회 이상 · 유형은 최근 6개월 기준", style = SitterText.Caption, color = s.inkMute)
        }
    }
}

@Composable
private fun KindCell(st: JellyState, modifier: Modifier) {
    val s = SitterSurface.current
    Column(
        modifier.clip(SitterShapes.Row).background(if (st.count > 0) s.tile else s.card).border(1.dp, s.line, SitterShapes.Row).padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
            if (st.count > 0) {
                JellyIcon(st.kind, st.stage, 84.dp)
            } else {
                Box(Modifier.size(18.dp).clip(CircleShape).background(Color(0xFFC9D2DC)))
            }
        }
        Text(st.kind.label, style = SitterText.Label, color = s.ink, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
        Text(stageName(st.kind, st.stage), style = SitterText.Caption, color = if (st.count > 0) Color(st.kind.argb) else s.inkMute, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val progress = (st.count.coerceAtMost(JellyStage.BORN.min)).toFloat() / JellyStage.BORN.min
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(6.dp).clip(SitterShapes.Pill).background(s.line)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(SitterShapes.Pill).background(Color(st.kind.argb)))
        }
        Text("${st.count.coerceAtMost(JellyStage.BORN.min)}/${JellyStage.BORN.min}", style = SitterText.Caption, color = s.inkMute, modifier = Modifier.padding(top = 2.dp))
    }
}

/** 캐릭터 탄생 시트 (B안 374:7543, 정차 중에만). */
@Composable
private fun BirthSheet(kind: JellyKind, childName: String, onDone: () -> Unit) {
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
            Text(
                kind.power,
                style = SitterText.Body2,
                color = s.inkSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            SitterButton("좋아요", onClick = onDone, size = SitterButtonSize.Large, modifier = Modifier.padding(top = 24.dp).fillMaxWidth())
        }
    }
}
