package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.data.Visit
import java.time.LocalDate
import java.time.LocalDateTime

/** 경험 분야 (지수 덱: 자연 / 전시·과학 / 체험·클래스 / 놀이). 서버 Theme 값 기준. */
val EXPERIENCE_FIELDS = linkedMapOf(
    "nature" to "자연",
    "exhibition" to "전시·과학",
    "experience" to "체험·클래스",
    "play" to "놀이",
)

/**
 * 오늘의 우리 아이 하루 (혜연 P07 최소 버전, #11).
 * 오늘 도착한 장소 타임라인과, 지금까지 다녀온 나들이의 경험 분야 분포를 보여 준다.
 * 기록은 차 안에만 있다.
 */
@Composable
fun ExperienceScreen(visits: List<Visit>, isParked: Boolean, onBack: () -> Unit) = DrivingAware(isParked) {
    val today = LocalDate.now()
    val todays = visits.filter { it.date == today }
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("오늘의 우리 아이 하루", fontSize = 40.sp)
            Text("도착하면 자동으로 남아요. 기록은 차 안에만 저장돼요.", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (todays.isEmpty()) {
                Text("오늘은 아직 다녀온 곳이 없어요", fontSize = 26.sp)
            } else {
                todays.forEach { TimelineRow(it) }
            }

            HorizontalDivider()
            FieldSummary(visits)
        }
        HorizontalDivider()
        Row(modifier = Modifier.padding(horizontal = 40.dp, vertical = 16.dp)) {
            BigOutlinedButton("돌아가기", onBack)
        }
    }
}

@Composable
private fun TimelineRow(visit: Visit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(140.dp)) {
                Text(LocalDateTime.parse(visit.arrivedAt).toLocalTime().toString().take(5), fontSize = 30.sp)
                Text(ROLE_LABELS[visit.role] ?: visit.role, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(visit.placeName, fontSize = 28.sp)
                val detail = listOfNotNull(
                    visit.stayMinutes()?.let { "${it}분 머무름" } ?: "머무는 중",
                    visit.theme?.let { EXPERIENCE_FIELDS[it] }?.let { "$it 경험" },
                )
                Text(detail.joinToString(" · "), fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 지금까지 다녀온 나들이의 분야별 횟수. 가장 적은 분야는 다음 추천 힌트로 보여 준다 (#18에서 추천에 반영). */
@Composable
private fun FieldSummary(visits: List<Visit>) {
    val outings = visits.filter { it.role == "outing" && it.theme != null }
    Text("지금까지의 경험", fontSize = 28.sp)
    if (outings.isEmpty()) {
        Text("나들이 장소에 도착하면 분야별로 쌓여요", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val counts = EXPERIENCE_FIELDS.keys.associateWith { field -> outings.count { it.theme == field } }
    val max = counts.values.max().coerceAtLeast(1)
    counts.forEach { (field, count) ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(EXPERIENCE_FIELDS.getValue(field), fontSize = 22.sp, modifier = Modifier.width(140.dp))
            Box(
                modifier = Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (count > 0) {
                    Box(
                        modifier = Modifier.fillMaxHeight().fillMaxWidth(count / max.toFloat())
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Text("${count}번", fontSize = 22.sp, modifier = Modifier.width(80.dp))
        }
    }
    val least = counts.minBy { it.value }
    if (least.value < max) {
        Text(
            "${EXPERIENCE_FIELDS.getValue(least.key)} 경험이 가장 적어요. 다음엔 ${EXPERIENCE_FIELDS.getValue(least.key)} 나들이는 어때요?",
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
