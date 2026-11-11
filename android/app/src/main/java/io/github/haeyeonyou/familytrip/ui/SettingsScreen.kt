package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.data.AccountUser
import io.github.haeyeonyou.familytrip.data.CarePreferences
import io.github.haeyeonyou.familytrip.data.FamilyProfile
import io.github.haeyeonyou.familytrip.data.FeedingRoutine
import io.github.haeyeonyou.familytrip.data.IntervalStepper
import io.github.haeyeonyou.familytrip.data.NapWindow
import io.github.haeyeonyou.familytrip.data.Preference
import io.github.haeyeonyou.familytrip.data.ageLabel
import io.github.haeyeonyou.familytrip.data.durationLabel
import kotlinx.coroutines.launch

/** 설정 화면이 밖으로 내보내는 동작 */
data class SettingsActions(
    val onEditProfile: () -> Unit,
    val onLogin: () -> Unit,
    val onLogout: () -> Unit,
    val onOpenDev: () -> Unit,
    val onPreference: (Preference, Boolean) -> Unit,
    /** 수유 간격(분). 케어 화면과 같은 값을 바꾼다. */
    val onInterval: (Int) -> Unit,
    val onNap: (NapWindow) -> Unit,
    /** 데이터 전체 삭제. [includeServer]이면 서버 데이터도 지운다. 성공하면 null, 실패하면 화면에 보일 안내를 돌려준다. */
    val onWipe: suspend (includeServer: Boolean) -> String?,
)

/**
 * 설정 (피그마 HF05). 안내 줄 아래 카드 4개: 가족 프로필 / 루틴 / 선호 조건 / 개인정보·데이터.
 * 로그인·로그아웃·개발자 화면은 피그마에 없어 카드 아래 한 줄로 둔다.
 *
 * 주행 중에도 버튼은 쓸 수 있다(조수석 조작). 키보드 입력이 필요한 가족 정보 수정은 그 화면에서 막는다.
 */
@Composable
fun SettingsScreen(
    profile: FamilyProfile?,
    account: AccountUser?,
    isParked: Boolean,
    prefs: CarePreferences,
    feeding: FeedingRoutine,
    nap: NapWindow,
    actions: SettingsActions,
) = DrivingAware(isParked) {
    val wide = !isNarrowWindow()
    val cards: List<@Composable (Modifier) -> Unit> = listOf(
        { m -> FamilyCard(profile, actions.onEditProfile, m) },
        // 수유 간격은 첫째 아이 값이라 첫째가 12개월 이하일 때만 보인다 (#217)
        { m -> RoutineCard(feeding.intervalMinutes, nap, actions.onInterval, actions.onNap, m, showFeeding = io.github.haeyeonyou.familytrip.care.isInfant(profile?.children?.firstOrNull())) },
        { m -> PreferenceCard(prefs, actions.onPreference, m) },
        { m -> DataCard(loggedIn = account != null, onWipe = actions.onWipe, modifier = m) },
    )
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 56.dp, end = 56.dp, top = 44.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        InfoBar()
        Text("설정", fontSize = 44.sp, fontWeight = FontWeight.Bold)
        if (wide) {
            cards.chunked(2).forEach { pair ->
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    pair.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        } else {
            cards.forEach { it(Modifier.fillMaxWidth()) }
        }
        AccountBar(account, actions)
    }
}

/** 맨 위 안내 줄 (피그마: 정차 중에만 편집 · 주행 중에는 보기만) */
@Composable
private fun InfoBar() {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TripColors.Tile).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(FigmaIcons.Parking, contentDescription = null, tint = TripColors.InkSoft, modifier = Modifier.size(24.dp))
        // 피그마 문구는 '주행 중에는 보기만 가능'이지만, 앱 규칙(버튼은 주행 중에도 허용, 키보드만 정차 중)에 맞춰 바꿨다.
        Text("키보드 입력은 정차 중에만 할 수 있어요 · 주행 중에는 버튼으로만 바꿀 수 있어요", fontSize = 19.sp, fontWeight = FontWeight.Medium, color = TripColors.InkSoft)
    }
}

