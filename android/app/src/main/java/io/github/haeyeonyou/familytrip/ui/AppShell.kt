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
 * 앱 전체 틀 (피그마 지수 PX의 메뉴·패널 구성을 창 전체에 적용).
 * PX는 내비 지도 위에 반투명 패널을 띄우지만, Pleos에서는 다른 앱 위에 겹칠 수 없어 패널을 창 전체로 쓴다 (11/7 결정).
 * 실제 지도는 화면 분할로 내장 내비를 옆에 띄워 보고, 코스 경로는 코스 화면의 미리보기 카드로 보여 준다.
 * 화면 분할(841dp)일 때는 메뉴를 아이콘만 남긴다.
 */
@Composable
fun AppShell(
    tab: MainTab?,
    onTab: (MainTab) -> Unit,
    profile: FamilyProfile?,
    content: @Composable () -> Unit,
) {
    val narrow = isNarrowWindow()
    // 로고를 누르면 메뉴를 펴고 접는다 (HF: 아이콘 레일, PX: 이름이 있는 넓은 메뉴). 화면 분할 때는 늘 접는다.
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(color = TripColors.Panel, modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (tab != null) SideMenu(tab, onTab, profile, compact = narrow || !expanded, onToggle = { expanded = !expanded })
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) { content() }
        }
    }
}

@Composable
private fun SideMenu(selected: MainTab, onTab: (MainTab) -> Unit, profile: FamilyProfile?, compact: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(if (compact) 140.dp else 280.dp)
            .background(TripColors.Card)
            .padding(vertical = 32.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Row(
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(TripColors.Green),
                contentAlignment = Alignment.Center,
            ) { Text("🌱", fontSize = 26.sp) }
            if (!compact) {
                Column {
                    Text("우리 아이", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("경험 내비게이션", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.size(24.dp))
        MainTab.entries.forEach { item -> MenuItem(item, item == selected, compact) { onTab(item) } }
        Spacer(Modifier.weight(1f))
        if (!compact) ChildCard(profile, compact = false)
        // 음성 요청 (#19, #70에서 연결)
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(TripColors.Tile),
            contentAlignment = Alignment.Center,
        ) { Text("🎙", fontSize = 28.sp) }
    }
}

@Composable
private fun MenuItem(tab: MainTab, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val color = if (selected) TripColors.Green else MaterialTheme.colorScheme.onSurfaceVariant
    val base = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(if (selected) TripColors.GreenSoft else Color.Transparent)
        .clickable(onClick = onClick)
    if (compact) {
        // HF 레일: 아이콘 아래 이름
        Column(
            modifier = base.padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(tab.icon, fontSize = 30.sp, color = color)
            Text(tab.label, fontSize = 18.sp, color = color, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    } else {
        Row(
            modifier = base.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(tab.icon, fontSize = 26.sp, color = color)
            Text(tab.label, fontSize = 24.sp, color = color, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

/** 메뉴 아래 아이 카드 (피그마: "서아 · 14개월 · 수유 3시간 간격"). 수유 간격은 루틴 기능에서 채운다. */
@Composable
private fun ChildCard(profile: FamilyProfile?, compact: Boolean) {
    val child = profile?.children?.firstOrNull()
    val name = profile?.childName?.takeIf { it.isNotBlank() } ?: "우리 아이"
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TripColors.GreenSoft).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) { Text(name.take(1), fontSize = 20.sp, color = TripColors.Green, fontWeight = FontWeight.Bold) }
        if (!compact) {
            Column {
                Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    child?.let { ageLabel(it.ageMonths()) } ?: "가족 정보를 등록해 주세요",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
