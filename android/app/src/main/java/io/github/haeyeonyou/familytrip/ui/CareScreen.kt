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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.care.CareKind
import io.github.haeyeonyou.familytrip.care.CareState
import io.github.haeyeonyou.familytrip.care.Nearby
import io.github.haeyeonyou.familytrip.care.ToneState
import io.github.haeyeonyou.familytrip.data.CarePlace
import io.github.haeyeonyou.familytrip.data.ClimateSettings
import io.github.haeyeonyou.familytrip.data.PresetState

/** 케어 화면이 밖에 알리는 동작 모음. */
data class CareActions(
    val onBabyCare: (Boolean) -> Unit,
    val onSleepMode: (Boolean) -> Unit,
    val onFed: () -> Unit,
    val onInterval: (Int) -> Unit,
    val onFind: (CareKind) -> Unit,
    val onAddWaypoint: (CarePlace) -> Unit,
    val onApplyPreset: (slot: String) -> Unit,
    val onSavePreset: (slot: String, settings: ClimateSettings) -> Unit,
    val onDismissPreset: (slot: String) -> Unit,
    val onEnter: () -> Unit,
)

/**
 * 케어 (피그마 HF03). 베이비케어 모드(실제 공조 제어) · 수유 타이머 · 공조 프리셋 3개 · 급한 케어 찾기 · 수면 모드.
 * 맨 위 지연 알림 배너(정체로 도착이 늦어질 때, #17)는 이번 범위가 아니라 [DelayBannerSlot] 자리만 둔다.
 */
