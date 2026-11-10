package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.haeyeonyou.familytrip.care.AddedWaypoint
import io.github.haeyeonyou.familytrip.care.CareKind
import io.github.haeyeonyou.familytrip.care.FrequentCarePlace
import io.github.haeyeonyou.familytrip.care.UrgentSheet
import io.github.haeyeonyou.familytrip.care.UrgentSheetMode
import io.github.haeyeonyou.familytrip.care.WAYPOINT_TOAST_MS
import io.github.haeyeonyou.familytrip.care.fallbackLead
import io.github.haeyeonyou.familytrip.care.isFrequent
import io.github.haeyeonyou.familytrip.care.urgentPlaceLine
import io.github.haeyeonyou.familytrip.care.urgentSubtitle
import io.github.haeyeonyou.familytrip.care.urgentTags
import io.github.haeyeonyou.familytrip.care.urgentTitle
import io.github.haeyeonyou.familytrip.care.waypointSlotLine
import io.github.haeyeonyou.familytrip.care.waypointToastText
import io.github.haeyeonyou.familytrip.data.CarePlace
import io.github.haeyeonyou.familytrip.data.FeedingRoutine
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/** 급한 케어 종류별 아이콘 (칩·타일·시트가 같이 쓴다). */
fun CareKind.icon(): ImageVector = when (this) {
    CareKind.NURSING -> SitterIcons.Milk
    CareKind.DIAPER -> SitterIcons.Diaper
    CareKind.REST -> SitterIcons.Parking
    CareKind.RESTROOM -> SitterIcons.Restroom
}

private const val SHEET_W = 1000

/** 경유지를 넣은 뒤 토스트를 보일 수 있는 시간 (내비에서 돌아오는 시간까지). */
private const val WAYPOINT_TOAST_WINDOW_MS = 120_000L

/**
 * 급한 케어 시트 (C09 수유실 3곳 · C10 결과 없음 → 정차 공간 · C11 정차 공간 3곳). 앱 프레임 가운데 1000 폭 모달.
 * 모양은 [UrgentSheet.mode]가 정한다 (`care/UrgentCare.kt`).
 */
