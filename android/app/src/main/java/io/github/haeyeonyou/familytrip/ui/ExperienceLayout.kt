package io.github.haeyeonyou.familytrip.ui

import io.github.haeyeonyou.familytrip.data.SavedCourse
import io.github.haeyeonyou.familytrip.data.Visit
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * 경험 우주의 순수 계산 (화면 그리기와 분리해 단위 테스트한다).
 * 좌표는 Figma HF 04(210:890)의 'Graph · space' 프레임(940x690) 기준이고,
 * 화면에 그릴 때 창 크기에 맞춰 같은 비율로 키우거나 줄인다.
 */

internal const val DESIGN_W = 940f
internal const val DESIGN_H = 690f

/** 분야 키 → 보이는 이름 (Figma는 가운뎃점 앞뒤에 공백을 둔다). */
internal val FIELD_TITLES = linkedMapOf(
    "play" to "쇼핑 · 놀이",
    "exhibition" to "전시 · 과학",
    "experience" to "체험 · 클래스",
    "nature" to "자연",
)

/** 분야 노드 색 (Figma HF 04). */
internal val FIELD_ARGB = mapOf(
    "play" to 0xFF7C9CFFL,
    "exhibition" to 0xFFB58CFFL,
    "experience" to 0xFFFFB86BL,
    "nature" to 0xFF5FE0A8L,
)

/** 안내 배너에서 분야를 부르는 말: (코스 이름, 영역 색 이름) */
private val FIELD_HINT = mapOf(
    "play" to ("놀이" to "파란"),
    "exhibition" to ("전시" to "보라"),
    "experience" to ("체험" to "주황"),
    "nature" to ("공원" to "초록"),
)

/** 육아 편의·상황 태그 (Figma HF 04). 장소와 이어지는 선은 코스에서 알게 된 속성이 있을 때만 그린다. */
internal val TAG_LABELS = listOf("수유실", "비 오는 날", "반려견 동반", "유모차")

private val TAG_ANCHORS = listOf(
    Pt(470f, 120f), Pt(470f, 600f), Pt(95f, 360f), Pt(860f, 360f),
)

private val FIELD_ANCHORS = mapOf(
    "play" to Pt(255f, 225f),
    "exhibition" to Pt(705f, 215f),
    "experience" to Pt(690f, 505f),
    "nature" to Pt(250f, 515f),
)

internal val CENTER = Pt(470f, 345f)
internal const val CENTER_RADIUS = 34f

/** 방문 횟수가 정해진 개수일 때 Figma가 놓은 장소 자리 (분야 중심에서의 dx, dy). 최근 방문 순서대로 채운다. */
private val FIGMA_SLOTS = mapOf(
    "play" to listOf(Pt(-132.6f, 32f), Pt(-31.2f, -103.9f), Pt(118.2f, -20.1f), Pt(6.4f, 105.6f)),
    "exhibition" to listOf(Pt(103.4f, -9f), Pt(-69.2f, 96.3f), Pt(-76.5f, -52.5f)),
    "experience" to listOf(Pt(119.1f, -26.3f), Pt(-112.8f, 7.8f)),
)

internal const val MAX_PLACES_PER_FIELD = 8
// 장소가 범례 칩(위)·안내 문구(아래)·확대 버튼(오른쪽 아래)과 겹치지 않는 범위
private const val PLACE_MARGIN_X = 40f
private const val PLACE_MARGIN_TOP = 58f
private const val PLACE_MARGIN_BOTTOM = 52f

internal class Pt(val x: Float, val y: Float)

/** 분야 노드 하나. [count]는 그 분야에서 다녀온 장소 수(같은 곳을 여러 번 가도 1)이고, 크기가 이에 비례한다. */
internal class FieldNode(val key: String, val count: Int, val last: LocalDate?) {
    val title: String get() = FIELD_TITLES.getValue(key)
}

/** 방문한 장소 노드. 같은 장소를 여러 번 가면 횟수만 늘어난다. */
internal class PlaceNode(
    val name: String,
    val field: String,
    val count: Int,
    val last: LocalDate,
    val latest: Boolean,
    /** 코스에서 알게 된 속성 (TAG_LABELS 중). 모르면 비어 있다. */
    val tags: Set<String> = emptySet(),
)

internal class UniverseData(val fields: List<FieldNode>, val places: List<PlaceNode>) {
    val isEmpty get() = places.isEmpty()
}

