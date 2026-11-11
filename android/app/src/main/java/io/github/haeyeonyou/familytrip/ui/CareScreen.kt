package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.haeyeonyou.familytrip.care.CareKind
import io.github.haeyeonyou.familytrip.care.CareSlot
import io.github.haeyeonyou.familytrip.care.CareState
import io.github.haeyeonyou.familytrip.care.FeedingPhase
import io.github.haeyeonyou.familytrip.care.FeedingStopSession
import io.github.haeyeonyou.familytrip.care.sleepVoicePercent
import io.github.haeyeonyou.familytrip.care.childChipLabel
import io.github.haeyeonyou.familytrip.care.childOptions
import io.github.haeyeonyou.familytrip.data.presetsForChild
import io.github.haeyeonyou.familytrip.care.selectedChildIndex
import io.github.haeyeonyou.familytrip.care.FrequentCarePlace
import io.github.haeyeonyou.familytrip.care.Nearby
import io.github.haeyeonyou.familytrip.care.QUICK_FED_CHOICES
import io.github.haeyeonyou.familytrip.care.detourLabel
import io.github.haeyeonyou.familytrip.care.feedingPhase
import io.github.haeyeonyou.familytrip.care.fedAtMinutesAgo
import io.github.haeyeonyou.familytrip.care.overdueMinutes
import io.github.haeyeonyou.familytrip.care.pickCareSlot
import io.github.haeyeonyou.familytrip.data.CarePlace
import io.github.haeyeonyou.familytrip.data.ClimateSettings
import io.github.haeyeonyou.familytrip.data.FeedingRoutine
import io.github.haeyeonyou.familytrip.data.PresetState
import io.github.haeyeonyou.familytrip.data.clockLabel
import io.github.haeyeonyou.familytrip.navi.DelayAlertUi
import io.github.haeyeonyou.familytrip.navi.DelayCandidate

/** 케어 화면이 밖에 알리는 동작 모음. */
data class CareActions(
    val onBabyCare: (Boolean) -> Unit,
    /** 마지막 수유 시각(epoch ms)을 기록한다. 지금이면 현재 시각. */
    val onFedAt: (Long) -> Unit,
    val onFind: (CareKind) -> Unit,
    /** 급한 케어 시트를 연다 (C09~C11). */
    val onOpenUrgent: (CareKind) -> Unit,
    val onCloseUrgent: () -> Unit,
    /** 케어 화면에 머문 채 경유지를 넣는다 (C12 토스트·슬롯). */
    val onAddWaypoint: (CarePlace) -> Unit,
    /** C12 "되돌리기"·"경유지 빼기" */
    val onUndoWaypoint: () -> Unit,
    /** C12 "안내 계속" */
    val onContinueGuidance: () -> Unit,
    /** C03 수유 정차 모드 켜기 (정차 중에만) */
    val onStartFeedingStop: () -> Unit,
    /** C03 "수유 끝났어요" */
    val onFinishFeedingStop: () -> Unit,
    /** 경유지 도착 후 "수유 정차 모드를 켤까요?"에 "괜찮아요" */
    val onDismissFeedingStopAsk: () -> Unit,
    /** C04 낮잠·수면 모드 켜기/끄기 */
    val onSleepMode: (Boolean) -> Unit,
    /** C07 케어 대상 아이 고르기 (null = 첫째) */
    val onSelectChild: (String?) -> Unit = {},
    /** C07 드롭다운 행: 아이별 수유 루틴, 서버 아이 정보 */
    val childRoutine: (String?) -> FeedingRoutine = { FeedingRoutine() },
    val serverChildren: () -> List<io.github.haeyeonyou.familytrip.data.ServerChild> = { emptyList() },
    /** 가족 기본값과 아이마다의 모드 값 (C07 행의 베이비케어 줄, QO5) */
    val allPresets: () -> List<io.github.haeyeonyou.familytrip.data.PresetState> = { emptyList() },
    val onDelayAddWaypoint: (DelayCandidate) -> Unit,
    val onDelayKeepGoing: () -> Unit,
    val onApplyPreset: (slot: String) -> Unit,
    val onSavePreset: (slot: String, settings: ClimateSettings) -> Unit,
    /** C08 모드 편집 저장: 바뀐 모드만, 끝나면 성공 여부를 알린다. */
    val onSaveModes: (edits: Map<String, ClimateSettings>, onDone: (Boolean) -> Unit) -> Unit,
    val onDismissPreset: (slot: String) -> Unit,
    val onEnter: () -> Unit,
)

