package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.data.FamilyProfile
import io.github.haeyeonyou.familytrip.data.TripConditions
import io.github.haeyeonyou.familytrip.data.ageLabel

private val EXAMPLES = listOf(
    "공룡 좋아하는 5살이랑 6시 전에 돌아올 수 있는 곳",
    "18개월 아기랑 비 오는 날, 수유실 있는 곳",
    "강아지랑 7살 딸, 자연에서 놀고 싶어",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InputScreen(
    state: TripUiState,
    isParked: Boolean,
    onTextChange: (String) -> Unit,
    onParse: () -> Unit,
    onConditionsChange: ((TripConditions) -> TripConditions) -> Unit,
    onMakeCourse: () -> Unit,
    onEditProfile: () -> Unit,
    onUseProfile: () -> Unit,
    onApplyPreset: () -> Unit,
    onDismissPreset: () -> Unit,
) = DrivingAware(isParked) {
    Column(
        modifier = Modifier.padding(40.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenTitle("오늘 어디 갈까요?", "반경 10km · 가족과 시간에 맞춘 추천")
        PresetCard(state, onApply = onApplyPreset, onDismiss = onDismissPreset)
        FamilyCard(state.profile, onEdit = onEditProfile, onUse = onUseProfile)
        Text("오늘 나들이를 한 문장으로 알려 주세요", fontSize = 26.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // 주행 중에는 키보드 입력만 막는다 (운전자 주의분산). 예시 선택과 버튼은 그대로 쓸 수 있다.
            OutlinedTextField(
                value = state.requestText,
                onValueChange = onTextChange,
                enabled = isParked,
                textStyle = TextStyle(fontSize = 24.sp),
                placeholder = { Text(EXAMPLES[0], fontSize = 22.sp) },
                supportingText = if (isParked) null else {
                    { Text("주행 중에는 글자를 입력할 수 없어요. 아래 예시를 누르거나 정차 후 입력해 주세요.", fontSize = 18.sp) }
                },
                modifier = Modifier.weight(1f),
            )
            BigButton("조건 만들기", onParse, enabled = state.requestText.isNotBlank() && !state.loading)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EXAMPLES.forEach { example ->
                TextButton(onClick = { onTextChange(example) }) { Text("예: $example", fontSize = 18.sp) }
            }
        }
        if (state.loading) CircularProgressIndicator()
        ErrorText(state.error)

        state.conditions?.let { c ->
            ConditionsEditor(c, state.parsedBy, onConditionsChange)
            BigButton("이 조건으로 코스 만들기", onMakeCourse, enabled = !state.loading, modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionsEditor(
    c: TripConditions,
    parsedBy: String?,
    onChange: ((TripConditions) -> TripConditions) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "이렇게 이해했어요" + if (parsedBy == "claude") " (AI)" else "",
            fontSize = 26.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Label("아이") {
            Text(
                when {
                    c.childAgeMonths.isNotEmpty() -> c.childAgeMonths.joinToString { ageLabel(it) } + " (가족 정보)"
                    c.childAges.isNotEmpty() -> c.childAges.joinToString { if (it == 0) "돌 전후" else "${it}살" }
                    else -> "나이 정보 없음"
                },
                fontSize = 24.sp,
            )
        }
        Label("테마") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                THEME_LABELS.forEach { (key, label) ->
                    FilterChip(
                        selected = key in c.themes,
                        onClick = { onChange { it.copy(themes = it.themes.toggle(key), withPet = if (key == "pet") key !in it.themes else it.withPet) } },
                        label = { Text(label, fontSize = 22.sp, modifier = Modifier.padding(8.dp)) },
                    )
                }
            }
        }
        Label("육아 편의") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NEED_LABELS.forEach { (key, label) ->
                    FilterChip(
                        selected = key in c.needs,
                        onClick = { onChange { it.copy(needs = it.needs.toggle(key)) } },
                        label = { Text(label, fontSize = 22.sp, modifier = Modifier.padding(8.dp)) },
                    )
                }
            }
        }
        if (c.interests.isNotEmpty()) {
            Label("좋아하는 것") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    c.interests.forEach { interest ->
                        InputChip(
                            selected = true,
                            onClick = { onChange { it.copy(interests = it.interests - interest) } },
                            label = { Text("$interest ✕", fontSize = 22.sp, modifier = Modifier.padding(8.dp)) },
                        )
                    }
                }
            }
        }
        // 가족 정보의 '반려견도 함께'가 기본값이고, 오늘 데려가지 않으면 여기서 끈다.
        Label("반려견 함께") {
            Switch(
                checked = c.withPet,
                onCheckedChange = { v ->
                    onChange { it.copy(withPet = v, themes = if (v) it.themes else it.themes - "pet") }
                },
            )
        }
        Label("실내만") {
            Switch(checked = c.indoorOnly, onCheckedChange = { v -> onChange { it.copy(indoorOnly = v) } })
        }
        Label("귀가") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigOutlinedButton("−", { onChange { it.copy(returnByHour = ((it.returnByHour ?: 18) - 1).coerceAtLeast(12)) } })
                Text(c.returnByHour?.let { "${it}시 전" } ?: "제한 없음", fontSize = 26.sp)
                BigOutlinedButton("+", { onChange { it.copy(returnByHour = ((it.returnByHour ?: 17) + 1).coerceAtMost(22)) } })
            }
        }
    }
}

