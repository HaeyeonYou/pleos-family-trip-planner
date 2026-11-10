package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.haeyeonyou.familytrip.care.FEEDING_STOP_MIN_RECORD_MINUTES
import io.github.haeyeonyou.familytrip.care.FeedingStopSession
import io.github.haeyeonyou.familytrip.care.feedingStopMinutes
import io.github.haeyeonyou.familytrip.care.feedingStopProgress
import io.github.haeyeonyou.familytrip.data.clockLabel
import kotlinx.coroutines.delay

/** 수유 타이머 카드의 "수유 중" 모습 (C03): N분째 · 시작 시각 · 진행 막대 · 수유 끝났어요. */
@Composable
fun FeedingNowCard(session: FeedingStopSession, now: Long, onFinish: () -> Unit, modifier: Modifier) {
    val s = SitterSurface.current
    SitterCard(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(s.tile), contentAlignment = Alignment.Center) {
                SitterIcon(SitterIcons.Timer, SitterPalette.Warn, size = SitterIconSize.Medium)
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(SitterShapes.Tag).background(s.tile).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SitterIcon(SitterIcons.Mic, s.inkMute, size = SitterIconSize.Small)
                Text("“다 먹었어”", style = SitterText.Caption, color = s.inkMute)
            }
        }
        Text("수유 타이머", style = SitterText.Title2, color = s.ink, modifier = Modifier.padding(top = 16.dp))
        Text("수유 중", style = SitterText.Label, color = s.inkSoft, modifier = Modifier.padding(top = 16.dp))
        Text("${feedingStopMinutes(session, now)}분째", style = SitterText.Display, color = s.ink)
        Text(
            "${clockLabel(session.startedAt)} 시작 · " + if (session.climateApplied) "정차 중 송풍" else "공조는 그대로",
            style = SitterText.Label,
            color = s.inkMute,
            modifier = Modifier.padding(top = 4.dp),
        )
        val progress = feedingStopProgress(session, now)
        Box(
            Modifier
                .padding(top = 18.dp)
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(s.tile)
                .drawBehind { drawRoundRect(SitterPalette.Primary, size = Size(size.width * progress, size.height), cornerRadius = CornerRadius(size.height / 2)) },
        )
        Spacer(Modifier.weight(1f))
        SitterButton("수유 끝났어요", onClick = onFinish, modifier = Modifier.fillMaxWidth().height(76.dp))
    }
}

/** 오른쪽 슬롯 "수유 정차 모드 적용 중" (C03). */
@Composable
fun FeedingStopSlot(session: FeedingStopSession, summary: String, modifier: Modifier = Modifier) {
    val s = SitterSurface.current
    SitterCard(modifier.fillMaxWidth(), tone = SitterCardTone.Active, contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SitterIcon(SitterIcons.Milk, s.primary, size = SitterIconSize.Medium)
            Text("수유 정차 모드 적용 중", style = SitterText.Title3, color = s.ink, modifier = Modifier.padding(start = 12.dp))
        }
        Text(
            if (session.climateApplied) "정차 중에만 켜져요 · $summary" else "이 차에서는 공조를 바꾸지 못해 수유 시간만 세요",
            style = SitterText.Caption,
            color = s.inkSoft,
            modifier = Modifier.padding(start = 36.dp, top = 6.dp),
        )
        Column(Modifier.padding(top = 26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CheckLine("[수유 끝났어요] → 시작 시각으로 기록, 다음 수유 시각 다시 계산")
            CheckLine("못 눌러도 출발하면 자동 기록 (${FEEDING_STOP_MIN_RECORD_MINUTES}분 이상일 때)")
            CheckLine("출발(P단 해제)하면 자동 종료 → 베이비케어 값 또는 이전 공조로")
        }
    }
}

@Composable
private fun CheckLine(text: String) {
    val s = SitterSurface.current
    Row(verticalAlignment = Alignment.Top) {
        SitterIcon(SitterIcons.Check, s.primary, size = SitterIconSize.Small)
        Text(text, style = SitterText.Body2, color = s.inkSoft, modifier = Modifier.padding(start = 10.dp))
    }
}

