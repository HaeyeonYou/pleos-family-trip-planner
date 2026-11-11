package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.haeyeonyou.familytrip.data.CourseOption
import io.github.haeyeonyou.familytrip.data.SavedCourse
import io.github.haeyeonyou.familytrip.data.Visit
import io.github.haeyeonyou.familytrip.data.WeatherInfo
import io.github.haeyeonyou.familytrip.data.ageLabel
import io.github.haeyeonyou.familytrip.vehicle.courseMeters
import io.github.haeyeonyou.familytrip.vehicle.fuelLevel
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/** 홈 화면이 밖으로 내보내는 동작 */
data class HomeActions(
    val load: () -> Unit,
    val toggleFilter: (change: (HomeFilters) -> HomeFilters) -> Unit,
    val retry: () -> Unit,
    /** 지금 보여 준 코스를 빼고 다른 코스를 받는다 (칩 `코스 다시 추천`) */
    val another: () -> Unit,
    /** 코스 상세(H02)를 연다 */
    val openOption: (CourseOption) -> Unit,
    /** 상세를 거치지 않고 바로 안내를 시작한다 (코스 행의 파란 화살표, 탑승 카드의 화살표) */
    val startOption: (CourseOption) -> Unit,
    val changeConditions: () -> Unit,
    val openCare: () -> Unit,
    val openGrow: () -> Unit,
    val reopenCourse: (SavedCourse) -> Unit,
    val fed: () -> Unit,
    val feedingInterval: (Int) -> Unit,
    /** 상단 음성 입력줄 */
    val onMic: () -> Unit = {},
    /** 아이 정보가 없을 때 안내 카드의 `아이 정보 넣기` → 온보딩 O04 (QO7, #184). null이면 카드를 그리지 않는다 */
    val openChildInfo: (() -> Unit)? = null,
    /** 연료 타일 [주유소 찾기]·[여기 들르기] (#215). null이면 버튼이 눌리지 않는다 */
    val fuel: FuelActions? = null,
)

/**
 * 홈 H01 (Figma 352:2601). 3열 그리드:
 * 1열 지도 카드 + 탑승 감지 추천 카드 / 2열 오늘의 추천 코스 3개 / 3열 주행 가능 거리·경험 젤리 타일 + 최근 방문 코스 + 바로가기 독.
 * 코스 행을 누르면 지도가 그 코스 경로로 바뀐다 (`05` Q27 [제안]: 추천 1 = 목록 첫 코스, 선택은 행 탭).
 */
@Composable
fun HomeScreen(
    state: TripUiState,
    @Suppress("UNUSED_PARAMETER") isParked: Boolean,
    rangeMeters: Float?,
    visits: List<Visit>,
    saved: List<SavedCourse>,
    actions: HomeActions,
    /** 전기차면 [충전소 찾기] (#215) */
    electric: Boolean = false,
) {
    LaunchedEffect(Unit) { actions.load() }
    val now = rememberNow()
    val filters = state.homeFilters
    // 선택한 코스. 추천이 바뀌어 사라지면 첫 코스로 돌아간다.
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val shown = state.options.take(HOME_COURSE_COUNT)
    val selected = shown.firstOrNull { it.placeKey == selectedKey } ?: shown.firstOrNull()

    SitterCanvas {
        SitterVoiceBar(
            hint = "어디로 갈까요?  “근처 실내 놀거리 찾아줘”",
            avatarLabel = state.profile?.children?.firstOrNull()?.name?.take(1),
            onMic = actions.onMic,
            onGo = { selected?.let(actions.startOption) },
            weather = { state.weather?.let { SitterWeather(temperatureLabel(it), weatherCaption(it)) } },
        )
        HomeChips(state, filters, actions)
        SitterThreeColumns(
            first = { MapColumn(selected, state.weather, now, state.going[selected?.placeKey] ?: 0, actions) },
            second = { CourseListColumn(state, shown, selected, filters, actions) { selectedKey = it.placeKey } },
            third = {
                RightColumn(visits, saved, actions, needsChildInfo = state.profile?.children.isNullOrEmpty()) { tileModifier ->
                    FuelRangeTile(
                        rangeMeters = rangeMeters,
                        level = fuelLevel(rangeMeters, selected?.let { courseMeters(it.course) }),
                        electric = electric,
                        toward = selected?.let { fuelToward(it.course) },
                        actions = actions.fuel,
                        modifier = tileModifier,
                    )
                }
            },
        )
    }
}

