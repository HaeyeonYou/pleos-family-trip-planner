package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import io.github.haeyeonyou.familytrip.grow.JellyKind
import io.github.haeyeonyou.familytrip.grow.JellyStage

/*
 * 젤리 캐릭터 그림 (#164). B안(374:6875)의 12종 젤리를 원본 그림 없이 같은 규칙으로 그린다:
 * 방울 = 얼굴 없는 물방울, 몽글 = 눈이 생긴 둥근 젤리, 탄생 = 캐릭터 특징(뿔·귀·부리 등)이 붙은 젤리.
 * Figma 원본 그림을 받으면 이 파일만 바꾸면 된다.
 */

private val Eye = Color(0xFF191F28)
private val Blush = Color(0xFFFF8FA3)

@Composable
fun JellyIcon(kind: JellyKind, stage: JellyStage, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f * 0.78f
        drawJelly(kind, stage, Offset(this.size.width / 2f, this.size.height / 2f + r * 0.08f), r)
    }
}

/** [c]를 가운데로 반지름 [r] 안에 젤리를 그린다. 빈 별(NONE)은 그리지 않는다(화면이 따로 그림). */
internal fun DrawScope.drawJelly(kind: JellyKind, stage: JellyStage, c: Offset, r: Float) {
    val base = Color(kind.argb)
    val light = lerp(base, Color.White, 0.55f)
    val dark = lerp(base, Color.Black, 0.25f)
    when (stage) {
        JellyStage.NONE -> Unit
        JellyStage.DROP -> drawDrop(c, r, base, light)
        JellyStage.PUFF -> {
            drawBody(c, r, base, light)
            drawFace(c, r, smile = false)
        }
        JellyStage.BORN -> {
            drawBehind(kind, c, r, base, dark)
            drawBody(c, r, base, light)
            drawFront(kind, c, r, base, light, dark)
            drawFace(c, r, smile = true)
        }
    }
}

private fun DrawScope.drawDrop(c: Offset, r: Float, base: Color, light: Color) {
    val path = Path().apply {
        moveTo(c.x, c.y - r)
        cubicTo(c.x + r * 0.35f, c.y - r * 0.45f, c.x + r * 0.85f, c.y - r * 0.05f, c.x + r * 0.85f, c.y + r * 0.3f)
        cubicTo(c.x + r * 0.85f, c.y + r * 0.8f, c.x + r * 0.45f, c.y + r, c.x, c.y + r)
        cubicTo(c.x - r * 0.45f, c.y + r, c.x - r * 0.85f, c.y + r * 0.8f, c.x - r * 0.85f, c.y + r * 0.3f)
        cubicTo(c.x - r * 0.85f, c.y - r * 0.05f, c.x - r * 0.35f, c.y - r * 0.45f, c.x, c.y - r)
        close()
    }
    drawPath(path, Brush.verticalGradient(listOf(light, base), startY = c.y - r, endY = c.y + r))
    drawOval(Color.White.copy(alpha = 0.7f), Offset(c.x - r * 0.5f, c.y - r * 0.05f), Size(r * 0.28f, r * 0.42f))
}

/** 몽글한 몸통: 아래가 조금 넓은 둥근 젤리. */
private fun DrawScope.drawBody(c: Offset, r: Float, base: Color, light: Color) {
    val path = Path().apply {
        moveTo(c.x, c.y - r * 0.9f)
        cubicTo(c.x + r * 0.62f, c.y - r * 0.9f, c.x + r, c.y - r * 0.35f, c.x + r, c.y + r * 0.25f)
        cubicTo(c.x + r, c.y + r * 0.78f, c.x + r * 0.6f, c.y + r * 0.92f, c.x, c.y + r * 0.92f)
        cubicTo(c.x - r * 0.6f, c.y + r * 0.92f, c.x - r, c.y + r * 0.78f, c.x - r, c.y + r * 0.25f)
        cubicTo(c.x - r, c.y - r * 0.35f, c.x - r * 0.62f, c.y - r * 0.9f, c.x, c.y - r * 0.9f)
        close()
    }
    drawPath(path, Brush.verticalGradient(listOf(light, base), startY = c.y - r, endY = c.y + r))
    drawOval(Color.White.copy(alpha = 0.65f), Offset(c.x - r * 0.62f, c.y - r * 0.55f), Size(r * 0.3f, r * 0.22f))
}

