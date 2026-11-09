package io.github.haeyeonyou.familytrip.ui

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.haeyeonyou.familytrip.data.CourseOption
import io.github.haeyeonyou.familytrip.data.SavedCourse
import io.github.haeyeonyou.familytrip.data.Visit
import io.github.haeyeonyou.familytrip.data.ageLabel
import io.github.haeyeonyou.familytrip.data.clockLabel
import io.github.haeyeonyou.familytrip.data.durationLabel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/** 홈 화면이 밖으로 내보내는 동작 */
data class HomeActions(
    val load: () -> Unit,
    val toggleFilter: (change: (HomeFilters) -> HomeFilters) -> Unit,
    val retry: () -> Unit,
    /** 지금 보여 준 코스를 빼고 다른 코스를 받는다 */
    val another: () -> Unit,
    val openOption: (CourseOption) -> Unit,
    val changeConditions: () -> Unit,
    val openCare: () -> Unit,
    val openGrow: () -> Unit,
    val reopenCourse: (SavedCourse) -> Unit,
    val fed: () -> Unit,
    val feedingInterval: (Int) -> Unit,
)

/**
 * 홈 (피그마 HF01). 위: 시각·제목·음성 안내 칩, 정보 칩, 필터 칩.
 * 아래 왼쪽: 추천 코스 카드 3개, 오른쪽: 다음 수유·경험 나무 카드. 좁은 창에서는 한 줄씩 쌓는다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: TripUiState,
    isParked: Boolean,
    rangeMeters: Float?,
    visits: List<Visit>,
    saved: List<SavedCourse>,
    actions: HomeActions,
) = DrivingAware(isParked) {
    LaunchedEffect(Unit) { actions.load() }
    val wide = !isNarrowWindow()
    val now = rememberNow()
    val filters = state.homeFilters

    val cards: @Composable (Modifier) -> Unit = { m ->
        Column(modifier = m, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            when {
                state.optionsLoading && state.options.isEmpty() -> LoadingCards(wide)
                state.optionsError != null && state.options.isEmpty() -> ErrorCard(state.optionsError, actions.retry)
                else -> CourseCards(state, wide, actions.openOption)
            }
            if (state.options.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (state.optionsLoading) Text("다른 코스를 찾는 중…", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else PillButton("다른 코스 보기", actions.another)
                }
            }
        }
    }
    val side: @Composable (Modifier) -> Unit = { m ->
        Column(modifier = m, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            FeedingCard(state.feeding, onFed = actions.fed, onInterval = actions.feedingInterval)
            ExperienceSummaryCard(visits, actions.openGrow)
            ShortcutsCard(saved.firstOrNull(), actions.reopenCourse, actions.openCare)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 56.dp, end = 56.dp, top = 48.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // 맨 위: 시각, 제목, 음성 안내 칩
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${nowLabel(now)} · 탑승을 감지했어요", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("오늘 아이와 갈 수 있는 코스", fontSize = 44.sp, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                // 음성 요청(#19)은 아직이라, 예시 문구를 누르면 실내 필터를 켜서 같은 결과를 보여 준다.
                PillButton("\u201c근처 실내 놀거리 찾아줘\u201d", icon = FigmaIcons.Mic, onClick = { if (!filters.indoor) actions.toggleFilter { it.copy(indoor = true) } })
                PillButton("조건 바꾸기", onClick = actions.changeConditions)
            }
        }
        InfoChips(state, rangeMeters)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip("수유실", filters.nursingRoom) { actions.toggleFilter { it.copy(nursingRoom = !it.nursingRoom) } }
            FilterChip("유모차", filters.stroller) { actions.toggleFilter { it.copy(stroller = !it.stroller) } }
            FilterChip("반려동물", filters.pet) { actions.toggleFilter { it.copy(pet = !it.pet) } }
            FilterChip("실내", filters.indoor) { actions.toggleFilter { it.copy(indoor = !it.indoor) } }
            FilterChip("키즈메뉴", filters.kidsMenu) { actions.toggleFilter { it.copy(kidsMenu = !it.kidsMenu) } }
            FilterChip("놀이방", filters.playroom) { actions.toggleFilter { it.copy(playroom = !it.playroom) } }
            FilterChip("주차", filters.parking) { actions.toggleFilter { it.copy(parking = !it.parking) } }
        }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                cards(Modifier.weight(3f))
                side(Modifier.weight(1f))
            }
        } else {
            cards(Modifier.fillMaxWidth())
            side(Modifier.fillMaxWidth())
        }
    }
}

private fun nowLabel(nowMs: Long): String {
    val t = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMs), java.time.ZoneId.systemDefault())
    val day = t.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
    val hour12 = if (t.hour % 12 == 0) 12 else t.hour % 12
    return "$day ${if (t.hour < 12) "오전" else "오후"} $hour12:${"%02d".format(t.minute)}"
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit, icon: ImageVector? = null) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White)
            .border(1.dp, TripColors.Line, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = TripColors.Green, modifier = Modifier.size(26.dp)) }
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}

/** 정보 칩 한 줄: 위치·반경, 날씨, 생후 개월, 반려견, 다음 수유, (읽을 수 있으면) 주행 가능 거리 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoChips(state: TripUiState, rangeMeters: Float?) {
    val child = state.profile?.children?.firstOrNull()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoChip("${state.weather?.region?.takeIf { it.isNotBlank() } ?: "현재 위치"} · 반경 10km", FigmaIcons.Pin)
        state.weather?.let { w ->
            val temp = w.temperature?.let { " · ${Math.round(it)}°C" }.orEmpty()
            // 피그마에는 맑음(해) 아이콘만 있어, 비·눈일 때는 기호로 구분한다.
            if (w.rainy) InfoChip("🌧  ${w.condition}$temp") else InfoChip("${w.condition}$temp", FigmaIcons.Sun)
        }
        child?.let {
            val months = it.ageMonths()
            InfoChip(if (months < 36) "생후 ${ageLabel(months)}" else "아이 ${ageLabel(months)}", FigmaIcons.Baby)
        }
        if (state.homeFilters.pet) InfoChip("반려견 동반", FigmaIcons.Paw)
        state.feeding.nextAt?.let { InfoChip("다음 수유 ${clockLabel(it)}", FigmaIcons.Clock) }
        rangeMeters?.takeIf { it > 0 }?.let { InfoChip("🔋  주행 가능 ${Math.round(it / 1000)}km") }
    }
}

/** 피그마 Context pill: 흰 바탕, 반지름 14, 선 아이콘 24 + 글자 22 */
@Composable
private fun InfoChip(text: String, icon: ImageVector? = null) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = TripColors.InkSoft, modifier = Modifier.size(24.dp)) }
        Text(text, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FilterChip(label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (on) TripColors.GreenSoft else TripColors.Tile)
            .border(2.dp, if (on) TripColors.Green else Color.Transparent, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (on) Icon(FigmaIcons.Check, contentDescription = null, tint = TripColors.Green, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = if (on) TripColors.Green else TripColors.Ink)
    }
}