// 카드 공통

@Composable
private fun SettingsCard(title: String, icon: ImageVector, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(TripColors.Card)
            .border(1.dp, TripColors.Line, RoundedCornerShape(24.dp))
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(TripColors.GreenSoft), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = TripColors.Green, modifier = Modifier.size(22.dp))
            }
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** 카드 안 한 줄: 왼쪽 이름, 오른쪽 값(또는 조작). [last]가 아니면 아래에 구분선. */
@Composable
private fun SettingRow(label: String, last: Boolean, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            trailing()
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(TripColors.Line))
    }
}

@Composable
private fun ValueText(text: String) = Text(text, fontSize = 19.sp, color = TripColors.InkSoft)

/** 피그마 'Button / Secondary': 회색 바탕 큰 버튼 */
@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.height(80.dp).clip(RoundedCornerShape(20.dp)).background(TripColors.Tile).clickable(onClick = onClick).padding(horizontal = 36.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 26.sp, fontWeight = FontWeight.SemiBold) }
}

// ① 가족 프로필

@Composable
private fun FamilyCard(profile: FamilyProfile?, onEdit: () -> Unit, modifier: Modifier) {
    SettingsCard("가족 프로필", FigmaIcons.Baby, modifier) {
        val children = profile?.children.orEmpty()
        if (children.isEmpty()) {
            SettingRow("아이", last = false) { ValueText("아직 등록하지 않았어요") }
        } else {
            children.forEachIndexed { i, child ->
                val label = child.name.ifBlank { if (children.size == 1) "아이" else "아이 ${i + 1}" }
                val months = child.ageMonths()
                val age = if (months < 36) "생후 ${ageLabel(months)}" else ageLabel(months)
                SettingRow(label, last = false) { ValueText("${child.birthYear}.${child.birthMonth.toString().padStart(2, '0')} 출생 · $age") }
            }
        }
        SettingRow("반려동물", last = true) { ValueText(if (profile?.petAlong == true) "반려견 함께" else "함께 가지 않아요") }
        Spacer(Modifier.height(10.dp))
        SecondaryButton(if (profile == null) "가족 등록" else "가족 정보 수정", onEdit, Modifier.fillMaxWidth())
    }
}

// ② 루틴

@Composable
private fun RoutineCard(intervalMinutes: Int, nap: NapWindow, onInterval: (Int) -> Unit, onNap: (NapWindow) -> Unit, modifier: Modifier, showFeeding: Boolean = true) {
    var napOpen by rememberSaveable { mutableStateOf(false) }
    SettingsCard("루틴", FigmaIcons.Clock, modifier) {
        if (showFeeding) SettingRow("수유 간격", last = false) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                RoundStepButton(FigmaIcons.Minus, "수유 간격 줄이기", enabled = intervalMinutes > IntervalStepper.MIN) { onInterval(IntervalStepper.step(intervalMinutes, -1)) }
                Text(durationLabel(intervalMinutes.toLong()), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 64.dp))
                RoundStepButton(FigmaIcons.Plus, "수유 간격 늘리기", enabled = intervalMinutes < IntervalStepper.MAX) { onInterval(IntervalStepper.step(intervalMinutes, +1)) }
            }
        }
        SettingRow("낮잠 시간", last = true, modifier = Modifier.clickable { napOpen = true }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ValueText(nap.label())
                Text("›", fontSize = 26.sp, color = TripColors.InkSoft)
            }
        }
    }
    if (napOpen) NapDialog(nap, onNap, onClose = { napOpen = false })
}

/** 40dp 동그란 −/+ 버튼. 누르는 영역은 56dp로 넓힌다. */
@Composable
private fun RoundStepButton(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(56.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(TripColors.Tile), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = if (enabled) TripColors.Ink else TripColors.InkSoft.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
        }
    }
}