internal fun buildUniverse(visits: List<Visit>, tagsOf: Map<String, Set<String>> = emptyMap()): UniverseData {
    val outings = visits.filter { it.role == "outing" && it.theme in EXPERIENCE_FIELDS }.sortedBy { it.arrivedAt }
    val latestId = outings.lastOrNull()?.id
    val fields = EXPERIENCE_FIELDS.keys.map { key ->
        val inField = outings.filter { it.theme == key }
        FieldNode(key, inField.map { it.placeName }.distinct().size, inField.lastOrNull()?.date)
    }
    val places = outings.groupBy { it.theme.orEmpty() to it.placeName }.map { (key, list) ->
        PlaceNode(key.second, key.first, list.size, list.last().date, list.any { it.id == latestId }, tagsOf[key.second].orEmpty())
    }.sortedByDescending { it.last }
    return UniverseData(fields, places)
}

private val INDOOR_WORDS = listOf("실내", "키즈카페", "박물관", "과학관", "미술관", "백화점", "몰", "아쿠아", "도서관", "체험관", "전시", "클래스")
private val PET_WORDS = listOf("반려", "애견", "펫", "동반")

/** 저장한 코스의 장소 속성으로 장소 이름 → 태그를 만든다. 코스에 없는 장소는 태그를 모른다. */
internal fun placeTags(courses: List<SavedCourse>): Map<String, Set<String>> {
    val out = HashMap<String, MutableSet<String>>()
    courses.forEach { c ->
        c.course.stops.forEach { stop ->
            val p = stop.place
            val tags = out.getOrPut(p.name) { linkedSetOf() }
            if (p.amenities["nursing_room"] == "available") tags += "수유실"
            if (p.amenities["stroller"] == "available") tags += "유모차"
            val text = p.name + p.category
            if (INDOOR_WORDS.any { it in text }) tags += "비 오는 날"
            if (PET_WORDS.any { it in text }) tags += "반려견 동반"
        }
    }
    return out
}

internal class Insight(val title: String, val body: String, val action: String?)

/** 기기에 쌓인 방문으로 가장 오래 비어 있는 분야를 찾는다. */
internal fun experienceInsight(visits: List<Visit>, today: LocalDate = LocalDate.now()): Insight {
    val outings = visits.filter { it.role == "outing" && it.theme in EXPERIENCE_FIELDS }
    if (outings.isEmpty()) {
        return Insight("첫 나들이를 기록해 보세요", "나들이 장소에 도착하면 우주에 별이 하나씩 생겨요.", "코스 만들기")
    }
    val gaps = EXPERIENCE_FIELDS.keys.map { key ->
        val last = outings.filter { it.theme == key }.maxOfOrNull { it.date }
        key to last?.let { ChronoUnit.DAYS.between(it, today) / 7 }
    }
    // 가장 오래 비어 있는 분야 (한 번도 안 간 곳이 먼저, 같으면 자연 먼저)
    val worst = gaps.maxWith(compareBy({ it.second ?: Long.MAX_VALUE }, { if (it.first == "nature") 1 else 0 }))
    val key = worst.first
    val label = EXPERIENCE_FIELDS.getValue(key)
    val weeks = worst.second
    val (course, color) = FIELD_HINT.getValue(key)
    val body = "반경 10km 안 $course 코스로 $color 영역을 넓혀 볼까요?"
    val action = "$course 코스 보기"
    return when {
        weeks == null -> Insight("$label 체험이 아직 없어요", body, action)
        weeks >= 2 -> Insight("$label 체험이 ${weeks}주째 없어요", body, action)
        else -> Insight("골고루 다니고 있어요", "최근 2주 안에 모든 분야를 다녀왔어요.", null)
    }
}

// ───────────────────────── 배치 ─────────────────────────

internal class NodePos<T>(val item: T, val x: Float, val y: Float, val r: Float)

internal class TagNode(val label: String, val x: Float, val y: Float)

/** 장소 둘레의 작은 점. */
internal class Satellite(val x: Float, val y: Float, val placeIndex: Int)

/** 태그 ↔ 장소 점선. 인덱스는 UniverseGeometry.tags / places 기준. */
internal class TagLink(val tag: Int, val place: Int)

internal class UniverseGeometry(
    val fields: List<NodePos<FieldNode>>,
    val places: List<NodePos<PlaceNode>>,
    val tags: List<TagNode>,
    val satellites: List<Satellite>,
    val tagLinks: List<TagLink>,
)

/** 분야 노드 반지름: 방문 0회도 18 (점선 원), 한 번 늘 때마다 2. */
internal fun fieldRadius(count: Int): Float = 16f + 2f * min(count, 8).coerceAtLeast(1)