private const val HOME_COURSE_COUNT = 3

/** 홈 지도 아래쪽을 덮는 탑승 감지 카드 높이(여백 포함). 경로는 이 위에 맞춘다. */
private val HOME_MAP_CARD_SPACE = 300.dp

// 상단 칩 줄

@Composable
private fun HomeChips(state: TripUiState, filters: HomeFilters, actions: HomeActions) {
    val child = state.profile?.children?.firstOrNull()
    SitterChipRow {
        HomeDuration.entries.forEach { d ->
            SitterChip(d.label, selected = filters.duration == d, icon = if (filters.duration == d) SitterIcons.Check else null, onClick = {
                actions.toggleFilter { it.copy(duration = if (it.duration == d) null else d) }
            })
        }
        RadiusChip(filters.radiusKm) { km -> actions.toggleFilter { it.copy(radiusKm = km) } }
        SitterChip("반려견 동반", filters.pet, { actions.toggleFilter { it.copy(pet = !it.pet) } }, icon = SitterIcons.Paw)
        SitterChip("수유실 필수", filters.nursingRoom, { actions.toggleFilter { it.copy(nursingRoom = !it.nursingRoom) } }, icon = SitterIcons.Milk)
        SitterChip("유모차", filters.stroller, { actions.toggleFilter { it.copy(stroller = !it.stroller) } }, icon = SitterIcons.Baby)
        // 읽기 전용: 가족 정보의 첫째 아이 나이. 누르면 조건 화면에서 고친다.
        child?.let {
            val months = it.ageMonths()
            SitterChip(if (months < 36) "생후 ${ageLabel(months)}" else "아이 ${ageLabel(months)}", false, actions.changeConditions, icon = SitterIcons.Users)
        }
        SitterChip("코스 다시 추천", false, actions.another, icon = SitterIcons.Refresh)
    }
}

/** 반경 칩 (Figma: "반경 10km ⌄"). 누르면 5·10·20km 중에서 고르는 목록이 열린다. */
@Composable
private fun RadiusChip(radiusKm: Int, onPick: (Int) -> Unit) {
    val s = SitterSurface.current
    var open by remember { mutableStateOf(false) }
    // 팝업은 SitterCanvas 확대(LocalDensity)를 물려받지 않아 글자가 작아진다. 캔버스 밀도를 그대로 넘긴다.
    val canvasDensity = androidx.compose.ui.platform.LocalDensity.current
    Box {
        SitterChip("반경 ${radiusKm}km", selected = false, onClick = { open = true }, trailingIcon = SitterIcons.ChevronDown)
        androidx.compose.material3.DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = s.card,
            shape = SitterShapes.Card,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides canvasDensity) {
            HOME_RADIUS_STEPS_KM.forEach { km ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("반경 ${km}km", style = SitterText.Body1, color = if (km == radiusKm) s.primary else s.ink) },
                    trailingIcon = if (km == radiusKm) ({ SitterIcon(SitterIcons.Check, s.primary, size = SitterIconSize.Small) }) else null,
                    onClick = { open = false; if (km != radiusKm) onPick(km) },
                    modifier = Modifier.height(64.dp).width(200.dp),
                )
            }
            }
        }
    }
}

// 1열: 지도 + 탑승 감지 카드

@Composable
private fun MapColumn(selected: CourseOption?, weather: WeatherInfo?, nowMs: Long, going: Int, actions: HomeActions) {
    val s = SitterSurface.current
    Box(Modifier.fillMaxSize().clip(SitterShapes.Card).background(s.tile)) {
        if (selected != null) {
            // 위쪽 배지와 아래 탑승 카드가 덮는 곳을 비워 경로가 그 사이에 오게 한다.
            CourseMap(
                stops = selected.course.stops,
                path = selected.course.path,
                modifier = Modifier.fillMaxSize(),
                style = CourseMapStyle.Preview,
                contentPadding = PaddingValues(start = 48.dp, top = 96.dp, end = 48.dp, bottom = HOME_MAP_CARD_SPACE),
            )
        }
        if (selected != null) {
            Row(
                Modifier.padding(16.dp).clip(SitterShapes.Pill).background(s.card).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SitterIcon(SitterIcons.Nav, s.primary, size = 16.dp)
                Text("추천 코스 미리보기", style = SitterText.Caption, color = s.ink)
            }
        }
        ProactiveCard(selected, weather, nowMs, going, actions, Modifier.align(Alignment.BottomCenter).padding(8.dp))
    }
}

