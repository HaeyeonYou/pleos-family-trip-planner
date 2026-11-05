package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.data.CourseResponse
import io.github.haeyeonyou.familytrip.data.CourseStop

@Composable
fun CourseScreen(
    course: CourseResponse,
    isParked: Boolean,
    onStartGuide: () -> Unit,
    onBack: () -> Unit,
) = DrivingAware(isParked) {
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
        course.stops.forEach { StopRow(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigButton("안내 시작", onStartGuide, modifier = Modifier.weight(1f))
            BigOutlinedButton("조건 바꾸기", onBack)
        }
    }
}

@Composable
private fun StopRow(stop: CourseStop) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(140.dp)) {
                Text(stop.arriveAt, fontSize = 30.sp)
                Text(ROLE_LABELS[stop.role] ?: stop.role, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stop.place.name, fontSize = 28.sp)
                val detail = buildList {
                    add("이동 ${stop.driveMinutes}분")
                    if (stop.stayMinutes > 0) add("머무름 ${stop.stayMinutes}분")
                    stop.place.category.substringAfterLast(" > ").takeIf { it.isNotBlank() && stop.place.source == "kakao" }?.let(::add)
                }
                Text(detail.joinToString(" · "), fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AmenityBadges(stop.place.amenities)
            }
        }
    }
}

@Composable
private fun AmenityBadges(amenities: Map<String, String>) {
    if (amenities.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        amenities.forEach { (key, value) ->
            val label = NEED_LABELS[key] ?: key
            // '정보 없음'은 없다는 뜻이 아니므로 그대로 표시한다.
            AssistChip(onClick = {}, label = { Text(if (value == "available") "$label 있음" else "$label 정보 없음", fontSize = 18.sp) })
        }
    }
}