/** 장소 노드 반지름: 여러 번 간 곳은 크게, 한 번 간 곳은 이름에서 정한 7~10 사이. */
internal fun placeRadius(name: String, count: Int): Float =
    if (count >= 2) 9f + min(count, 4) else 7.25f + (stableHash(name) % 12) * 0.25f

internal fun stableHash(s: String): Int = s.fold(7) { h, c -> (h * 31 + c.code) and 0x7fffffff }

internal fun computeGeometry(data: UniverseData): UniverseGeometry {
    val fields = data.fields.map { f ->
        val a = FIELD_ANCHORS.getValue(f.key)
        NodePos(f, a.x, a.y, fieldRadius(f.count))
    }
    val places = ArrayList<NodePos<PlaceNode>>()
    val satellites = ArrayList<Satellite>()
    fields.forEach { field ->
        val mine = data.places.filter { it.field == field.item.key }.take(MAX_PLACES_PER_FIELD)
        val slots = placeSlots(field.item.key, mine.size, field.x, field.y)
        mine.forEachIndexed { i, p ->
            val x = (field.x + slots[i].x).coerceIn(PLACE_MARGIN_X, DESIGN_W - PLACE_MARGIN_X)
            val y = (field.y + slots[i].y).coerceIn(PLACE_MARGIN_TOP, DESIGN_H - PLACE_MARGIN_BOTTOM)
            val index = places.size
            places += NodePos(p, x, y, placeRadius(p.name, p.count))
            val h = stableHash(p.name)
            repeat(2 + (min(p.count, 4) - 2).coerceAtLeast(0)) { k ->
                val angle = ((h % 360) + k * 137f + 40f) * PI.toFloat() / 180f
                val dist = 26f + ((h / 7 + k * 5) % 16)
                satellites += Satellite(
                    (x + cos(angle) * dist).coerceIn(8f, DESIGN_W - 8f),
                    (y + sin(angle) * dist).coerceIn(8f, DESIGN_H - 8f),
                    index,
                )
            }
        }
    }
    val tags = TAG_LABELS.mapIndexed { i, label -> TagNode(label, TAG_ANCHORS[i].x, TAG_ANCHORS[i].y) }
    val links = places.flatMapIndexed { pi, p ->
        TAG_LABELS.mapIndexedNotNull { ti, label -> if (label in p.item.tags) TagLink(ti, pi) else null }
    }
    return UniverseGeometry(fields, places, tags, satellites, links)
}

/**
 * 장소 [n]개를 분야 중심 둘레에 놓는 자리 (분야 중심에서의 dx, dy). 최근 방문이 먼저.
 * Figma가 그려 둔 개수(쇼핑·놀이 4, 전시·과학 3, 체험·클래스 2)는 그 자리 그대로,
 * 그 밖의 개수는 가운데(우리 아이)에서 먼 쪽부터 한 바퀴를 고르게 나눈다.
 */
internal fun placeSlots(fieldKey: String, n: Int, fx: Float, fy: Float): List<Pt> {
    FIGMA_SLOTS[fieldKey]?.takeIf { it.size == n }?.let { return it }
    val away = atan2(fy - CENTER.y, fx - CENTER.x)
    return List(n) { i ->
        val angle = away + 2f * PI.toFloat() * i / n.coerceAtLeast(1) + if (n == 1) 0f else PI.toFloat() / n
        val dist = 108f + (i % 2) * 20f
        Pt(cos(angle) * dist, sin(angle) * dist)
    }
}

// ───────────────────────── 빛 번짐 ─────────────────────────

/** Figma 그림자 효과: 같은 색, 번짐 σ, 투명도 0.75. */
internal const val GLOW_ALPHA = 0.75f

internal fun fieldGlowSigma(r: Float) = r * 0.625f
internal fun placeGlowSigma(r: Float) = r * 1.1f
internal const val CENTER_GLOW_SIGMA = 30f
internal const val TAG_GLOW_SIGMA = 5f

/**
 * 반지름 [r]인 원을 σ=[sigma]로 가우시안 번지게 했을 때, 중심에서 d = k/(samples-1) · (r + 3σ) 만큼 떨어진 곳의 밝기(0~1).
 * 원 안쪽도 그대로 계산해 작은 원이 σ보다 작을 때의 흐려짐까지 같게 한다.
 */
internal fun glowProfile(r: Float, sigma: Float, samples: Int = 16): FloatArray {
    val reach = r + 3f * sigma
    return FloatArray(samples) { k -> blurredDisk(r, sigma, reach * k / (samples - 1)) }
}

internal fun glowReach(r: Float, sigma: Float) = r + 3f * sigma

