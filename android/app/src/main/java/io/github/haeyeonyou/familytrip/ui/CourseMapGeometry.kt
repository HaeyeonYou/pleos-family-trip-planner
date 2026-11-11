package io.github.haeyeonyou.familytrip.ui

import io.github.haeyeonyou.familytrip.data.CourseStop
import io.github.haeyeonyou.familytrip.data.LatLng
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min

/** 지도 카드 안의 화면 좌표 (px, 왼쪽 위 원점). */
data class MapPoint(val x: Float, val y: Float)

/** 지도에 그릴 내용: 경로선 꼭짓점, 번호 핀을 꽂을 장소, 출발지·귀가 지점. */
data class CourseMapShape(
    val line: List<LatLng>,
    val visits: List<CourseStop>,
    val origin: LatLng?,
    val home: LatLng?,
)

/**
 * 가상 코스 지도(#231)의 좌표 계산. 화면 그리기와 떼어 단위 테스트한다.
 * 실제 지도 타일 없이 위경도를 카드 크기에 맞춰 비율 그대로(찌그러뜨리지 않고) 옮긴다.
 */
object CourseMapGeometry {

    /**
     * 경로선은 서버가 준 실제 도로 경로 [path]를 쓴다. 두 점이 안 되면 출발지(있으면)→장소 순서→귀가 지점을 직선으로 잇는다.
     * 출발지는 실제 경로의 첫 점이다 (코스 응답에는 출발지가 따로 없다). path가 없으면 알 수 없어 표시하지 않는다.
     */
    fun shape(stops: List<CourseStop>, path: List<LatLng>): CourseMapShape {
        val visits = stops.filter { it.role != "home" && it.place.hasCoordinates() }
        val home = stops.lastOrNull { it.role == "home" && it.place.hasCoordinates() }?.let { LatLng(it.place.latitude, it.place.longitude) }
        val road = path.filter { it.hasCoordinates() }
        if (road.size >= 2) return CourseMapShape(road, visits, origin = road.first(), home = home)
        val straight = visits.map { LatLng(it.place.latitude, it.place.longitude) } + listOfNotNull(home)
        return CourseMapShape(straight, visits, origin = null, home = home)
    }

    /**
     * [points]가 모두 들어가도록 [width]×[height] 안, 사방 여백(px)을 뺀 영역에 맞추는 변환을 만든다.
     * 경도는 위도에 따라 실제 거리가 줄어드므로 cos(중심 위도)를 곱해 가로·세로 축척을 같게 한다(북쪽이 위).
     */
    fun fit(
        points: List<LatLng>,
        width: Float,
        height: Float,
        padLeft: Float = 0f,
        padTop: Float = 0f,
        padRight: Float = 0f,
        padBottom: Float = 0f,
    ): MapProjection {
        val boxW = (width - padLeft - padRight).coerceAtLeast(1f)
        val boxH = (height - padTop - padBottom).coerceAtLeast(1f)
        if (points.isEmpty()) return MapProjection(0.0, 0.0, 1.0, 0.0, padLeft + boxW / 2, padTop + boxH / 2)
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLng = points.minOf { it.longitude }
        val maxLng = points.maxOf { it.longitude }
        val lngFactor = cos(Math.toRadians((minLat + maxLat) / 2)).coerceAtLeast(0.01)
        val spanX = (maxLng - minLng) * lngFactor
        val spanY = maxLat - minLat
        // 점이 하나뿐이거나 한 줄로만 늘어선 경우 0으로 나누지 않게 남은 축은 무시한다.
        val scale = when {
            spanX < EPS && spanY < EPS -> 1.0
            spanX < EPS -> boxH / spanY
            spanY < EPS -> boxW / spanX
            else -> min(boxW / spanX, boxH / spanY)
        }
        // 맞춘 그림을 여백 안 영역 한가운데에 둔다.
        val centerX = padLeft + boxW / 2
        val centerY = padTop + boxH / 2
        return MapProjection(
            centerLat = (minLat + maxLat) / 2,
            centerLng = (minLng + maxLng) / 2,
            scale = scale,
            lngFactor = lngFactor,
            centerX = centerX,
            centerY = centerY,
        )
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

    private const val EPS = 1e-9
}

/** 위경도 → 화면 좌표 변환 ([CourseMapGeometry.fit]이 만든다). */
data class MapProjection(
    val centerLat: Double,
    val centerLng: Double,
    val scale: Double,
    val lngFactor: Double,
    val centerX: Float,
    val centerY: Float,
) {
    fun toScreen(p: LatLng): MapPoint = MapPoint(
        x = (centerX + (p.longitude - centerLng) * lngFactor * scale).toFloat(),
        y = (centerY - (p.latitude - centerLat) * scale).toFloat(),
    )
}

/** 좌표가 없는 장소(0,0)는 그리지 않는다. 옛 저장본·수동 입력 장소가 이렇게 올 수 있다. */
private fun LatLng.hasCoordinates() = !(latitude == 0.0 && longitude == 0.0)
private fun io.github.haeyeonyou.familytrip.data.CoursePlace.hasCoordinates() = !(latitude == 0.0 && longitude == 0.0)
