package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.data.Child
import io.github.haeyeonyou.familytrip.data.FamilyProfile
import io.github.haeyeonyou.familytrip.data.ageLabel
import java.time.YearMonth

private const val MAX_CHILDREN = 4
private const val MAX_CHILD_AGE_YEARS = 13

/**
 * 가족 정보 등록·수정 (혜연 P09). 생년월은 버튼으로만 고른다 (주행 중 키보드 입력 금지와 같은 원칙).
 * 저장한 정보는 차 안에만 둔다.
 */
@Composable
fun ProfileScreen(
    initial: FamilyProfile?,
    isParked: Boolean,
    onSave: (FamilyProfile) -> Unit,
    onBack: () -> Unit,
) = DrivingAware(isParked) {
    val now = remember { YearMonth.now() }
    var profile by remember { mutableStateOf(initial ?: FamilyProfile()) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("우리 가족", fontSize = 40.sp)
            Text(
                "한 번 등록하면 코스를 만들 때마다 아이 나이를 다시 말하지 않아도 돼요. 생년월은 차 안에만 저장돼요.",
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("아이", fontSize = 28.sp)
            if (profile.children.isEmpty()) {
                Text("아직 등록한 아이가 없어요", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            profile.children.forEachIndexed { index, child ->
                ChildRow(
                    order = index + 1,
                    child = child,
                    now = now,
                    isParked = isParked,
                    onChange = { updated ->
                        profile = profile.copy(children = profile.children.toMutableList().apply { this[index] = updated })
                    },
                    onRemove = { profile = profile.copy(children = profile.children.filterIndexed { i, _ -> i != index }) },
                )
            }
            if (profile.children.size < MAX_CHILDREN) {
                BigOutlinedButton("아이 추가", {
                    // 기본값은 한 살 전 이번 달. 버튼으로 바로 고칠 수 있다.
                    val birth = now.minusYears(1)
                    profile = profile.copy(children = profile.children + Child(birth.year, birth.monthValue))
                })
            }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("어른", fontSize = 28.sp, modifier = Modifier.width(160.dp))
                BigOutlinedButton("−", { profile = profile.copy(adults = (profile.adults - 1).coerceAtLeast(1)) })
                Text("${profile.adults}명", fontSize = 28.sp)
                BigOutlinedButton("+", { profile = profile.copy(adults = (profile.adults + 1).coerceAtMost(6)) })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("반려견도 함께 다녀요", fontSize = 28.sp, modifier = Modifier.weight(1f))
                Switch(checked = profile.petAlong, onCheckedChange = { profile = profile.copy(petAlong = it) })
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.padding(horizontal = 40.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BigButton("저장", { onSave(profile) }, modifier = Modifier.weight(1f))
            BigOutlinedButton("취소", onBack)
        }
    }
}

@Composable
private fun ChildRow(order: Int, child: Child, now: YearMonth, isParked: Boolean, onChange: (Child) -> Unit, onRemove: () -> Unit) {
    val birth = YearMonth.of(child.birthYear, child.birthMonth)
    fun shift(months: Long) {
        val moved = birth.plusMonths(months)
        // 미래와 너무 먼 과거는 고를 수 없다.
        if (moved > now || moved < now.minusYears(MAX_CHILD_AGE_YEARS.toLong())) return
        onChange(child.copy(birthYear = moved.year, birthMonth = moved.monthValue))
    }
    val info: @Composable () -> Unit = {
        Column(modifier = Modifier.width(240.dp)) {
            Text("${order}번째 아이", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
            Text("${child.birthYear}년 ${child.birthMonth}월생", fontSize = 26.sp)
            Text("지금 ${ageLabel(child.ageMonths(now))}", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val steppers: @Composable () -> Unit = {
        Text("연", fontSize = 22.sp)
        BigOutlinedButton("−", { shift(-12) })
        BigOutlinedButton("+", { shift(12) })
        Text("월", fontSize = 22.sp, modifier = Modifier.padding(start = 16.dp))
        BigOutlinedButton("−", { shift(-1) })
        BigOutlinedButton("+", { shift(1) })
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        // 이름은 키보드 입력이라 정차 중에만 바꿀 수 있다.
        OutlinedTextField(
            value = child.name,
            onValueChange = { onChange(child.copy(name = it.take(10))) },
            enabled = isParked,
            label = { Text("이름 (선택)") },
            singleLine = true,
            modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp),
        )
        if (isNarrowWindow()) {
            // 세로 창: 정보·삭제 한 줄, 연·월 버튼 한 줄
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    info()
                    Text("", modifier = Modifier.weight(1f))
                    BigOutlinedButton("삭제", onRemove)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { steppers() }
            }
        } else {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                info()
                steppers()
                Text("", modifier = Modifier.weight(1f))
                BigOutlinedButton("삭제", onRemove)
            }
        }
    }
}