/** 탑승 감지 추천 카드: 그라데이션 바탕. 감지 신호는 임시로 "앱이 켜진 시각"(D8, `05` Q27). */
@Composable
private fun ProactiveCard(option: CourseOption?, weather: WeatherInfo?, nowMs: Long, going: Int, actions: HomeActions, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().clip(SitterShapes.Card).background(SitterPalette.Gradient).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (option == null) {
            Text("오늘 어디로 갈까요?", style = SitterText.Title3, color = androidx.compose.ui.graphics.Color.White)
            Text("조건에 맞는 코스를 찾으면 여기에서 추천해 드려요", style = SitterText.Caption, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f))
            return@Column
        }
        Text("${nowLabel(nowMs)} · 탑승을 감지했어요", style = SitterText.Caption, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f))
        Text(proactiveSentence(weather, option), style = SitterText.Title3, color = androidx.compose.ui.graphics.Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Row(
            modifier = Modifier.fillMaxWidth().clip(SitterShapes.Row).background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.16f)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(option.summary, style = SitterText.Label, color = androidx.compose.ui.graphics.Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(courseMeta(option, going), style = SitterText.Caption, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.padding(start = 12.dp).size(56.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.White)
                    .clickable { actions.startOption(option) },
                contentAlignment = Alignment.Center,
            ) { SitterIcon(SitterIcons.Turn, SitterSurface.current.primary) }
        }
    }
}

// 2열: 오늘의 추천 코스

@Composable
private fun CourseListColumn(
    state: TripUiState,
    shown: List<CourseOption>,
    selected: CourseOption?,
    filters: HomeFilters,
    actions: HomeActions,
    onSelect: (CourseOption) -> Unit,
) {
    val s = SitterSurface.current
    SitterCard(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.Bottom) {
            Text("오늘의 추천 코스", style = SitterText.Title3, color = s.ink, modifier = Modifier.weight(1f))
            Text("반경 ${filters.radiusKm}km · ${shown.size}개", style = SitterText.Caption, color = s.inkMute)
        }
        when {
            state.optionsLoading && shown.isEmpty() -> CenterMessage { CircularProgressIndicator(); Text("추천 코스를 찾는 중…", style = SitterText.Body2, color = s.inkSoft) }
            state.optionsError != null && shown.isEmpty() -> CenterMessage {
                Text(state.optionsError, style = SitterText.Body2, color = s.inkSoft)
                SitterButton("다시 시도", actions.retry, kind = SitterButtonKind.Soft)
            }
            shown.isEmpty() -> CenterMessage {
                Text("조건에 맞는 코스가 없어요", style = SitterText.Body1, color = s.ink)
                SitterButton("조건 줄이기", actions.changeConditions, kind = SitterButtonKind.Soft)
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                shown.forEach { option ->
                    CourseItem(
                        title = homeCourseTitle(option.name),
                        option = option,
                        going = state.going[option.placeKey] ?: 0,
                        selected = option.placeKey == selected?.placeKey,
                        // 디자이너 Note 357:1716 "코스 행 탭 = 상세": 지도 미리보기도 이 코스로 맞추고 H02로 간다
                        onSelect = { onSelect(option); actions.openOption(option) },
                        onStart = { actions.startOption(option) },
                    )
                }
                if (state.optionsLoading) Text("추천을 다시 계산하는 중…", style = SitterText.Caption, color = s.primary)
            }
        }
    }
}

