package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.data.CoursePlace
import io.github.haeyeonyou.familytrip.data.CourseResponse
import io.github.haeyeonyou.familytrip.data.CourseStop

@Composable
fun CourseScreen(
    course: CourseResponse,
    isParked: Boolean,
    loading: Boolean,
    error: String?,
    onSwap: (stopIndex: Int, place: CoursePlace) -> Unit,
    onStartGuide: () -> Unit,
    onBack: () -> Unit,
) = DrivingAware(isParked) {
    // 교체 후보를 펼친 장소 (한 번에 하나만)
    var swapping by remember { mutableStateOf<Int?>(null) }
    Column(
        modifier = Modifier.padding(40.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("오늘의 코스", fontSize = 40.sp)
        Text(
            "${course.returnAt} 귀가 · 이동 ${course.totalDriveMinutes}분" +
                if (course.routeSource == "estimate") " (추정)" else "",
            fontSize = 26.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        course.warnings.forEach { Text("⚠ $it", fontSize = 22.sp, color = MaterialTheme.colorScheme.tertiary) }
        if (loading) CircularProgressIndicator()
        ErrorText(error)
        course.stops.forEachIndexed { index, stop ->
            val alternatives = course.alternatives[stop.role].orEmpty()
            StopCard(
                stop = stop,
                alternatives = alternatives,
                expanded = swapping == index,
                enabled = !loading,
                onToggle = { swapping = if (swapping == index) null else index },
                onPick = { place ->
                    swapping = null
                    onSwap(index, place)
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigButton("안내 시작", onStartGuide, modifier = Modifier.weight(1f), enabled = !loading)
            BigOutlinedButton("조건 바꾸기", onBack)
        }
    }
}

@Composable
private fun StopCard(
    stop: CourseStop,
    alternatives: List<CoursePlace>,
    expanded: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onPick: (CoursePlace) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.width(140.dp)) {
                    Text(stop.arriveAt, fontSize = 30.sp)
                    Text(ROLE_LABELS[stop.role] ?: stop.role, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stop.place.name, fontSize = 28.sp)
                    val detail = buildList {
                        add("이동 ${stop.driveMinutes}분")
                        if (stop.stayMinutes > 0) add("머무름 ${stop.stayMinutes}분")
                        stop.place.shortCategory()?.let(::add)
                    }
                    Text(detail.joinToString(" · "), fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AmenityBadges(stop.place.amenities)
                }
                if (alternatives.isNotEmpty()) {
                    OutlinedButton(onClick = onToggle, enabled = enabled, modifier = Modifier.heightIn(min = 64.dp)) {
                        Text(if (expanded) "닫기" else "다른 곳", fontSize = 22.sp)
                    }
                }
            }
            if (expanded) {
                HorizontalDivider()
                Text("이 장소 대신 갈 수 있는 곳", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                alternatives.forEach { place -> AlternativeRow(place, enabled) { onPick(place) } }
            }
        }
    }
}

@Composable
private fun AlternativeRow(place: CoursePlace, enabled: Boolean, onPick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(place.name, fontSize = 24.sp)
                listOfNotNull(place.shortCategory(), place.address.takeIf { it.isNotBlank() })
                    .takeIf { it.isNotEmpty() }
                    ?.let { Text(it.joinToString(" · "), fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                AmenityBadges(place.amenities)
            }
            BigButton("이곳으로", onPick, enabled = enabled)
        }
    }
}

/** 카카오 분류는 "음식점 > 한식"처럼 오므로 마지막 단계만 쓴다. TourAPI 분류코드는 보여 주지 않는다. */
private fun CoursePlace.shortCategory(): String? =
    category.substringAfterLast(" > ").takeIf { it.isNotBlank() && source == "kakao" }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmenityBadges(amenities: Map<String, String>) {
    if (amenities.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        amenities.forEach { (key, value) ->
            val label = NEED_LABELS[key] ?: key
            // '정보 없음'은 없다는 뜻이 아니므로 그대로 표시한다.
            AssistChip(onClick = {}, label = { Text(if (value == "available") "$label 있음" else "$label 정보 없음", fontSize = 18.sp) })
        }
    }
}
