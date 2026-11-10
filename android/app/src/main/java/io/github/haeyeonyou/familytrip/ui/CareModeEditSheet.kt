package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.haeyeonyou.familytrip.care.FAN_CHOICES
import io.github.haeyeonyou.familytrip.care.MODE_SLOTS
import io.github.haeyeonyou.familytrip.care.VOLUME_CHOICES
import io.github.haeyeonyou.familytrip.care.changedModes
import io.github.haeyeonyou.familytrip.care.fanChoiceIndex
import io.github.haeyeonyou.familytrip.care.modeTabLabel
import io.github.haeyeonyou.familytrip.care.snapVolume
import io.github.haeyeonyou.familytrip.care.stepTemperature
import io.github.haeyeonyou.familytrip.care.stepVolume
import io.github.haeyeonyou.familytrip.care.volumeChoiceIndex
import io.github.haeyeonyou.familytrip.data.ClimateSettings
import io.github.haeyeonyou.familytrip.data.PresetState

/**
 * 모드 편집 시트 (C08, 800x766 앱 프레임 (228,60)). 탭 3개(베이비케어 기본 · 낮잠·수면 · 수유 정차) × 목표 온도 · 풍량 · 음량.
 * 탭을 바꿔도 고친 값은 남고, 저장하면 바뀐 모드만 서버(웹앱과 같은 값)에 올린다. 정차 중에만 연다.
 */
@Composable
fun BoxScope.ModeEditSheet(
    presets: List<PresetState>,
    initialSlot: String,
    error: String?,
    onSave: (Map<String, ClimateSettings>) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = SitterSurface.current
    val slots = MODE_SLOTS.filter { slot -> presets.any { it.slot == slot } }
    val original = remember(presets) { presets.associate { it.slot to it.settings } }
    var drafts by remember { mutableStateOf(original) }
    var tab by remember { mutableStateOf(initialSlot.takeIf { it in slots } ?: slots.firstOrNull()) }
    val current = tab?.let { drafts[it] }
    fun edit(f: (ClimateSettings) -> ClimateSettings) { tab?.let { t -> drafts[t]?.let { drafts = drafts + (t to f(it)) } } }

    SitterScrim(onClick = onDismiss)
    Column(
        Modifier
            .align(Alignment.TopCenter)
            .offset(y = 60.dp)
            .width(800.dp)
            .shadow(24.dp, SitterShapes.Sheet)
            .clip(SitterShapes.Sheet)
            .background(s.card)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(start = 32.dp, top = 28.dp, end = 32.dp, bottom = 16.dp),
    ) {
        Text("모드 편집", style = SitterText.Title1, color = s.ink)
        Text("정차 중에만 바꿀 수 있어요 · 웹앱에서도 같은 값으로 바뀌어요", style = SitterText.Caption, color = s.inkMute, modifier = Modifier.padding(top = 6.dp))
        Segments(
            options = slots.map { slot -> modeTabLabel(slot) + if (original[slot] != drafts[slot]) " •" else "" },
            selected = slots.indexOf(tab),
            onSelect = { tab = slots[it] },
            modifier = Modifier.padding(top = 20.dp).fillMaxWidth(),
            height = 64.dp,
            filled = true,
        )
        if (current == null) {
            Text("모드 값을 아직 받지 못했어요. 로그인하면 웹앱의 모드를 불러와요", style = SitterText.Body1, color = s.inkSoft, modifier = Modifier.padding(vertical = 40.dp))
        } else {
            EditLine("목표 온도", Modifier.padding(top = 28.dp)) {
                RoundStep("−") { edit { it.copy(temperature = stepTemperature(it.temperature, up = false)) } }
                Text("${formatTemp(current.temperature)}°C", style = SitterText.Number.copy(fontSize = SitterText.Title1.fontSize), color = s.primary, textAlign = TextAlign.Center, modifier = Modifier.width(110.dp))
                RoundStep("+") { edit { it.copy(temperature = stepTemperature(it.temperature, up = true)) } }
            }
            EditLine("풍량", Modifier.padding(top = 24.dp)) {
                Segments(
                    options = FAN_CHOICES.map { it.first },
                    selected = fanChoiceIndex(current.fanSpeed),
                    onSelect = { i -> edit { it.copy(fanSpeed = FAN_CHOICES[i].second) } },
                    itemWidth = 86.dp,
                )
            }
            EditLine("음량", Modifier.padding(top = 24.dp)) {
                val picked = volumeChoiceIndex(current.mediaVolume)
                Segments(
                    options = VOLUME_CHOICES.map { "${it.first} ${it.second}%" } + listOf(if (picked == null && current.mediaVolume != null) "직접 ${current.mediaVolume}%" else "직접"),
                    selected = picked ?: if (current.mediaVolume != null) VOLUME_CHOICES.size else -1,
                    onSelect = { i ->
                        if (i < VOLUME_CHOICES.size) edit { it.copy(mediaVolume = VOLUME_CHOICES[i].second) }
                        else edit { it.copy(mediaVolume = stepVolume(it.mediaVolume, up = true)) }
                    },
                    itemWidth = 118.dp,
                )
            }
            VolumeSlider(current.mediaVolume, onChange = { v -> edit { it.copy(mediaVolume = v) } }, Modifier.padding(top = 16.dp))
            EditLine("적용 시점", Modifier.padding(top = 24.dp)) {
                // 적용 시점은 서버에 아직 필드가 없어 지금 정해진 시점만 보인다 (지시서 "서버에 필요한 것").
                val whenLabel = presets.firstOrNull { it.slot == tab }?.whenLabel.orEmpty().ifBlank { "직접 실행" }
                Segments(options = listOf(whenLabel), selected = 0, onSelect = {}, itemWidth = 164.dp)
            }
        }
        error?.let { Text(it, style = SitterText.Label, color = SitterPalette.Danger, modifier = Modifier.padding(top = 16.dp)) }
        Box(Modifier.padding(top = 24.dp).fillMaxWidth().height(1.dp).background(s.line))
        Row(Modifier.padding(top = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("모드 이름 · 개수는 바꿀 수 없어요 (음성 호출 고정)", style = SitterText.Caption, color = s.inkMute, modifier = Modifier.weight(1f))
            SitterButton("취소", onClick = onDismiss, kind = SitterButtonKind.Soft, modifier = Modifier.width(120.dp).height(64.dp))
            Spacer(Modifier.width(12.dp))
            val changed = changedModes(original, drafts)
            SitterButton("저장", onClick = { onSave(changed) }, enabled = changed.isNotEmpty(), modifier = Modifier.width(136.dp).height(64.dp))
        }
    }
}

@Composable
private fun EditLine(label: String, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val s = SitterSurface.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = SitterText.Title3, color = s.ink, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

@Composable
private fun RoundStep(label: String, onClick: () -> Unit) {
    val s = SitterSurface.current
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(s.tile).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = SitterText.Title2, color = s.ink) }
}