@Composable
fun BoxScope.UrgentCareSheet(
    sheet: UrgentSheet,
    frequent: List<FrequentCarePlace>,
    onAdd: (CarePlace) -> Unit,
    onClose: () -> Unit,
) {
    val s = SitterSurface.current
    SitterScrim(onClick = onClose)
    Column(
        Modifier
            .align(Alignment.Center)
            .width(SHEET_W.dp)
            .shadow(24.dp, SitterShapes.Sheet)
            .clip(SitterShapes.Sheet)
            .background(s.card)
            // 시트 안을 눌러도 뒤의 막(닫기)으로 넘어가지 않게 한다.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(start = 28.dp, top = 28.dp, end = 28.dp, bottom = 24.dp),
    ) {
        val headerKind = if (sheet.mode == UrgentSheetMode.Found && sheet.kind == CareKind.REST) CareKind.REST else sheet.kind
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(s.tile), contentAlignment = Alignment.Center) {
                val dim = sheet.mode == UrgentSheetMode.Fallback || sheet.mode == UrgentSheetMode.Empty
                SitterIcon(headerKind.icon(), if (dim) s.inkMute else s.primary, size = SitterIconSize.Medium)
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(urgentTitle(sheet), style = SitterText.Title1, color = s.ink)
                Text(urgentSubtitle(sheet), style = SitterText.Caption, color = s.inkMute, modifier = Modifier.padding(top = 6.dp))
            }
            SitterButton("닫기", onClick = onClose, kind = SitterButtonKind.Soft)
        }
        when (sheet.mode) {
            UrgentSheetMode.Loading -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = s.primary)
            }
            UrgentSheetMode.Empty -> Text(
                "주변 10km 안에서 찾지 못했어요. 잠시 뒤 다시 찾거나 음성으로 “쉴 곳 찾아줘”라고 말해 보세요",
                style = SitterText.Body1,
                color = s.inkSoft,
                modifier = Modifier.padding(vertical = 40.dp),
            )
            else -> {
                if (sheet.mode == UrgentSheetMode.Fallback) {
                    Text(fallbackLead(sheet.kind), style = SitterText.Title3, color = s.ink, modifier = Modifier.padding(top = 20.dp))
                }
                Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val rowKind = if (sheet.mode == UrgentSheetMode.Fallback) CareKind.REST else sheet.kind
                    sheet.places.forEachIndexed { i, place ->
                        UrgentPlaceRow(
                            place = place,
                            icon = rowKind.icon(),
                            highlighted = i == 0 && sheet.mode == UrgentSheetMode.Found,
                            // C11: 정차 공간은 첫 곳만 큰 "경유지 추가", 나머지는 "추가"
                            compact = i > 0 && rowKind == CareKind.REST && sheet.mode == UrgentSheetMode.Found,
                            frequent = isFrequent(place, frequent),
                            onAdd = { onAdd(place) },
                        )
                    }
                }
            }
        }
        Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            SitterIcon(SitterIcons.Mic, s.inkSoft, size = SitterIconSize.Small)
            Text(
                when (sheet.mode) {
                    UrgentSheetMode.Fallback -> "오프라인일 때는 자주 가는 케어 장소로 대신 보여 드려요"
                    else -> "주행 중에는 가장 가까운 1곳만 음성으로 안내해요"
                },
                style = SitterText.Caption,
                color = s.inkSoft,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun UrgentPlaceRow(
    place: CarePlace,
    icon: ImageVector,
    highlighted: Boolean,
    compact: Boolean,
    frequent: Boolean,
    onAdd: () -> Unit,
) {
    val s = SitterSurface.current
    SitterRow(Modifier.fillMaxWidth().height(88.dp), selected = highlighted, contentPadding = PaddingValues(horizontal = 20.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(s.card), contentAlignment = Alignment.Center) {
            SitterIcon(icon, s.primary, size = SitterIconSize.Medium)
        }
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 12.dp)) {
            Text(place.name, style = SitterText.Title3, color = s.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(urgentPlaceLine(place, frequent), style = SitterText.Caption, color = s.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 16.dp)) {
            urgentTags(place).forEach { SitterTag(it, available = true) }
        }
        if (compact) {
            Box(
                Modifier.size(84.dp, 56.dp).clip(SitterShapes.Pill).background(s.card).border(1.5.dp, s.line, SitterShapes.Pill)
                    .clickable(role = Role.Button, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) { Text("추가", style = SitterText.Button, color = s.ink) }
        } else {
            SitterButton("경유지 추가", onClick = onAdd, icon = SitterIcons.Turn, modifier = Modifier.width(168.dp))
        }
    }
}

/**
 * C12 경유지 추가 완료 토스트 (맨 위 800x76, 짙은 바탕). 케어 화면이 보이는 동안 [WAYPOINT_TOAST_MS] 보이고 "되돌리기"로 뺄 수 있다.
 * 내장 내비는 경유지를 받으면 스스로 앞으로 나오므로, 화면이 다시 보일 때부터 시간을 센다 (넣은 지 [WAYPOINT_TOAST_WINDOW_MS] 안에서만).
 */
@Composable
fun BoxScope.WaypointToast(added: AddedWaypoint?, onUndo: () -> Unit) {
    added ?: return
    var visible by remember(added.addedAt) { mutableStateOf(System.currentTimeMillis() - added.addedAt < WAYPOINT_TOAST_WINDOW_MS) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(added.addedAt) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(WAYPOINT_TOAST_MS)
            visible = false
        }
    }
    if (!visible) return
    Row(
        Modifier
            .align(Alignment.TopCenter)
            .padding(top = 16.dp)
            .width(800.dp)
            .height(76.dp)
            .shadow(16.dp, SitterShapes.Pill)
            .clip(SitterShapes.Pill)
            .background(SitterPalette.Scrim)
            .padding(start = 16.dp, end = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(SitterPalette.Gradient), contentAlignment = Alignment.Center) {
            SitterIcon(SitterIcons.Check, Color.White, size = SitterIconSize.Medium)
        }
        Text(
            waypointToastText(added),
            style = SitterText.Body1,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
        )
        Text(
            "되돌리기",
            style = SitterText.Label,
            color = Color(0xFFA9B1FF),
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onUndo).padding(8.dp),
        )
    }
}

/** C12 오른쪽 슬롯 "다음 경유지" (그라데이션 카드). */
@Composable
fun WaypointSlot(
    added: AddedWaypoint,
    feeding: FeedingRoutine,
    onRemove: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(SitterShapes.Card)
            .background(SitterPalette.Gradient)
            .padding(start = 24.dp, top = 24.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Text(if (added.asWaypoint) "다음 경유지" else "안내 중인 목적지", style = SitterText.Label, color = Color.White.copy(alpha = 0.8f))
        Text(added.place.name, style = SitterText.Title2, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(waypointSlotLine(added, feeding), style = SitterText.Body2, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.weight(1f).height(64.dp).clip(SitterShapes.Pill).background(Color.White).clickable(role = Role.Button, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) { Text(if (added.asWaypoint) "경유지 빼기" else "안내 멈추기", style = SitterText.Button, color = SitterPalette.Scrim) }
            Box(
                Modifier.weight(1f).height(64.dp).clip(SitterShapes.Pill).background(Color.White).clickable(role = Role.Button, onClick = onContinue),
                contentAlignment = Alignment.Center,
            ) { Text("안내 계속", style = SitterText.Button, color = SitterPalette.Scrim) }
        }
    }
}
