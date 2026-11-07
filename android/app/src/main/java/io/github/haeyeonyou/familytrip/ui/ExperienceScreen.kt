package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
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

/**
 * 그로우 탭: 우리 아이 경험 우주 (HF04, #68).
 * 지금까지 다녀온 나들이를 분야·장소 노드로 보여 주고, 비어 있는 분야를 다음 나들이 힌트로 알려 준다.
 * 기록은 차 안에만 있다. 다시 가기(#51)와 모바일 QR(#22)은 자리만 두었다.
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
    val data = remember(visits) { buildUniverse(visits) }
    val insight = remember(visits) { experienceInsight(visits) }
    val recent = remember(visits) { visits.filter { it.role == "outing" }.sortedByDescending { it.arrivedAt }.take(3) }
    val name = childName.ifBlank { "우리 아이" }
    var selected by remember { mutableStateOf<String?>(null) }

    fun courseWith(placeName: String) = courses.firstOrNull { c -> c.course.stops.any { it.place.name == placeName } }

    val panel: @Composable (Modifier) -> Unit = { modifier ->
        UniversePanel(
            data = data,
            insight = insight,
            centerLabel = name,
            selected = selected,
            courseFor = ::courseWith,
            onSelect = { selected = it?.name },
            onOpenCourse = onOpenCourse,
            onPlan = onPlan,
            modifier = modifier,
        )
    }
    val side: @Composable ColumnScope.() -> Unit = {
        RecentCard(recent)
        ReturnCard(visits, courses, onOpenCourse)
        QrPlaceholder()
    }
    val title: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${name}의 경험 우주", fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(summary(data), fontSize = 22.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        }
    }

    if (isNarrowWindow()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            title()
            panel(Modifier.fillMaxWidth().height(900.dp))
            side()
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            title()
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                panel(Modifier.weight(1.8f).fillMaxSize())
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp), content = side)
            }
        }
    }
}

/** "지난 3개월 · 9곳 · 4개 분야" */
private fun summary(data: UniverseData, today: LocalDate = LocalDate.now()): String {
    if (data.isEmpty) return "아직 비어 있어요 · 첫 나들이로 시작해요"
    val months = ChronoUnit.MONTHS.between(data.places.minOf { it.last }, today).coerceAtLeast(1)
    return "지난 ${months}개월 · ${data.places.size}곳 · ${data.fields.count { it.count > 0 }}개 분야"
}

@Composable
private fun UniversePanel(
    data: UniverseData,
    insight: Insight,
    centerLabel: String,
    selected: String?,
    courseFor: (String) -> SavedCourse?,
    onSelect: (PlaceNode?) -> Unit,
    onOpenCourse: (SavedCourse) -> Unit,
    onPlan: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.clip(RoundedCornerShape(24.dp)).background(TripColors.GreenSoft)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            UniverseCanvas(data, centerLabel, selected, onPlaceClick = onSelect, modifier = Modifier.fillMaxSize())
            if (data.isEmpty) {
                Text(
                    "나들이 장소에 도착하면\n우주에 별이 하나씩 생겨요",
                    fontSize = 22.sp,
                    color = Color(0xFF9AA3BD),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 150.dp),
                )
            }
            data.places.firstOrNull { it.name == selected }?.let { place ->
                val course = courseFor(place.name)
                SelectedPlaceCard(
                    place = place,
                    fieldLabel = EXPERIENCE_FIELDS[place.field].orEmpty(),
                    hasCourse = course != null,
                    onOpenCourse = { course?.let(onOpenCourse) },
                    onClose = { onSelect(null) },
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 72.dp, end = 120.dp),
                )
            }
        }
        InsightBar(insight, onPlan)
    }
}

@Composable
private fun InsightBar(insight: Insight, onPlan: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(insight.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TripColors.Ink)
            Text(insight.body, fontSize = 20.sp, color = TripColors.InkSoft)
        }
        insight.action?.let { OutlinedButton(onClick = onPlan, modifier = Modifier.heightIn(min = 64.dp)) { Text("$it ›", fontSize = 22.sp, color = TripColors.Green) } }
    }
}

@Composable
private fun SideCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun RecentCard(recent: List<Visit>) = SideCard("최근 방문") {
    if (recent.isEmpty()) {
        Text("아직 다녀온 곳이 없어요", fontSize = 22.sp, color = TripColors.InkSoft)
        return@SideCard
    }
    recent.forEachIndexed { i, v ->
        if (i > 0) HorizontalDivider(color = TripColors.Line)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(v.date.format(MONTH_DAY), fontSize = 20.sp, color = TripColors.InkSoft, modifier = Modifier.width(76.dp))
            Text(v.placeName, fontSize = 24.sp, modifier = Modifier.weight(1f))
            v.theme?.let { EXPERIENCE_FIELDS[it] }?.let {
                Text(it, fontSize = 17.sp, color = TripColors.InkSoft, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(TripColors.Tile).padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
}

/** 다녀온 장소가 들어 있는 저장 코스. 다시 출발(#51)은 아직이라 코스 화면을 다시 연다. */
@Composable
private fun ReturnCard(visits: List<Visit>, courses: List<SavedCourse>, onOpenCourse: (SavedCourse) -> Unit) = SideCard("다시 가기 코스") {
    val rows = courses.mapNotNull { c ->
        val names = c.course.stops.filter { it.role == "outing" }.map { it.place.name }.toSet()
        val hits = visits.filter { it.role == "outing" && it.placeName in names }
        if (hits.isEmpty()) null else Triple(c, hits.size, hits.maxOf { it.date })
    }.distinctBy { it.first.title }.take(2)
    if (rows.isEmpty()) {
        Text("다녀온 장소가 든 코스를 저장하면 여기서 다시 볼 수 있어요", fontSize = 20.sp, color = TripColors.InkSoft)
        return@SideCard
    }
    rows.forEach { (course, count, last) ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${course.title} 코스", fontSize = 24.sp)
                Text("${count}회 · 마지막 ${last.format(MONTH_DAY)}", fontSize = 18.sp, color = TripColors.InkSoft)
            }
            Button(onClick = { onOpenCourse(course) }, modifier = Modifier.heightIn(min = 64.dp)) { Text("코스 다시 보기", fontSize = 20.sp) }
        }
    }
}

/** 모바일 QR(#22) 자리. 기능은 이번 범위가 아니다. */
@Composable
private fun QrPlaceholder() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, TripColors.Line),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("모바일에서 크게 보기 · 확장", fontSize = 22.sp)
            Text("QR을 스캔하면 경험 우주 전체를 볼 수 있어요 (준비 중)", fontSize = 18.sp, color = TripColors.InkSoft)
        }
    }
}