/** 낮잠 시작·끝 시각을 30분씩 올리고 내린다 (키보드 없이 버튼만). */
@Composable
private fun NapDialog(nap: NapWindow, onNap: (NapWindow) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("낮잠 시간", fontSize = 28.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NapLine("시작", nap.label().substringBefore(" –"), { onNap(nap.shiftStart(-NapWindow.STEP)) }, { onNap(nap.shiftStart(NapWindow.STEP)) })
                NapLine("끝", nap.label().substringAfter("– "), { onNap(nap.shiftEnd(-NapWindow.STEP)) }, { onNap(nap.shiftEnd(NapWindow.STEP)) })
                Text("30분 단위로 바꿀 수 있어요", fontSize = 18.sp, color = TripColors.InkSoft)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("확인", fontSize = 24.sp) } },
    )
}

@Composable
private fun NapLine(label: String, time: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundStepButton(FigmaIcons.Minus, "$label 30분 앞으로", onClick = onMinus)
            Text(time, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(88.dp), textAlign = TextAlign.Center)
            RoundStepButton(FigmaIcons.Plus, "$label 30분 뒤로", onClick = onPlus)
        }
    }
}

// ③ 선호 조건

private val PREFERENCE_LABELS = listOf(
    Preference.NURSING_ROOM to "수유실 있는 곳만",
    Preference.STROLLER to "유모차 진입 가능",
    Preference.INDOOR_ON_RAIN to "비 · 눈 오면 실내 우선",
    Preference.PET to "반려견 동반 가능만",
)

@Composable
private fun PreferenceCard(prefs: CarePreferences, onChange: (Preference, Boolean) -> Unit, modifier: Modifier) {
    SettingsCard("선호 조건", FigmaIcons.Sliders, modifier) {
        PREFERENCE_LABELS.forEachIndexed { i, (preference, label) ->
            val on = prefs.isOn(preference)
            SettingRow(
                label,
                last = i == PREFERENCE_LABELS.lastIndex,
                modifier = Modifier.toggleable(value = on, role = Role.Switch, onValueChange = { onChange(preference, it) }),
            ) { Toggle(on) }
        }
    }
}

/** 피그마 Toggle 76×42: 켜면 초록 바탕에 흰 동그라미가 오른쪽 */
@Composable
private fun Toggle(on: Boolean) {
    Box(
        modifier = Modifier.size(width = 76.dp, height = 42.dp).clip(RoundedCornerShape(21.dp)).background(if (on) TripColors.Green else TripColors.Line),
    ) {
        Box(Modifier.offset(x = if (on) 38.dp else 4.dp, y = 4.dp).size(34.dp).clip(CircleShape).background(Color.White))
    }
}

// ④ 개인정보 · 데이터

