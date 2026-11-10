package io.github.haeyeonyou.familytrip.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** 하단 바로가기 (피그마 `Nav · 바로가기`: 홈 · 케어 · 그로우 · 설정). */
enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("홈", SitterIcons.Home),
    CARE("케어", SitterIcons.Heart),
    GROW("그로우", SitterIcons.Sprout),
    SETTINGS("설정", SitterIcons.Sliders),
}

/**
 * 창 크기 → 앱 프레임 위치·크기·확대 배율 (단위 dp). 화면에 그리지 않는 순수 계산이라 단위 테스트한다.
 *
 * 기준은 Figma의 앱 노출 영역 1280x902(640,68)와 그 안의 앱 프레임 1256x886(좌우 12, 위아래 8 안쪽).
 * 넓은 창은 노출 영역을 창에 맞춰 같은 비율로 키우고(가운데 정렬) 프레임을 그 안쪽에 둔다.
 * 분할 화면처럼 좁은 창(세로 창)은 디자인이 없으므로 창 전체를 작은 여백만 두고 쓰고, 배율은 프레임에 들어가는 만큼 줄인다.
 */
data class FrameGeometry(val scale: Float, val left: Float, val top: Float, val width: Float, val height: Float) {
    companion object {
        const val EXPOSURE_W = 1280f
        const val EXPOSURE_H = 902f
        const val FRAME_W = 1256f
        const val FRAME_H = 886f
        const val INSET_X = 12f
        const val INSET_Y = 8f
        const val NARROW_INSET = 8f

        fun of(windowW: Float, windowH: Float): FrameGeometry {
            if (windowW < NARROW_WINDOW_DP) {
                val w = windowW - 2 * NARROW_INSET
                val h = windowH - 2 * NARROW_INSET
                return FrameGeometry(min(w / FRAME_W, h / FRAME_H), NARROW_INSET, NARROW_INSET, w, h)
            }
            val s = min(windowW / EXPOSURE_W, windowH / EXPOSURE_H)
            val left = (windowW - EXPOSURE_W * s) / 2 + INSET_X * s
            val top = (windowH - EXPOSURE_H * s) / 2 + INSET_Y * s
            return FrameGeometry(s, left, top, FRAME_W * s, FRAME_H * s)
        }
    }
}

/** 셸이 아래 화면에 알려 주는 것. 화면은 [LocalShell]로 읽는다. */
@Immutable
class ShellInfo(
    /** 프레임 확대 배율 (Figma px → 실제 dp). 1256x886 디자인 캔버스를 쓰면 이 값만큼 커진다. */
    val scale: Float,
    val variant: ShellVariant,
    val tab: MainTab?,
    val onTab: (MainTab) -> Unit,
    val onMic: () -> Unit,
)

val LocalShell = staticCompositionLocalOf<ShellInfo?> { null }

/**
 * 셸 바깥 장치의 표시 여부. 새 디자인으로 옮긴 화면([SitterCanvas])은 3열째에 바로가기 독을 직접 두므로
 * 아직 옮기지 않은 화면을 위해 셸이 아래에 임시로 두는 독을 숨긴다.
 */
@Stable
class ShellChrome {
    var ownDockCount by mutableIntStateOf(0)
}

val LocalShellChrome = staticCompositionLocalOf { ShellChrome() }

/** 이 화면이 바로가기 독을 직접 둔다고 알린다 (화면이 사라지면 자동으로 되돌린다). */
@Composable
fun OwnsShortcutDock() {
    val chrome = LocalShellChrome.current
    DisposableEffect(chrome) {
        chrome.ownDockCount++
        onDispose { chrome.ownDockCount-- }
    }
}

