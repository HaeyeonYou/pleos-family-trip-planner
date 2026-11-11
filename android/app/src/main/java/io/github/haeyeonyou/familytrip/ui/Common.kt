package io.github.haeyeonyou.familytrip.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ROLE_LABELS = mapOf("meal" to "식사", "outing" to "나들이", "cafe" to "카페", "fuel" to "주유", "home" to "귀가")
val THEME_LABELS = mapOf(
    "nature" to "자연",
    "experience" to "체험",
    "exhibition" to "전시",
    "play" to "쇼핑·놀이",
    "pet" to "반려동물",
)
val NEED_LABELS = mapOf(
    "stroller" to "유아차",
    "nursing_room" to "수유실",
    "kids_chair" to "유아 의자",
    "kids_zone" to "키즈존",
    "parking" to "주차",
)

/** 장소 카드에 보여 줄 편의 정보 이름 (육아 편의 + 반려견 동반). */
val AMENITY_LABELS = NEED_LABELS + ("pet" to "반려견 동반")

/** 세로 창으로 볼 폭. 앱 2개를 띄우면 창이 약 840dp가 된다 (가로 1개일 때 1682dp). */
internal const val NARROW_WINDOW_DP = 1200

/**
 * 화면 분할로 우리 앱이 세로 창이 됐는지. Pleos는 모든 앱이 가로·세로를 지원해야 한다 (Window Policy).
 * 분할 창에서도 Configuration 폭은 창 폭이다.
 */
@Composable
fun isNarrowWindow(): Boolean = LocalConfiguration.current.screenWidthDp < NARROW_WINDOW_DP

/** 차량 화면 기준의 큰 버튼 (터치 영역 76dp 이상). 11/10 디자인의 Primary(그라데이션) 버튼이다. */
@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    SitterButton(text, onClick, modifier, SitterButtonKind.Primary, SitterButtonSize.Large, enabled)

/** 큰 Line 버튼 (흰 바탕 + 테두리). */
@Composable
fun BigOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
    SitterButton(text, onClick, modifier, SitterButtonKind.Line, SitterButtonSize.Large)

/**
 * 주행 중이면 화면 위에 안내 띠를 띄운다. 조수석에서 조작할 수 있으므로 버튼은 막지 않고,
 * 키보드 입력만 각 화면에서 막는다.
 */
@Composable
fun DrivingAware(isParked: Boolean, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (!isParked) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 40.dp, vertical = 16.dp),
            ) {
                Text(
                    "주행 중이에요. 운전자는 조작하지 말고 동승자가 조작해 주세요.",
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        content()
    }
}

/** PX 화면 제목 (큰 제목 + 회색 부제). */
@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun ErrorText(message: String?) {
    message?.let {
        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 22.sp, modifier = Modifier.padding(vertical = 8.dp))
    }
}

/** 알림 띠를 보일지. 첫 실행 온보딩(O01~) 동안은 숨기고, 권한을 묻는 O03에서 거절한 뒤 앱으로 들어왔을 때만 보인다. */
fun notificationBannerVisible(allowed: Boolean, onboarding: Boolean): Boolean = !allowed && !onboarding

/**
 * 알림이 꺼져 있으면 내비가 앞에 있을 때 도착 안내를 받을 수 없음을 알린다.
 * 앱 프레임을 줄이지 않도록 떠 있는 띠로 그린다 (부르는 쪽이 [modifier]로 자리를 정한다. 지금은 위쪽 음성 바의 안내 문구 자리).
 * X로 닫으면 앱을 다시 켤 때까지 숨긴다.
 */
@Composable
fun NotificationOffBanner(visible: Boolean, onAllow: () -> Unit, modifier: Modifier = Modifier) {
    var dismissed by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (!visible || dismissed) return
    val s = SitterSurface.current
    Row(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .shadow(8.dp, SitterShapes.Row)
            .clip(SitterShapes.Row)
            .background(SitterPalette.WarnFill)
            .padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SitterIcon(SitterIcons.Alert, SitterPalette.Warn, size = SitterIconSize.Medium)
        Text(
            "알림이 꺼져 있어 내비 화면에서 도착 안내를 실시간으로 받을 수 없어요",
            style = SitterText.Body1,
            color = s.ink,
            modifier = Modifier.weight(1f, fill = false),
        )
        SitterButton("알림 허용", onAllow)
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "알림 안내 닫기") { dismissed = true },
            contentAlignment = Alignment.Center,
        ) { SitterIcon(SitterIcons.X, s.inkSoft, size = SitterIconSize.Medium) }
    }
}

