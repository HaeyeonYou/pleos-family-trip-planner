package io.github.haeyeonyou.familytrip.ui

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.haeyeonyou.familytrip.R
import io.github.haeyeonyou.familytrip.grow.JellyKind
import io.github.haeyeonyou.familytrip.grow.JellyStage
import kotlin.math.roundToInt

/*
 * 젤리 캐릭터 그림 (#164, 원본 반영 #181). Figma 컴포넌트 세트 `Jelly Character`(371:6514)의 PNG 2배를
 * `res/drawable-nodpi/jelly_<캐릭터>_<drop|puff|born>.png`로 넣었다 (`docs/design/pleos-1110/assets/grow/README.md`).
 * PNG는 200x200 프레임을 2배(400)로 내보내면서 그림자가 오른쪽·아래로 번진 만큼 더 크다. 그래서 왼쪽 위를 프레임 원점으로 보고
 * 400px = 프레임 한 변으로 맞춰 그린다. SVG는 흐림 필터를 써서 벡터로 옮기면 그림자·광택이 빠지므로 쓰지 않는다.
 */

/** PNG에서 Figma 프레임(200) 한 변의 픽셀 수 (2배). */
private const val FRAME_PX = 400f

/**
 * 경험 우주에서 젤리 프레임 한 변(설계 단위). 원본 그림이 단계마다 프레임 안 크기가 달라(방울은 작고 탄생은 꽉 참)
 * 모든 단계를 같은 프레임으로 그리면 Figma 그대로 커진다. 탄생 몸통이 분류 노드 반지름(28)쯤 된다.
 */
internal const val UNIVERSE_JELLY_FRAME = 76f

internal fun jellyRes(kind: JellyKind, stage: JellyStage): Int {
    val (drop, puff, born) = when (kind) {
    JellyKind.ART -> Triple(R.drawable.jelly_unicorn_drop, R.drawable.jelly_unicorn_puff, R.drawable.jelly_unicorn_born)
    JellyKind.SCIENCE -> Triple(R.drawable.jelly_dinosaur_drop, R.drawable.jelly_dinosaur_puff, R.drawable.jelly_dinosaur_born)
    JellyKind.NATURE -> Triple(R.drawable.jelly_bear_drop, R.drawable.jelly_bear_puff, R.drawable.jelly_bear_born)
    JellyKind.ANIMAL -> Triple(R.drawable.jelly_puppy_drop, R.drawable.jelly_puppy_puff, R.drawable.jelly_puppy_born)
    JellyKind.WATER -> Triple(R.drawable.jelly_capybara_drop, R.drawable.jelly_capybara_puff, R.drawable.jelly_capybara_born)
    JellyKind.MOVE -> Triple(R.drawable.jelly_horse_drop, R.drawable.jelly_horse_puff, R.drawable.jelly_horse_born)
    JellyKind.MUSIC -> Triple(R.drawable.jelly_cat_drop, R.drawable.jelly_cat_puff, R.drawable.jelly_cat_born)
    JellyKind.BOOK -> Triple(R.drawable.jelly_wombat_drop, R.drawable.jelly_wombat_puff, R.drawable.jelly_wombat_born)
    JellyKind.FOOD -> Triple(R.drawable.jelly_hamster_drop, R.drawable.jelly_hamster_puff, R.drawable.jelly_hamster_born)
    JellyKind.FRIEND -> Triple(R.drawable.jelly_penguin_drop, R.drawable.jelly_penguin_puff, R.drawable.jelly_penguin_born)
    JellyKind.MAKE -> Triple(R.drawable.jelly_beaver_drop, R.drawable.jelly_beaver_puff, R.drawable.jelly_beaver_born)
    JellyKind.TRAVEL -> Triple(R.drawable.jelly_otter_drop, R.drawable.jelly_otter_puff, R.drawable.jelly_otter_born)
    }
    return when (stage) {
        JellyStage.NONE, JellyStage.DROP -> drop
        JellyStage.PUFF -> puff
        JellyStage.BORN -> born
    }
}

internal fun loadJelly(resources: Resources, kind: JellyKind, stage: JellyStage): ImageBitmap =
    BitmapFactory.decodeResource(resources, jellyRes(kind, stage)).asImageBitmap()

/** 화면에 필요한 젤리 그림만 읽어 둔다 (분류·단계가 바뀔 때만 다시 읽음). */
@Composable
internal fun rememberJellyImages(needed: Set<Pair<JellyKind, JellyStage>>): Map<Pair<JellyKind, JellyStage>, ImageBitmap> {
    val resources = LocalContext.current.resources
    return remember(needed) { needed.associateWith { (k, s) -> loadJelly(resources, k, s) } }
}

@Composable
fun JellyIcon(kind: JellyKind, stage: JellyStage, size: Dp, modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val image = remember(kind, stage) { loadJelly(resources, kind, stage) }
    Canvas(modifier.size(size)) {
        val frame = this.size.minDimension
        drawJellyImage(image, Offset(this.size.width / 2f, this.size.height / 2f), frame)
    }
}

/** 경험 우주의 젤리: 같은 프레임으로 [c]에 그린다. */
internal fun DrawScope.drawJelly(image: ImageBitmap, c: Offset) = drawJellyImage(image, c, UNIVERSE_JELLY_FRAME)

/** 프레임 한 변이 [frame]이 되게, 프레임 가운데를 [c]에 맞춰 그린다. */
private fun DrawScope.drawJellyImage(image: ImageBitmap, c: Offset, frame: Float) {
    val k = frame / FRAME_PX
    drawImage(
        image,
        dstOffset = IntOffset((c.x - frame / 2f).roundToInt(), (c.y - frame / 2f).roundToInt()),
        dstSize = IntSize((image.width * k).roundToInt().coerceAtLeast(1), (image.height * k).roundToInt().coerceAtLeast(1)),
        filterQuality = FilterQuality.High,
    )
}
