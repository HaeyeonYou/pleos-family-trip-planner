package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.data.CourseResponse
import io.github.haeyeonyou.familytrip.data.CourseStop
import io.github.haeyeonyou.familytrip.data.StopParking
import io.github.haeyeonyou.familytrip.data.FeedingRoutine
import java.time.Duration
import java.time.LocalTime

/**
 * 코스 상세 (피그마 HF02). 왼쪽 흰 카드에 코스 타임라인, 오른쪽에 지도·도착 정보·대안·코스 시작.
 * 화면 분할(좁은 창)일 때는 지도와 정보를 타임라인 위에 쌓는다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CourseOverview(
    course: CourseResponse,
    loading: Boolean,
    error: String?,
    message: String?,
    feeding: FeedingRoutine,
    onStartGuide: () -> Unit,
    onEdit: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val wide = !isNarrowWindow()
    val outing = course.stops.firstOrNull { it.role == "outing" } ?: course.stops.first()
    val side: @Composable (Modifier) -> Unit = { m ->
        Column(modifier = m, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            CourseMap(
                stops = course.stops,
                path = course.path,
                modifier = Modifier.fillMaxWidth().height(if (wide) 420.dp else 300.dp).clip(RoundedCornerShape(32.dp)),
            )
            ArrivalCard(course, outing, feeding)
            AlternativeRow(onEdit)
            if (loading) CircularProgressIndicator()
            ErrorText(error)
            message?.let { Text(it, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigButton("➤  코스 시작", onStartGuide, modifier = Modifier.weight(1f), enabled = !loading)
                GrayButton("✎  경유지 편집", onEdit)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GrayButton("코스 저장", onSave)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            "←  뒤로",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(vertical = 6.dp),
        )
        FlowRow(verticalArrangement = Arrangement.Bottom, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("${outing.place.name} 코스", fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Text(summary(course), fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        }
        course.warnings.forEach { Text("⚠ $it", fontSize = 20.sp, color = MaterialTheme.colorScheme.tertiary) }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Timeline(course, Modifier.weight(1f))
                side(Modifier.weight(1.15f))
            }
        } else {
            side(Modifier.fillMaxWidth())
            Timeline(course, Modifier.fillMaxWidth())
        }
    }
}

/** "총 4시간 · 첫 이동 18분" */
private fun summary(course: CourseResponse): String {
    val parts = mutableListOf<String>()
    runCatching {
        var minutes = Duration.between(LocalTime.parse(course.departureAt), LocalTime.parse(course.returnAt)).toMinutes()
        if (minutes < 0) minutes += 24 * 60
        parts += if (minutes % 60 == 0L) "총 ${minutes / 60}시간" else "총 ${minutes / 60}시간 ${minutes % 60}분"
    }
    course.stops.firstOrNull()?.let { parts += "첫 이동 ${it.driveMinutes}분" }
    parts += "이동 ${course.totalDriveMinutes}분" + if (course.routeSource == "estimate") " (추정)" else ""
    return parts.joinToString(" · ")
}

@Composable
private fun Timeline(course: CourseResponse, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White)
            .border(1.dp, TripColors.Line, RoundedCornerShape(32.dp))
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("코스 타임라인", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
        TimelineRow(course.departureAt, "출발", hollow = true, highlight = false) {}
        course.stops.forEach { stop ->
            HorizontalDivider(color = TripColors.Line)
            val home = stop.role == "home"
            TimelineRow(stop.arriveAt, if (home) "귀가" else stop.place.name, hollow = home, highlight = false) {
                if (home) {
                    Sub("오늘 경험이 경험 나무에 기록돼요")
                } else {
                    Sub(listOfNotNull(ROLE_LABELS[stop.role], stop.place.shortCategory(), "${stop.stayMinutes}분 머무름").joinToString(" · "))
                    AmenityChips(stop)
                    ParkingInfo(stop.parking)
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(time: String, title: String, hollow: Boolean, highlight: Boolean, body: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (highlight) TripColors.GreenSoft else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(time, fontSize = 22.sp, color = if (highlight) TripColors.Green else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(76.dp))
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (hollow) Color.White else TripColors.Green)
                .border(4.dp, TripColors.Green, CircleShape),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            body()
        }
    }
}

@Composable
private fun Sub(text: String) = Text(text, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** 편의 칩: 있으면 ✓, 정보가 없으면 — (없다는 뜻은 아님). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmenityChips(stop: CourseStop) {
    val amenities = stop.place.amenities
    if (amenities.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        amenities.forEach { (key, value) ->
            val available = value == "available"
            Text(
                (if (available) "✓ " else "— ") + (AMENITY_LABELS[key] ?: key),
                fontSize = 18.sp,
                color = if (available) TripColors.Ink else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(TripColors.Tile).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * 주차 정보: 추천 1순위를 한 줄로 ("주차 공영 120면 · 30분 400원 · 도보 약 2분"), 나머지는 작게 이름·거리만.
 * 목록이 비면 줄 자체를 숨긴다 ('정보 없음'과 '없음'을 구분해 없다고 단정하지 않는다).
 */
@Composable
private fun ParkingInfo(lots: List<StopParking>) {
    val best = lots.firstOrNull() ?: return
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TripColors.GreenSoft).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(parkingLine(best), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = TripColors.Ink)
        parkingNote(best)?.let { Text(it, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        lots.drop(1).forEach {
            Text("다른 곳 · ${it.name} · ${walkText(it)}", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun walkText(lot: StopParking) = "도보 약 ${lot.walkMinutes}분"

/** "주차 공영 120면 · 30분 400원 · 도보 약 2분" — 모르는 항목은 뺀다. */
internal fun parkingLine(lot: StopParking): String =
    listOfNotNull(
        "주차 " + listOfNotNull(lot.kind, lot.capacity?.let { "${it}면" }).joinToString(" ").ifBlank { lot.name },
        lot.feeSummary.takeIf { it.isNotBlank() },
        walkText(lot),
    ).joinToString(" · ")

/** 이름과, 도착 시각에 운영하지 않을 때의 경고. 운영시간을 모르면 아무 말도 하지 않는다. */
internal fun parkingNote(lot: StopParking): String? =
    when (lot.openAtArrival) {
        false -> "${lot.name} · 도착 때 운영 안 함 (${lot.hours})"
        else -> lot.name
    }

/** 도착 정보 (HF02: 도착 시각과 도착 기준 다음 수유까지 여유). */
@Composable
private fun ArrivalCard(course: CourseResponse, outing: CourseStop, feeding: FeedingRoutine) {
    val now = rememberNow()
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(TripColors.GreenSoft).padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("◷", fontSize = 28.sp, color = TripColors.Green)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${outing.place.name} 도착 ${outing.arriveAt} · 귀가 ${course.returnAt}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                feedingSlackText(feeding, outing.arriveAt, now) ?: "케어 탭에서 수유 시각을 기록하면 도착 때 다음 수유까지 여유를 알려 드려요",
                fontSize = 18.sp,
                color = TripColors.Green,
            )
        }
    }
}

@Composable
private fun AlternativeRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .border(1.dp, TripColors.Line, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⟳  계획이 바뀌었나요? 휴무·마감이면 대안 장소로", fontSize = 20.sp, modifier = Modifier.weight(1f))
        Text("대안 보기 ›", fontSize = 20.sp, color = TripColors.Green)
    }
}

@Composable
private fun GrayButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(TripColors.Tile)
            .clickable(onClick = onClick)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 20.dp)) }
}
