package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ROLE_LABELS = mapOf("meal" to "식사", "outing" to "나들이", "cafe" to "카페", "home" to "귀가")
val THEME_LABELS = mapOf(
    "nature" to "자연",
    "experience" to "체험",
    "exhibition" to "전시",
    "play" to "놀이",
    "pet" to "반려동물",
)
val NEED_LABELS = mapOf(
    "stroller" to "유아차",
    "nursing_room" to "수유실",
    "kids_chair" to "유아 의자",
    "kids_zone" to "키즈존",
)

/** 차량 화면 기준의 큰 버튼 (터치 영역 76dp 이상). */
@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 76.dp)) {
        Text(text, fontSize = 26.sp)
    }
}

@Composable
fun BigOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 76.dp)) {
        Text(text, fontSize = 26.sp)
    }
}

/**
 * 주행 중이면 화면 위에 안내 띠를 띄운다. 조수석에서 조작할 수 있으므로 버튼은 막지 않고,
 * 키보드 입력만 각 화면에서 막는다.
 */
@Composable
fun DrivingAware(isParked: Boolean, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (!isParked) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 40.dp, vertical = 16.dp),
            ) {
                Text(
                    "주행 중이에요. 운전자는 조작하지 말고 동승자가 조작해 주세요.",
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        content()
    }
}

@Composable
fun ErrorText(message: String?) {
    message?.let {
        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 22.sp, modifier = Modifier.padding(vertical = 8.dp))
    }
}

/** 알림이 꺼져 있으면 내비가 앞에 있을 때 도착 안내를 받을 수 없음을 알린다. */
@Composable
fun NotificationOffBanner(visible: Boolean, onAllow: () -> Unit) {
    if (!visible) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 40.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            "알림이 꺼져 있어 내비 화면에서 도착 안내를 실시간으로 받을 수 없어요",
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        BigOutlinedButton("알림 허용", onAllow)
    }
}
