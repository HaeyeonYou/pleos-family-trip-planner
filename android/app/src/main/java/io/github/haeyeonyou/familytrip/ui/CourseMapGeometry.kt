package io.github.haeyeonyou.familytrip.ui

import io.github.haeyeonyou.familytrip.data.CoursePlace
import io.github.haeyeonyou.familytrip.data.CourseStop
import io.github.haeyeonyou.familytrip.data.LatLng
import kotlin.math.cos
import kotlin.math.hypot

/** 지도 카드 안의 화면 좌표 (px, 왼쪽 위 원점). */
data class MapPoint(val x: Float, val y: Float)

/** 곡선 한 토막: [from]에서 [to]까지, 조절점 [c1]·[c2]인 3차 베지어. */
data class CurveSegment(val from: MapPoint, val c1: MapPoint, val c2: MapPoint, val to: MapPoint)

/** 지도에 꽂을 지점 하나. [number]가 있으면 장소 번호 핀, 없으면 출발 고리. */
data class MapAnchor(val at: LatLng, val number: Int?, val label: String)

/**
 * 가상 코스 지도(#231)의 배치 계산. 화면 그리기와 떼어 단위 테스트한다.
 *
 * 사용자 결정(2026-11-12 추가, #233·#239): Figma H01·H02와 같게. 경로선은 실제 도로 경로처럼 그리지 않고, Figma 디자인처럼
 * 출발지 → 장소들 → 귀가 지점의 **순서만 맞게** 부드러운 곡선으로 잇는다. 핀 위치는 위경도 비율을
 * 대략 따르되 한곳에 몰리지 않게 순서 간격과 섞어 펼친다.
 */
object CourseMapGeometry {

    /** 실제 위경도 비율과 순위(고른 간격)를 섞는 비율. 0이면 실제 비율 그대로, 1이면 고른 간격. */
    private const val RANK_BLEND = 0.5f

    /**
     * 지도에 꽂을 지점을 경로 순서대로 고른다: 출발 지점 → 장소 ①②③ (Figma H01·H02, #239).
     * 귀가 지점과 돌아오는 선은 그리지 않는다. 출발지는 코스 응답에 따로 없어 서버 경로의 첫 점([path] 첫 점, 위치로만 쓴다)을 쓰고,
     * 없으면 ①에서 시작한다. [label]은 장소 이름에 수유실이 있으면 " · 수유실"을 붙인다 (H01 "숲속 카페 · 수유실 2F" 라벨).
     */
    fun anchors(stops: List<CourseStop>, path: List<LatLng>): List<MapAnchor> {
        val visits = stops.filter { it.role != "home" && it.place.hasCoordinates() }
        val origin = path.firstOrNull { it.hasCoordinates() }
        return buildList {
            origin?.let { add(MapAnchor(it, number = null, label = "")) }
            visits.forEachIndexed { i, s -> add(MapAnchor(LatLng(s.place.latitude, s.place.longitude), number = i + 1, label = placeLabel(s.place))) }
        }
    }

    /** 홈 미리보기 라벨: 이름, 수유실이 확인되면 " · 수유실". */
    fun placeLabel(place: CoursePlace): String =
        if (place.amenities["nursing_room"] == "available") "${place.name} · 수유실" else place.name

    /**
     * 지점들을 [width]×[height]에서 사방 여백(px)을 뺀 영역에 배치한다. 북쪽이 위, 동쪽이 오른쪽.
     * 축마다 실제 비율(경도는 cos(위도) 보정)과 순위 간격을 반씩 섞어, 가까이 몰린 장소도 서로 떨어져 보이게 한다.
     * 두 점의 동서·남북 순서는 그대로 유지된다.
     */
    fun place(
        points: List<LatLng>,
        width: Float,
        height: Float,
        padLeft: Float = 0f,
        padTop: Float = 0f,
        padRight: Float = 0f,
        padBottom: Float = 0f,
    ): List<MapPoint> {
        if (points.isEmpty()) return emptyList()
        val boxW = (width - padLeft - padRight).coerceAtLeast(1f)
        val boxH = (height - padTop - padBottom).coerceAtLeast(1f)
        val midLat = (points.minOf { it.latitude } + points.maxOf { it.latitude }) / 2
        val lngFactor = cos(Math.toRadians(midLat))
        val fx = spreadAxis(points.map { it.longitude * lngFactor })
        // 화면 y는 아래로 커지므로 위도를 뒤집는다.
        val fy = spreadAxis(points.map { -it.latitude })
        return points.indices.map { i -> MapPoint(padLeft + fx[i] * boxW, padTop + fy[i] * boxH) }
    }

