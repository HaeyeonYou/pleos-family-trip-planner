package io.github.haeyeonyou.familytrip.ui

import io.github.haeyeonyou.familytrip.grow.Experience
import io.github.haeyeonyou.familytrip.grow.JellyKind
import io.github.haeyeonyou.familytrip.grow.JellyStage
import io.github.haeyeonyou.familytrip.grow.JellyState
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * 그로우 G01 경험 우주의 배치 계산 (#164, 화면 그리기와 분리해 단위 테스트한다).
 * 좌표는 G01(354:3033) 경험 우주 카드 안 그래프 영역(752x540) 기준이고, 그릴 때 같은 비율로 맞춘다.
 * 12분류는 가운데 "우리 아이"를 둘러싼 타원 위 시계 자리(12시부터 30도씩)에 고정한다.
 * 다녀온 분류는 성장 단계 크기의 젤리, 안 간 분류는 흐린 빈 별, 추천 분류 하나는 점선 원이다.
 */

internal const val DESIGN_W = 752f
internal const val DESIGN_H = 540f
internal val CENTER = Pt(376f, 262f)
internal const val CENTER_RADIUS = 34f
internal const val RING_RX = 280f
internal const val RING_RY = 190f

/** 분류마다 보이는 최근 장소 수 (12분류가 다 차도 겹치지 않게). */
internal const val MAX_PLACES_PER_KIND = 2

private const val MARGIN_X = 28f
private const val MARGIN_TOP = 18f
private const val MARGIN_BOTTOM = 30f

internal class Pt(val x: Float, val y: Float)

/** 다녀온 장소 하나 (같은 곳 여러 번은 횟수만 는다). */
internal class GrowPlace(
    val name: String,
    val kind: JellyKind,
    val count: Int,
    val last: LocalDate,
    val latest: Boolean,
    val months: Set<YearMonth>,
    val latitude: Double?,
    val longitude: Double?,
)

internal fun growPlaces(list: List<Experience>): List<GrowPlace> {
    val latestId = list.maxByOrNull { it.visit.arrivedAt }?.visit?.id
    return list.groupBy { it.visit.placeName }.map { (name, xs) ->
        val last = xs.maxBy { it.visit.arrivedAt }
        val located = xs.lastOrNull { it.visit.latitude != null && it.visit.longitude != null }?.visit
        GrowPlace(
            name = name,
            kind = last.kind,
            count = xs.size,
            last = last.date,
            latest = xs.any { it.visit.id == latestId },
            months = xs.map { YearMonth.from(it.date) }.toSet(),
            latitude = located?.latitude,
            longitude = located?.longitude,
        )
    }.sortedByDescending { it.last }
}

/** 분류의 시계 자리 (0 = 12시, 시계 방향 30도씩). */
internal fun kindAngle(kind: JellyKind): Float = (-90f + kind.ordinal * 30f) * PI.toFloat() / 180f

internal fun kindAnchor(kind: JellyKind): Pt {
    val a = kindAngle(kind)
    return Pt(CENTER.x + cos(a) * RING_RX, CENTER.y + sin(a) * RING_RY)
}

/** 젤리 반지름: 빈 별 5, 방울 14, 몽글 21, 탄생 28. */
internal fun jellyRadius(stage: JellyStage): Float = when (stage) {
    JellyStage.NONE -> 5f
    JellyStage.DROP -> 14f
    JellyStage.PUFF -> 21f
    JellyStage.BORN -> 28f
}

/** 장소 점 반지름: 한 번 7, 여러 번 갈수록 커져 최대 11. */
internal fun placeRadius(count: Int): Float = 6f + min(count, 5).toFloat()

internal class KindNode(val state: JellyState, val x: Float, val y: Float, val r: Float, val suggested: Boolean)

internal class PlaceDot(val place: GrowPlace, val x: Float, val y: Float, val r: Float, val kindIndex: Int)

internal class JellyGeometry(val kinds: List<KindNode>, val places: List<PlaceDot>)

internal fun computeJellyGeometry(states: List<JellyState>, places: List<GrowPlace>, suggested: JellyKind?): JellyGeometry {
    val kinds = states.map { st ->
        val a = kindAnchor(st.kind)
        val r = if (st.kind == suggested && st.count == 0) 22f else jellyRadius(st.stage)
        KindNode(st, a.x, a.y, r, st.kind == suggested)
    }
    val dots = ArrayList<PlaceDot>()
    kinds.forEachIndexed { index, node ->
        val mine = places.filter { it.kind == node.state.kind }.take(MAX_PLACES_PER_KIND)
        val dx = node.x - CENTER.x
        val dy = node.y - CENTER.y
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        mine.forEachIndexed { i, p ->
            val spread = if (mine.size == 1) 0f else if (i == 0) -0.5f else 0.5f
            val ux = dx / len
            val uy = dy / len
            val rx = ux * cos(spread) - uy * sin(spread)
            val ry = ux * sin(spread) + uy * cos(spread)
            val dist = node.r + 34f
            dots += PlaceDot(
                place = p,
                x = (node.x + rx * dist).coerceIn(MARGIN_X, DESIGN_W - MARGIN_X),
                y = (node.y + ry * dist).coerceIn(MARGIN_TOP, DESIGN_H - MARGIN_BOTTOM),
                r = placeRadius(p.count),
                kindIndex = index,
            )
        }
    }
    return JellyGeometry(kinds, dots)
}

// ───────────────────────── 창 맞춤 · 누르기 ─────────────────────────

/** 그래프 상자(너비 [w], 높이 [h])에 설계를 비율 그대로 맞춰 넣는 배율과 왼쪽 위 여백. */
internal class Fit(val unit: Float, val left: Float, val top: Float)

internal fun fitDesign(w: Float, h: Float): Fit {
    if (w <= 0f || h <= 0f) return Fit(1f, 0f, 0f)
    val unit = min(w / DESIGN_W, h / DESIGN_H)
    return Fit(unit, (w - DESIGN_W * unit) / 2f, (h - DESIGN_H * unit) / 2f)
}

/** 누른 것: 장소, 분류, 또는 없음. */
internal sealed interface UniverseHit {
    data class Place(val index: Int) : UniverseHit
    data class Kind(val kind: JellyKind) : UniverseHit
}

/**
 * 설계 좌표 ([x], [y])에서 가장 가까운 노드. 장소를 먼저 본다. [minHit]은 설계 단위 최소 반경(손가락 크기).
 */
internal fun hitUniverse(g: JellyGeometry, x: Float, y: Float, minHit: Float): UniverseHit? {
    var best: UniverseHit? = null
    var bestD = Float.MAX_VALUE
    g.places.forEachIndexed { i, p ->
        val d = hypot(x - p.x, y - p.y)
        if (d < bestD && d <= maxOf(p.r, minHit)) {
            best = UniverseHit.Place(i)
            bestD = d
        }
    }
    if (best != null) return best
    g.kinds.forEach { k ->
        val d = hypot(x - k.x, y - k.y)
        if (d < bestD && d <= maxOf(k.r, minHit)) {
            best = UniverseHit.Kind(k.state.kind)
            bestD = d
        }
    }
    return best
}

// ───────────────────────── 별 ─────────────────────────

internal class Star(val x: Float, val y: Float, val r: Float, val alpha: Float)

/** 배경 별 140개 (옛 HF04 940x690 표를 그래프 영역 비율로 줄여 쓴다). */
internal val STARS: List<Star> by lazy {
    STAR_TABLE.split(';').map { s ->
        val v = s.split(',')
        Star(v[0].toFloat() * DESIGN_W / 940f, v[1].toFloat() * DESIGN_H / 690f, v[2].toFloat(), v[3].toFloat())
    }
}

private const val STAR_TABLE = "0.1,635.3,0.70,0.26;685.6,367.6,0.76,0.53;709.8,374.6,1.26,0.47;767.8,167.0,0.82,0.5;656.2,37.2,1.26,0.38;867.1,557.1,0.47,0.41;867.8,475.1,1.20,0.44;857.3,626.8,0.82,0.32;786.9,229.3,0.51,0.3;402.8,203.5,1.61,0.43;685.5,605.7,0.38,0.29;527.0,351.1,0.89,0.25;854.4,37.9,0.81,0.32;624.6,637.8,1.02,0.23;381.3,195.7,0.81,0.36;312.6,366.8,1.16,0.27;852.1,315.9,1.51,0.47;596.3,356.2,1.01,0.49;883.2,273.7,1.29,0.28;551.6,241.9,0.76,0.35;747.0,605.5,0.46,0.56;73.1,280.7,0.52,0.19;936.4,151.8,1.19,0.22;429.7,409.1,0.49,0.61;831.5,614.5,1.54,0.6;715.5,190.0,0.31,0.27;71.5,1.1,1.16,0.27;696.7,337.7,1.26,0.27;829.2,162.3,0.82,0.6;111.3,337.6,0.30,0.61;598.7,636.3,0.33,0.6;176.8,668.4,1.09,0.49;250.3,153.7,0.59,0.43;49.2,530.9,1.28,0.51;468.3,22.6,0.45,0.61;122.9,474.6,1.55,0.17;830.1,594.3,1.69,0.63;719.0,289.5,0.65,0.51;829.3,274.8,0.63,0.35;833.8,385.6,0.79,0.46;690.8,231.2,1.32,0.36;186.7,215.1,1.68,0.65;594.5,601.8,1.59,0.25;606.5,93.4,1.00,0.43;725.1,652.6,0.97,0.24;419.9,114.6,1.23,0.41;527.8,349.6,1.36,0.33;705.8,18.0,0.72,0.42;190.6,498.8,0.71,0.27;620.0,296.1,0.41,0.54;773.5,631.7,1.57,0.49;901.4,17.4,1.37,0.45;680.6,619.8,0.35,0.22;75.5,74.7,0.42,0.15;568.7,652.6,0.57,0.22;190.1,465.7,0.64,0.38;934.3,291.6,1.24,0.54;373.1,287.3,0.39,0.27;759.2,672.3,0.51,0.61;478.2,84.3,0.59,0.45;160.3,53.9,0.68,0.47;522.7,331.6,1.69,0.19;388.6,217.0,0.31,0.15;388.7,63.0,0.63,0.61;730.8,527.1,0.91,0.58;904.7,552.7,1.23,0.44;327.2,280.8,1.00,0.4;533.4,498.0,1.23,0.47;685.2,610.3,1.48,0.64;411.8,336.1,1.64,0.64;336.3,54.7,0.78,0.59;665.9,509.3,1.64,0.54;194.6,164.3,1.42,0.2;860.6,552.1,1.19,0.51;503.7,305.7,1.52,0.43;821.3,186.7,1.26,0.37;371.4,129.4,1.12,0.27;844.9,567.2,0.77,0.49;824.7,80.0,0.73,0.61;1.2,91.0,0.86,0.16;651.3,619.5,0.54,0.26;890.5,482.9,0.55,0.21;589.0,11.0,1.44,0.45;706.1,88.4,0.76,0.51;897.7,378.0,1.12,0.54;660.3,506.2,1.14,0.2;42.7,423.2,0.61,0.44;235.9,153.9,1.17,0.46;764.1,165.2,1.57,0.18;649.2,280.6,1.41,0.48;756.4,72.2,1.59,0.4;773.6,293.2,1.51,0.55;683.5,626.4,1.50,0.25;676.5,106.7,0.48,0.62;897.5,39.0,0.41,0.28;884.3,600.1,1.64,0.29;186.4,20.5,0.96,0.43;133.1,222.9,0.97,0.26;51.7,30.6,0.43,0.46;439.9,557.6,1.32,0.41;658.4,267.7,1.16,0.63;724.4,663.8,1.30,0.45;754.5,118.1,0.39,0.51;607.9,297.5,1.06,0.34;464.8,84.1,0.39,0.19;333.0,237.8,0.58,0.47;117.9,36.0,0.30,0.57;817.3,30.7,1.38,0.31;378.2,626.6,1.42,0.16;933.7,141.8,1.20,0.36;31.2,119.2,1.36,0.53;234.2,591.7,1.44,0.31;431.7,206.2,0.65,0.59;817.8,468.0,1.00,0.58;770.6,561.0,1.36,0.64;649.9,196.9,1.30,0.31;812.2,112.4,1.48,0.48;767.0,322.2,1.41,0.56;40.0,160.0,0.57,0.16;542.9,560.2,1.18,0.58;813.5,561.3,0.59,0.43;432.1,266.4,1.05,0.53;486.5,264.8,1.00,0.27;661.1,532.4,0.72,0.28;577.2,380.7,1.61,0.43;297.3,414.2,1.10,0.47;536.8,111.4,0.59,0.21;313.5,437.4,1.52,0.47;365.9,239.7,0.86,0.21;145.5,36.6,0.97,0.34;85.9,437.6,0.87,0.38;93.6,579.8,0.68,0.3;114.2,140.0,0.91,0.25;571.2,398.9,1.36,0.54;462.8,598.2,1.26,0.21;586.5,508.0,1.55,0.44;909.2,200.5,1.69,0.17;97.0,212.3,1.50,0.6;680.0,377.2,0.63,0.42;729.9,128.0,1.44,0.49"
