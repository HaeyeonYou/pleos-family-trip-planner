package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.care.CareKind
import io.github.haeyeonyou.familytrip.navi.DelayAlertController
import io.github.haeyeonyou.familytrip.navi.DelayAlertUi
import io.github.haeyeonyou.familytrip.navi.DelayCandidate

/**
 * 주행 중 지연 알림 (피그마 HF06). 정체로 도착이 5분 이상 늦어지면 앱 화면 위에 덮인다.
 * 위: 경고 아이콘 + "정체로 도착이 N분 늦어져요" + 도착 예정 시각. 가운데: 경유지 후보 카드(1·2번). 아래: "그대로 가기".
 * 주행 중에도 쓰도록 버튼은 크게, 키보드 입력은 없다. 피그마의 "첫 번째로 가 줘" 음성 안내는 음성 명령이 아직 없어 넣지 않았다.
 */
@Composable
fun DelayAlertPanel(
    alert: DelayAlertUi?,
    onAddWaypoint: (DelayCandidate) -> Unit,
    onKeepGoing: () -> Unit,
) {
    if (alert == null) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TripColors.Panel)
            .clickable(enabled = false) {},
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(if (SitterSurface.current.dark) Color(0xFF3A2E16) else TripColors.AmberSoft),
                    contentAlignment = Alignment.Center,
                ) { Text("⚠", fontSize = 34.sp, color = Color(0xFFB45309)) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        DelayAlertController.headline(alert),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "도착 예정 ${alert.expectedArrival} · 원래 ${alert.plannedArrival}",
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(alert.cause.detail, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            when {
                alert.loading -> Text("쉬어 갈 곳을 찾고 있어요…", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                alert.candidates.isEmpty() -> alert.message?.let {
                    Text(it, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> alert.candidates.forEachIndexed { index, candidate ->
                    CandidateCard(index + 1, candidate, primary = index == 0, onClick = { onAddWaypoint(candidate) })
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(TripColors.Tile)
                    .clickable(onClick = onKeepGoing),
                contentAlignment = Alignment.Center,
            ) { Text("그대로 가기", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface) }
        }
    }
}

/** 경유지 후보 한 장. 1번은 primary 바탕, 나머지는 카드 바탕 (HF06). 수면(다크)에서도 셸 토큰을 따른다. */
@Composable
private fun CandidateCard(number: Int, candidate: DelayCandidate, primary: Boolean, onClick: () -> Unit) {
    val place = candidate.place
    val shape = RoundedCornerShape(24.dp)
    val s = SitterSurface.current
    val titleColor = if (primary) s.onPrimary else MaterialTheme.colorScheme.onSurface
    val subColor = if (primary) s.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .clip(shape)
            .background(if (primary) TripColors.Green else TripColors.Card)
            .then(if (primary) Modifier else Modifier.border(BorderStroke(1.dp, TripColors.Line), shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(number.toString(), fontSize = 48.sp, fontWeight = FontWeight.Bold, color = if (primary) s.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            modifier = Modifier.size(68.dp).clip(CircleShape).background(if (primary) s.onPrimary.copy(alpha = 0.15f) else TripColors.GreenSoft),
            contentAlignment = Alignment.Center,
        ) { Text(if (candidate.kind == CareKind.NURSING) "🍼" else "🅿", fontSize = 30.sp) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(place.name, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = titleColor)
            Text(detailLine(candidate), fontSize = 21.sp, color = subColor)
        }
        Text("경유지로 추가", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = if (primary) s.onPrimary else TripColors.Green)
    }
}

/** "경유 +4분 · 수유실" 꼴. 경로를 모르면 현재 위치에서 걸리는 시간으로 바꾼다. */
internal fun detailLine(candidate: DelayCandidate): String {
    val place = candidate.place
    val time = place.detourMinutes?.let { "경유 +${it}분" } ?: "차로 ${place.driveMinutes}분"
    return listOf(time, candidate.kind.label, place.note.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · ")
}