/** ∫₀ʳ (ρ/σ²)·exp(-(ρ²+d²)/2σ²)·I₀(ρd/σ²) dρ (심프슨 48구간). */
internal fun blurredDisk(r: Float, sigma: Float, d: Float): Float {
    val n = 48
    val h = r / n
    fun f(rho: Float): Double {
        val s2 = (sigma * sigma).toDouble()
        val x = rho * d / s2
        // exp(-(ρ²+d²)/2σ²)·I0(x) = exp(-(ρ-d)²/2σ²)·i0e(x)
        return rho / s2 * exp(-((rho - d) * (rho - d)) / (2 * s2)) * i0e(x)
    }
    var sum = f(0f) + f(r)
    for (i in 1 until n) sum += f(i * h) * if (i % 2 == 1) 4 else 2
    return (sum * h / 3).toFloat().coerceIn(0f, 1f)
}

/** exp(-x)·I₀(x) (Abramowitz–Stegun 9.8.1·9.8.2 근사). */
private fun i0e(x: Double): Double {
    val ax = abs(x)
    return if (ax < 3.75) {
        val t = (ax / 3.75) * (ax / 3.75)
        val i0 = 1.0 + t * (3.5156229 + t * (3.0899424 + t * (1.2067492 + t * (0.2659732 + t * (0.0360768 + t * 0.0045813)))))
        i0 * exp(-ax)
    } else {
        val t = 3.75 / ax
        (0.39894228 + t * (0.01328592 + t * (0.00225319 + t * (-0.00157565 + t * (0.00916281 + t * (-0.02057706 + t * (0.02635537 + t * (-0.01647633 + t * 0.00392377)))))))) / sqrt(ax)
    }
}

// ───────────────────────── 별 ─────────────────────────

internal class Star(val x: Float, val y: Float, val r: Float, val alpha: Float)

/** Figma 'Stars + edges'의 별 140개 (x,y,반지름,투명도). */
internal val STARS: List<Star> by lazy {
    STAR_TABLE.split(';').map { s ->
        val v = s.split(',')
        Star(v[0].toFloat(), v[1].toFloat(), v[2].toFloat(), v[3].toFloat())
    }
}

// ───────────────────────── 창 맞춤 ─────────────────────────

/** 그래프 상자(너비 [w], 높이 [h])에 940x690 설계를 비율 그대로 맞춰 넣는 배율과 왼쪽 위 여백. */
internal class Fit(val unit: Float, val left: Float, val top: Float)

internal fun fitDesign(w: Float, h: Float): Fit {
    if (w <= 0f || h <= 0f) return Fit(1f, 0f, 0f)
    val unit = min(w / DESIGN_W, h / DESIGN_H)
    return Fit(unit, (w - DESIGN_W * unit) / 2f, (h - DESIGN_H * unit) / 2f)
}

/** 화면 위치 → 가장 가까운 장소 번호. 눌린 곳이 노드 반경(또는 [minHit])보다 멀면 null. */
internal fun hitPlace(places: List<NodePos<PlaceNode>>, tapX: Float, tapY: Float, toScreen: (Float, Float) -> Pt, unit: Float, minHit: Float): Int? {
    var best: Int? = null
    var bestD = Float.MAX_VALUE
    places.forEachIndexed { i, p ->
        val s = toScreen(p.x, p.y)
        val d = hypot(tapX - s.x, tapY - s.y)
        if (d < bestD && d <= maxOf(p.r * unit, minHit)) {
            best = i
            bestD = d
        }
    }
    return best
}

