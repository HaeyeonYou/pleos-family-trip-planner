package io.github.haeyeonyou.familytrip.ui

import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.data.FeedingRoutine
import io.github.haeyeonyou.familytrip.data.clockLabel
import io.github.haeyeonyou.familytrip.data.durationLabel
import kotlinx.coroutines.delay

/** 지금 시각. 30초마다 갱신해 남은 시간이 살아 있게 한다. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** 간격 표시: 150분 → "2시간 30분" */
fun intervalLabel(minutes: Int): String = durationLabel(minutes.toLong())

/**
 * 다음 수유까지 카드 (피그마 HF01 오른쪽 위). 남은 시간, 진행 막대, 마지막 수유·간격,
 * '지금 수유했어요'와 간격 변경(2 / 2.5 / 3 / 4시간)을 둔다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedingCard(
    routine: FeedingRoutine,
    onFed: () -> Unit,
    onInterval: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = rememberNow()
    var choosing by rememberSaveable { mutableStateOf(false) }
    val remaining = routine.remainingMinutes(now)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(TripColors.Card)
            .border(1.dp, TripColors.Line, RoundedCornerShape(24.dp))
            .padding(horizontal = 28.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(FigmaIcons.Milk, contentDescription = null, tint = TripColors.Green, modifier = Modifier.size(24.dp))
            Text("다음 수유까지", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            when {
                remaining == null -> "기록 없음"
                remaining < 0 -> "${durationLabel(remaining)} 지났어요"
                else -> durationLabel(remaining)
            },
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            color = if (remaining != null && remaining < 0) MaterialTheme.colorScheme.tertiary else TripColors.Ink,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(TripColors.Tile)
                .drawBehind {
                    drawRect(TripColors.Green, size = Size(size.width * routine.progress(now), size.height))
                },
        )
        Text(
            if (routine.lastFedAt == null) "마지막 수유 시각을 기록하면 시간을 알려 드려요"
            else "마지막 수유 ${clockLabel(routine.lastFedAt)} · 다음 ${clockLabel(routine.nextAt!!)} · 간격 ${intervalLabel(routine.intervalMinutes)}",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "지금 수유했어요",
                maxLines = 1,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(TripColors.Green)
                    .clickable(onClick = onFed)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            )
            Text(
                if (choosing) "닫기" else "간격",
                fontSize = 20.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(TripColors.Tile)
                    .clickable { choosing = !choosing }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
        if (choosing) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FeedingRoutine.INTERVAL_CHOICES.forEach { minutes ->
                    val on = minutes == routine.intervalMinutes
                    Text(
                        intervalLabel(minutes),
                        fontSize = 20.sp,
                        color = if (on) TripColors.Green else TripColors.Ink,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (on) TripColors.GreenSoft else TripColors.Tile)
                            .border(if (on) 2.dp else 0.dp, if (on) TripColors.Green else Color.Transparent, RoundedCornerShape(20.dp))
                            .clickable { onInterval(minutes) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

/** 코스 도착 카드에 쓸 문구: 도착 시각 기준 다음 수유까지 여유. 기록이 없으면 null. */
fun feedingSlackText(routine: FeedingRoutine, arriveAt: String, now: Long): String? {
    val slack = routine.slackAtArrival(arriveAt, now) ?: return null
    val next = clockLabel(routine.nextAt!!)
    return if (slack >= 0) "도착 시각 기준 다음 수유(${next})까지 ${durationLabel(slack)} 여유" 
    else "도착 전 ${next}에 수유 시간이에요 (${durationLabel(slack)} 일찍) · 경로 위 수유실을 확인해 보세요"
}
