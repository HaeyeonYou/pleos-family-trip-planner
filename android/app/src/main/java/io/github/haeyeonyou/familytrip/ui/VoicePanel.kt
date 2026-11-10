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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.care.ToneState
import io.github.haeyeonyou.familytrip.data.CarePlace
import io.github.haeyeonyou.familytrip.voice.VOICE_SAMPLES
import io.github.haeyeonyou.familytrip.voice.VoiceUiState

/**
 * 음성 케어 패널 (피그마 VX01~VX04). 앱 화면 위에 덮이며, 색은 앱 패널 안에서만 바뀐다
 * (지도·회전 안내·주행 정보는 다른 앱이라 영향이 없다).
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
    val tone = ToneState.current
    val accent = state.situation?.tone?.accent ?: tone.accent
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TripColors.Panel)
            .clickable(enabled = false) {},
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 머리: 마이크 + "방금 말씀하신 것"
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(accent).clickable(onClick = onListen),
                    contentAlignment = Alignment.Center,
                ) { Text("🎙", fontSize = 30.sp) }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (state.listening) "듣고 있어요…" else "방금 말씀하신 것",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (state.heard.isBlank()) (if (state.listening) "말씀해 주세요" else "—") else "“${state.heard}”",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                state.situation?.let {
                    Text(
                        if (it.name == "SLEEP") "수면 모드" else "급한 케어",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(accent).padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                Text(
                    "닫기",
                    fontSize = 20.sp,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(TripColors.Tile).clickable(onClick = onClose).padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }

            // 답변
            val line = when {
                state.busy -> "찾고 있어요…"
                state.message.isNotBlank() -> state.message
                state.notice != null -> state.notice
                else -> ""
            }
            if (line.isNotBlank()) {
                Text(
                    line,
                    fontSize = 24.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TripColors.Card).border(1.dp, TripColors.Line, RoundedCornerShape(20.dp)).padding(20.dp),
                )
            }
            if (state.notice != null && state.message.isNotBlank()) {
                Text(state.notice, fontSize = 18.sp, color = MaterialTheme.colorScheme.error)
            }

            // 되묻기: 운전 중 위험할 수 있는 동작은 확인 뒤에 한다
            if (state.confirm != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(
                        "응, 해 주세요",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(accent).clickable { onSubmit("응") }.padding(horizontal = 40.dp, vertical = 20.dp),
                    )
                    Text(
                        "아니야",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(TripColors.Tile).clickable { onSubmit("아니야") }.padding(horizontal = 40.dp, vertical = 20.dp),
                    )
                }
            }

            // 후보
            if (state.options.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    state.options.forEachIndexed { i, place ->
                        OptionCard(i + 1, place, selected = state.chosen == i + 1, accent = accent, modifier = Modifier.weight(1f)) {
                            onSubmit("${i + 1}번으로 가줘")
                        }
                    }
                    if (state.options.size == 1) Box(Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(
                        "그대로 가기",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(TripColors.Tile).clickable { onSubmit("그대로 가기") }.padding(horizontal = 40.dp, vertical = 20.dp),
                    )
                    Text("🎙 “1번으로 가줘”라고 말해도 돼요 · “아니야”로 취소", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 문장 버튼 (운전 중에도 가능) + 주차 중에만 입력창
            Text("이렇게 말해도 돼요", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                VOICE_SAMPLES.forEach { sample ->
                    Text(
                        sample,
                        fontSize = 22.sp,
                        modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(TripColors.Card).border(1.dp, TripColors.Line, RoundedCornerShape(24.dp)).clickable { onSubmit(sample) }.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            }
            if (isParked) TypedInput(onSubmit)
        }
    }
}

@Composable
private fun OptionCard(number: Int, place: CarePlace, selected: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(TripColors.Card)
            .border(if (selected) 3.dp else 1.dp, if (selected) accent else TripColors.Line, shape)
            .clickable(onClick = onClick)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(if (selected) accent else TripColors.Ink), contentAlignment = Alignment.Center) {
            Text("$number", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TripColors.Panel)
        }
        Text(place.name, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(placeDetail(place), fontSize = 18.sp, color = if (place.onRoute) accent else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 시연·주차 중 입력용: 말 대신 문장을 쳐서 같은 처리를 태운다. */
@Composable
private fun TypedInput(onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    fun send() {
        if (text.isNotBlank()) onSubmit(text.trim())
        text = ""
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(TripColors.Card).border(1.dp, TripColors.Line, RoundedCornerShape(20.dp)).padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            if (text.isEmpty()) Text("직접 입력 (주차 중에만)", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 22.sp, color = TripColors.Ink),
                cursorBrush = SolidColor(TripColors.Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            "보내기",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(TripColors.Green).clickable { send() }.padding(horizontal = 28.dp, vertical = 18.dp),
        )
    }
}
