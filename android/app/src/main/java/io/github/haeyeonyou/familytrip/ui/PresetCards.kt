package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.data.ClimateSettings
import io.github.haeyeonyou.familytrip.data.PresetState

/** 프리셋 카드가 밖에 알리는 동작. */
data class PresetActions(
    val onApply: (slot: String) -> Unit,
    val onSave: (slot: String, settings: ClimateSettings) -> Unit,
    val onDismissMessage: (slot: String) -> Unit,
)

/** 설정을 한 줄로: "24°C · 풍량 2단 · 에어컨 · 음량 30%" */
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

/** 슬롯의 상태 문구. */
private fun statusLabel(p: PresetState): String = when {
    !p.sent -> "아직 폰에서 보내지 않았어요 · 기본 설정"
    p.pending -> "폰에서 보냈어요 · 적용 대기 중"
    else -> "차에 적용했어요"
}

/**
 * 공조 프리셋 3개(탑승 준비 · 낮잠 · 수유 정차)를 카드로 보인다. 폰(웹)에서 보낸 것은 '대기 중'으로 눈에 띄고,
 * '차에 적용'을 누르면 차 공조·음량에 적용하고 서버에 알린다. 정차 중에는 '수정'으로 값을 고쳐 저장할 수 있다.
 */
@Composable
fun PresetSlots(
    presets: List<PresetState>,
    messages: Map<String, String>,
    applyingSlot: String?,
    isParked: Boolean,
    narrow: Boolean,
    actions: PresetActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("공조 프리셋", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (presets.isEmpty()) {
            Text(
                "로그인하면 폰에서 정한 탑승 준비 · 낮잠 · 수유 정차 설정 3개가 여기에 나타나요",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (narrow) {
            presets.forEach { PresetCard(it, messages[it.slot], applyingSlot, isParked, actions, Modifier.fillMaxWidth()) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                presets.forEach { PresetCard(it, messages[it.slot], applyingSlot, isParked, actions, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: PresetState,
    message: String?,
    applyingSlot: String?,
    isParked: Boolean,
    actions: PresetActions,
    modifier: Modifier,
) {
    var editing by remember(preset.slot) { mutableStateOf(false) }
    var draft by remember(preset.slot) { mutableStateOf(preset.settings) }
    val busy = applyingSlot != null
    val highlight = preset.pending
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(if (highlight) TripColors.GreenSoft else TripColors.Card)
            .border(if (highlight) 2.dp else 1.dp, if (highlight) TripColors.Green else TripColors.Line, RoundedCornerShape(28.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(preset.label, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (preset.whenLabel.isNotBlank()) {
            Text(preset.whenLabel, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            statusLabel(preset),
            fontSize = 18.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight) TripColors.Green else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (editing) {
            PresetEditor(draft) { draft = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton(if (applyingSlot == preset.slot) "저장하는 중…" else "차에 적용하고 저장", {
                    actions.onSave(preset.slot, draft)
                    editing = false
                }, enabled = !busy)
                BigOutlinedButton("취소", { editing = false })
            }
        } else {
            Text(presetSummary(preset.settings), fontSize = 20.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton(if (applyingSlot == preset.slot) "적용하는 중…" else "차에 적용", { actions.onApply(preset.slot) }, enabled = !busy)
                // 값을 고치는 일은 정차 중에만 (주행 중에는 보여 주기와 적용만)
                if (isParked) {
                    BigOutlinedButton("수정", {
                        draft = preset.settings
                        editing = true
                    })
                }
            }
        }
        message?.let {
            Text(it, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { actions.onDismissMessage(preset.slot) })
        }
    }
}

/** 버튼으로만 고치는 편집기 (키보드 입력 없음). */
@Composable
private fun PresetEditor(s: ClimateSettings, onChange: (ClimateSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StepRow("온도", "${trimTemp(s.temperature)}°C",
            { onChange(s.copy(temperature = (s.temperature - 0.5f).coerceAtLeast(16f))) },
            { onChange(s.copy(temperature = (s.temperature + 0.5f).coerceAtMost(30f))) })
        StepRow("풍량", "${s.fanSpeed}단",
            { onChange(s.copy(fanSpeed = (s.fanSpeed - 1).coerceAtLeast(1))) },
            { onChange(s.copy(fanSpeed = (s.fanSpeed + 1).coerceAtMost(8))) })
        StepRow("에어컨", if (s.ac) "켜짐" else "꺼짐", { onChange(s.copy(ac = false)) }, { onChange(s.copy(ac = true)) })
        StepRow("공기청정", if (s.airCleaner) "켜짐" else "꺼짐", { onChange(s.copy(airCleaner = false)) }, { onChange(s.copy(airCleaner = true)) })
        StepRow("운전석 열선", heatLabel(s.driverSeatHeat),
            { onChange(s.copy(driverSeatHeat = (s.driverSeatHeat - 1).coerceAtLeast(0))) },
            { onChange(s.copy(driverSeatHeat = (s.driverSeatHeat + 1).coerceAtMost(3))) })
        StepRow("조수석 열선", heatLabel(s.passengerSeatHeat),
            { onChange(s.copy(passengerSeatHeat = (s.passengerSeatHeat - 1).coerceAtLeast(0))) },
            { onChange(s.copy(passengerSeatHeat = (s.passengerSeatHeat + 1).coerceAtMost(3))) })
        StepRow("핸들 열선", heatLabel(s.steeringWheelHeat),
            { onChange(s.copy(steeringWheelHeat = (s.steeringWheelHeat - 1).coerceAtLeast(0))) },
            { onChange(s.copy(steeringWheelHeat = (s.steeringWheelHeat + 1).coerceAtMost(3))) })
        // 음량: 0% 아래로 내리면 '건드리지 않음'
        StepRow("음량", s.mediaVolume?.let { "${it}%" } ?: "건드리지 않음",
            { onChange(s.copy(mediaVolume = s.mediaVolume?.let { if (it <= 0) null else it - 10 })) },
            { onChange(s.copy(mediaVolume = ((s.mediaVolume ?: -10) + 10).coerceAtMost(100))) })
    }
}

private fun heatLabel(level: Int) = if (level == 0) "끔" else "${level}단"

@Composable
private fun StepRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, fontSize = 18.sp, modifier = Modifier.weight(1f))
        BigOutlinedButton("−", onMinus)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
        BigOutlinedButton("+", onPlus)
    }
}
