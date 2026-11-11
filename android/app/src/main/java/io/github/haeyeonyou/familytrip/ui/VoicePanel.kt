package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.haeyeonyou.familytrip.care.CareTone
import io.github.haeyeonyou.familytrip.care.ToneState
import io.github.haeyeonyou.familytrip.data.CarePlace
import io.github.haeyeonyou.familytrip.voice.VOICE_SAMPLES
import io.github.haeyeonyou.familytrip.voice.VoiceUiState

/**
 * 음성 케어 패널 (VX01~VX04). 앱 프레임 위에 덮이며, 지도·회전 안내·주행 정보는 다른 앱이라 영향이 없다.
 * 11/10 셸 톤: Figma에 "듣는 중" 화면이 없어 D01 위쪽(마이크 원 + 파형 + "방금 말씀하신 것")과
 * Voice Button·Button·Chip 컴포넌트를 기준으로 그렸다. 색은 [SitterSurface] (수면 모드면 다크).
 * 문장 버튼은 운전 중에도 누를 수 있고, 키보드 입력창은 주차 중에만 보인다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoicePanel(
    state: VoiceUiState,
    isParked: Boolean,
    onSubmit: (String) -> Unit,
    onListen: () -> Unit,
    onClose: () -> Unit,
) {
    if (!state.open) return
    // 주행 중 1곳 제안 (D01): 목록·문장 버튼 대신 큰 버튼 2개짜리 카드
    state.proposal?.let { proposal ->
        DrivingVoiceCard(state, proposal, onAccept = { onSubmit("좋아") }, onDecline = { onSubmit("아니요") })
        return
    }
    val s = SitterSurface.current
    // 급한 케어·수면처럼 상황이 정해지면 그 상황 색으로 표시 (태그·고른 후보 테두리). 기본 톤(NONE)의 옛 초록 대신 인디고.
    val tone = state.situation?.tone ?: ToneState.current
    val accent = if (tone == CareTone.NONE) s.primary else tone.accent
    Box(Modifier.fillMaxSize().background(s.app).clickable(enabled = false) {}) {
        // 좁은 창에서도 통째로 줄이지 않는다: 이미 전체 폭 세로 스크롤이라 폭만 창에 맞추면 된다 (#245)
        SitterCanvas(ownsDock = false) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 40.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // 머리: 마이크 원(누르면 다시 듣기) + 파형 + "방금 말씀하신 것"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradientCircleButton(size = 80.dp, onClick = onListen) { SitterIcon(SitterIcons.Mic, Color.White, size = SitterIconSize.Large) }
                    VoiceWave(s.primary, Modifier.padding(start = 20.dp, end = 24.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (state.listening) "듣고 있어요…" else "방금 말씀하신 것", style = SitterText.Caption, color = s.inkMute)
                        Text(
                            if (state.heard.isBlank()) (if (state.listening) "말씀해 주세요" else "—") else "“${state.heard}”",
                            style = SitterText.Title1,
                            color = s.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    state.situation?.let {
                        Text(
                            if (it.name == "SLEEP") "수면 모드" else "급한 케어",
                            style = SitterText.Label,
                            color = Color.White,
                            modifier = Modifier.padding(end = 16.dp).clip(SitterShapes.Tag).background(accent).padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    SitterButton("닫기", onClose, kind = SitterButtonKind.Soft, icon = SitterIcons.X)
                }

                // 답변 (D01 제안 문장 자리와 같은 연보라 상자)
                val line = when {
                    state.busy -> "찾고 있어요…"
                    state.message.isNotBlank() -> state.message
                    state.notice != null -> state.notice
                    else -> ""
                }
                if (line.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(SitterShapes.Row).background(s.primarySoft).padding(horizontal = 32.dp, vertical = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        SitterIcon(SitterIcons.Sprout, s.primary, size = SitterIconSize.Medium)
                        Text(line, style = SitterText.Title2, color = s.ink, modifier = Modifier.weight(1f))
                    }
                }
                if (state.notice != null && state.message.isNotBlank()) {
                    Text(state.notice, style = SitterText.Body2, color = SitterPalette.Danger)
                }

                // 되묻기: 운전 중 위험할 수 있는 동작은 확인 뒤에 한다
                if (state.confirm != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SitterButton("응, 해 주세요", { onSubmit("응") }, size = SitterButtonSize.Large)
                        SitterButton("아니야", { onSubmit("아니야") }, kind = SitterButtonKind.Soft, size = SitterButtonSize.Large)
                    }
                }

                // 후보
                if (state.options.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        state.options.forEachIndexed { i, place ->
                            OptionCard(i + 1, place, if (state.optionsAreDestinations) place.note else placeDetail(place), selected = state.chosen == i + 1, accent = accent, modifier = Modifier.weight(1f)) {
                                onSubmit("${i + 1}번으로 가줘")
                            }
                        }
                        if (state.options.size == 1) Box(Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        SitterButton("그대로 가기", { onSubmit("그대로 가기") }, kind = SitterButtonKind.Soft, size = SitterButtonSize.Large)
                        Text("“1번으로 가줘”라고 말해도 돼요 · “아니야”로 취소", style = SitterText.Body2, color = s.inkMute)
                    }
                }

                // 문장 버튼 (운전 중에도 가능) + 주차 중에만 입력창
                Text("이렇게 말해도 돼요", style = SitterText.Title3, color = s.inkSoft)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    VOICE_SAMPLES.forEach { sample -> SitterChip(sample, selected = false, onClick = { onSubmit(sample) }) }
                }
                if (isParked) TypedInput(onSubmit)
            }
        }
    }
}

@Composable
private fun OptionCard(number: Int, place: CarePlace, detail: String, selected: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val s = SitterSurface.current
    Column(
        modifier = modifier
            .clip(SitterShapes.Card)
            .background(s.card)
            .border(if (selected) 3.dp else 1.5.dp, if (selected) accent else s.line, SitterShapes.Card)
            .clickable(onClick = onClick)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (selected) accent else s.primarySoft), contentAlignment = Alignment.Center) {
            Text("$number", style = SitterText.Button, color = if (selected) Color.White else s.primary)
        }
        Text(place.name, style = SitterText.Title2, color = s.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, style = SitterText.Body2, color = if (place.onRoute) accent else s.inkMute)
    }
}

/** 주차 중 입력용: 말 대신 문장을 쳐서 같은 처리를 태운다. 주행 중에는 그리지 않는다(키보드 입력 금지). */
@Composable
private fun TypedInput(onSubmit: (String) -> Unit) {
    val s = SitterSurface.current
    var text by remember { mutableStateOf("") }
    fun send() {
        if (text.isNotBlank()) onSubmit(text.trim())
        text = ""
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
                .clip(SitterShapes.Pill)
                .background(s.card)
                .border(1.5.dp, s.line, SitterShapes.Pill)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) Text("직접 입력 (주차 중에만)", style = SitterText.Body1, color = s.inkMute)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = SitterText.Body1.copy(color = s.ink),
                cursorBrush = SolidColor(s.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SitterButton("보내기", ::send, size = SitterButtonSize.Large)
    }
}