/** 묶음 선택 (탭·풍량·음량·적용 시점). 고른 칸은 흰 바탕 + Primary 테두리 (탭은 테두리 없이 흰 바탕). */
@Composable
private fun Segments(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemWidth: androidx.compose.ui.unit.Dp? = null,
    height: androidx.compose.ui.unit.Dp = 56.dp,
    filled: Boolean = false,
) {
    val s = SitterSurface.current
    Row(modifier.clip(RoundedCornerShape(18.dp)).background(s.tile).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier
                    .then(if (itemWidth != null) Modifier.width(itemWidth) else Modifier.weight(1f))
                    .height(height)
                    .clip(shape)
                    .background(if (on) s.card else Color.Transparent)
                    .then(if (on && !filled) Modifier.border(2.dp, s.primary, shape) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = if (on) SitterText.Button else SitterText.Body1, color = if (on) s.primary else s.inkSoft, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeSlider(volume: Int?, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val s = SitterSurface.current
    Row(
        modifier.fillMaxWidth().height(84.dp).clip(SitterShapes.Row).background(s.tile).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Slider(
            value = (volume ?: 0).toFloat(),
            onValueChange = { onChange(snapVolume(it)) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(thumbColor = s.primary, activeTrackColor = s.primary, inactiveTrackColor = s.line),
            modifier = Modifier.weight(1f),
            // Figma: 흰 원 손잡이 + Primary 테두리
            thumb = { Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White).border(3.dp, s.primary, CircleShape)) },
        )
        Spacer(Modifier.width(24.dp))
        StepOnCard("−") { onChange(stepVolume(volume, up = false)) }
        Text(volume?.let { "$it%" } ?: "그대로", style = SitterText.Title1, color = s.primary, textAlign = TextAlign.Center, modifier = Modifier.width(130.dp))
        StepOnCard("+") { onChange(stepVolume(volume, up = true)) }
    }
}

@Composable
private fun StepOnCard(label: String, onClick: () -> Unit) {
    val s = SitterSurface.current
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(s.card).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = SitterText.Title3, color = s.ink) }
}

private fun formatTemp(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v)