@Composable
fun CareScreen(
    state: TripUiState,
    care: CareState,
    isParked: Boolean,
    actions: CareActions,
) = DrivingAware(isParked) {
    val months = state.profile?.ageMonths()?.minOrNull()
    val narrow = isNarrowWindow()
    LaunchedEffect(Unit) { actions.onEnter() }

    Column(
        modifier = Modifier.padding(horizontal = if (narrow) 24.dp else 40.dp, vertical = 32.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        DelayBannerSlot()
        ScreenTitle("케어", months?.let { "생후 ${it}개월 · 출발 전 준비와 급한 순간" } ?: "출발 전 준비와 급한 순간")
        val presetActions = PresetActions(actions.onApplyPreset, actions.onSavePreset, actions.onDismissPreset)
        // 정차 중 프리셋 편집 창 (HF03-1). 열려 있는 슬롯
        var editingSlot by rememberSaveable { mutableStateOf<String?>(null) }
        state.presets.firstOrNull { it.slot == editingSlot }?.let { preset ->
            PresetEditDialog(
                preset = preset,
                onDismiss = { editingSlot = null },
                onSave = { settings ->
                    presetActions.onSave(preset.slot, settings)
                    editingSlot = null
                },
            )
        }

        val babyCare: @Composable (Modifier) -> Unit = { m ->
            BabyCareCard(state, care, isParked, narrow, actions.onBabyCare, presetActions, { editingSlot = it }, m)
        }
        val feeding: @Composable (Modifier) -> Unit = { m ->
            FeedingCard(care.feeding, onFed = actions.onFed, onInterval = actions.onInterval, modifier = m)
        }
        if (narrow) {
            babyCare(Modifier.fillMaxWidth())
            feeding(Modifier.fillMaxWidth())
        } else {
            // 디자인(HF03 v2)은 베이비케어 카드가 수유 타이머 카드의 두 배 너비
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                babyCare(Modifier.weight(2f))
                feeding(Modifier.weight(1f))
            }
        }

        Text("급한 케어 찾기", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (narrow) {
            CareKind.cards.forEach { UrgentCard(it, care.nearby[it], actions, Modifier.fillMaxWidth()) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                CareKind.cards.forEach { UrgentCard(it, care.nearby[it], actions, Modifier.weight(1f)) }
            }
        }
        care.waypointMessage?.let { Text(it, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary) }

        SleepModeCard(care.sleepMode, actions.onSleepMode)
    }
}

/**
 * 지연 알림 배너 자리 (HF03 맨 위: "정체로 도착이 12:45로 늦어져요 / 경유지 추가 / 그대로 가기").
 * 정체 감지와 도착 시각 갱신이 아직 없어 이번에는 그리지 않는다 (#17, HF06).
 */
@Composable
private fun DelayBannerSlot() = Unit

@Composable
private fun CardFrame(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(TripColors.Card)
            .border(1.dp, TripColors.Line, RoundedCornerShape(28.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
fun IconBadge(icon: String) {
    Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(if (ToneState.current.dark) TripColors.Tile else TripColors.GreenSoft), contentAlignment = Alignment.Center) {
        Text(icon, fontSize = 22.sp)
    }
}

@Composable
private fun CareSwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(checkedTrackColor = TripColors.Green, checkedThumbColor = Color.White),
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

/**
 * 베이비케어 모드 (HF03 v2). 켜면 '탑승 준비' 프리셋(폰에서 보낸 값, 없으면 서버 기본값)으로 실제 차 공조를 제어하고,
 * 카드 안의 작은 카드 둘(낮잠·수면 모드, 수유 정차 모드)로 나머지 프리셋을 적용·수정한다. 프리셋은 서버 슬롯
 * boarding · nap · feeding_stop과 같다.
 */
@Composable
private fun BabyCareCard(
    state: TripUiState,
    care: CareState,
    isParked: Boolean,
    narrow: Boolean,
    onToggle: (Boolean) -> Unit,
    presetActions: PresetActions,
    onEdit: (String) -> Unit,
    modifier: Modifier,
) {
    val boarding = state.babyCarePreset
    val busy = state.applyingSlot != null
    CardFrame(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge("☺")
            Box(modifier = Modifier.weight(1f))
            // 탑승 준비 값을 고치는 일은 정차 중에만
            if (isParked && boarding != null) {
                Text(
                    "탑승 준비 수정",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TripColors.Green,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onEdit(boarding.slot) }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            CareSwitch(care.babyCare, enabled = !care.babyCareBusy, onChange = onToggle)
        }
        val temps: @Composable () -> Unit = {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column {
                    Text("차 공조 설정", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(care.currentTemperature?.let { "${trim(it)}°C" } ?: "—", fontSize = 48.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("설정한 목표", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${trim(care.targetTemperature)}°C", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = TripColors.Green)
                }
            }
        }
        val summary: @Composable (Modifier) -> Unit = { m ->
            Column(modifier = m, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("베이비케어 모드", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        care.babyCareBusy -> "차에 적용하는 중…"
                        care.climateMessage != null -> care.climateMessage
                        boarding != null -> presetShortSummary(boarding) + if (boarding.pending) " · 폰에서 보낸 설정" else ""
                        else -> "켜면 24°C · 약한 바람 · 시트와 핸들 열선은 꺼요 (지원 차량에서만)"
                    },
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (narrow) {
            summary(Modifier.fillMaxWidth())
            temps()
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                summary(Modifier.weight(1f))
                temps()
            }
        }

        val nap = state.presets.firstOrNull { it.slot == "nap" }
        val feedingStop = state.presets.firstOrNull { it.slot == "feeding_stop" }
        if (nap == null || feedingStop == null) {
            Text(
                "로그인하면 폰에서 정한 낮잠·수유 정차 설정이 여기에 나타나요",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            PresetTilePair(nap, feedingStop, state, isParked, narrow, presetActions, onEdit)
        }
        // 적용·저장 결과 (눌러서 닫는다)
        listOf("boarding", "nap", "feeding_stop").forEach { slot ->
            state.presetMessages[slot]?.let {
                Text(it, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { presetActions.onDismissMessage(slot) })
            }
        }
    }
}

/** 베이비케어 카드 안의 작은 프리셋 카드 둘: 낮잠 · 수면 모드(애 잔다), 수유 정차 모드(수유할게). */
@Composable
private fun PresetTilePair(
    nap: PresetState,
    feedingStop: PresetState,
    state: TripUiState,
    isParked: Boolean,
    narrow: Boolean,
    presetActions: PresetActions,
    onEdit: (String) -> Unit,
) {
    @Composable
    fun tile(p: PresetState, icon: String, title: String, hint: String, modifier: Modifier) = PresetTile(
        preset = p,
        icon = icon,
        title = title,
        hint = hint,
        applying = state.applyingSlot == p.slot,
        busy = state.applyingSlot != null,
        isParked = isParked,
        onApply = { presetActions.onApply(p.slot) },
        onEdit = { onEdit(p.slot) },
        modifier = modifier,
    )
    if (narrow) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tile(nap, "☾", "낮잠 · 수면 모드", "애 잔다", Modifier.fillMaxWidth())
            tile(feedingStop, "🍼", "수유 정차 모드", "수유할게", Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            tile(nap, "☾", "낮잠 · 수면 모드", "애 잔다", Modifier.weight(1f))
            tile(feedingStop, "🍼", "수유 정차 모드", "수유할게", Modifier.weight(1f))
        }
    }
}

/** 급한 케어 한 종류. 첫 후보를 요약으로 보이고, 눌러서 펼치면 2곳과 '경유지 추가'가 나온다. */
@Composable
private fun UrgentCard(kind: CareKind, result: Nearby?, actions: CareActions, modifier: Modifier) {
    var expanded by rememberSaveable(kind.name) { mutableStateOf(false) }
    CardFrame(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                expanded = !expanded
                if (result == null || result is Nearby.Failed) actions.onFind(kind)
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(when (kind) { CareKind.NURSING -> "🍼"; CareKind.DIAPER -> "☺"; CareKind.REST -> "P"; CareKind.RESTROOM -> "WC" })
            Column(modifier = Modifier.weight(1f)) {
                Text(kind.label, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(summary(result), fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (expanded) "⌃" else "›", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            when (result) {
                is Nearby.Found -> result.places.forEachIndexed { i, place -> PlaceRow(i + 1, place) { actions.onAddWaypoint(place) } }
                is Nearby.Failed -> Text(result.message, fontSize = 18.sp, color = MaterialTheme.colorScheme.error)
                else -> Text("찾는 중…", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun summary(result: Nearby?): String = when (result) {
    null, Nearby.Loading -> "찾는 중…"
    is Nearby.Failed -> "눌러서 다시 찾기"
    is Nearby.Found -> result.places.firstOrNull()?.let { "${it.name} · ${distanceLabel(it.distanceM)} · ${it.driveMinutes}분" } ?: "10km 안에 찾지 못했어요"
}

@Composable
private fun PlaceRow(number: Int, place: CarePlace, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TripColors.Tile).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("$number", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TripColors.Green)
        Column(modifier = Modifier.weight(1f)) {
            Text(place.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(placeDetail(place), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "경유지 추가",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(TripColors.Green).clickable(onClick = onAdd).padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

fun distanceLabel(meters: Int): String = if (meters < 1000) "${meters}m" else "%.1fkm".format(meters / 1000.0)

fun placeDetail(place: CarePlace): String = buildList {
    if (place.onRoute) add("경로 위") else place.detourMinutes?.let { add("+${it}분") }
    add("${distanceLabel(place.distanceM)} · ${place.driveMinutes}분")
    if (place.note.isNotBlank()) add(place.note)
}.joinToString(" · ")

@Composable
private fun SleepModeCard(on: Boolean, onToggle: (Boolean) -> Unit) {
    CardFrame(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge("☾")
            Column(modifier = Modifier.weight(1f)) {
                Text("수면 모드", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (on) "켜짐 · 앱 안내 음성 30% · 알림은 소리 없이 · 도착 5분 전에 조용히 알려요" else "\"아기 자요\"라고 말하면 앱 음성·알림을 줄여요",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CareSwitch(on, onChange = onToggle)
        }
    }
}

/** 23.0 → "23", 23.5 → "23.5" */
private fun trim(value: Float): String = if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)
