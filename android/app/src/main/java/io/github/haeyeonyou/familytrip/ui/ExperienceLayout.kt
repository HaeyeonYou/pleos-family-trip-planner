package io.github.haeyeonyou.familytrip.ui

import io.github.haeyeonyou.familytrip.grow.JellyKind
import io.github.haeyeonyou.familytrip.grow.JellyStage
import io.github.haeyeonyou.familytrip.grow.JellyState
import io.github.haeyeonyou.familytrip.grow.SUGGEST_KIND_ORDER
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/*
 * 그로우 G01 "경험 젤리"(354:3033, #216)의 배치 계산 (화면 그리기와 분리해 단위 테스트한다).
 * 좌표는 G01 경험 젤리 카드 안 그림 영역(800x560) 기준이고, 그릴 때 같은 비율로 맞춘다.
 * 가장 많이 간 분야를 가운데 크게, 나머지 다녀온 분야를 점선 타원 궤도 위에 많이 간 순으로 놓는다.
 * 아이 원·분야 선·장소 점은 없다(Figma 최신안). 빈 분야는 점선 원 "과학 ?"으로 최대 [MAX_EMPTY]개 (QF2 제안).
 */

internal const val DESIGN_W = 800f
internal const val DESIGN_H = 560f
internal val CENTER = Pt(400f, 262f)

/** 안쪽 궤도 (가운데 젤리를 두르는 장식). */
internal const val INNER_RX = 200f
internal const val INNER_RY = 135f

/** 바깥 궤도 (몽글·캐릭터·빈 분야가 놓인다). 방울은 조금 안쪽 [DROP_RING]배 자리. */
internal const val OUTER_RX = 330f
internal const val OUTER_RY = 220f
internal const val DROP_RING = 0.84f

/** 빈 분야 점선 원 최대 개수 (QF2 제안: 그림처럼 4개). */
internal const val MAX_EMPTY = 4

/** 젤리 그림 프레임 한 변 (그림이 단계마다 프레임 안 크기가 달라 같은 프레임이면 방울은 작고 캐릭터는 꽉 찬다). */
internal const val CENTER_FRAME = 250f
internal const val ORBIT_FRAME = 124f
internal const val EMPTY_RADIUS = 22f

/** 첫 자리 각도 (왼쪽 위, Figma에서 두 번째로 많이 간 분야 자리). 0 = 3시, 시계 방향. */
private const val START_DEG = -150f

internal class Pt(val x: Float, val y: Float)

/** 궤도 위 한 분야. [frame]은 젤리 그림 프레임(빈 분야는 0), [hitR]은 누르는 반경. */
internal class OrbitNode(
    val state: JellyState,
    val x: Float,
    val y: Float,
    val frame: Float,
    val hitR: Float,
    val center: Boolean,
) {
    val kind: JellyKind get() = state.kind
    val empty: Boolean get() = state.count == 0

    /** 이름 글자의 윗변 y. */
    val labelY: Float
        get() = when {
            empty -> y + EMPTY_RADIUS + 6f
            center -> y + frame * 0.47f
            state.stage == JellyStage.DROP -> y + frame * 0.36f
            else -> y + frame * 0.45f
        }
}

internal class JellyOrbit(val nodes: List<OrbitNode>) {
    val center: OrbitNode? get() = nodes.firstOrNull { it.center }
}

/** 많이 간 순 (같으면 최근에 간 쪽, 그다음 분류 순서). */
internal fun rankByCount(states: List<JellyState>): List<JellyState> =
    states.filter { it.count > 0 }.sortedWith(
        compareByDescending<JellyState> { it.count }.thenByDescending { it.last }.thenBy { it.kind.ordinal },
    )

/** 그릴 빈 분야: 권하는 순서로 [MAX_EMPTY]개. */
internal fun emptyKinds(states: List<JellyState>): List<JellyState> {
    val byKind = states.associateBy { it.kind }
    return SUGGEST_KIND_ORDER.mapNotNull { byKind[it] }.filter { it.count == 0 }.take(MAX_EMPTY)
}