    /** 한 축의 값을 0..1로: 실제 비율과 순위 간격을 섞는다. 값이 모두 같으면 가운데(0.5). */
    internal fun spreadAxis(values: List<Double>): List<Float> {
        val min = values.min()
        val span = values.max() - min
        if (span < 1e-12) return values.map { 0.5f }
        val sorted = values.sorted()
        val last = (values.size - 1).coerceAtLeast(1)
        return values.map { v ->
            val actual = ((v - min) / span).toFloat()
            // 같은 값은 같은 순위(첫·끝 위치의 평균)를 받아 순서가 뒤집히지 않는다.
            val rank = (sorted.indexOfFirst { it == v } + sorted.indexOfLast { it == v }) / 2f / last
            actual * (1 - RANK_BLEND) + rank * RANK_BLEND
        }
    }

    /**
     * 화면에서 [minGap]보다 가까운 점을 앞의 점에서 밀어내 겹치지 않게 한다 (앞 점이 우선, 제자리).
     * 같은 자리에 겹친 점은 오른쪽 아래로 민다. 몇 번 밀어도 겹치면 그 자리에 둔다.
     */
    fun spread(points: List<MapPoint>, minGap: Float): List<MapPoint> {
        val out = mutableListOf<MapPoint>()
        points.forEach { p ->
            var q = p
            repeat(8) {
                val hit = out.firstOrNull { hypot(it.x - q.x, it.y - q.y) < minGap - 0.01f } ?: return@repeat
                val dx = q.x - hit.x
                val dy = q.y - hit.y
                val d = hypot(dx, dy)
                val (ux, uy) = if (d < 0.01f) 0.7071f to 0.7071f else dx / d to dy / d
                q = MapPoint(hit.x + ux * minGap, hit.y + uy * minGap)
            }
            out += q
        }
        return out
    }

    /**
     * 지점을 순서대로 지나는 부드러운 곡선 (Catmull-Rom → 3차 베지어). 모든 지점을 정확히 지난다.
     * [tension]이 작을수록 직선에 가깝다 (1/6이 표준 Catmull-Rom).
     * 앞뒤 토막과 거의 반대로 꺾이는 토막(예: 출발 → ① 가는 길 중간에 ②가 있어 되돌아오는 경우)은
     * 선이 겹쳐 순서가 안 보이므로, 진행 방향 왼쪽으로 [bow]만큼(토막 길이 비율) 휘게 한다.
     */
    fun smoothCurve(points: List<MapPoint>, tension: Float = 1f / 6f, bow: Float = 0.3f): List<CurveSegment> {
        if (points.size < 2) return emptyList()
        val segments = points.zipWithNext()
        fun reverses(i: Int, j: Int): Boolean {
            if (i !in segments.indices || j !in segments.indices) return false
            val (a0, a1) = segments[i]
            val (b0, b1) = segments[j]
            val ax = a1.x - a0.x; val ay = a1.y - a0.y
            val bx = b1.x - b0.x; val by = b1.y - b0.y
            val la = hypot(ax, ay); val lb = hypot(bx, by)
            if (la < 0.01f || lb < 0.01f) return false
            return (ax * bx + ay * by) / (la * lb) < -0.3f
        }
        return segments.indices.map { k ->
            val i = k
            val j = k + 1
            val p0 = points[(i - 1).coerceAtLeast(0)]
            val p1 = points[i]
            val p2 = points[j]
            val p3 = points[(j + 1).coerceAtMost(points.lastIndex)]
            var c1 = MapPoint(p1.x + (p2.x - p0.x) * tension, p1.y + (p2.y - p0.y) * tension)
            var c2 = MapPoint(p2.x - (p3.x - p1.x) * tension, p2.y - (p3.y - p1.y) * tension)
            if (reverses(k - 1, k) || reverses(k, k + 1)) {
                // 진행 방향의 왼쪽 수직(화면 좌표: (dy, -dx))으로 조절점을 민다. 되돌아오는 토막은 방향이 반대라 반대쪽으로 휘어 겹치지 않는다.
                val dx = p2.x - p1.x
                val dy = p2.y - p1.y
                val ox = dy * bow
                val oy = -dx * bow
                c1 = MapPoint(c1.x + ox, c1.y + oy)
                c2 = MapPoint(c2.x + ox, c2.y + oy)
            }
            CurveSegment(from = p1, c1 = c1, c2 = c2, to = p2)
        }
    }
}

/** 좌표가 없는 장소(0,0)는 그리지 않는다. 옛 저장본·수동 입력 장소가 이렇게 올 수 있다. */
private fun LatLng.hasCoordinates() = !(latitude == 0.0 && longitude == 0.0)
private fun CoursePlace.hasCoordinates() = !(latitude == 0.0 && longitude == 0.0)
