package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.haeyeonyou.familytrip.care.BABY_TARGET_TEMPERATURE
import io.github.haeyeonyou.familytrip.care.CareKind
import io.github.haeyeonyou.familytrip.care.CareState
import io.github.haeyeonyou.familytrip.care.FAN_CHOICES
import io.github.haeyeonyou.familytrip.care.Nearby
import io.github.haeyeonyou.familytrip.care.VOLUME_CHOICES
import io.github.haeyeonyou.familytrip.care.detourLabel
import io.github.haeyeonyou.familytrip.care.fanChoiceIndex
import io.github.haeyeonyou.familytrip.care.stepTemperature
import io.github.haeyeonyou.familytrip.care.toddlerVolumeIndex
import io.github.haeyeonyou.familytrip.data.CarePlace
import io.github.haeyeonyou.familytrip.data.ClimateSettings
import kotlinx.coroutines.delay

/** 가까운 화장실 몇 곳 (Figma C05-T1). */
const val TODDLER_RESTROOMS = 3

/** 공조 직접 조작을 차·서버에 보내기 전에 기다리는 시간 (+ 연타를 한 번으로). */
private const val ADJUST_DEBOUNCE_MS = 700L

/** 베이비케어 값이 없을 때 (로그인 전): 컨트롤러 기본값과 같다. */
private val TODDLER_DEFAULT = ClimateSettings(name = "베이비케어", power = true, temperature = BABY_TARGET_TEMPERATURE, fanSpeed = 1)

/**
 * 케어 · 유아기 (12개월 초과, Figma ⑦ C05-T1 `427:14732`). 수유 타이머·수유실 없이
 * 넓은 열 = 베이비케어 공조 직접 조작 + 빠른 모드 2개, 3열 = 가까운 화장실 3곳 · 자주 가는 케어 장소 · 바로가기.
 */
@Composable
fun ToddlerCareBody(
    state: TripUiState,
    care: CareState,
    actions: CareActions,
    isParked: Boolean,
    onEdit: (String) -> Unit,
) {
    SitterWideAndColumn(
        wide = { ToddlerClimateCard(state, care, actions, isParked, onEdit) },
        third = {
            RestroomCard(care.nearby[CareKind.RESTROOM], actions.onAddWaypoint, Modifier.height(386.dp))
            Spacer(Modifier.height(16.dp))
            FrequentPlacesCard(care.frequent, actions.onAddWaypoint, Modifier.weight(1f), emptyText = "방문 기록이 쌓이면 자주 들른 케어 장소를 알려 드려요")
            Spacer(Modifier.height(16.dp))
            ShortcutDock()
        },
    )
}

