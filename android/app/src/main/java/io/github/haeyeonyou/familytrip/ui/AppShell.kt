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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.data.CourseStop
import io.github.haeyeonyou.familytrip.data.FamilyProfile
import io.github.haeyeonyou.familytrip.data.ageLabel

/** 왼쪽 메뉴 (피그마 PX: 홈 · 케어 · 그로우 · 설정). */
enum class MainTab(val label: String, val icon: String) {
    HOME("홈", "⌂"),
    CARE("케어", "♡"),
    GROW("그로우", "✦"),
    SETTINGS("설정", "☰"),
}

/** 한 창일 때 지도를 보이게 남겨 두는 왼쪽 폭. */
private val MAP_STRIP = 360.dp

/**
 * 앱 전체 틀 (피그마 지수 PX00 레이어 구조).
 * - 한 창(1682dp): 우리 창 안에 지도 레이어(L0)를 깔고, 그 위에 반투명 앱 패널(L2)을 띄운다.
 * - 화면 분할(841dp, 내비 지도는 옆 창): 지도 없이 패널만, 메뉴는 아이콘만.
 * Pleos에서는 다른 앱(내장 내비) 위에 겹칠 수 없어서, 지도는 우리 창 안에 그린다.
 */
@Composable
fun AppShell(
    tab: MainTab?,
    onTab: (MainTab) -> Unit,
    profile: FamilyProfile?,
    mapStops: List<CourseStop>,
    content: @Composable () -> Unit,
) {
    val narrow = isNarrowWindow()
    Box(modifier = Modifier.fillMaxSize().background(TripColors.MapGround)) {
        if (!narrow) CourseMapLayer(stops = mapStops, modifier = Modifier.fillMaxSize())
        Surface(
            color = TripColors.Panel.copy(alpha = if (narrow) 1f else 0.94f),
            shape = RoundedCornerShape(if (narrow) 0.dp else 40.dp),
            modifier = Modifier
                .fillMaxHeight()
                .then(if (narrow) Modifier.fillMaxWidth() else Modifier.padding(start = MAP_STRIP, top = 24.dp, end = 24.dp, bottom = 24.dp))
                .then(if (narrow) Modifier else Modifier.shadow(24.dp, RoundedCornerShape(40.dp), clip = false)),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (tab != null) SideMenu(tab, onTab, profile, compact = narrow)
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) { content() }
            }
        }
    }
}

@Composable
private fun SideMenu(selected: MainTab, onTab: (MainTab) -> Unit, profile: FamilyProfile?, compact: Boolean) {
    Column(
        modifier = Modifier.fillMaxHeight().width(if (compact) 112.dp else 260.dp).padding(vertical = 32.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(TripColors.Green),
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
        ChildCard(profile, compact)
    }
}

@Composable
private fun MenuItem(tab: MainTab, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) TripColors.Tile else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (compact) Arrangement.Center else Arrangement.spacedBy(16.dp),
    ) {
        Text(tab.icon, fontSize = 26.sp, color = if (selected) TripColors.Green else MaterialTheme.colorScheme.onSurfaceVariant)
        if (!compact) Text(tab.label, fontSize = 24.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
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
