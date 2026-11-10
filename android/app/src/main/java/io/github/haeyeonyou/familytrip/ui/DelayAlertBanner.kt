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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.data.FeedingRoutine
import io.github.haeyeonyou.familytrip.data.clockLabel
import io.github.haeyeonyou.familytrip.navi.DelayAlertUi
import io.github.haeyeonyou.familytrip.navi.TrafficCause

/** 지연 알림 줄에 그릴 문구. */
data class DelayBannerView(
    /** "정체로 도착이 12:45로 늦어져요" */
    val title: String,
    /** "다음 수유(12:30) 전에 들를 수 있는 곳이 경로 위에 2곳 있어요" */
    val subtitle: String,
    /** '경유지 추가'를 누를 수 있는지 (후보를 찾은 뒤에만) */
    val canAddWaypoint: Boolean,
)

/**
 * 케어 화면 맨 위 지연 알림 줄의 문구 (피그마 HF03 v2). 주행 중 전체 화면 알림(HF06)과 같은 [DelayAlertUi]를 쓴다.
 * 후보를 찾는 중이면 그렇다고, 못 찾았으면 이유를, 찾았으면 다음 수유 시각과 후보 수를 알린다.
 * 다음 수유 시각이 이미 지났으면 "다음 수유 전에"라고 하지 않고 지났다고 알린다.
 */
fun delayBannerView(alert: DelayAlertUi, feeding: FeedingRoutine, now: Long): DelayBannerView {
    val title = "${TrafficCause.withRo(alert.cause.reason)} 도착이 ${alert.expectedArrival}로 늦어져요"
    val places = alert.candidates
    val subtitle = when {
        alert.loading -> "쉬어 갈 곳을 찾고 있어요…"
        places.isEmpty() -> alert.message ?: "근처에서 경유지를 찾지 못했어요"
        else -> {
            val where = if (places.all { it.place.onRoute }) "경로 위에" else "근처에"
            val found = "들를 수 있는 곳이 $where ${places.size}곳 있어요"
            val next = feeding.nextAt
            when {
                next == null -> found
                next > now -> "다음 수유(${clockLabel(next)}) 전에 $found"
                else -> "수유 시간(${clockLabel(next)})이 지났어요 · $found"
            }
        }
    }
    return DelayBannerView(title, subtitle, canAddWaypoint = !alert.loading && places.isNotEmpty())
}