/**
 * 케어 (11/10 디자인 C01·C02·C05·C06). 3열: 베이비케어 모드 · 수유 타이머와 자주 가는 케어 장소 · 급한 케어 타일 + 상황 슬롯(392x370) + 바로가기.
 * 슬롯에 무엇을 보일지는 `care/CareSlot.kt`의 [pickCareSlot]이 정한다 (D2 우선순위).
 */
@Composable
fun CareScreen(
    state: TripUiState,
    care: CareState,
    delayAlert: DelayAlertUi?,
    actions: CareActions,
    /** 정차(P단) 중인지. 모드 편집 등 정차 중에만 되는 동작에 쓴다 (D10). */
    isParked: Boolean = true,
) {
    LaunchedEffect(Unit) { actions.onEnter() }
    val now = rememberNow()
    val children = state.profile?.children.orEmpty()
    val child = children.getOrNull(selectedChildIndex(children, care.selectedChildId))
    val chipLabel = childChipLabel(children, care.selectedChildId)
    var choosingChild by remember { mutableStateOf(false) }
    // 아이 선택은 정차 중에만 (주행하면 닫는다)
    LaunchedEffect(isParked) { if (!isParked) choosingChild = false }
    var editingSlot by rememberSaveable { mutableStateOf<String?>(null) }
    var modeSaveError by remember { mutableStateOf<String?>(null) }
    // 모드 편집은 정차(P단) 중에만. 주행을 시작하면 닫는다.
    LaunchedEffect(isParked) { if (!isParked) editingSlot = null }

    SitterCanvas {
        SitterVoiceBar(
            hint = "“애 잔다” · “수유할게” · “수유실 찾아줘”라고 말해 보세요",
            avatarLabel = child?.name?.firstOrNull()?.toString(),
            weather = state.weather?.let { w ->
                {
                    SitterWeather(
                        w.temperature?.let { "${Math.round(it)}°" } ?: "--",
                        listOf(w.condition, w.region).filter { it.isNotBlank() }.joinToString(" · "),
                    )
                }
            },
        )
        SitterChipRow {
            SitterChip(chipLabel, selected = true, onClick = { if (isParked && children.size > 1) choosingChild = true }, icon = SitterIcons.Baby, trailingIcon = if (children.size > 1) SitterIcons.ChevronDown else null)
            CareKindChip(CareKind.NURSING, actions)
            CareKindChip(CareKind.DIAPER, actions)
            CareKindChip(CareKind.REST, actions)
            CareKindChip(CareKind.RESTROOM, actions)
            // 소아과·약국은 서버 종류가 생기기 전까지 준비 중 (D4)
            SitterChip("소아과", selected = false, onClick = {}, modifier = Modifier.alpha(0.4f), icon = SitterIcons.Clinic)
            SitterChip("약국", selected = false, onClick = {}, modifier = Modifier.alpha(0.4f), icon = SitterIcons.Pill)
        }
        SitterThreeColumns(
            first = { BabyCareColumn(state, care, actions, isParked, onEdit = { editingSlot = it }) },
            second = {
                FeedingTimerCard(care.feeding, care.feedingStop, now, actions, Modifier.height(410.dp))
                Spacer(Modifier.height(16.dp))
                FrequentPlacesCard(care.frequent, actions.onAddWaypoint, Modifier.weight(1f))
            },
            third = {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    UrgentTile(CareKind.NURSING, SitterIcons.Milk, care.nearby[CareKind.NURSING], actions)
                    UrgentTile(CareKind.REST, SitterIcons.Parking, care.nearby[CareKind.REST], actions)
                }
                Spacer(Modifier.height(16.dp))
                CareSlotCard(care, delayAlert, now, actions, Modifier.height(370.dp), stopSummary = state.presets.firstOrNull { it.slot == "feeding_stop" }?.let { modeSummary(it.settings) } ?: "24°C · 송풍", napVolume = state.presets.firstOrNull { it.slot == "nap" }?.settings?.mediaVolume)
                Spacer(Modifier.weight(1f))
                ShortcutDock()
            },
        )
        care.urgent?.let { UrgentCareSheet(it, care.frequent, onAdd = actions.onAddWaypoint, onClose = actions.onCloseUrgent) }
        WaypointToast(care.addedWaypoint, onUndo = actions.onUndoWaypoint)
        CareNoticeToast(care.notice, care.noticeAt)
        if (choosingChild) {
            ChildSelectDropdown(
                options = actions.allPresets().let { all ->
                    childOptions(
                        children,
                        actions.serverChildren(),
                        care.selectedChildId,
                        modeOf = { c -> all.takeIf { it.isNotEmpty() }?.let { presetsForChild(it, c.serverId).firstOrNull { p -> p.slot == "boarding" }?.let { p -> modeSummary(p.settings) } } },
                        routineOf = actions.childRoutine,
                    )
                },
                onSelect = { key -> actions.onSelectChild(key); choosingChild = false },
                onDismiss = { choosingChild = false },
            )
        }
        if (care.askFeedingStop && care.feedingStop == null) {
            FeedingStopAsk(
                place = care.addedWaypoint?.place?.name,
                summary = state.presets.firstOrNull { it.slot == "feeding_stop" }?.let { modeSummary(it.settings) } ?: "24°C · 송풍",
                onYes = actions.onStartFeedingStop,
                onNo = actions.onDismissFeedingStopAsk,
            )
        }
        editingSlot?.let { slot ->
            ModeEditSheet(
                presets = state.presets,
                childName = child?.name?.takeIf { children.size > 1 },
                departureAt = state.course?.departureAt,
                initialSlot = slot,
                error = modeSaveError,
                onSave = { edits ->
                    modeSaveError = null
                    actions.onSaveModes(edits) { ok ->
                        if (ok) editingSlot = null else modeSaveError = "저장하지 못했어요. 로그인과 연결을 확인해 주세요"
                    }
                },
                onDismiss = { editingSlot = null; modeSaveError = null },
            )
        }
    }
}

