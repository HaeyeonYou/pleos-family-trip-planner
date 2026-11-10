package io.github.haeyeonyou.familytrip.care

import io.github.haeyeonyou.familytrip.data.CarePlace
import io.github.haeyeonyou.familytrip.data.LatLng
import io.github.haeyeonyou.familytrip.data.Visit
import io.github.haeyeonyou.familytrip.data.VisitStore
import java.time.LocalDate
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 케어 화면 '자주 가는 케어 장소' 한 곳 (피그마 HF03 v2).
 * [amenities]는 방문하면서 확인된 시설만 담는다 (nursing_room, stroller, pet). 확인된 것이 없으면 비어 있어 칩을 그리지 않는다.
 */
data class FrequentCarePlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** 최근 [FrequentCarePlaces.WINDOW_DAYS]일 안의 방문 수 */
    val visitCount: Int,
    val distanceM: Int,
    val driveMinutes: Int,
    val amenities: List<String>,
) {
    /** 경유지 추가·길 안내가 받는 모양으로 바꾼다. */
    fun toCarePlace() = CarePlace(
        name = name,
        latitude = latitude,
        longitude = longitude,
        distanceM = distanceM,
        driveMinutes = driveMinutes,
    )
}

/**
 * 방문 기록에서 '자주 가는 케어 장소'를 고른다 (순수 함수).
 *
 * 케어 장소로 보는 곳은 둘 중 하나다.
 *  - 방문할 때 수유실이 있다고 확인된 곳 ([Visit.amenities]에 nursing_room)
 *  - 지금 `care/nearby`가 찾은 수유실·기저귀 교환대·정차 공간 후보와 이름이 같은 곳 ([nearby])
 * 최근 [WINDOW_DAYS]일에 [MIN_VISITS]번 이상 간 곳 중 [MAX_DISTANCE_M] 안에 있는 곳을, 많이 간 순 · 최근에 간 순으로 [MAX_PLACES]곳까지.
 * 거리·시간은 서버 `care/nearby`와 같은 방식(직선거리 × 1.3, 시속 30km)으로 어림한다. 좌표를 모르는 예전 기록은
 * `care/nearby`에서 같은 이름을 찾았을 때만 그 좌표를 쓴다.
 */
object FrequentCarePlaces {
    const val WINDOW_DAYS = 90L
    const val MIN_VISITS = 2
    const val MAX_PLACES = 2
    const val MAX_DISTANCE_M = 30_000
    private const val ROAD_FACTOR = 1.3
    private const val URBAN_KMH = 30.0

    fun from(visits: List<Visit>, here: LatLng, nearby: Collection<CarePlace>, today: LocalDate = LocalDate.now()): List<FrequentCarePlace> {
        val since = today.minusDays(WINDOW_DAYS)
        val nearbyByName = nearby.associateBy { it.name }
        return visits
            .filter { it.role != "home" && !it.date.isBefore(since) }
            .groupBy { it.placeKey ?: it.placeName }
            .values
            .mapNotNull { group ->
                if (group.size < MIN_VISITS) return@mapNotNull null
                val latest = group.maxBy { it.arrivedAt }
                val match = nearbyByName[latest.placeName]
                val amenities = VisitStore.CARE_AMENITIES.filter { a -> group.any { a in it.amenities } }
                if ("nursing_room" !in amenities && match == null) return@mapNotNull null
                val withCoords = group.lastOrNull { it.latitude != null && it.longitude != null }
                val lat = withCoords?.latitude ?: match?.latitude ?: return@mapNotNull null
                val lng = withCoords?.longitude ?: match?.longitude ?: return@mapNotNull null
                val distance = distanceMeters(here, lat, lng)
                if (distance > MAX_DISTANCE_M) return@mapNotNull null
                latest to FrequentCarePlace(
                    name = latest.placeName,
                    latitude = lat,
                    longitude = lng,
                    visitCount = group.size,
                    distanceM = distance,
                    driveMinutes = minutes(distance),
                    amenities = amenities,
                )
            }
            .sortedWith(compareByDescending<Pair<Visit, FrequentCarePlace>> { it.second.visitCount }.thenByDescending { it.first.arrivedAt })
            .take(MAX_PLACES)
            .map { it.second }
    }

    /** 직선거리(m) */
    fun distanceMeters(from: LatLng, lat: Double, lng: Double): Int {
        val r = 6_371_000.0
        val p1 = Math.toRadians(from.latitude)
        val p2 = Math.toRadians(lat)
        val dp = p2 - p1
        val dl = Math.toRadians(lng - from.longitude)
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return (2 * r * asin(sqrt(a))).roundToInt()
    }

    fun minutes(distanceM: Int): Int = maxOf(1, (distanceM * ROAD_FACTOR / 1000 / URBAN_KMH * 60).roundToInt())
}