@Composable
private fun ToddlerClimateCard(state: TripUiState, care: CareState, actions: CareActions, isParked: Boolean, onEdit: (String) -> Unit) {
    val s = SitterSurface.current
    val saved = state.babyCarePreset?.settings ?: TODDLER_DEFAULT
    var draft by remember(saved) { mutableStateOf(saved) }
    // 바꾼 값은 잠시 모았다가 한 번에: 베이비케어가 켜져 있으면 차에, 로그인했다면 이 아이의 베이비케어 값으로 서버에도.
    LaunchedEffect(draft) {
        if (draft == saved) return@LaunchedEffect
        delay(ADJUST_DEBOUNCE_MS)
        actions.onAdjustBabyCare(draft)
    }
    SitterCard(Modifier.fillMaxSize(), contentPadding = PaddingValues(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(SitterIcons.Baby, s.primary, s.primarySoft)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text("베이비케어 공조", style = SitterText.Title2, color = s.ink)
                Text(
                    "아이에게 맞춘 차 공조 값 · " + when {
                        care.babyCareBusy -> "바꾸는 중이에요"
                        care.babyCare -> "지금 켜져 있어요"
                        else -> "꺼져 있어요"
                    },
                    style = SitterText.Body2,
                    color = s.inkMute,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            SitterToggle(care.babyCare, onCheckedChange = { if (!care.babyCareBusy) actions.onBabyCare(it) })
        }
        // 지금 차 설정 · 목표 (SDK에 실내 온도 센서가 없어 공조의 설정값을 읽는다)
        Row(
            Modifier.padding(top = 24.dp).fillMaxWidth().clip(SitterShapes.Row).background(s.tile).padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("지금 차 설정", style = SitterText.Label, color = s.inkSoft)
                Text(care.currentTemperature?.let { "${trimTemperature(it)}°" } ?: "—", style = SitterText.Display, color = s.ink)
            }
            StepCircle("−", "목표 온도 낮추기") { draft = draft.copy(temperature = stepTemperature(draft.temperature, up = false)) }
            Column(Modifier.padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("목표", style = SitterText.Label, color = s.inkSoft)
                Text("%.1f°".format(draft.temperature), style = SitterText.Display, color = s.primary)
            }
            StepCircle("+", "목표 온도 높이기") { draft = draft.copy(temperature = stepTemperature(draft.temperature, up = true)) }
        }
        ControlLine("바람 세기", Modifier.padding(top = 18.dp)) {
            Pills(FAN_CHOICES.map { windName(it.first) }, fanChoiceIndex(draft.fanSpeed)) { i -> draft = draft.copy(fanSpeed = FAN_CHOICES[i].second, power = true) }
        }
        ControlLine("앱 음량", Modifier.padding(top = 12.dp)) {
            Pills(VOLUME_CHOICES.map { it.first }, toddlerVolumeIndex(draft.mediaVolume)) { i -> draft = draft.copy(mediaVolume = VOLUME_CHOICES[i].second) }
        }
        Text(
            "앱 안내 음성 · 미디어 음량만 바뀌어요. 내비 안내 음성은 그대로예요." + if (!care.babyCare) " 켜면 이 값으로 맞춰요." else "",
            style = SitterText.Caption,
            color = s.inkMute,
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("빠른 모드", style = SitterText.Title3, color = s.ink, modifier = Modifier.weight(1f))
            if (state.presets.isNotEmpty()) {
                SitterButton("모드 편집", onClick = { onEdit("nap") }, kind = SitterButtonKind.Line, icon = SitterIcons.Pencil, enabled = isParked)
            }
        }
        val nap = state.presets.firstOrNull { it.slot == "nap" }
        val stop = state.presets.firstOrNull { it.slot == "feeding_stop" }
        Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f)) {
                ModeRow(
                    SitterIcons.Moon,
                    "낮잠 · 수면 모드",
                    nap?.let { modeSummary(it.settings) } ?: "23°C · 미풍 · 음량 최소",
                    "“애 잔다”",
                    active = care.sleepMode,
                    activeLabel = "알림 · 안내 음성 최소화 중",
                ) { actions.onSleepMode(!care.sleepMode) }
            }
            Box(Modifier.weight(1f)) {
                // 정차 모드 = 수유 정차 모드와 같은 공조 값(서버 슬롯 feeding_stop), 수유 기록은 하지 않는다 (#217)
                ModeRow(
                    SitterIcons.Parking,
                    "정차 모드",
                    (stop?.let { modeSummary(it.settings) } ?: "24°C · 송풍") + if (isParked) " · 정차 시에만" else " · 정차하면 켤 수 있어요",
                    "“쉬었다 갈게”",
                    active = care.feedingStop != null,
                    activeLabel = "정차 모드 적용 중 · 출발하면 꺼져요",
                ) {
                    when {
                        care.feedingStop != null -> actions.onFinishFeedingStop()
                        isParked -> actions.onStartRestStop()
                    }
                }
            }
        }
    }
}

/** 디자인 이름 "미풍 · 약풍 · 중풍 · 강풍" (편집 시트는 "미풍 · 약 · 중 · 강"). */
private fun windName(label: String) = if (label.endsWith("풍")) label else "${label}풍"