// ───────────────────────── 11/10 공통 컴포넌트 (Figma Components 361:5000 + 화면에서 반복되는 것) ─────────────────────────
// 색·글자는 SitterSurface.current / SitterText에서 읽는다. 화면 파일은 hex나 fontSize를 직접 쓰지 않는다.

enum class SitterButtonKind { Primary, Soft, Line }

/**
 * 버튼 높이·글자. Figma는 Regular 56(Line 59), 코스 시작 72, 주행 중 제안(D01) 96을 섞어 쓴다.
 * 차량 터치 영역 규칙(76dp 이상)에 맞춰 Large는 76으로 둔다 (Figma 72보다 4 큼). 주행 중 큰 버튼은 Drive(96).
 */
enum class SitterButtonSize(val height: Dp, val text: TextStyle, val horizontalPadding: Dp) {
    Regular(56.dp, SitterText.Button, 24.dp),
    Large(76.dp, SitterText.Title2, 32.dp),
    Drive(96.dp, SitterText.Title2, 40.dp),
}

/**
 * 버튼 (Figma Button 361:5025). 알약 모양. Primary = 그라데이션 + 흰 글자, Soft = 타일색 + 진한 글자,
 * Line = 카드색 + 테두리 1.5. [icon]은 글자 앞에 24 크기로 붙는다.
 */
@Composable
fun SitterButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: SitterButtonKind = SitterButtonKind.Primary,
    size: SitterButtonSize = SitterButtonSize.Regular,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val s = SitterSurface.current
    val contentColor = if (kind == SitterButtonKind.Primary) Color.White else s.ink
    val base = modifier
        .heightIn(min = size.height)
        .alpha(if (enabled) 1f else 0.4f)
        .then(
            if (kind == SitterButtonKind.Primary && enabled) {
                Modifier.shadow(6.dp, SitterShapes.Pill, ambientColor = SitterPalette.GradientShadow, spotColor = SitterPalette.GradientShadow)
            } else {
                Modifier
            },
        )
        .clip(SitterShapes.Pill)
    val styled = when (kind) {
        SitterButtonKind.Primary -> base.background(SitterPalette.Gradient)
        SitterButtonKind.Soft -> base.background(s.tile)
        SitterButtonKind.Line -> base.background(s.card).border(1.5.dp, s.line, SitterShapes.Pill)
    }
    Row(
        modifier = styled
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = size.horizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        icon?.let { SitterIcon(it, contentColor, size = SitterIconSize.Medium) }
        Text(text, style = size.text, color = contentColor)
    }
}