@Composable
private fun CenterMessage(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().height(320.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

/**
 * 코스 항목 (Figma Course Item 361:5170): 제목·경로·메타·편의 태그 + 오른쪽 길 안내 원 버튼(52).
 * 행을 누르면 지도 미리보기에 선택, 원 버튼은 바로 안내. 상세(H02)는 선택된 행을 다시 누르면 연다.
 */
@Composable
private fun CourseItem(title: String, option: CourseOption, going: Int, selected: Boolean, onSelect: () -> Unit, onStart: () -> Unit) {
    val s = SitterSurface.current
    val tags = homeCourseTags(option.course)
    SitterRow(selected = selected, onClick = onSelect, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = SitterText.Title3, color = s.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(option.summary, style = SitterText.Caption, color = s.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "총 ${io.github.haeyeonyou.familytrip.data.durationLabel(option.totalMinutes.toLong())} · 첫 이동 ${option.firstDriveMinutes}분",
                    style = SitterText.Caption, color = s.inkMute, maxLines = 1,
                )
                if (going > 0) Text("● 지금 ${going}대", style = SitterText.Caption, color = s.primary, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { (label, ok) -> SitterTag(label, available = ok) }
            }
        }
        GradientCircleButton(size = 52.dp, modifier = Modifier.padding(start = 12.dp), onClick = onStart) {
            SitterIcon(SitterIcons.Turn, androidx.compose.ui.graphics.Color.White)
        }
    }
}

// 3열

@Composable
private fun RightColumn(
    visits: List<Visit>,
    saved: List<SavedCourse>,
    actions: HomeActions,
    needsChildInfo: Boolean = false,
    fuelTile: @Composable (Modifier) -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(SitterGrid.GAP.dp)) {
        Row(Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.spacedBy(SitterGrid.GAP.dp)) {
            // 디자이너 메모 411:13221: 정차 공간 타일을 빼고 연료 기능으로 (#215). 정차 공간 찾기는 케어(C11)에 그대로 있다.
            fuelTile(Modifier.weight(1f).fillMaxHeight())
            ExperienceTile(visits, actions.openGrow, Modifier.weight(1f).fillMaxHeight())
        }
        val openChildInfo = actions.openChildInfo
        if (needsChildInfo && openChildInfo != null) ChildInfoCard(openChildInfo) else RecentCourses(saved, actions.reopenCourse, Modifier.weight(1f))
        if (needsChildInfo && openChildInfo != null) Spacer(Modifier.weight(1f))
        ShortcutDock()
    }
}

/** 온보딩을 `나중에 하기`로 넘겨 아이 정보가 없을 때 1장 (QO7). 누르면 온보딩 O04부터. */
@Composable
private fun ChildInfoCard(onOpen: () -> Unit) {
    val s = SitterSurface.current
    SitterCard(Modifier.fillMaxWidth().clickable(onClick = onOpen), tone = SitterCardTone.Active) {
        SitterIcon(SitterIcons.Baby, s.primary)
        Text("아이 정보를 넣으면 맞춰 드려요", style = SitterText.Title3, color = s.ink, modifier = Modifier.padding(top = 12.dp))
        Text("월령에 맞춰 케어 모드와 추천 코스를 준비해요", style = SitterText.Caption, color = s.inkMute, modifier = Modifier.padding(top = 4.dp))
        Text("아이 정보 넣기", style = SitterText.Label, color = s.primary, modifier = Modifier.padding(top = 12.dp))
    }
}