private const val STAR_TABLE = "0.1,635.3,0.70,0.26;685.6,367.6,0.76,0.53;709.8,374.6,1.26,0.47;767.8,167.0,0.82,0.5;656.2,37.2,1.26,0.38;867.1,557.1,0.47,0.41;867.8,475.1,1.20,0.44;857.3,626.8,0.82,0.32;786.9,229.3,0.51,0.3;402.8,203.5,1.61,0.43;685.5,605.7,0.38,0.29;527.0,351.1,0.89,0.25;854.4,37.9,0.81,0.32;624.6,637.8,1.02,0.23;381.3,195.7,0.81,0.36;312.6,366.8,1.16,0.27;852.1,315.9,1.51,0.47;596.3,356.2,1.01,0.49;883.2,273.7,1.29,0.28;551.6,241.9,0.76,0.35;747.0,605.5,0.46,0.56;73.1,280.7,0.52,0.19;936.4,151.8,1.19,0.22;429.7,409.1,0.49,0.61;831.5,614.5,1.54,0.6;715.5,190.0,0.31,0.27;71.5,1.1,1.16,0.27;696.7,337.7,1.26,0.27;829.2,162.3,0.82,0.6;111.3,337.6,0.30,0.61;598.7,636.3,0.33,0.6;176.8,668.4,1.09,0.49;250.3,153.7,0.59,0.43;49.2,530.9,1.28,0.51;468.3,22.6,0.45,0.61;122.9,474.6,1.55,0.17;830.1,594.3,1.69,0.63;719.0,289.5,0.65,0.51;829.3,274.8,0.63,0.35;833.8,385.6,0.79,0.46;690.8,231.2,1.32,0.36;186.7,215.1,1.68,0.65;594.5,601.8,1.59,0.25;606.5,93.4,1.00,0.43;725.1,652.6,0.97,0.24;419.9,114.6,1.23,0.41;527.8,349.6,1.36,0.33;705.8,18.0,0.72,0.42;190.6,498.8,0.71,0.27;620.0,296.1,0.41,0.54;773.5,631.7,1.57,0.49;901.4,17.4,1.37,0.45;680.6,619.8,0.35,0.22;75.5,74.7,0.42,0.15;568.7,652.6,0.57,0.22;190.1,465.7,0.64,0.38;934.3,291.6,1.24,0.54;373.1,287.3,0.39,0.27;759.2,672.3,0.51,0.61;478.2,84.3,0.59,0.45;160.3,53.9,0.68,0.47;522.7,331.6,1.69,0.19;388.6,217.0,0.31,0.15;388.7,63.0,0.63,0.61;730.8,527.1,0.91,0.58;904.7,552.7,1.23,0.44;327.2,280.8,1.00,0.4;533.4,498.0,1.23,0.47;685.2,610.3,1.48,0.64;411.8,336.1,1.64,0.64;336.3,54.7,0.78,0.59;665.9,509.3,1.64,0.54;194.6,164.3,1.42,0.2;860.6,552.1,1.19,0.51;503.7,305.7,1.52,0.43;821.3,186.7,1.26,0.37;371.4,129.4,1.12,0.27;844.9,567.2,0.77,0.49;824.7,80.0,0.73,0.61;1.2,91.0,0.86,0.16;651.3,619.5,0.54,0.26;890.5,482.9,0.55,0.21;589.0,11.0,1.44,0.45;706.1,88.4,0.76,0.51;897.7,378.0,1.12,0.54;660.3,506.2,1.14,0.2;42.7,423.2,0.61,0.44;235.9,153.9,1.17,0.46;764.1,165.2,1.57,0.18;649.2,280.6,1.41,0.48;756.4,72.2,1.59,0.4;773.6,293.2,1.51,0.55;683.5,626.4,1.50,0.25;676.5,106.7,0.48,0.62;897.5,39.0,0.41,0.28;884.3,600.1,1.64,0.29;186.4,20.5,0.96,0.43;133.1,222.9,0.97,0.26;51.7,30.6,0.43,0.46;439.9,557.6,1.32,0.41;658.4,267.7,1.16,0.63;724.4,663.8,1.30,0.45;754.5,118.1,0.39,0.51;607.9,297.5,1.06,0.34;464.8,84.1,0.39,0.19;333.0,237.8,0.58,0.47;117.9,36.0,0.30,0.57;817.3,30.7,1.38,0.31;378.2,626.6,1.42,0.16;933.7,141.8,1.20,0.36;31.2,119.2,1.36,0.53;234.2,591.7,1.44,0.31;431.7,206.2,0.65,0.59;817.8,468.0,1.00,0.58;770.6,561.0,1.36,0.64;649.9,196.9,1.30,0.31;812.2,112.4,1.48,0.48;767.0,322.2,1.41,0.56;40.0,160.0,0.57,0.16;542.9,560.2,1.18,0.58;813.5,561.3,0.59,0.43;432.1,266.4,1.05,0.53;486.5,264.8,1.00,0.27;661.1,532.4,0.72,0.28;577.2,380.7,1.61,0.43;297.3,414.2,1.10,0.47;536.8,111.4,0.59,0.21;313.5,437.4,1.52,0.47;365.9,239.7,0.86,0.21;145.5,36.6,0.97,0.34;85.9,437.6,0.87,0.38;93.6,579.8,0.68,0.3;114.2,140.0,0.91,0.25;571.2,398.9,1.36,0.54;462.8,598.2,1.26,0.21;586.5,508.0,1.55,0.44;909.2,200.5,1.69,0.17;97.0,212.3,1.50,0.6;680.0,377.2,0.63,0.42;729.9,128.0,1.44,0.49"
