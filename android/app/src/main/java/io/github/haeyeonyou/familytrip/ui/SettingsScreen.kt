package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.data.AccountUser
import io.github.haeyeonyou.familytrip.data.FamilyProfile
import io.github.haeyeonyou.familytrip.data.ageLabel

/** 설정 (PX05). 가족 정보·계정·개발자 화면. 수유 간격·추천 반경 같은 기준은 다음 작업에서 더한다. */
@Composable
fun SettingsScreen(
    profile: FamilyProfile?,
    account: AccountUser?,
    isParked: Boolean,
    onEditProfile: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpenDev: () -> Unit,
) = DrivingAware(isParked) {
    Column(
        modifier = Modifier.padding(40.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenTitle("설정", "${profile?.childName?.ifBlank { null } ?: "우리 아이"}에게 맞춘 기준을 바꿔요")
        Section("가족 프로필") {
            val summary = profile?.let { p ->
                buildList {
                    p.children.forEach { add("${it.name.ifBlank { "아이" }} ${it.birthYear}.${it.birthMonth.toString().padStart(2, '0')} 출생 · ${ageLabel(it.ageMonths())}") }
                    add("어른 ${p.adults}명")
                    if (p.petAlong) add("반려견 함께")
                }.joinToString("\n")
            } ?: "아직 등록하지 않았어요"
            Text(summary, fontSize = 22.sp)
            BigOutlinedButton(if (profile == null) "가족 등록" else "가족 정보 수정", onEditProfile)
        }
        Section("계정") {
            if (account != null) {
                Text("${account.displayName} (${account.loginId}) 로그인 중", fontSize = 22.sp)
                BigOutlinedButton("로그아웃", onLogout)
            } else {
                Text("로그인하면 폰에서 보낸 공조 설정을 받을 수 있어요", fontSize = 22.sp)
                BigButton("폰으로 로그인 (QR)", onLogin)
            }
        }
        Section("데이터") {
            Text("가족 정보·방문 기록은 차 안에 저장돼요. 로그인하면 계정에 연결돼요.", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onOpenDev) { Text("개발자 화면", fontSize = 18.sp) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(TripColors.Tile).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        content()
    }
}
