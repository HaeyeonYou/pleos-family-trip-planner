package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.data.SavedCourse
import io.github.haeyeonyou.familytrip.data.Visit
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 경험 분야 (지수 HF04: 쇼핑·놀이 / 전시·과학 / 체험·클래스 / 자연). 서버 Theme 값 기준. */
val EXPERIENCE_FIELDS = linkedMapOf(
    "play" to "쇼핑·놀이",
    "exhibition" to "전시·과학",
    "experience" to "체험·클래스",
    "nature" to "자연",
)

// Figma HF 04의 위쪽 카드 안 높이: 그래프 아래 안내 배너 (패딩 20x2 + 두 줄)
private val INSIGHT_BAR_HEIGHT = 100.dp
private val OnGreen = Color(0xFF0A8A60)

/**
 * 그로우 탭: 우리 아이 경험 우주 (Figma HF 04, #68·#129).
 * 지금까지 다녀온 나들이를 분야·장소 노드로 보여 주고, 비어 있는 분야를 다음 나들이 힌트로 알려 준다.
 * 기록은 차 안에만 있다. 모바일 QR(#22)은 자리만 두었다.
 */
@Composable
fun ExperienceScreen(
    visits: List<Visit>,
    courses: List<SavedCourse>,
    childName: String,
    isParked: Boolean,
    onOpenCourse: (SavedCourse) -> Unit,
    onPlan: () -> Unit,
) = DrivingAware(isParked) {
    val data = remember(visits, courses) { buildUniverse(visits, placeTags(courses)) }
    val insight = remember(visits) { experienceInsight(visits) }
    // 같은 곳은 가장 최근 방문 한 줄만
    val recent = remember(visits) {
        visits.filter { it.role == "outing" }.sortedByDescending { it.arrivedAt }.distinctBy { it.placeName }.take(3)
    }
    val name = childName.ifBlank { "우리 아이" }
    var selected by remember { mutableStateOf<String?>(null) }

    fun courseWith(placeName: String) = courses.firstOrNull { c -> c.course.stops.any { it.place.name == placeName } }

    val card: @Composable (Dp?) -> Unit = { maxHeight ->
        UniverseCard(
            data = data,
            insight = insight,
            centerLabel = name,
            selected = selected,
            courseFor = ::courseWith,
            onSelect = { selected = it?.name },
            onOpenCourse = onOpenCourse,
            onPlan = onPlan,
            maxHeight = maxHeight,
        )
    }
    val side: @Composable ColumnScope.() -> Unit = {
        RecentCard(recent)
        ReturnCard(visits, courses, onOpenCourse)
        QrPlaceholder()
    }
    val narrow = isNarrowWindow()
    val title: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                if (name == "우리 아이") "우리 아이 경험 우주" else "${name}의 경험 우주",
                fontSize = if (narrow) 36.sp else 44.sp,
                fontWeight = FontWeight.Bold,
                color = TripColors.Ink,
            )
            Text(summary(data), fontSize = 22.sp, color = TripColors.InkSoft, modifier = Modifier.padding(bottom = 6.dp))
        }
    }

    if (narrow) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            title()
            card(null)
            side()
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, top = 44.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            title()
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                // Figma: 왼쪽 카드 940 : 오른쪽 700
                Box(modifier = Modifier.weight(940f).fillMaxSize()) { card(Dp.Unspecified) }
                Column(
                    modifier = Modifier.weight(700f).fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    content = side,
                )
            }
        }
    }
}

/** "지난 3개월 · 9곳 · 4개 분야" */
internal fun summary(data: UniverseData, today: LocalDate = LocalDate.now()): String {
    if (data.isEmpty) return "아직 비어 있어요 · 첫 나들이로 시작해요"
    val months = ChronoUnit.MONTHS.between(data.places.minOf { it.last }, today).coerceAtLeast(1)
    return "지난 ${months}개월 · ${data.places.size}곳 · ${data.fields.count { it.count > 0 }}개 분야"
}

/** 흰 카드 안에 별 배경 그래프(940x690 비율)와 아래 안내 배너. 창이 낮으면 그래프를 비율 그대로 줄인다. */
@Composable
private fun UniverseCard(
    data: UniverseData,
    insight: Insight,
    centerLabel: String,
    selected: String?,
    courseFor: (String) -> SavedCourse?,
    onSelect: (PlaceNode?) -> Unit,
    onOpenCourse: (SavedCourse) -> Unit,
    onPlan: () -> Unit,
    maxHeight: Dp?,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val byWidth = maxWidth * (DESIGN_H / DESIGN_W)
        val limit = if (maxHeight == null || !this.constraints.hasBoundedHeight) null else this.maxHeight
        val graphHeight = limit?.let { minOf(byWidth, (it - INSIGHT_BAR_HEIGHT).coerceAtLeast(240.dp)) } ?: byWidth
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(TripColors.Card).border(1.dp, TripColors.Line, RoundedCornerShape(24.dp))) {
            Box(modifier = Modifier.fillMaxWidth().height(graphHeight)) {
                UniverseCanvas(data, centerLabel, selected, onPlaceClick = onSelect, modifier = Modifier.fillMaxSize())
                data.places.firstOrNull { it.name == selected }?.let { place ->
                    val course = courseFor(place.name)
                    SelectedPlaceCard(
                        place = place,
                        fieldLabel = FIELD_TITLES[place.field].orEmpty(),
                        hasCourse = course != null,
                        onOpenCourse = { course?.let(onOpenCourse) },
                        onClose = { onSelect(null) },
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 64.dp, end = 120.dp),
                    )
                }
            }
            InsightBar(insight, onPlan)
        }
    }
}