private fun DrawScope.drawFace(c: Offset, r: Float, smile: Boolean) {
    val eyeY = c.y + r * 0.05f
    val eyeR = r * 0.09f
    drawCircle(Eye, eyeR, Offset(c.x - r * 0.32f, eyeY))
    drawCircle(Eye, eyeR, Offset(c.x + r * 0.32f, eyeY))
    drawCircle(Color.White, eyeR * 0.4f, Offset(c.x - r * 0.29f, eyeY - eyeR * 0.35f))
    drawCircle(Color.White, eyeR * 0.4f, Offset(c.x + r * 0.35f, eyeY - eyeR * 0.35f))
    drawOval(Blush.copy(alpha = 0.55f), Offset(c.x - r * 0.68f, eyeY + r * 0.12f), Size(r * 0.24f, r * 0.13f))
    drawOval(Blush.copy(alpha = 0.55f), Offset(c.x + r * 0.44f, eyeY + r * 0.12f), Size(r * 0.24f, r * 0.13f))
    if (smile) {
        val p = Path().apply {
            moveTo(c.x - r * 0.12f, eyeY + r * 0.2f)
            quadraticTo(c.x, eyeY + r * 0.32f, c.x + r * 0.12f, eyeY + r * 0.2f)
        }
        drawPath(p, Eye, style = Stroke(width = r * 0.06f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.ears(c: Offset, r: Float, color: Color, round: Boolean, spread: Float = 0.55f, size: Float = 0.3f) {
    listOf(-1f, 1f).forEach { s ->
        val ex = c.x + s * r * spread
        val ey = c.y - r * 0.78f
        if (round) {
            drawCircle(color, r * size, Offset(ex, ey))
        } else {
            val p = Path().apply {
                moveTo(ex - r * 0.25f, ey + r * 0.2f)
                lineTo(ex + s * r * 0.05f, ey - r * 0.38f)
                lineTo(ex + r * 0.25f, ey + r * 0.2f)
                close()
            }
            drawPath(p, color)
        }
    }
}

/** 몸통 뒤에 그리는 특징 (귀·지느러미). */
private fun DrawScope.drawBehind(kind: JellyKind, c: Offset, r: Float, base: Color, dark: Color) {
    when (kind) {
        JellyKind.NATURE -> ears(c, r, base, round = true)
        JellyKind.ANIMAL -> listOf(-1f, 1f).forEach { s ->
            drawOval(dark, Offset(c.x + s * r * 0.82f - r * 0.2f, c.y - r * 0.55f), Size(r * 0.4f, r * 0.75f))
        }
        JellyKind.WATER, JellyKind.BOOK, JellyKind.TRAVEL, JellyKind.MAKE -> ears(c, r, dark, round = true, spread = 0.6f, size = 0.18f)
        JellyKind.FOOD -> ears(c, r, base, round = true, spread = 0.58f, size = 0.2f)
        JellyKind.MUSIC -> ears(c, r, base, round = false)
        JellyKind.MOVE -> ears(c, r, base, round = false, spread = 0.42f)
        JellyKind.FRIEND -> listOf(-1f, 1f).forEach { s ->
            drawOval(dark, Offset(c.x + s * r * 0.95f - r * 0.14f, c.y - r * 0.05f), Size(r * 0.28f, r * 0.6f))
        }
        else -> Unit
    }
}

/** 몸통 앞에 그리는 특징 (뿔·가시·주둥이·배·이빨·수염). */
private fun DrawScope.drawFront(kind: JellyKind, c: Offset, r: Float, base: Color, light: Color, dark: Color) {
    when (kind) {
        JellyKind.ART -> {
            val horn = Path().apply {
                moveTo(c.x - r * 0.16f, c.y - r * 0.82f)
                lineTo(c.x, c.y - r * 1.38f)
                lineTo(c.x + r * 0.16f, c.y - r * 0.82f)
                close()
            }
            drawPath(horn, Color(0xFFFFC94D))
            drawCircle(Color(0xFFFF9EC7), r * 0.14f, Offset(c.x - r * 0.42f, c.y - r * 0.72f))
            drawCircle(Color(0xFF8FD3FF), r * 0.12f, Offset(c.x - r * 0.6f, c.y - r * 0.55f))
        }
        JellyKind.SCIENCE -> listOf(-0.42f, 0f, 0.42f).forEach { dx ->
            val top = if (dx == 0f) 1.22f else 1.08f
            val p = Path().apply {
                moveTo(c.x + r * (dx - 0.17f), c.y - r * (0.86f - kotlin.math.abs(dx) * 0.2f))
                lineTo(c.x + r * dx, c.y - r * top)
                lineTo(c.x + r * (dx + 0.17f), c.y - r * (0.86f - kotlin.math.abs(dx) * 0.2f))
                close()
            }
            drawPath(p, dark)
        }
        JellyKind.NATURE -> drawOval(light, Offset(c.x - r * 0.24f, c.y + r * 0.18f), Size(r * 0.48f, r * 0.32f))
        JellyKind.WATER -> {
            drawOval(dark.copy(alpha = 0.35f), Offset(c.x - r * 0.3f, c.y + r * 0.16f), Size(r * 0.6f, r * 0.36f))
            drawCircle(Eye, r * 0.04f, Offset(c.x - r * 0.1f, c.y + r * 0.28f))
            drawCircle(Eye, r * 0.04f, Offset(c.x + r * 0.1f, c.y + r * 0.28f))
            drawCircle(Color(0xFFFFB347), r * 0.16f, Offset(c.x, c.y - r * 0.92f))
        }
        JellyKind.MOVE -> listOf(-0.15f, 0.05f, 0.25f).forEachIndexed { i, dx ->
            drawCircle(dark, r * 0.15f, Offset(c.x + r * dx, c.y - r * (0.9f - i * 0.05f)))
        }
        JellyKind.MUSIC -> listOf(-1f, 1f).forEach { s ->
            repeat(2) { k ->
                drawLine(Eye.copy(alpha = 0.6f), Offset(c.x + s * r * 0.55f, c.y + r * (0.25f + k * 0.1f)), Offset(c.x + s * r * 0.92f, c.y + r * (0.2f + k * 0.16f)), strokeWidth = r * 0.04f)
            }
        }
        JellyKind.BOOK -> drawOval(dark, Offset(c.x - r * 0.15f, c.y + r * 0.15f), Size(r * 0.3f, r * 0.2f))
        JellyKind.FOOD -> listOf(-1f, 1f).forEach { s ->
            drawCircle(lerp(base, Color.White, 0.75f), r * 0.26f, Offset(c.x + s * r * 0.62f, c.y + r * 0.38f))
        }
        JellyKind.FRIEND -> {
            drawOval(Color.White.copy(alpha = 0.9f), Offset(c.x - r * 0.48f, c.y + r * 0.02f), Size(r * 0.96f, r * 0.86f))
            val beak = Path().apply {
                moveTo(c.x - r * 0.11f, c.y + r * 0.2f)
                lineTo(c.x + r * 0.11f, c.y + r * 0.2f)
                lineTo(c.x, c.y + r * 0.34f)
                close()
            }
            drawPath(beak, Color(0xFFFFA733))
        }
        JellyKind.MAKE -> {
            drawRect(Color.White, Offset(c.x - r * 0.12f, c.y + r * 0.3f), Size(r * 0.11f, r * 0.16f))
            drawRect(Color.White, Offset(c.x + r * 0.01f, c.y + r * 0.3f), Size(r * 0.11f, r * 0.16f))
        }
        JellyKind.TRAVEL -> {
            drawOval(light, Offset(c.x - r * 0.28f, c.y + r * 0.14f), Size(r * 0.56f, r * 0.34f))
            listOf(-1f, 1f).forEach { s ->
                drawLine(Eye.copy(alpha = 0.55f), Offset(c.x + s * r * 0.2f, c.y + r * 0.3f), Offset(c.x + s * r * 0.62f, c.y + r * 0.24f), strokeWidth = r * 0.035f)
            }
        }
        JellyKind.ANIMAL -> drawCircle(Eye, r * 0.08f, Offset(c.x, c.y + r * 0.2f))
    }
}
