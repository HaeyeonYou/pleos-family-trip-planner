package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.haeyeonyou.familytrip.data.ClimateSettings
import io.github.haeyeonyou.familytrip.data.PresetState

/** 프리셋 카드가 밖에 알리는 동작. */
data class PresetActions(
    val onApply: (slot: String) -> Unit,
    val onSave: (slot: String, settings: ClimateSettings) -> Unit,
    val onDismissMessage: (slot: String) -> Unit,
)

/** 설정을 한 줄로: "24°C · 풍량 2단 · 에어컨 · 음량 30%" (코스 입력 화면의 안내 카드용) */
fun presetSummary(s: ClimateSettings): String {
    val parts = buildList {
        if (!s.power) {
            add("공조 끄기")
        } else {
            add("${trimTemp(s.temperature)}°C")
            add("풍량 ${s.fanSpeed}단")
            if (s.ac) add("에어컨")
            if (s.airCleaner) add("공기청정")
        }
        if (s.driverSeatHeat > 0) add("운전석 열선 ${s.driverSeatHeat}단")
        if (s.passengerSeatHeat > 0) add("조수석 열선 ${s.passengerSeatHeat}단")
        if (s.steeringWheelHeat > 0) add("핸들 열선 ${s.steeringWheelHeat}단")
        s.mediaVolume?.let { add("음량 ${it}%") }
    }
    return parts.joinToString(" · ")
}

private fun trimTemp(value: Float): String = if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)

/** 풍량 단계를 말로: 1 미풍 · 2 약풍 · 3~4 중풍 · 5 이상 강풍. 공조를 끄는 프리셋은 '공조 끔'. */
fun windLabel(s: ClimateSettings): String = when {
    !s.power -> "공조 끔"
    s.fanSpeed <= 1 -> "미풍"
    s.fanSpeed == 2 -> "약풍"
    s.fanSpeed <= 4 -> "중풍"
    else -> "강풍"
}

/** 음량(%)을 말로: 10 이하 최소 · 39 이하 낮게 · 69 이하 보통 · 그 위 높게. 음량을 건드리지 않으면 null. */
fun volumeLabel(percent: Int?): String? = when {
    percent == null -> null
    percent <= 10 -> "음량 최소"
    percent < 40 -> "음량 낮게"
    percent < 70 -> "음량 보통"
    else -> "음량 높게"
}

/** 디자인(HF03)의 한 줄 요약: "23°C · 미풍 · 음량 최소". 음량이 없으면 적용 시점 문구를 붙인다. */
fun presetShortSummary(p: PresetState): String {
    val s = p.settings
    val tail = volumeLabel(s.mediaVolume) ?: p.whenLabel.takeIf { it.isNotBlank() }
    return listOfNotNull(if (s.power) "${trimTemp(s.temperature)}°C" else null, windLabel(s), tail).joinToString(" · ")
}

/** 음성 힌트 칩 ("애 잔다") */
@Composable
fun VoiceHintChip(text: String) {
    Text(
        "🎙 “$text”",
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(TripColors.Card).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * 베이비케어 카드 안의 작은 프리셋 카드 (낮잠·수면 모드, 수유 정차 모드). 누르면 차에 적용하고,
 * 폰에서 보내 아직 적용하지 않은 것은 초록 테두리로 눈에 띈다. 정차 중에는 '수정'으로 값을 고친다.
 */
@Composable
fun PresetTile(
    preset: PresetState,
    icon: String,
    title: String,
    hint: String,
    applying: Boolean,
    busy: Boolean,
    isParked: Boolean,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val highlight = preset.pending
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(TripColors.Tile)
            .border(if (highlight) 2.dp else 0.dp, if (highlight) TripColors.Green else Color.Transparent, RoundedCornerShape(20.dp))
            .clickable(enabled = !busy, onClick = onApply)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Box(modifier = Modifier.weight(1f))
            VoiceHintChip(hint)
        }
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        Text(presetShortSummary(preset), fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val status = when {
                applying -> "적용하는 중…"
                preset.pending -> "폰에서 보냈어요 · 눌러서 적용"
                else -> null
            }
            Text(
                status ?: "눌러서 적용",
                fontSize = 16.sp,
                fontWeight = if (highlight || applying) FontWeight.Bold else FontWeight.Normal,
                color = if (highlight || applying) TripColors.Green else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // 값을 고치는 일은 정차 중에만 (주행 중에는 적용만)
            if (isParked) {
                Text(
                    "수정",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TripColors.Green,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onEdit).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** 디자인(HF03-1 프리셋 편집)의 편집 창. 목표 온도 · 풍량 약/중/강 · 음량 낮게/보통/높게. 값은 버튼으로만 고친다. */
@Composable
fun PresetEditDialog(preset: PresetState, onDismiss: () -> Unit, onSave: (ClimateSettings) -> Unit) {
    var draft by remember(preset.slot) { mutableStateOf(preset.settings) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width(720.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(TripColors.Card)
                .padding(36.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${preset.label} 편집", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("정차 중에만 바꿀 수 있어요 · 더 자세한 설정은 웹앱에서", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            EditRow("목표 온도") {
                StepButton("−") { draft = draft.copy(temperature = (draft.temperature - 0.5f).coerceAtLeast(16f)) }
                Text(
                    "${trimTemp(draft.temperature)}°C",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = TripColors.Green,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(140.dp),
                )
                StepButton("+") { draft = draft.copy(temperature = (draft.temperature + 0.5f).coerceAtMost(30f)) }
            }
            EditRow("풍량") {
                // 약 1~2단 · 중 3~5단 · 강 6~8단. 고르면 각 구간의 대표 단계(2 · 4 · 6)로 맞춘다.
                Segmented(
                    options = listOf("약", "중", "강"),
                    selected = when { draft.fanSpeed <= 2 -> 0; draft.fanSpeed <= 5 -> 1; else -> 2 },
                    onSelect = { draft = draft.copy(fanSpeed = listOf(2, 4, 6)[it]) },
                )
            }
            EditRow("음량") {
                // 낮게 39% 이하 · 보통 40~69% · 높게 70% 이상. 고르면 30 · 50 · 70%로 맞춘다.
                Segmented(
                    options = listOf("낮게", "보통", "높게"),
                    selected = (draft.mediaVolume ?: 30).let { v -> when { v < 40 -> 0; v < 70 -> 1; else -> 2 } },
                    onSelect = { draft = draft.copy(mediaVolume = listOf(30, 50, 70)[it]) },
                )
            }
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 1.dp).background(TripColors.Line))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("웹앱에서도 같은 값으로 바뀌어요", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                BigOutlinedButton("취소", onDismiss)
                BigButton("저장", { onSave(draft) })
            }
        }
    }
}

@Composable
private fun EditRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.width(64.dp).heightIn(min = 64.dp).clip(RoundedCornerShape(16.dp)).background(TripColors.Tile).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    }
}

/** 하나를 고르는 묶음 버튼. 고른 것은 초록 테두리로 보인다. */
@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(TripColors.Tile).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) TripColors.Card else Color.Transparent)
                    .border(if (on) 2.dp else 0.dp, if (on) TripColors.Green else Color.Transparent, RoundedCornerShape(14.dp))
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 22.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) TripColors.Green else TripColors.Ink,
                )
            }
        }
    }
}