/**
 * 앱 전체 틀 (11/10 확정 디자인: `App · 시터노트` 프레임). 왼쪽 메뉴 레일은 없어졌고 홈·케어·그로우·설정 이동은
 * 각 화면 3열째의 바로가기 독([ShortcutDock])으로 옮겼다 (코스 상세 H02·H02-1은 독 없이 뒤로가기 버튼).
 *
 * 셸이 하는 일: 창에 맞춘 앱 프레임(둥근 32, 상단 그래버) · 정차/주행·Light/Dark 변형([ShellVariant]) ·
 * [DriveMode] 내려 주기 · (옮기지 않은 화면을 위한) 임시 독.
 * 아직 옛 디자인인 화면은 프레임 안을 1:1 dp로 채우고, 새 화면은 [SitterCanvas]로 1256x886 캔버스를 쓴다.
 * PX는 내비 지도 위에 반투명 패널을 띄우지만, Pleos에서는 다른 앱 위에 겹칠 수 없어 패널을 창 전체로 쓴다 (11/7 결정).
 */
@Composable
fun AppShell(
    tab: MainTab?,
    onTab: (MainTab) -> Unit,
    onMic: () -> Unit = {},
    drive: DriveMode = DriveMode.Stopped,
    content: @Composable () -> Unit,
) {
    val surface = SitterSurface.current
    val chrome = remember { ShellChrome() }
    val shellArea by animateColorAsState(surface.shellArea, label = "shellArea")
    val appColor by animateColorAsState(surface.app, label = "app")
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(shellArea)) {
        val g = remember(maxWidth, maxHeight) { FrameGeometry.of(maxWidth.value, maxHeight.value) }
        val info = ShellInfo(g.scale, ShellVariant.of(surface.dark, drive), tab, onTab, onMic)
        CompositionLocalProvider(LocalShell provides info, LocalShellChrome provides chrome, LocalDriveMode provides drive) {
            Box(
                modifier = Modifier
                    .offset(g.left.dp, g.top.dp)
                    .size(g.width.dp, g.height.dp)
                    .clip(RoundedCornerShape((32 * g.scale).dp))
                    .background(appColor),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
                    if (tab != null && chrome.ownDockCount == 0) LegacyDockStrip(onMic)
                }
                Grabber(g.scale, Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

/** 프레임 위 가운데의 그래버 80x6 (프레임 안 (588,10), #C4CAD1). 터치는 받지 않는다. */
@Composable
private fun Grabber(scale: Float, modifier: Modifier) {
    val color = if (SitterSurface.current.dark) SitterSurface.current.line else Color(0xFFC4CAD1)
    Box(modifier.padding(top = (10 * scale).dp).size((80 * scale).dp, (6 * scale).dp).clip(SitterShapes.Pill).background(color))
}

/** 아직 새 디자인으로 옮기지 않은 화면 아래의 임시 독 줄: 음성 버튼(옛 레일의 마이크 자리)과 바로가기 독. */
@Composable
private fun LegacyDockStrip(onMic: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        GradientCircleButton(size = 72.dp, onClick = onMic) { SitterIcon(SitterIcons.Mic, Color.White, size = 32.dp) }
        ShortcutDock()
    }
}

/**
 * 바로가기 독 `Nav · 바로가기` (Figma 352:3003, 392x92 흰 카드). 아이콘 28 아래 라벨 16 Medium,
 * 선택은 Primary, 나머지는 보조 글자색. 선택·이동은 셸이 내려 준 값이 기본이다.
 */
@Composable
fun ShortcutDock(
    modifier: Modifier = Modifier,
    selected: MainTab? = LocalShell.current?.tab,
    onTab: (MainTab) -> Unit = LocalShell.current?.onTab ?: {},
) {
    val s = SitterSurface.current
    Row(
        modifier = modifier
            .width(392.dp)
            .height(92.dp)
            .clip(SitterShapes.Card)
            .background(s.card),
    ) {
        MainTab.entries.forEach { item ->
            val color = if (item == selected) s.primary else s.inkSoft
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().clickable { onTab(item) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                SitterIcon(item.icon, color, size = SitterIconSize.Dock)
                Text(item.label, style = SitterText.Label, color = color, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
