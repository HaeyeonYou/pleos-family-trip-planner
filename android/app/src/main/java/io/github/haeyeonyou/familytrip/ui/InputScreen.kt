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
import io.github.haeyeonyou.familytrip.data.TripConditions

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
    onOpenDev: () -> Unit,
) = DrivingAware(isParked) {
    Column(
        modifier = Modifier.padding(40.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("오늘, 어디 가지?", fontSize = 40.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenDev) { Text("개발자 화면", fontSize = 18.sp) }
        }
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
                if (c.childAges.isEmpty()) "나이 정보 없음" else c.childAges.joinToString { if (it == 0) "돌 전후" else "${it}살" },
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

@Composable
private fun Label(title: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(title, fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
        content()
    }
}

private fun List<String>.toggle(item: String) = if (item in this) this - item else this + item
