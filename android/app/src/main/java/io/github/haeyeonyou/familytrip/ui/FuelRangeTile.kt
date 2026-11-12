package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.data.FuelStation
import io.github.haeyeonyou.familytrip.data.LatLng
import io.github.haeyeonyou.familytrip.vehicle.FuelLevel
import io.github.haeyeonyou.familytrip.vehicle.rangeKm
import io.github.haeyeonyou.familytrip.vehicle.stationMeta
import kotlinx.coroutines.launch

/** 연료 타일 색 (Figma H01 `413:13521` PNG에서 읽은 값). 감사 delta의 새 회색 `#D9DDE2`는 값 없을 때 게이지. */
object SitterFuelColors {
    private val dark get() = SitterSurface.current.dark
    val Gauge = Color(0xFFFCE68A)
    /** 수면(다크)에서는 Figma 다크 값이 없어 같은 계열의 어두운 값을 쓴다 (추정) */
    val GaugeEmpty get() = if (dark) SitterSurface.current.line else Color(0xFFD9DDE2)
    val EnoughFill get() = if (dark) Color(0xFF1E3A2A) else Color(0xFFDCFCE7)
    val Enough get() = if (dark) Color(0xFF5CC98A) else Color(0xFF2E9E5B)
    val LowFill get() = if (dark) Color(0xFF3A2231) else Color(0xFFFCF2F8)
    val Low get() = if (dark) Color(0xFFF08DB8) else Color(0xFFD66A98)
}

/** 홈 연료 타일이 밖으로 내보내는 동작 (#215) */
data class FuelActions(
    /** 근처(다음 지점이 있으면 그 길에서 덜 돌아가는) 주유소·충전소 1곳. 못 찾으면 null */
    val find: suspend (toward: LatLng?) -> FuelStation?,
    /** 안내 중이면 경유지로, 아니면 목적지로 내비에 넣는다 */
    val go: (FuelStation) -> Unit,
)

private sealed interface FuelSearch {
    data object Idle : FuelSearch
    data object Searching : FuelSearch
    data class NotFound(val toward: LatLng?, val electric: Boolean) : FuelSearch
    /** [toward]·[electric]: 이 결과를 찾을 때의 기준. 추천 코스가 바뀌면 지난 결과를 보이지 않는다 */
    data class Found(val station: FuelStation, val toward: LatLng?, val electric: Boolean) : FuelSearch
}

/**
 * 홈 H01 3열 주행 가능 거리 타일 (피드백 H01 `369:12016`의 `413:13521`, #215).
 * 노란 물방울 안에 "N km", 오른쪽 위 "충분"/"부족" 태그, "주행 가능 거리", [주유소 찾기](전기차는 [충전소 찾기]).
 * 찾으면 "OO주유소 / 1.2km · 3분 · 경로 위" 줄과 [여기 들르기]로 바뀐다. 값이 없으면 숫자 대신 "확인 중".
 */
@Composable
fun FuelRangeTile(
    rangeMeters: Float?,
    level: FuelLevel?,
    electric: Boolean,
    toward: LatLng?,
    actions: FuelActions?,
    modifier: Modifier = Modifier,
) {
    val s = SitterSurface.current
    val scope = rememberCoroutineScope()
    var stored by remember { mutableStateOf<FuelSearch>(FuelSearch.Idle) }
    val search = when (val st = stored) {
        is FuelSearch.Found -> st.takeIf { it.toward == toward && it.electric == electric } ?: FuelSearch.Idle
        is FuelSearch.NotFound -> st.takeIf { it.toward == toward && it.electric == electric } ?: FuelSearch.Idle
        else -> st
    }
    val kind = if (electric) "충전소" else "주유소"
    val found = (search as? FuelSearch.Found)?.station

    SitterCard(modifier, contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 14.dp)) {
        Box(Modifier.fillMaxSize()) {
            level?.let { LevelTag(it, Modifier.align(Alignment.TopEnd)) }
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                RangeDrop(rangeMeters, if (found != null) 64.dp else 84.dp)
                when (val st = search) {
                    is FuelSearch.Found -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(st.station.place.name, style = SitterText.Label, color = s.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stationMeta(st.station), style = SitterText.Caption, color = s.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    is FuelSearch.NotFound -> Text("근처 ${kind}를 못 찾았어요", style = SitterText.Caption, color = s.inkMute, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    else -> Text(
                        if (rangeMeters == null) "주행 가능 거리 확인 중" else "주행 가능 거리",
                        style = SitterText.Caption, color = s.inkMute, maxLines = 1, textAlign = TextAlign.Center,
                    )
                }
                val label = when (search) {
                    FuelSearch.Searching -> "찾는 중…"
                    is FuelSearch.Found -> "여기 들르기"
                    else -> "$kind 찾기"
                }
                Text(
                    label,
                    style = SitterText.Label,
                    color = s.ink,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().height(44.dp).clip(SitterShapes.Pill).background(s.tile)
                        .clickable(enabled = actions != null && search != FuelSearch.Searching) {
                            val a = actions ?: return@clickable
                            when (val st = search) {
                                is FuelSearch.Found -> a.go(st.station)
                                else -> {
                                    stored = FuelSearch.Searching
                                    val at = toward
                                    scope.launch {
                                        stored = a.find(at)?.let { FuelSearch.Found(it, at, electric) } ?: FuelSearch.NotFound(at, electric)
                                    }
                                }
                            }
                        }
                        .padding(top = 11.dp),
                )
            }
        }
    }
}

@Composable
private fun LevelTag(level: FuelLevel, modifier: Modifier) {
    val (label, fill, ink) = when (level) {
        FuelLevel.ENOUGH -> Triple("충분", SitterFuelColors.EnoughFill, SitterFuelColors.Enough)
        FuelLevel.LOW -> Triple("부족", SitterFuelColors.LowFill, SitterFuelColors.Low)
    }
    Text(
        label,
        style = SitterText.Caption,
        color = ink,
        modifier = modifier.clip(SitterShapes.Tag).background(fill).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** 노란 물방울 게이지 + 안의 "100 / km". 값이 없으면 회색 물방울에 "--". */
@Composable
private fun RangeDrop(rangeMeters: Float?, size: Dp) {
    val fill = if (rangeMeters == null) SitterFuelColors.GaugeEmpty else SitterFuelColors.Gauge
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val r = w * 0.42f
            val cx = w / 2f
            val cy = h - r - h * 0.02f
            val path = Path().apply {
                moveTo(cx, 0f)
                cubicTo(cx + r * 0.35f, h * 0.18f, cx + r, cy - r * 0.55f, cx + r, cy)
                arcTo(Rect(cx - r, cy - r, cx + r, cy + r), 0f, 180f, false)
                cubicTo(cx - r, cy - r * 0.55f, cx - r * 0.35f, h * 0.18f, cx, 0f)
                close()
            }
            drawPath(path, fill)
        }
        Column(Modifier.padding(top = size * 0.22f), horizontalAlignment = Alignment.CenterHorizontally) {
            val big = if (size > 70.dp) 30.sp else 24.sp
            Text(rangeMeters?.let { "${rangeKm(it)}" } ?: "--", style = SitterText.Title1.copy(fontSize = big, lineHeight = big), color = SitterPalette.Scrim, maxLines = 1)
            Text("km", style = SitterText.Label.copy(lineHeight = 18.sp), color = SitterPalette.Scrim)
        }
    }
}