@Composable
private fun InsightBar(insight: Insight, onPlan: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(TripColors.GreenSoft).padding(horizontal = 28.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SvgIcon(ICON_SPROUT, OnGreen, 26.dp, 26f)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(insight.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = TripColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(insight.body, fontSize = 18.sp, color = TripColors.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        insight.action?.let {
            Row(
                modifier = Modifier.heightIn(min = 64.dp).clickable(onClick = onPlan),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(it, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = OnGreen, maxLines = 1)
                SvgIcon(ICON_CHEV, OnGreen, 20.dp, 20f)
            }
        }
    }
}

@Composable
private fun SideCard(title: String, gap: Dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(TripColors.Card)
            .border(1.dp, TripColors.Line, RoundedCornerShape(24.dp)).padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = TripColors.Ink)
        content()
    }
}

@Composable
private fun RecentCard(recent: List<Visit>) = SideCard("최근 방문", 0.dp) {
    if (recent.isEmpty()) {
        Text("아직 다녀온 곳이 없어요", fontSize = 20.sp, color = TripColors.InkSoft, modifier = Modifier.padding(top = 14.dp))
        return@SideCard
    }
    recent.forEachIndexed { i, v ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(v.date.format(MONTH_DAY), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = TripColors.InkSoft)
            Text(v.placeName, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = TripColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            v.theme?.let { FIELD_TITLES[it] }?.let {
                Text(
                    it, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TripColors.InkSoft,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(TripColors.Tile).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        if (i < recent.lastIndex) HorizontalDivider(color = TripColors.Line)
    }
}

/** 다녀온 장소가 들어 있는 저장 코스. 다시 출발(#51)은 아직이라 코스 화면을 다시 연다. */
@Composable
private fun ReturnCard(visits: List<Visit>, courses: List<SavedCourse>, onOpenCourse: (SavedCourse) -> Unit) = SideCard("다시 가기 코스", 12.dp) {
    val rows = courses.mapNotNull { c ->
        val names = c.course.stops.filter { it.role == "outing" }.map { it.place.name }.toSet()
        val hits = visits.filter { it.role == "outing" && it.placeName in names }
        // 몇 회: 코스 안 나들이 장소 중 가장 자주 간 곳의 방문 횟수
        if (hits.isEmpty()) null else Triple(c, hits.groupingBy { it.placeName }.eachCount().values.max(), hits.maxOf { it.date })
    }.distinctBy { it.first.title }.take(2)
    if (rows.isEmpty()) {
        Text("다녀온 장소가 든 코스를 저장하면 여기서 다시 볼 수 있어요", fontSize = 18.sp, color = TripColors.InkSoft)
        return@SideCard
    }
    rows.forEach { (course, count, last) ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${course.title} 코스", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = TripColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${count}회 · 마지막 ${last.format(MONTH_DAY)}", fontSize = 17.sp, color = TripColors.InkSoft)
            }
            RestartButton { onOpenCourse(course) }
        }
    }
}

/** Figma 초록 '다시 출발' 버튼 (높이 64, 모서리 20, 아이콘 + 글자 26). */
@Composable
private fun RestartButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier.height(64.dp).clip(RoundedCornerShape(20.dp)).background(OnGreen).clickable(onClick = onClick).padding(horizontal = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SvgIcon(ICON_NAV, Color.White, 26.dp, 26f)
        Text("다시 출발", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
    }
}

/** 모바일 QR(#22) 자리. 기능은 이번 범위가 아니다. */
@Composable
private fun QrPlaceholder() {
    val line = TripColors.Line
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .drawBehind {
                val sw = 2.dp.toPx()
                drawRoundRect(
                    line, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), CornerRadius(24.dp.toPx()),
                    style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))),
                )
            }
            .padding(horizontal = 28.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(TripColors.Tile), contentAlignment = Alignment.Center) {
            SvgIcon(ICON_QR, TripColors.InkSoft, 24.dp, 24f)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("모바일에서 크게 보기 · 확장", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TripColors.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("QR을 스캔하면 경험 나무 전체를 볼 수 있어요", fontSize = 17.sp, color = TripColors.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