@Composable
private fun DataCard(loggedIn: Boolean, onWipe: suspend (Boolean) -> String?, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    // 0 없음, 1 확인, 2 서버도 지울지(로그인 중), 3 지우는 중, 4 결과
    var step by rememberSaveable { mutableStateOf(0) }
    var result by rememberSaveable { mutableStateOf("") }

    fun run(includeServer: Boolean) {
        step = 3
        scope.launch {
            val error = onWipe(includeServer)
            result = error ?: if (includeServer) "이 차와 서버의 데이터를 모두 지웠어요" else "이 차의 데이터를 지웠어요"
            step = 4
        }
    }

    SettingsCard("개인정보 · 데이터", FigmaIcons.Shield, modifier) {
        // 문구는 docs/research/web-server-contract.md 7절(폰 웹과 공통)을 따른다. Figma 문구와 다른 점은 PR에 적었다.
        SettingRow("생년월일 · 방문 기록", last = false) { ValueText(if (loggedIn) "차 안 + 내 계정(서버)에 저장" else "차 안에만 저장") }
        SettingRow("서버 전송", last = true) { ValueText("생년월일의 일 · 방문 시각 · 음성은 안 보내요") }
        Text(
            if (loggedIn) "로그인 중이라 아이 정보·수유 기록·방문 기록이 서버에 저장돼 폰과 차에서 같이 봐요. 서버에는 태어난 연·월만 가요. 코스를 찾을 때 쓰는 위치·아이 나이(개월)·다녀온 곳 요약은 저장하지 않아요."
            else "로그인하지 않아 아이·수유·방문 기록은 서버에 저장하지 않아요. 코스를 찾을 때만 위치·아이 나이(개월)·다녀온 곳 요약이 그 요청에 쓰이고, 저장하지 않아요.",
            fontSize = 17.sp,
            color = TripColors.InkSoft,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
        )
        SecondaryButton("데이터 전체 삭제", { step = 1 })
        Text(
            if (loggedIn) "지우면 되돌릴 수 없어요. 서버 데이터도 지울지 따로 물어봐요."
            else "지우면 되돌릴 수 없어요. 로그인하지 않아 지울 서버 데이터는 없어요.",
            fontSize = 17.sp,
            color = TripColors.InkSoft,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    when (step) {
        1 -> AlertDialog(
            onDismissRequest = { step = 0 },
            title = { Text("데이터를 모두 지울까요?", fontSize = 28.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "이 차에 저장된 가족 정보, 방문 기록, 저장한 코스, 수유 기록, 선호 조건, 낮잠 시간이 모두 지워져요.\n\n되돌릴 수 없어요.",
                    fontSize = 22.sp,
                )
            },
            confirmButton = { TextButton(onClick = { if (loggedIn) step = 2 else run(false) }) { Text(if (loggedIn) "다음" else "모두 지우기", fontSize = 24.sp, color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text("취소", fontSize = 24.sp) } },
        )
        2 -> AlertDialog(
            onDismissRequest = { step = 0 },
            title = { Text("서버에 저장된 데이터도 지울까요?", fontSize = 28.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "서버에는 내 계정의 가족 정보, 수유 기록, 방문 기록이 있어요. 서버도 지우면 폰 웹에서도 사라지고 되돌릴 수 없어요.\n\n" +
                        "이 차만 지우면 서버에 남은 데이터를 다음 동기화 때 다시 받아올 수 있어요.",
                    fontSize = 22.sp,
                )
            },
            confirmButton = { TextButton(onClick = { run(true) }) { Text("서버도 지우기", fontSize = 24.sp, color = MaterialTheme.colorScheme.error) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { step = 0 }) { Text("취소", fontSize = 24.sp) }
                    TextButton(onClick = { run(false) }) { Text("이 차만 지우기", fontSize = 24.sp) }
                }
            },
        )
        3 -> AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("지우는 중…", fontSize = 28.sp, fontWeight = FontWeight.Bold) })
        4 -> AlertDialog(
            onDismissRequest = { step = 0 },
            text = { Text(result, fontSize = 22.sp) },
            confirmButton = { TextButton(onClick = { step = 0 }) { Text("확인", fontSize = 24.sp) } },
        )
    }
}

// 계정·개발자 (피그마에 없는 기존 기능)

@Composable
private fun AccountBar(account: AccountUser?, actions: SettingsActions) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (account != null) "${account.displayName} (${account.loginId}) 로그인 중" else "로그인하면 폰에서 보낸 공조 설정을 받을 수 있어요",
            fontSize = 20.sp,
            color = TripColors.InkSoft,
            modifier = Modifier.weight(1f),
        )
        if (account != null) {
            TextButton(onClick = actions.onLogout) { Text("로그아웃", fontSize = 20.sp) }
        } else {
            TextButton(onClick = actions.onLogin) { Text("폰으로 로그인 (QR)", fontSize = 20.sp) }
        }
        TextButton(onClick = actions.onOpenDev) { Text("개발자 화면", fontSize = 18.sp, color = TripColors.InkSoft) }
    }
}