/** 폰에서 보낸 공조 프리셋 (#62). 적용 후에는 결과 문구만 남는다. */
@Composable
fun PresetCard(state: TripUiState, onApply: () -> Unit, onDismiss: () -> Unit) {
    val preset = state.pendingPreset
    if (preset == null) {
        state.presetMessage?.let { Text(it, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary) }
        return
    }
    val s = preset.settings
    val parts = buildList {
        if (!s.power) {
            add("공조 끄기")
        } else {
            add("${s.temperature}°C")
            add("풍량 ${s.fanSpeed}단")
            if (s.ac) add("에어컨")
            if (s.airCleaner) add("공기청정")
        }
        if (s.driverSeatHeat > 0) add("운전석 열선 ${s.driverSeatHeat}단")
        if (s.passengerSeatHeat > 0) add("조수석 열선 ${s.passengerSeatHeat}단")
        if (s.steeringWheelHeat > 0) add("핸들 열선 ${s.steeringWheelHeat}단")
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("폰에서 보낸 설정 · ${s.name}", fontSize = 26.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(parts.joinToString(" · "), fontSize = 24.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigButton(if (state.applyingPreset) "적용하는 중…" else "차에 적용", onApply, enabled = !state.applyingPreset)
                BigOutlinedButton("나중에", onDismiss)
            }
        }
    }
}

/** 등록한 가족 요약 (혜연 P01 가족 프로필 카드). 없으면 등록을 권한다. */
@Composable
private fun FamilyCard(profile: FamilyProfile?, onEdit: () -> Unit, onUse: () -> Unit) {
    val summary: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("우리 가족", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
            if (profile == null) {
                Text("가족 정보를 한 번 등록하면 매번 아이 나이를 말하지 않아도 돼요", fontSize = 22.sp)
            } else {
                val parts = buildList {
                    add("어른 ${profile.adults}명")
                    if (profile.children.isEmpty()) add("아이 없음") else add("아이 " + profile.ageMonths().joinToString { ageLabel(it) })
                    if (profile.petAlong) add("반려견 함께")
                }
                Text(parts.joinToString(" · "), fontSize = 24.sp)
            }
        }
    }
    val actions: @Composable () -> Unit = {
        if (profile != null) BigButton("이 가족으로 조건 만들기", onUse)
        BigOutlinedButton(if (profile == null) "등록" else "수정", onEdit)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        if (isNarrowWindow()) {
            // 세로 창: 요약 아래에 버튼
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                summary()
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { actions() }
            }
        } else {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) { summary() }
                actions()
            }
        }
    }
}

@Composable
private fun Label(title: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(title, fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
        content()
    }
}

private fun List<String>.toggle(item: String) = if (item in this) this - item else this + item
