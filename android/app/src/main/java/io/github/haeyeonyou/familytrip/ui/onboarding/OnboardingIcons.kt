package io.github.haeyeonyou.familytrip.ui.onboarding

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 온보딩에만 쓰는 선 아이콘 (24, 선 2). [io.github.haeyeonyou.familytrip.ui.SitterIcons]에 없는 차·문·공조(팬)·아이 얼굴.
 * 경로는 Lucide(ISC 라이선스) `car`·`door-open`·`fan`·`smile`에서 옮겼다. 색은 쓰는 곳에서 정한다.
 */
private fun lineIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

object OnboardingIcons {
    val Car = lineIcon(
        "car",
        "M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3c-.5-.4-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.4 2.9A3.7 3.7 0 0 0 2 12v4c0 .6.4 1 1 1h2",
        "M7 17m-2 0a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
        "M9 17h6",
        "M17 17m-2 0a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
    )
    val Door = lineIcon(
        "door",
        "M13 4h3a2 2 0 0 1 2 2v14",
        "M2 20h3",
        "M13 20h9",
        "M10 12v.01",
        "M13 4.562v16.157a1 1 0 0 1-1.242.97L5 20V5.562a2 2 0 0 1 1.515-1.94l4-1A2 2 0 0 1 13 4.561Z",
    )
    val Fan = lineIcon(
        "fan",
        "M10.827 16.379a6.082 6.082 0 0 1-8.618-7.002l5.412 1.45a6.082 6.082 0 0 1 7.002-8.618l-1.45 5.412a6.082 6.082 0 0 1 8.618 7.002l-5.412-1.45a6.082 6.082 0 0 1-7.002 8.618l1.45-5.412Z",
        "M12 12v.01",
    )
    val Smile = lineIcon(
        "smile",
        "M12 12m-10 0a10 10 0 1 0 20 0a10 10 0 1 0 -20 0",
        "M8 14s1.5 2 4 2 4-2 4-2",
        "M9 9h.01",
        "M15 9h.01",
    )
}
