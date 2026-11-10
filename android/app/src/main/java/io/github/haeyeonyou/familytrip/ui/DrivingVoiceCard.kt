package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.haeyeonyou.familytrip.care.CareKind
import io.github.haeyeonyou.familytrip.data.CarePlace
import io.github.haeyeonyou.familytrip.voice.DriveProposal
import io.github.haeyeonyou.familytrip.voice.VoiceUiState

/**
 * 주행 중 음성 1곳 제안 (11/10 디자인 D01, Figma 356:7052). 앱 프레임 가운데 모달(약 1000x660).
 * 목록 대신 1곳만 보이고, 큰 버튼 2개(Drive 규격 높이 96)로 답한다. 키보드·문장 버튼은 두지 않는다.
 * 문장 자리는 답을 받은 뒤 결과 문구로 바뀌고 버튼은 사라진다. 8초 무응답 닫기는 컨트롤러가 센다.
 */
@Composable
fun DrivingVoiceCard(
    state: VoiceUiState,
    proposal: DriveProposal,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Figma D01은 상황 톤 없이 기본 밝은 색 (카드 흰색, 문장 연보라, 후보 행 회색)
    val s = SitterSurface.Light
    Box(modifier = modifier.fillMaxSize().clickable(enabled = false) {}) {
        SitterScrim(alpha = 0.5f)
        // 앱 프레임(1256x886) 기준 크기로 그린다: 카드 1000x660 가운데
        SitterCanvas(ownsDock = false) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = 1000.dp, height = 660.dp)
                    .clip(SitterShapes.Sheet)
                    .background(s.card)
                    .padding(horizontal = 40.dp, vertical = 38.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradientCircleButton(size = 80.dp) { SitterIcon(SitterIcons.Mic, Color.White, size = SitterIconSize.Large) }
                    VoiceWave(s.primary, Modifier.padding(start = 20.dp, end = 24.dp))
                    Column {
                        Text("방금 말씀하신 것", style = SitterText.Caption, color = s.inkMute)
                        Text(if (state.heard.isBlank()) "—" else "“${state.heard}”", style = SitterText.Title1, color = s.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                // 제안 문장 (답한 뒤에는 결과 문구)
                Row(
                    modifier = Modifier.padding(top = 28.dp).fillMaxWidth().height(150.dp).clip(SitterShapes.Row).background(s.primarySoft).padding(horizontal = 36.dp, vertical = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SitterIcon(SitterIcons.Sprout, s.primary, size = SitterIconSize.Medium)
                    Text(state.message, style = SitterText.Title2, color = s.ink, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.padding(top = 24.dp)) { PlaceRow(proposal, s) }
                Spacer(Modifier.weight(1f))
                if (proposal.waiting) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SitterButton(if (proposal.destination) "좋아 · 안내" else "좋아 · 추가", onAccept, Modifier.weight(1.6f), size = SitterButtonSize.Drive, icon = SitterIcons.Turn)
                        SitterButton("아니요", onDecline, Modifier.weight(1f), kind = SitterButtonKind.Soft, size = SitterButtonSize.Drive)
                    }
                    Text("주행 중에는 목록 대신 1곳만 음성으로 제안해요 · 8초 안에 답이 없으면 닫혀요", style = SitterText.Caption, color = s.inkMute, modifier = Modifier.padding(top = 24.dp))
                }
            }
        }
    }
}

/** 마이크 옆 음성 파형 (Figma D01: 높이가 다른 세로 막대 9개, Primary 그라데이션 느낌을 알파로) */
@Composable
private fun VoiceWave(color: Color, modifier: Modifier = Modifier) {
    val heights = listOf(14, 26, 38, 22, 44, 30, 40, 24, 16)
    Row(modifier.height(48.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        heights.forEachIndexed { i, h ->
            Box(Modifier.width(4.dp).height(h.dp).clip(CircleShape).background(color.copy(alpha = 0.45f + 0.55f * (i + 1) / heights.size)))
        }
    }
}

@Composable
private fun PlaceRow(proposal: DriveProposal, s: SitterSurface) {
    val place = proposal.place
    SitterRow(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(s.card), contentAlignment = Alignment.Center) {
            val jelly = proposal.jelly
            if (jelly != null) {
                JellyIcon(jelly, io.github.haeyeonyou.familytrip.grow.JellyStage.DROP, 48.dp)
            } else {
                SitterIcon(
                    when (proposal.kind) {
                        CareKind.NURSING -> SitterIcons.Milk
                        CareKind.DIAPER -> SitterIcons.Baby
                        else -> SitterIcons.Parking
                    },
                    s.primary,
                    size = SitterIconSize.Medium,
                )
            }
        }
        Column(Modifier.padding(start = 20.dp)) {
            Text(place.name, style = SitterText.Title2, color = s.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // 그로우 다시 가볼 곳은 거리 대신 "3주 전 · 자연·숲 · 4번 방문"
            Text(if (proposal.destination) place.note else proposalDetail(place), style = SitterText.Body2, color = s.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "1.2km · 경로에서 +3분 · 유모차 · 주차" (D01 후보 행) */
fun proposalDetail(place: CarePlace): String = buildList {
    add(distanceLabel(place.distanceM))
    if (place.onRoute) add("경로 위") else place.detourMinutes?.let { add("경로에서 +${it}분") } ?: add("${place.driveMinutes}분")
    if (place.note.isNotBlank()) add(place.note)
}.joinToString(" · ")
