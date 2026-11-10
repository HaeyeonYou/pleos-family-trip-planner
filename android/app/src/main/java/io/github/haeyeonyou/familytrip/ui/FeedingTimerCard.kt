package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.data.FeedingRoutine
import io.github.haeyeonyou.familytrip.data.clockLabel
import io.github.haeyeonyou.familytrip.data.durationLabel

/** 수유 타이머 카드에 그릴 문구와 상태. 시각만 바뀌므로 화면과 따로 두어 시험한다. */
data class FeedingTimerView(
    /** 다음 수유 시각 "12:30". 기록이 없으면 null */
    val nextClock: String?,
    /** 다음 수유 시각이 이미 지났다 */
    val passed: Boolean,
    /** 지났을 때만: "8시간 52분 지났어요" */
    val passedText: String?,
    /** "마지막 09:30 · 간격 3시간", 기록이 없으면 안내 문구 */
    val info: String,
    /** 0~1 */
    val progress: Float,
)

fun feedingTimerView(routine: FeedingRoutine, now: Long): FeedingTimerView {
    val last = routine.lastFedAt
    val next = routine.nextAt
    if (last == null || next == null) {
        return FeedingTimerView(null, false, null, "마지막 수유 시각을 기록하면 다음 수유를 알려 드려요", 0f)
    }
    val remaining = routine.remainingMinutes(now) ?: 0L
    return FeedingTimerView(
        nextClock = clockLabel(next),
        passed = remaining < 0,
        passedText = if (remaining < 0) "${durationLabel(remaining)} 지났어요" else null,
        info = "마지막 ${clockLabel(last)} · 간격 ${intervalLabel(routine.intervalMinutes)}",
        progress = routine.progress(now),
    )
}