/**
 * 궤도 순서: 다녀온 분야를 많이 간 순으로 두고, 빈 분야를 그 사이에 고르게 끼운다
 * (그림처럼 빈 원이 한쪽에 몰리지 않게).
 */
internal fun orbitOrder(filled: List<JellyState>, empty: List<JellyState>): List<JellyState> {
    val n = filled.size + empty.size
    if (empty.isEmpty()) return filled
    val emptySlots = empty.indices.map { i -> (((i + 0.5f) * n) / empty.size).toInt().coerceIn(0, n - 1) }.toMutableList()
    // 같은 자리가 겹치면 다음 칸으로
    for (i in 1 until emptySlots.size) if (emptySlots[i] <= emptySlots[i - 1]) emptySlots[i] = emptySlots[i - 1] + 1
    val out = ArrayList<JellyState>(n)
    var f = 0
    var e = 0
    for (slot in 0 until n) {
        if (e < empty.size && emptySlots[e] == slot) out += empty[e++] else if (f < filled.size) out += filled[f++] else out += empty[e++]
    }
    return out
}

internal fun computeJellyOrbit(states: List<JellyState>): JellyOrbit {
    val ranked = rankByCount(states)
    val empty = emptyKinds(states)
    val nodes = ArrayList<OrbitNode>()
    val first = ranked.firstOrNull()
    if (first != null) nodes += OrbitNode(first, CENTER.x, CENTER.y, CENTER_FRAME, 90f, center = true)
    val around = orbitOrder(ranked.drop(1), empty)
    val n = around.size
    around.forEachIndexed { i, st ->
        val a = (START_DEG + i * 360f / n.coerceAtLeast(1)) * PI.toFloat() / 180f
        val k = if (st.stage == JellyStage.DROP) DROP_RING else 1f
        val x = CENTER.x + cos(a) * OUTER_RX * k
        val y = CENTER.y + sin(a) * OUTER_RY * k
        if (st.count == 0) {
            nodes += OrbitNode(st, x, y, 0f, EMPTY_RADIUS + 8f, center = false)
        } else {
            val hit = if (st.stage == JellyStage.DROP) 30f else 46f
            nodes += OrbitNode(st, x, y, ORBIT_FRAME, hit, center = false)
        }
    }
    return JellyOrbit(nodes)
}

/** 이름 아래 글자: "전시·미술 12" / 빈 분야 "과학 ?" */
internal fun orbitLabel(state: JellyState): String = if (state.count == 0) "${state.kind.chip} ?" else "${state.kind.label} ${state.count}"

// ───────────────────────── 창 맞춤 · 누르기 ─────────────────────────

/** 그림 상자(너비 [w], 높이 [h])에 설계를 비율 그대로 맞춰 넣는 배율과 왼쪽 위 여백. */
internal class Fit(val unit: Float, val left: Float, val top: Float)

internal fun fitDesign(w: Float, h: Float): Fit {
    if (w <= 0f || h <= 0f) return Fit(1f, 0f, 0f)
    val unit = min(w / DESIGN_W, h / DESIGN_H)
    return Fit(unit, (w - DESIGN_W * unit) / 2f, (h - DESIGN_H * unit) / 2f)
}

/** 설계 좌표 ([x], [y])에서 누른 분야. 가장 가까운 노드 중 반경 안에 든 것. [minHit]은 손가락 크기(설계 단위). */
internal fun hitOrbit(o: JellyOrbit, x: Float, y: Float, minHit: Float): JellyKind? =
    o.nodes.map { it to hypot(x - it.x, y - it.y) }
        .filter { (n, d) -> d <= maxOf(n.hitR, minHit) }
        .minByOrNull { it.second }?.first?.kind