/** 경험 젤리 요약: 이번 달 방문 장소 수(링)와 분야 수. 기록은 기기 VisitStore에서 읽기만 한다. */
@Composable
private fun ExperienceTile(visits: List<Visit>, onOpen: () -> Unit, modifier: Modifier) {
    val s = SitterSurface.current
    val month = LocalDateTime.now().let { it.year to it.monthValue }
    val thisMonth = visits.filter { it.role == "outing" && (it.date.year to it.date.monthValue) == month }
    val places = thisMonth.map { it.placeName }.distinct().size
    val fields = thisMonth.mapNotNull { it.theme }.distinct().size
    SitterCard(modifier.clickable(onClick = onOpen), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { (places / 5f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    color = s.primary,
                    trackColor = s.tile,
                    strokeWidth = 6.dp,
                )
                Text("$places", style = SitterText.Number, color = s.ink)
            }
            Text(if (places == 0) "첫 나들이를 기록해 보세요" else "이번 달 ${places}곳 · ${fields}분야", style = SitterText.Caption, color = s.inkSoft, maxLines = 2)
            Text(
                "경험 젤리 보기",
                style = SitterText.Label,
                color = s.ink,
                modifier = Modifier.clip(SitterShapes.Pill).background(s.tile).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** 최근 방문 코스: 저장한 코스 최근 3개. 눌러서 같은 순서로 다시 안내한다. */
@Composable
private fun RecentCourses(saved: List<SavedCourse>, onReopen: (SavedCourse) -> Unit, modifier: Modifier) {
    val s = SitterSurface.current
    val recent = saved.sortedByDescending { it.savedAt }.take(3)
    SitterCard(modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("최근 방문 코스", style = SitterText.Title3, color = s.ink, modifier = Modifier.weight(1f))
            if (recent.isNotEmpty()) {
                Text("다시 가기", style = SitterText.Label, color = s.primary, modifier = Modifier.clickable { onReopen(recent.first()) })
            }
        }
        if (recent.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("코스를 저장하면 여기에서 다시 갈 수 있어요", style = SitterText.Body2, color = s.inkMute)
            }
            return@SitterCard
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp).weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tints = listOf(0xFFF3E8FF, 0xFFDBEAFE, 0xFFD5ECD9)
            recent.forEachIndexed { i, course ->
                Column(Modifier.weight(1f).clickable { onReopen(course) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier.fillMaxWidth().height(100.dp).clip(SitterShapes.Row).background(androidx.compose.ui.graphics.Color(tints[i % tints.size])),
                        contentAlignment = Alignment.Center,
                    ) {
                        SitterIcon(recentIcon(course), s.inkSoft)
                        Box(
                            Modifier.align(Alignment.TopStart).padding(8.dp).size(28.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(0xFF4E5968)),
                            contentAlignment = Alignment.Center,
                        ) { Text("${i + 1}", style = SitterText.Caption, color = androidx.compose.ui.graphics.Color.White) }
                    }
                    Text(course.title, style = SitterText.Label, color = s.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(recentMeta(course), style = SitterText.Caption, color = s.inkMute, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Text(
            "코스 선택하면 같은 순서로 다시 안내해요",
            style = SitterText.Caption,
            color = s.inkSoft,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clip(SitterShapes.Pill).background(s.tile).padding(vertical = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private fun recentIcon(course: SavedCourse) = when (course.course.stops.firstOrNull { it.role == "outing" }?.place?.theme) {
    "nature" -> SitterIcons.Trees
    "play", "experience" -> SitterIcons.Waves
    else -> SitterIcons.Store
}

private fun recentMeta(course: SavedCourse): String {
    val stay = course.course.stops.sumOf { it.stayMinutes }
    val drive = course.course.totalDriveMinutes
    return "${io.github.haeyeonyou.familytrip.data.durationLabel((stay + drive).toLong())}"
}

// 글

/** 주유소를 고를 때 향할 곳: 코스의 첫 방문지(서버가 넣은 주유 경유지와 귀가는 뺀다). */
internal fun fuelToward(course: io.github.haeyeonyou.familytrip.data.CourseResponse): io.github.haeyeonyou.familytrip.data.LatLng? =
    course.stops.firstOrNull { it.role != "home" && it.role != "fuel" }?.place?.let { io.github.haeyeonyou.familytrip.data.LatLng(it.latitude, it.longitude) }

private fun courseMeta(option: CourseOption, going: Int): String {
    val base = "총 ${io.github.haeyeonyou.familytrip.data.durationLabel(option.totalMinutes.toLong())} · 첫 이동 ${option.firstDriveMinutes}분"
    return if (going > 0) "$base · 지금 ${going}대" else base
}

/** "맑고 14°예요. 아차산 숲길 반나절 어때요?" 형태. 날씨를 못 받으면 코스 문장만. */
internal fun proactiveSentence(weather: WeatherInfo?, option: CourseOption): String {
    val wx = weather?.let { w ->
        val cond = w.condition.takeIf { it.isNotBlank() }?.let { c -> if (c.endsWith("음") || c.endsWith("름")) "${c.dropLast(1)}고" else "$c," }
        val temp = w.temperature?.let { "${Math.round(it)}°예요." }
        listOfNotNull(cond, temp).joinToString(" ").takeIf { cond != null || temp != null }
    }
    return listOfNotNull(wx, "${homeCourseTitle(option.name)} 어때요?").joinToString("\n")
}

private fun temperatureLabel(w: WeatherInfo) = w.temperature?.let { "${Math.round(it)}°" } ?: "--"

private fun weatherCaption(w: WeatherInfo): String =
    listOf(w.condition, w.region).filter { it.isNotBlank() }.joinToString(" · ")

private fun nowLabel(nowMs: Long): String {
    val t = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMs), java.time.ZoneId.systemDefault())
    val day = t.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
    val hour12 = if (t.hour % 12 == 0) 12 else t.hour % 12
    return "$day ${if (t.hour < 12) "오전" else "오후"} $hour12:${"%02d".format(t.minute)}"
}