@Composable
private fun CareKindChip(kind: CareKind, actions: CareActions) {
    SitterChip(kind.label, selected = false, onClick = { actions.onOpenUrgent(kind) }, icon = kind.icon())
}

// ───────────── 1열: 베이비케어 모드 ─────────────

@Composable
private fun IconCircle(icon: ImageVector, tint: Color, background: Color, size: Int = 48) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        SitterIcon(icon, tint, size = SitterIconSize.Medium)
    }
}

/** 호환: 홈·프리셋 카드가 쓰는 동그란 이모지 배지. */
@Composable
fun IconBadge(icon: String) {
    val s = SitterSurface.current
    Box(Modifier.size(44.dp).clip(CircleShape).background(s.tile), contentAlignment = Alignment.Center) { Text(icon) }
}

@Composable
private fun BabyCareColumn(state: TripUiState, care: CareState, actions: CareActions, isParked: Boolean, onEdit: (String) -> Unit) {
    val s = SitterSurface.current
    val boarding = state.babyCarePreset
    SitterCard(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(SitterIcons.Baby, s.primary, s.primarySoft)
            Spacer(Modifier.weight(1f))
            SitterToggle(care.babyCare, onCheckedChange = { if (!care.babyCareBusy) actions.onBabyCare(it) })
        }
        Text("베이비케어 모드", style = SitterText.Title2, color = s.ink, modifier = Modifier.padding(top = 16.dp))
        val stopSummary = state.presets.firstOrNull { it.slot == "feeding_stop" }?.let { modeSummary(it.settings) } ?: "24°C · 송풍"
        val napSummary = state.presets.firstOrNull { it.slot == "nap" }?.let { modeSummary(it.settings) } ?: "23°C · 미풍"
        Text(if (care.feedingStop != null) "수유 정차 모드 적용 중 · $stopSummary" else if (care.sleepMode) "낮잠 · 수면 모드 적용 중 · $napSummary" else boarding?.let { modeSummary(it.settings) } ?: "24°C · 약풍", style = SitterText.Body2, color = s.inkMute, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column {
                // SDK에 실내 온도 센서가 없어 공조의 설정값을 읽는다 (Q11)
                Text("차 공조 설정", style = SitterText.Label, color = s.inkSoft)
                Text(care.currentTemperature?.let { "${trimTemp(it)}°" } ?: "—", style = SitterText.Number, color = s.ink)
            }
            Column {
                Text("설정한 목표", style = SitterText.Label, color = s.inkSoft)
                Text("${trimTemp(care.targetTemperature)}°", style = SitterText.Number, color = s.primary)
            }
        }
        Spacer(Modifier.height(20.dp))
        val nap = state.presets.firstOrNull { it.slot == "nap" }
        val stop = state.presets.firstOrNull { it.slot == "feeding_stop" }
        ModeRow(
            SitterIcons.Moon,
            "낮잠 · 수면 모드",
            nap?.let { modeSummary(it.settings) } ?: "23°C · 미풍",
            "“애 잔다”",
            active = care.sleepMode,
            activeLabel = "알림 · 안내 음성 최소화 중",
        ) { actions.onSleepMode(!care.sleepMode) }
        Spacer(Modifier.height(16.dp))
        ModeRow(
            SitterIcons.Milk,
            "수유 정차 모드",
            (stop?.let { modeSummary(it.settings) } ?: "24°C · 송풍") + if (isParked) " · 정차 시에만" else " · 정차하면 켤 수 있어요",
            "“수유할게”",
            active = care.feedingStop != null,
        ) { if (isParked && care.feedingStop == null) actions.onStartFeedingStop() }
        Spacer(Modifier.weight(1f))
        if (state.presets.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SitterButton("모드 편집", onClick = { onEdit(boarding?.slot ?: state.presets.first().slot) }, kind = SitterButtonKind.Line, icon = SitterIcons.Pencil, enabled = isParked)
                if (!isParked) Text("정차 중에만 바꿀 수 있어요", style = SitterText.Caption, color = s.inkMute, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun ModeRow(icon: ImageVector, title: String, summary: String, voice: String, active: Boolean = false, activeLabel: String = "적용 중", onClick: () -> Unit) {
    val s = SitterSurface.current
    SitterRow(Modifier.fillMaxWidth(), selected = active, outlined = active, onClick = onClick) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                IconCircle(icon, s.ink, s.card)
                Spacer(Modifier.weight(1f))
                SitterIcon(SitterIcons.Mic, s.inkMute, size = SitterIconSize.Small)
                Text(voice, style = SitterText.Caption, color = s.inkMute, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
            }
            Text(title, style = SitterText.Title3, color = s.ink, modifier = Modifier.padding(top = 12.dp))
            if (active) {
                // Figma C03: 적용 중 태그 (진한 채움 + 흰 글자)
                Text(activeLabel, style = SitterText.Label, color = Color.White, modifier = Modifier.padding(top = 6.dp).clip(SitterShapes.Tag).background(s.primary).padding(horizontal = 12.dp, vertical = 4.dp))
            } else {
                Text(summary, style = SitterText.Caption, color = s.inkMute, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** "24°C · 약풍 · 음량 낮게" */
fun modeSummary(c: ClimateSettings): String = buildList {
    add("${trimTemp(c.temperature)}°C")
    add(
        when {
            !c.power -> "공조 끔"
            c.fanSpeed <= 1 -> "미풍"
            c.fanSpeed == 2 -> "약풍"
            c.fanSpeed == 3 -> "중풍"
            else -> "강풍"
        },
    )
    c.mediaVolume?.let { add(if (it <= 10) "음량 최소" else if (it <= 40) "음량 낮게" else "음량 ${it}%") }
}.joinToString(" · ")

private fun trimTemp(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v)

// ───────────── 2열: 수유 타이머 · 자주 가는 케어 장소 ─────────────

/** 수유 타이머 (C02 첫 기록 전 · C01 보통 · C05 임박 · C06 지남). 단계는 [feedingPhase]가 정한다. */
@Composable
private fun FeedingTimerCard(routine: FeedingRoutine, stop: FeedingStopSession?, now: Long, actions: CareActions, modifier: Modifier) {
    val s = SitterSurface.current
    if (stop != null) {
        FeedingNowCard(stop, now, actions.onFinishFeedingStop, modifier)
        return
    }
    val phase = feedingPhase(routine, now)
    val accent = when (phase) {
        FeedingPhase.Soon -> SitterPalette.Warn
        FeedingPhase.Overdue -> SitterPalette.Danger
        else -> s.primary
    }
    var picking by rememberSaveable { mutableStateOf(false) }
    if (picking) AgoPickerDialog(onDismiss = { picking = false }, onPick = { minutes -> picking = false; actions.onFedAt(fedAtMinutesAgo(minutes, System.currentTimeMillis())) })
    SitterCard(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(SitterIcons.Timer, if (phase == FeedingPhase.Normal || phase == FeedingPhase.NoRecord) SitterPalette.Warn else accent, s.tile)
            Spacer(Modifier.weight(1f))
            VoiceHint("“방금 수유했어”")
        }
        Text("수유 타이머", style = SitterText.Title2, color = s.ink, modifier = Modifier.padding(top = 16.dp))
        if (phase == FeedingPhase.NoRecord) {
            Text("마지막 수유가 언제였나요?", style = SitterText.Title3, color = s.ink, modifier = Modifier.padding(top = 20.dp))
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QUICK_FED_CHOICES.take(3).forEach { (label, min) -> SitterChip(label, selected = min == 0, onClick = { actions.onFedAt(fedAtMinutesAgo(min, System.currentTimeMillis())) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QUICK_FED_CHOICES.drop(3).forEach { (label, min) -> SitterChip(label, selected = false, onClick = { actions.onFedAt(fedAtMinutesAgo(min, System.currentTimeMillis())) }) }
                    SitterChip("시간 직접 선택", selected = false, onClick = { picking = true })
                }
            }
            Text(
                "간격 ${intervalLabel(routine.intervalMinutes)} 기준으로 다음 수유를 계산해요\n“1시간 전에 먹었어”라고 말해도 돼요",
                style = SitterText.Caption,
                color = s.inkMute,
                modifier = Modifier.padding(top = 16.dp),
            )
            return@SitterCard
        }
        val last = routine.lastFedAt!!
        val remaining = routine.remainingMinutes(now) ?: 0L
        Text(
            when (phase) {
                FeedingPhase.Soon -> "곧 수유 시간"
                FeedingPhase.Overdue -> "수유 시각 지남"
                else -> "다음 수유"
            },
            style = SitterText.Label,
            color = if (phase == FeedingPhase.Normal) s.inkSoft else accent,
            modifier = Modifier.padding(top = 16.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (phase == FeedingPhase.Overdue) "+${overdueMinutes(routine, now)}분" else clockLabel(routine.nextAt!!),
                style = SitterText.Display,
                color = if (phase == FeedingPhase.Normal) s.ink else accent,
            )
            when (phase) {
                FeedingPhase.Soon -> StateTag("${remaining}분 전", accent)
                FeedingPhase.Overdue -> StateTag("지남", accent)
                else -> Unit
            }
        }
        Text(
            when (phase) {
                FeedingPhase.Soon -> "${remaining}분 남음 · 마지막 ${clockLabel(last)}"
                FeedingPhase.Overdue -> "예정 ${clockLabel(routine.nextAt!!)} · 마지막 ${clockLabel(last)}"
                else -> "마지막 ${clockLabel(last)} · 간격 ${intervalLabel(routine.intervalMinutes)}"
            },
            style = SitterText.Label,
            color = s.inkMute,
            modifier = Modifier.padding(top = 4.dp),
        )
        val progress = routine.progress(now)
        Box(
            Modifier
                .padding(top = 18.dp)
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(s.tile)
                .drawBehind { drawRoundRect(accent, size = Size(size.width * progress, size.height), cornerRadius = CornerRadius(size.height / 2)) },
        )
        Spacer(Modifier.weight(1f))
        // Figma: 높이 76, 글자는 버튼 기본 크기 (Large의 Title2는 커서 Regular 글자에 높이만 맞춘다)
        SitterButton("지금 수유했어요", onClick = { actions.onFedAt(System.currentTimeMillis()) }, modifier = Modifier.fillMaxWidth().height(76.dp), kind = SitterButtonKind.Soft)
    }
}

@Composable
private fun StateTag(text: String, color: Color) {
    // Figma C05·C06: 진한 채움 + 흰 글자
    Text(
        text,
        style = SitterText.Label,
        color = Color.White,
        modifier = Modifier.clip(SitterShapes.Tag).background(color).padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun VoiceHint(text: String) {
    val s = SitterSurface.current
    Row(
        Modifier.clip(SitterShapes.Tag).background(s.tile).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SitterIcon(SitterIcons.Mic, s.inkMute, size = SitterIconSize.Small)
        Text(text, style = SitterText.Caption, color = s.inkMute, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "시간 직접 선택": 몇 분 전에 먹었는지 10분 단위로 고른다. */
@Composable
private fun AgoPickerDialog(onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val s = SitterSurface.current
    var minutes by rememberSaveable { mutableStateOf(90) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        SitterCard(Modifier.width(420.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp)) {
            Text("마지막 수유가 언제였나요?", style = SitterText.Title3, color = s.ink)
            Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SitterButton("−10분", { minutes = (minutes - 10).coerceAtLeast(10) }, kind = SitterButtonKind.Soft)
                Text(durationText(minutes) + " 전", style = SitterText.Title2, color = s.ink)
                SitterButton("+10분", { minutes = (minutes + 10).coerceAtMost(MAX_AGO_MINUTES) }, kind = SitterButtonKind.Soft)
            }
            SitterButton("확인", { onPick(minutes) }, modifier = Modifier.fillMaxWidth(), size = SitterButtonSize.Large)
        }
    }
}

private const val MAX_AGO_MINUTES = 12 * 60

private fun durationText(minutes: Int): String = when {
    minutes < 60 -> "${minutes}분"
    minutes % 60 == 0 -> "${minutes / 60}시간"
    else -> "${minutes / 60}시간 ${minutes % 60}분"
}

@Composable
private fun FrequentPlacesCard(places: List<FrequentCarePlace>, onGo: (CarePlace) -> Unit, modifier: Modifier) {
    val s = SitterSurface.current
    SitterCard(modifier.fillMaxWidth()) {
        Text("자주 가는 케어 장소", style = SitterText.Title3, color = s.ink)
        if (places.isEmpty()) {
            Text("방문 기록이 쌓이면 자주 가는 수유실·케어 장소를 알려 드려요", style = SitterText.Body2, color = s.inkMute, modifier = Modifier.padding(top = 12.dp))
            return@SitterCard
        }
        val first = places[0]
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(SitterIcons.Star, s.primary, s.tile)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(first.name, style = SitterText.Title3, color = s.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(placeMeta(first), style = SitterText.Caption, color = s.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            GradientCircleButton(size = 52.dp, onClick = { onGo(first.toCarePlace()) }) { SitterIcon(SitterIcons.Turn, Color.White) }
        }
        if (first.amenities.isNotEmpty()) {
            Row(Modifier.padding(top = 10.dp, start = 60.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                first.amenities.forEach { SitterTag(amenityLabel(it)) }
            }
        }
        places.getOrNull(1)?.let { second ->
            Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(1.dp).background(s.line))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp).clickable { onGo(second.toCarePlace()) }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(second.name, style = SitterText.Body1, color = s.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${distanceLabel(second.distanceM)} · ${second.driveMinutes}분", style = SitterText.Caption, color = s.inkMute)
                }
                Text("길 안내", style = SitterText.Label, color = s.primary)
            }
        }
    }
}

private fun placeMeta(p: FrequentCarePlace) = "${distanceLabel(p.distanceM)} · ${p.driveMinutes}분 · 최근 ${p.visitCount}회 방문"
fun distanceLabel(meters: Int): String = if (meters < 1000) "${meters}m" else "%.1fkm".format(meters / 1000.0)

/** 음성 패널이 쓰는 후보 한 줄 설명. */
fun placeDetail(place: CarePlace): String = buildList {
    if (place.onRoute) add("경로 위") else place.detourMinutes?.let { add("+${it}분") }
    add("${distanceLabel(place.distanceM)} · ${place.driveMinutes}분")
    if (place.note.isNotBlank()) add(place.note)
}.joinToString(" · ")
private fun amenityLabel(key: String) = when (key) {
    "nursing_room" -> "수유실"
    "stroller" -> "유모차"
    "pet" -> "반려동물"
    else -> key
}

// ───────────── 3열: 급한 케어 타일 · 슬롯 ─────────────

@Composable
private fun UrgentTile(kind: CareKind, icon: ImageVector, result: Nearby?, actions: CareActions) {
    val s = SitterSurface.current
    val place = (result as? Nearby.Found)?.places?.firstOrNull()
    SitterCard(
        Modifier.width(188.dp).height(200.dp).clip(SitterShapes.Card).clickable { actions.onOpenUrgent(kind) },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(icon, s.ink, s.tile)
            Spacer(Modifier.weight(1f))
            if (place != null) {
                GradientCircleButton(size = 44.dp, onClick = { actions.onAddWaypoint(place) }) { SitterIcon(SitterIcons.Turn, Color.White, size = SitterIconSize.Medium) }
            }
        }
        Text(kind.label, style = SitterText.Title3, color = s.ink, modifier = Modifier.padding(top = 12.dp))
        Text(
            when {
                place != null -> place.name
                result is Nearby.Loading || result == null -> "찾는 중이에요"
                else -> "근처에서 못 찾았어요"
            },
            style = SitterText.Caption,
            color = s.inkMute,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        place?.let {
            Text(
                "${distanceLabel(it.distanceM)} · ${it.driveMinutes}분" + if (it.onRoute) " · 경로 위" else "",
                style = SitterText.Caption,
                color = s.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 오른쪽 가변 슬롯 392x370. 무엇을 보일지는 [pickCareSlot]이 정한다. 닫은 슬롯은 수유 기록이 바뀌면 다시 살아난다. */
@Composable
private fun CareSlotCard(care: CareState, delay: DelayAlertUi?, now: Long, actions: CareActions, modifier: Modifier, stopSummary: String, napVolume: Int?) {
    val phase = feedingPhase(care.feeding, now)
    var dismissed by remember(care.feeding.lastFedAt) { mutableStateOf(setOf<CareSlot>()) }
    val slot = pickCareSlot(phase, delay != null, dismissed, waypointActive = care.addedWaypoint != null, feedingNow = care.feedingStop != null, sleeping = care.sleepMode)
    fun dismiss(s: CareSlot) { dismissed = dismissed + s }
    val nursing = (care.nearby[CareKind.NURSING] as? Nearby.Found)?.places.orEmpty()
    val nursingRows = nursing.take(2).map { SlotRow(it.name, detourLabel(it.onRoute, it.detourMinutes, it.driveMinutes)) { actions.onAddWaypoint(it) } }

    when (slot) {
        CareSlot.Delay -> {
            val ui = delay!!
            val view = delayBannerView(ui, care.feeding, now)
            AlertSlot(
                modifier, SitterCardTone.Warn, SitterPalette.Warn, view.title, view.subtitle,
                ui.candidates.take(2).map { c -> SlotRow(c.place.name, detourLabel(c.place.onRoute, c.place.detourMinutes, c.place.driveMinutes)) { actions.onDelayAddWaypoint(c) } },
                "경유지 추가", view.canAddWaypoint, { ui.candidates.firstOrNull()?.let(actions.onDelayAddWaypoint) },
                "그대로 가기",
            ) { actions.onDelayKeepGoing() }
        }
        CareSlot.FeedingSoon -> AlertSlot(
            modifier, SitterCardTone.Warn, SitterPalette.Warn, "${care.feeding.remainingMinutes(now)}분 뒤 수유 시간이에요",
            if (nursing.isEmpty()) "근처 수유실을 찾고 있어요" else "지금 경로 위에 들를 수 있는 수유실이에요",
            nursingRows, "경유지 추가", nursing.isNotEmpty(), { nursing.firstOrNull()?.let(actions.onAddWaypoint) }, "나중에",
        ) { dismiss(CareSlot.FeedingSoon) }
        CareSlot.FeedingOverdue -> AlertSlot(
            modifier, SitterCardTone.Danger, SitterPalette.Danger, "수유 시각이 ${overdueMinutes(care.feeding, now)}분 지났어요",
            if (nursing.isEmpty()) "근처 수유실을 찾고 있어요" else "경로 위 가장 가까운 수유실부터 보여 드려요",
            nursingRows, "수유실 경유지 추가", nursing.isNotEmpty(), { nursing.firstOrNull()?.let(actions.onAddWaypoint) }, "나중에",
        ) { dismiss(CareSlot.FeedingOverdue) }
        CareSlot.Sleep -> SleepSlot(care.sleepStartedAt, now, sleepVoicePercent(napVolume), onWake = { actions.onSleepMode(false) }, modifier = modifier)
        CareSlot.FeedingNow -> FeedingStopSlot(care.feedingStop!!, stopSummary, modifier)
        CareSlot.Waypoint -> WaypointSlot(care.addedWaypoint!!, care.feeding, actions.onUndoWaypoint, actions.onContinueGuidance, modifier)
        else -> TodaySlot(care.feeding, modifier)
    }
}

private data class SlotRow(val name: String, val detail: String, val onClick: () -> Unit)

@Composable
private fun AlertSlot(
    modifier: Modifier,
    tone: SitterCardTone,
    accent: Color,
    title: String,
    body: String,
    rows: List<SlotRow>,
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
) {
    val s = SitterSurface.current
    SitterCard(modifier.fillMaxWidth(), tone = tone, contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SitterIcon(SitterIcons.Alert, accent, size = SitterIconSize.Medium)
            Text(title, style = SitterText.Title3, color = s.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(body, style = SitterText.Caption, color = s.inkSoft, modifier = Modifier.padding(start = 32.dp, top = 10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        rows.forEach { r ->
            Row(
                Modifier.padding(bottom = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(s.card).clickable(onClick = r.onClick).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SitterIcon(SitterIcons.Milk, accent, size = SitterIconSize.Small)
                Text(r.name, style = SitterText.Body1, color = s.ink, modifier = Modifier.weight(1f).padding(horizontal = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(r.detail, style = SitterText.Caption, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1.3f).height(60.dp)
                    .background(if (primaryEnabled) accent else accent.copy(alpha = 0.4f), SitterShapes.Pill)
                    .clip(SitterShapes.Pill)
                    .clickable(enabled = primaryEnabled, onClick = onPrimary),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                SitterIcon(SitterIcons.Turn, Color.White, size = SitterIconSize.Small)
                Text(primaryLabel, style = SitterText.Button, color = Color.White, modifier = Modifier.padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.weight(1f).height(60.dp).clip(SitterShapes.Pill).background(s.card).clickable(onClick = onSecondary), contentAlignment = Alignment.Center) {
                Text(secondaryLabel, style = SitterText.Button, color = s.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 오늘 케어 기록 (C02 오른쪽, 다른 슬롯 상태가 없을 때). */
@Composable
private fun TodaySlot(routine: FeedingRoutine, modifier: Modifier) {
    val s = SitterSurface.current
    val last = routine.lastFedAt
    SitterCard(modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Text("오늘 케어 기록", style = SitterText.Title3, color = s.ink)
        if (last == null) {
            Text("아직 기록이 없어요", style = SitterText.Body1, color = s.inkSoft, modifier = Modifier.padding(top = 18.dp))
            Text("마지막 수유를 알려 주면 다음 수유 시각과 경로 위 수유실을 미리 챙겨 드려요", style = SitterText.Body2, color = s.inkMute, modifier = Modifier.padding(top = 8.dp))
        } else {
            Text("마지막 수유 ${clockLabel(last)}", style = SitterText.Body1, color = s.inkSoft, modifier = Modifier.padding(top = 18.dp))
            Text("다음 수유 ${clockLabel(routine.nextAt!!)} · 간격 ${intervalLabel(routine.intervalMinutes)}", style = SitterText.Body2, color = s.inkMute, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
