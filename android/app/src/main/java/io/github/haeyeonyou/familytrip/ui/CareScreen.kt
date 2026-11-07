package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 케어 (PX03). 지금 동작하는 것은 폰에서 보낸 공조 설정 적용이고,
 * 수유 타이머·수면 모드·급한 케어 찾기는 다음 작업(수유 루틴, 음성)에서 채운다.
 */
@Composable
fun CareScreen(
    state: TripUiState,
    isParked: Boolean,
    onApplyPreset: () -> Unit,
    onDismissPreset: () -> Unit,
) = DrivingAware(isParked) {
    val months = state.profile?.ageMonths()?.minOrNull()
    Column(
        modifier = Modifier.padding(40.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenTitle("케어", "필요한 순간 한 번에")
        if (state.pendingPreset != null || state.presetMessage != null) {
            PresetCard(state, onApply = onApplyPreset, onDismiss = onDismissPreset)
        }
        Tile(
            title = "권장 실내 온도  ${recommendedTemperature(months)}°C",
            body = if (months == null) "가족 정보에 아이를 등록하면 나이에 맞춰 알려 드려요" else "아이 나이에 맞춘 권장값이에요",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Tile("수유 타이머", "다음 수유 시각 알림 · 준비 중", Modifier.weight(1f))
            Tile("수면 모드", "\"애 잔다\" → 안내 음성·알림 줄이기 · 준비 중", Modifier.weight(1f))
        }
        Text("급한 케어 찾기", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Tile("수유실", "경로 위 · 10km 이내 · 준비 중", Modifier.weight(1f))
            Tile("기저귀 교환대", "정보가 있는 곳만 · 준비 중", Modifier.weight(1f))
            Tile("잠깐 쉬어가기", "주차장 · 공원 · 준비 중", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Tile(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(TripColors.Tile).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(body, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 영아는 조금 따뜻하게. 기준은 팀 확정 전 임시값. */
private fun recommendedTemperature(months: Int?): String = when {
    months == null -> "23.0"
    months < 12 -> "24.0"
    months < 36 -> "23.5"
    else -> "23.0"
}