/** 질문을 보이는 시간 (D01과 같은 8초). 지나면 "괜찮아요"로 본다. */
private const val ASK_CLOSE_MS = 8_000L

/**
 * 케어 경유지에 도착해 정차하면 묻는다: "수유 정차 모드를 켤까요?" (C10·C12 안내, Q08 ②). 자동으로 켜지 않는다.
 */
@Composable
fun BoxScope.FeedingStopAsk(place: String?, summary: String, onYes: () -> Unit, onNo: () -> Unit) {
    val s = SitterSurface.current
    val latestNo by rememberUpdatedState(onNo)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // 도착할 때는 내장 내비가 앞에 있다. 케어 화면이 보일 때부터 8초를 센다 (안 보일 때 혼자 닫히지 않게).
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(ASK_CLOSE_MS)
            latestNo()
        }
    }
    SitterScrim(onClick = onNo)
    Column(
        Modifier
            .align(Alignment.Center)
            .width(640.dp)
            .shadow(24.dp, SitterShapes.Sheet)
            .clip(SitterShapes.Sheet)
            .background(s.card)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(28.dp),
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(s.primarySoft), contentAlignment = Alignment.Center) {
            SitterIcon(SitterIcons.Milk, s.primary, size = SitterIconSize.Medium)
        }
        Text("수유 정차 모드를 켤까요?", style = SitterText.Title1, color = s.ink, modifier = Modifier.padding(top = 16.dp))
        Text(
            (place?.let { "${it}에 정차했어요. " } ?: "정차했어요. ") + "$summary 설정으로 맞추고 수유 시간을 셀게요",
            style = SitterText.Body1,
            color = s.inkSoft,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SitterButton("켜기", onClick = onYes, modifier = Modifier.weight(1f), size = SitterButtonSize.Large)
            SitterButton("괜찮아요", onClick = onNo, modifier = Modifier.weight(1f), kind = SitterButtonKind.Soft, size = SitterButtonSize.Large)
        }
    }
}

/** 한 줄 안내 토스트 (맨 위 800x76). 화면이 보이는 동안 6초. [at]이 바뀌면 다시 보인다. 1분 지난 안내는 보이지 않는다. */
@Composable
fun BoxScope.CareNoticeToast(message: String?, at: Long) {
    message ?: return
    var visible by remember(at) { mutableStateOf(System.currentTimeMillis() - at < 60_000L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(at) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(6_000L)
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
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(SitterPalette.Gradient), contentAlignment = Alignment.Center) {
            SitterIcon(SitterIcons.Milk, Color.White, size = SitterIconSize.Medium)
        }
        Text(message, style = SitterText.Body1, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

/** 오른쪽 슬롯 "잠든 지 N분" (C04 수면 모드, 다크 팔레트에서 그려진다). */
@Composable
fun SleepSlot(startedAt: Long?, now: Long, voicePercent: Int, onWake: () -> Unit, modifier: Modifier = Modifier) {
    val s = SitterSurface.current
    SitterCard(modifier.fillMaxWidth(), tone = SitterCardTone.Active, contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 16.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SitterIcon(SitterIcons.Moon, s.primary, size = SitterIconSize.Medium)
            Text(io.github.haeyeonyou.familytrip.care.sleepingLabel(startedAt, now) ?: "수면 모드 켜짐", style = SitterText.Title2, color = s.ink, modifier = Modifier.padding(start = 12.dp))
        }
        Text("낮잠 · 수면 모드 적용 중", style = SitterText.Caption, color = s.primary, modifier = Modifier.padding(start = 36.dp, top = 6.dp))
        Column(Modifier.padding(top = 26.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            CheckLine("안내 음성 $voicePercent% · 알림은 소리 없이")
            CheckLine("앱 화면 어둡게")
            CheckLine("도착 5분 전 조용히 알림")
        }
        Spacer(Modifier.weight(1f))
        SitterButton("수면 모드 끄기 · “애 깼다”", onClick = onWake, kind = SitterButtonKind.Soft, modifier = Modifier.fillMaxWidth().height(64.dp))
    }
}
