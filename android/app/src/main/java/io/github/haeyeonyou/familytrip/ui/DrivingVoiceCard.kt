package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
    val s = SitterSurface.current
    Box(modifier = modifier.fillMaxSize().clickable(enabled = false) {}) {
        SitterScrim(alpha = 0.5f)
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 1000.dp)
                .fillMaxWidth(0.8f)
                .clip(SitterShapes.Sheet)
                .background(s.card)
                .padding(40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                GradientCircleButton(size = 80.dp) { SitterIcon(SitterIcons.Mic, androidx.compose.ui.graphics.Color.White, size = SitterIconSize.Large) }
                Column {
                    Text("방금 말씀하신 것", style = SitterText.Caption, color = s.inkMute)
                    Text(if (state.heard.isBlank()) "—" else "“${state.heard}”", style = SitterText.Title1, color = s.ink)
                }
            }
            // 제안 문장 (답한 뒤에는 결과 문구)
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp).clip(SitterShapes.Row).background(s.primarySoft).padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SitterIcon(SitterIcons.Sprout, s.primary, size = SitterIconSize.Medium)
                Text(state.message, style = SitterText.Title2, color = s.ink, modifier = Modifier.weight(1f))
            }
            PlaceRow(proposal.kind, proposal.place)
            if (proposal.waiting) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SitterButton("좋아 · 추가", onAccept, Modifier.weight(1.6f), size = SitterButtonSize.Drive, icon = SitterIcons.Turn)
                    SitterButton("아니요", onDecline, Modifier.weight(1f), kind = SitterButtonKind.Soft, size = SitterButtonSize.Drive)
                }
                Text("주행 중에는 목록 대신 1곳만 음성으로 제안해요 · 8초 안에 답이 없으면 닫혀요", style = SitterText.Caption, color = s.inkMute)
            }
        }
    }
}

@Composable
private fun PlaceRow(kind: CareKind, place: CarePlace) {
    val s = SitterSurface.current
    SitterRow(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(s.card), contentAlignment = Alignment.Center) {
            SitterIcon(
                when (kind) {
                    CareKind.NURSING -> SitterIcons.Milk
                    CareKind.DIAPER -> SitterIcons.Baby
                    else -> SitterIcons.Parking
                },
                s.primary,
                size = SitterIconSize.Medium,
            )
        }
        Column(Modifier.padding(start = 20.dp)) {
            Text(place.name, style = SitterText.Title2, color = s.ink)
            Text(proposalDetail(place), style = SitterText.Body2, color = s.primary)
        }
    }
}

/** "1.2km · 경로에서 +3분 · 유모차 · 주차" (D01 후보 행) */
fun proposalDetail(place: CarePlace): String = buildList {
    add(distanceLabel(place.distanceM))
    if (place.onRoute) add("경로 위") else place.detourMinutes?.let { add("경로에서 +${it}분") } ?: add("${place.driveMinutes}분")
    if (place.note.isNotBlank()) add(place.note)
}.joinToString(" · ")
