package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.haeyeonyou.familytrip.care.ChildOption
import io.github.haeyeonyou.familytrip.care.STAGE_INFANT
import io.github.haeyeonyou.familytrip.care.STAGE_TODDLER

/** 아바타 바탕 (Figma C07: 첫째 노랑, 둘째 파랑). 셋째부터는 돌아가며. */
private val AVATAR_COLORS = listOf(Color(0xFFFFE79A) to Color(0xFF5B4A00), Color(0xFFDCE6FF) to Color(0xFF2F3FA8), Color(0xFFE6F6EA) to Color(0xFF1F6B3A))

/**
 * 아이 선택 드롭다운 (C07, 앱 프레임 (24,160) 480 폭, 막 0.35). 아이를 바꾸면 수유 타이머와 케어 모드 값(공조 프리셋)이
 * 그 아이 기준이 된다 (QO5, #192). 행마다 그 아이의 베이비케어 값을 한 줄 더 보여 준다.
 */
@Composable
fun BoxScope.ChildSelectDropdown(options: List<ChildOption>, onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    val s = SitterSurface.current
    SitterScrim(alpha = 0.35f, onClick = onDismiss)
    Column(
        Modifier
            .offset(x = 24.dp, y = 160.dp)
            .width(480.dp)
            .shadow(24.dp, SitterShapes.Sheet)
            .clip(SitterShapes.Sheet)
            .background(s.card)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(12.dp),
    ) {
        options.take(MAX_ROWS).forEachIndexed { i, o ->
            val (bg, fg) = AVATAR_COLORS[i % AVATAR_COLORS.size]
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(if (o.mode != null) 116.dp else 100.dp)
                    .clip(SitterShapes.Row)
                    .background(if (o.selected) s.primarySoft else Color.Transparent)
                    .clickable(enabled = o.enabled, role = Role.RadioButton) { onSelect(o.key) }
                    .alpha(if (o.enabled) 1f else 0.5f)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                    Text(o.name.take(1), style = SitterText.Title3, color = fg)
                }
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(o.name, style = SitterText.Title3, color = s.ink)
                        Text("${o.ageMonths}개월", style = SitterText.Label, color = s.inkMute)
                        AgeStageBadge(o.stage)
                    }
                    if (o.line.isNotBlank()) Text(o.line, style = SitterText.Caption, color = s.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    o.mode?.let { Text("베이비케어 $it", style = SitterText.Caption, color = s.inkMute, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
                }
                if (o.selected) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SitterIcon(SitterIcons.Check, s.primary, size = SitterIconSize.Medium)
                        Text("케어 중", style = SitterText.Caption, color = s.primary, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (i < options.size - 1) Box(Modifier.height(12.dp))
        }
        Box(Modifier.padding(horizontal = 12.dp, vertical = 12.dp).fillMaxWidth().height(1.dp).background(s.line))
        Column(Modifier.padding(horizontal = 12.dp)) {
            Text("아이를 바꾸면 케어 화면 전체가 그 아이 기준으로 바뀌어요", style = SitterText.Label, color = s.ink)
            Text("수유 타이머와 모드 값(베이비케어·낮잠·수유 정차)을 아이마다 따로 써요", style = SitterText.Caption, color = s.inkMute, modifier = Modifier.padding(top = 8.dp))
            Text("아이 추가 · 정보 수정은 웹앱 MY에서", style = SitterText.Caption, color = s.inkSoft, modifier = Modifier.padding(top = 18.dp, bottom = 12.dp))
        }
    }
}

private const val MAX_ROWS = 3

/** 월령 배지 색 (Figma C07 `369:13974`): 영아기 노랑, 유아기 연두. */
private val STAGE_COLORS = mapOf(STAGE_INFANT to (Color(0xFFFFE79A) to Color(0xFF5B4A00)), STAGE_TODDLER to (Color(0xFFDDF5C2) to Color(0xFF2F6B12)))

@Composable
private fun RowScope.AgeStageBadge(stage: String) {
    val (bg, fg) = STAGE_COLORS[stage] ?: return
    Text(
        stage,
        style = SitterText.Caption,
        color = fg,
        modifier = Modifier.align(Alignment.CenterVertically).clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