private fun trimTemperature(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v)

@Composable
private fun ControlLine(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val s = SitterSurface.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = SitterText.Body1, color = s.inkSoft, modifier = Modifier.width(112.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

/** 묶음 선택: 고른 칸만 흰 바탕 + Primary 글자 (Figma C05-T1). */
@Composable
private fun Pills(options: List<String>, selected: Int?, onSelect: (Int) -> Unit) {
    val s = SitterSurface.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(s.tile).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).background(if (on) s.card else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = if (on) SitterText.Button else SitterText.Body1, color = if (on) s.primary else s.inkSoft, maxLines = 1)
            }
        }
    }
}

@Composable
private fun StepCircle(label: String, description: String, onClick: () -> Unit) {
    val s = SitterSurface.current
    Box(
        Modifier.size(60.dp).clip(CircleShape).background(s.card).clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = SitterText.Title2, color = s.ink) }
}

/** 가까운 화장실 3곳 (가까운 순 · 지금 위치 기준). [→]는 경유지 추가. 서버 `/care/nearby?kind=restroom` 실제 결과만 보인다. */
@Composable
private fun RestroomCard(result: Nearby?, onGo: (CarePlace) -> Unit, modifier: Modifier) {
    val s = SitterSurface.current
    SitterCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(SitterIcons.Restroom, s.primary, s.primarySoft)
            Column(Modifier.padding(start = 12.dp)) {
                Text("가까운 화장실", style = SitterText.Title3, color = s.ink)
                Text("가까운 순 · 지금 위치 기준", style = SitterText.Caption, color = s.inkMute)
            }
        }
        val places = (result as? Nearby.Found)?.places.orEmpty().take(TODDLER_RESTROOMS)
        if (places.isEmpty()) {
            Text(
                when (result) {
                    null, Nearby.Loading -> "찾는 중이에요"
                    is Nearby.Failed -> "서버에 닿지 않아 찾지 못했어요"
                    else -> "근처에서 화장실을 찾지 못했어요"
                },
                style = SitterText.Body2,
                color = s.inkMute,
                modifier = Modifier.padding(top = 20.dp),
            )
            return@SitterCard
        }
        places.forEachIndexed { i, p ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(s.line))
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(if (i == 0) s.primary else s.tile),
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", style = SitterText.Label, color = if (i == 0) Color.White else s.inkSoft) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(p.name, style = SitterText.Body1, color = s.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${distanceLabel(p.distanceM)} · " + detourLabel(p.onRoute, p.detourMinutes, p.driveMinutes).let { d ->
                            if (p.detourMinutes == null) d else "${p.driveMinutes}분 · $d"
                        },
                        style = SitterText.Caption,
                        color = if (i == 0) s.primary else s.inkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GradientCircleButton(size = 48.dp, onClick = { onGo(p) }) { SitterIcon(SitterIcons.Turn, Color.White, size = SitterIconSize.Medium) }
            }
        }
    }
}

/** 칩 줄 (유아기): 화장실 · 기저귀 교환대 · 정차 공간 · 소아과 · 약국. 수유실은 없다. */
@Composable
fun ToddlerChips(onOpen: (CareKind) -> Unit) {
    listOf(CareKind.RESTROOM, CareKind.DIAPER, CareKind.REST).forEach { kind ->
        SitterChip(kind.label, selected = false, onClick = { onOpen(kind) }, icon = kind.icon())
    }
    // 소아과·약국은 서버 종류가 생기기 전까지 준비 중 (D4)
    SitterChip("소아과", selected = false, onClick = {}, modifier = Modifier.alpha(0.4f), icon = SitterIcons.Clinic)
    SitterChip("약국", selected = false, onClick = {}, modifier = Modifier.alpha(0.4f), icon = SitterIcons.Pill)
}
