package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.data.FamilyProfile
import io.github.haeyeonyou.familytrip.data.ageLabel

/** 왼쪽 메뉴 (피그마 PX: 홈 · 케어 · 그로우 · 설정). */
enum class MainTab(val label: String, val icon: String) {
    HOME("홈", "⌂"),
    CARE("케어", "♡"),
    GROW("그로우", "✦"),
    SETTINGS("설정", "☰"),
}

/**
 * 앱 전체 틀 (피그마 HF: 아이콘 아래 이름이 붙은 접힌 왼쪽 메뉴). 메뉴는 펴지지 않는다.
 * PX는 내비 지도 위에 반투명 패널을 띄우지만, Pleos에서는 다른 앱 위에 겹칠 수 없어 패널을 창 전체로 쓴다 (11/7 결정).
 * 실제 지도는 화면 분할로 내장 내비를 옆에 띄워 보고, 코스 경로는 코스 화면의 미리보기 카드로 보여 준다.
 */
@Composable
fun AppShell(
    tab: MainTab?,
    onTab: (MainTab) -> Unit,
    onMic: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Surface(color = TripColors.Panel, modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (tab != null) SideMenu(tab, onTab, onMic)
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) { content() }
        }
    }
}

@Composable
private fun SideMenu(selected: MainTab, onTab: (MainTab) -> Unit, onMic: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(140.dp)
            .background(TripColors.Card)
            .padding(vertical = 32.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(TripColors.Green),
            contentAlignment = Alignment.Center,
        ) { Text("🌱", fontSize = 26.sp) }
        Spacer(Modifier.size(24.dp))
        MainTab.entries.forEach { item -> MenuItem(item, item == selected) { onTab(item) } }
        Spacer(Modifier.weight(1f))
        // 음성 요청 (#19, #70): 누르면 음성 케어 패널이 열리고 한 문장을 듣는다
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(TripColors.Tile).clickable(onClick = onMic),
            contentAlignment = Alignment.Center,
        ) { Text("🎙", fontSize = 28.sp) }
    }
}

/** HF 레일: 아이콘 아래 이름 */
@Composable
private fun MenuItem(tab: MainTab, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) TripColors.Green else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) TripColors.GreenSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(tab.icon, fontSize = 30.sp, color = color)
        Text(tab.label, fontSize = 18.sp, color = color, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