/** 그라데이션 원 버튼 (음성 버튼 44, 길 안내 64, 코스 항목의 화살표 52 등). 안에 아이콘 등을 넣는다. */
@Composable
fun GradientCircleButton(
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(6.dp, CircleShape, ambientColor = SitterPalette.GradientShadow, spotColor = SitterPalette.GradientShadow)
            .clip(CircleShape)
            .background(SitterPalette.Gradient)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** 음성 버튼 (Figma Voice Button 368:5723): 44 그라데이션 원 + 마이크 24. */
@Composable
fun VoiceButton(modifier: Modifier = Modifier, onClick: () -> Unit = LocalShell.current?.onMic ?: {}) {
    if (!io.github.haeyeonyou.familytrip.onboarding.LocalVoiceEnabled.current) return // 온보딩 O03에서 음성을 끄면 마이크만 숨김 (QO6)
    GradientCircleButton(size = 44.dp, modifier = modifier, onClick = onClick) {
        SitterIcon(SitterIcons.Mic, Color.White, size = SitterIconSize.Medium)
    }
}

/**
 * 태그 (Figma Tag 361:5009): 둥근 10, 타일색 바탕, 글자 15 Regular. [available]이 true면 체크(Primary), false면 빼기(약한 글자색),
 * null이면 아이콘 없이 글자만 (예: "총 5시간 30분"은 [icon]을 따로 넘긴다).
 */
@Composable
fun SitterTag(label: String, modifier: Modifier = Modifier, available: Boolean? = true, icon: ImageVector? = null) {
    val s = SitterSurface.current
    Row(
        modifier = modifier.clip(SitterShapes.Tag).background(s.tile).padding(start = 8.dp, top = 5.dp, end = 10.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when {
            icon != null -> SitterIcon(icon, s.inkSoft, size = 16.dp)
            available == true -> SitterIcon(SitterIcons.Check, s.primary, size = 16.dp)
            available == false -> SitterIcon(SitterIcons.Minus, s.inkMute, size = 16.dp)
        }
        Text(label, style = SitterText.Caption, color = s.inkSoft, maxLines = 1)
    }
}

/**
 * 칩 (필터·아이 선택·빠른 길 안내 줄, 높이 53, 알약). 선택되면 Soft 바탕 + Primary 테두리·글자.
 * [icon]은 글자 앞 아이콘 (선택 시 체크 등), [trailingIcon]은 글자 뒤 아이콘 (드롭다운 화살표 등).
 */
@Composable
fun SitterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    val s = SitterSurface.current
    val color = if (selected) s.primary else s.ink
    Row(
        modifier = modifier
            .height(53.dp)
            .clip(SitterShapes.Pill)
            .background(if (selected) s.primarySoft else s.card)
            .border(1.5.dp, if (selected) s.primary else s.line, SitterShapes.Pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon?.let { SitterIcon(it, color, size = SitterIconSize.Medium) }
        Text(label, style = SitterText.Body1, color = color, maxLines = 1)
        trailingIcon?.let { SitterIcon(it, color, size = SitterIconSize.Small) }
    }
}

/** 카드 색 의미 (C05 임박 = Warn, C06 지남 = Danger, C03·C04 정차·수면 적용 중 = Active). */
enum class SitterCardTone { Plain, Active, Warn, Danger }

/**
 * 카드 (둥근 24, 안쪽 20). Plain은 카드색, Active는 Soft 바탕 + Primary 테두리 2,
 * Warn·Danger는 주황·빨강 바탕 + 테두리 2.
 */
@Composable
fun SitterCard(
    modifier: Modifier = Modifier,
    tone: SitterCardTone = SitterCardTone.Plain,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val s = SitterSurface.current
    val (fill, line) = when (tone) {
        SitterCardTone.Plain -> s.card to null
        SitterCardTone.Active -> s.primarySoft to s.primary
        SitterCardTone.Warn -> SitterPalette.WarnFill to SitterPalette.Warn
        SitterCardTone.Danger -> SitterPalette.DangerFill to SitterPalette.Danger
    }
    Column(
        modifier = modifier
            .clip(SitterShapes.Card)
            .background(fill)
            .then(if (line != null) Modifier.border(2.dp, line, SitterShapes.Card) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * 행 (코스 항목·타임라인 행·장소 행의 바탕, 둥근 20). 기본은 타일색, 선택하면 Soft 바탕 (+ [outlined]이면 Primary 테두리 2).
 * [onClick]이 있으면 눌러서 쓴다.
 */
@Composable
fun SitterRow(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    outlined: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val s = SitterSurface.current
    Row(
        modifier = modifier
            .clip(SitterShapes.Row)
            .background(if (selected) s.primarySoft else s.tile)
            .then(if (selected && outlined) Modifier.border(2.dp, s.primary, SitterShapes.Row) else Modifier)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** 토글 (C04 베이비케어 모드 스위치): 72x40 알약, 켜지면 Primary, 손잡이는 흰색 32. */
@Composable
fun SitterToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val s = SitterSurface.current
    val track by animateColorAsState(if (checked) s.primary else s.line, label = "toggleTrack")
    val knobX by animateDpAsState(if (checked) 36.dp else 4.dp, label = "toggleKnob")
    Box(
        modifier = modifier
            .size(72.dp, 40.dp)
            .clip(SitterShapes.Pill)
            .background(track)
            .semantics { role = Role.Switch; toggleableState = if (checked) ToggleableState.On else ToggleableState.Off }
            .clickable(role = Role.Switch) { onCheckedChange(!checked) },
    ) {
        Box(Modifier.offset(x = knobX, y = 4.dp).size(32.dp).clip(CircleShape).background(Color.White))
    }
}