// 추천 코스 카드

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseCards(state: TripUiState, wide: Boolean, onOpen: (CourseOption) -> Unit) {
    if (state.optionsLoading) Text("추천을 다시 계산하는 중…", fontSize = 20.sp, color = TripColors.Green)
    val cards: @Composable (CourseOption, Modifier) -> Unit = { option, m ->
        CourseCard(option, state.going[option.placeKey] ?: 0, m) { onOpen(option) }
    }
    if (wide) {
        Row(modifier = Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            state.options.forEach { cards(it, Modifier.weight(1f).fillMaxHeight()) }
            // 후보가 3개보다 적어도 카드 폭을 맞춘다
            repeat((3 - state.options.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
        }
    } else {
        state.options.forEach { cards(it, Modifier.fillMaxWidth()) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseCard(option: CourseOption, going: Int, modifier: Modifier, onStart: () -> Unit) {
    val outing = option.course.stops.firstOrNull { it.role == "outing" }?.place
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .border(1.dp, TripColors.Line, RoundedCornerShape(24.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)).background(TripColors.Tile),
            contentAlignment = Alignment.Center,
        ) {
            // 사진이 없거나 못 받으면 분야 아이콘(없으면 피그마의 가방 아이콘)이 보인다
            val themeIcon = THEME_ICONS[outing?.theme]
            if (themeIcon != null) Text(themeIcon, fontSize = 40.sp)
            else Icon(FigmaIcons.Bag, contentDescription = null, tint = TripColors.InkSoft, modifier = Modifier.size(40.dp))
            if (outing != null && outing.imageUrl.isNotBlank()) {
                AsyncImage(model = outing.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Text(option.name, fontSize = 26.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(option.summary, fontSize = 19.sp, lineHeight = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(FigmaIcons.Clock, contentDescription = null, tint = TripColors.InkSoft, modifier = Modifier.size(20.dp))
            Text("총 ${durationLabel(option.totalMinutes.toLong())} · 첫 이동 ${option.firstDriveMinutes}분", fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // 편의 칩: 나들이 장소에서 '있음'이 확인된 것만
        val available = outing?.amenities?.filterValues { it == "available" }?.keys.orEmpty().filter { it in AMENITY_LABELS }
        if (available.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                available.forEach {
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(TripColors.Tile).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(FigmaIcons.Check, contentDescription = null, tint = TripColors.Green, modifier = Modifier.size(18.dp))
                        Text(AMENITY_LABELS.getValue(it), fontSize = 19.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        // 숫자만 보여 주고, 0이면 줄을 숨긴다 (개인 식별 정보 없음)
        if (going > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(TripColors.Green))
                Text("지금 ${going}대 가는 중", fontSize = 19.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(TripColors.Green)
                .clickable(onClick = onStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            Icon(FigmaIcons.Nav, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            Text("코스 시작", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

private val THEME_ICONS = mapOf("nature" to "🌳", "exhibition" to "🏛", "play" to "🧸", "experience" to "🎨", "pet" to "🐕")

@Composable
private fun LoadingCards(wide: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        repeat(if (wide) 3 else 1) {
            Column(
                modifier = Modifier.weight(1f).height(480.dp).clip(RoundedCornerShape(24.dp)).background(Color.White)
                    .border(1.dp, TripColors.Line, RoundedCornerShape(24.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text("추천 코스를 찾는 중…", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.White)
            .border(1.dp, TripColors.Line, RoundedCornerShape(28.dp)).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(message, fontSize = 24.sp, color = MaterialTheme.colorScheme.error)
        PillButton("다시 시도", onRetry)
    }
}

// 오른쪽 열

private fun sideCard(modifier: Modifier = Modifier) = modifier
    .fillMaxWidth()
    .clip(RoundedCornerShape(24.dp))
    .background(Color.White)
    .border(1.dp, TripColors.Line, RoundedCornerShape(24.dp))

/** 우리 아이 경험 나무: 이번 달 방문 수와 분야 수 (기록은 기기 VisitStore) */
@Composable
private fun ExperienceSummaryCard(visits: List<Visit>, onOpen: () -> Unit) {
    val month = LocalDate.now().let { it.year to it.monthValue }
    val thisMonth = visits.filter { it.role == "outing" && (it.date.year to it.date.monthValue) == month }
    val places = thisMonth.map { it.placeName }.distinct().size
    val fields = thisMonth.mapNotNull { it.theme }.distinct().size
    val insight = remember(visits) { experienceInsight(visits) }
    Column(sideCard().padding(horizontal = 28.dp, vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(FigmaIcons.Sprout, contentDescription = null, tint = TripColors.Green, modifier = Modifier.size(24.dp))
            Text("우리 아이 경험 나무", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("이번 달 ${places}곳 · ${fields}개 분야", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text("${insight.title}. ${insight.body}", fontSize = 18.sp, lineHeight = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("경험 나무 보기", fontSize = 19.sp, fontWeight = FontWeight.Medium, color = TripColors.Green)
            Icon(FigmaIcons.Chev, contentDescription = null, tint = TripColors.Green, modifier = Modifier.size(20.dp))
        }
    }
}

/** 혜연 home 프레임에서 가져온 바로가기: 지난 코스 다시 가기, 베이비 케어 */
@Composable
private fun ShortcutsCard(last: SavedCourse?, onReopen: (SavedCourse) -> Unit, onCare: () -> Unit) {
    Column(sideCard().padding(8.dp)) {
        if (last != null) {
            ShortcutRow("🔁  지난 코스 다시 가기", last.title) { onReopen(last) }
        }
        ShortcutRow("🍼  베이비 케어", "수유·공조 프리셋·급한 케어 찾기", onCare)
    }
}

@Composable
private fun ShortcutRow(title: String, sub: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(sub, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
