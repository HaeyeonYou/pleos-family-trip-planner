package io.github.haeyeonyou.familytrip.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.LocalDate

/**
 * 계정에 올리는 자주 가는 케어 장소 한 곳 (서버 `app/care_places.py`, #184 QO9).
 * 좌표는 보내지 않는다. 차는 이름으로 `care/nearby` 후보와 맞춰 좌표를 찾는다 (`care/FrequentCarePlaces.kt`).
 */
@Serializable
data class CarePlaceItem(
    @SerialName("place_key") val placeKey: String = "",
    @SerialName("place_name") val placeName: String,
    val amenities: List<String> = emptyList(),
    @SerialName("visit_count") val visitCount: Int,
    @SerialName("last_visited_on") val lastVisitedOn: String? = null,
)

@Serializable
data class CarePlaceList(val places: List<CarePlaceItem> = emptyList())

/** 서버 케어 장소 API. 테스트에서는 가짜로 바꾼다. */
interface CarePlaceRemote {
    suspend fun list(): List<CarePlaceItem>
    suspend fun put(places: List<CarePlaceItem>): List<CarePlaceItem>
}

class TripApiCarePlaceRemote(private val api: TripApi) : CarePlaceRemote {
    override suspend fun list() = api.carePlaces().places
    override suspend fun put(places: List<CarePlaceItem>) = api.putCarePlaces(CarePlaceList(places)).places
}

object CarePlaceRules {
    const val WINDOW_DAYS = 90L
    const val MIN_VISITS = 2
    const val MAX_UPLOAD = 20

    /**
     * 차의 방문 기록에서 계정에 올릴 케어 장소를 고른다 (순수 함수).
     * 최근 [WINDOW_DAYS]일에 [MIN_VISITS]번 이상 갔고 수유실이 확인된 곳만 (케어 화면 '자주 가는 케어 장소'의 기준과 같다.
     * 수유실 후보와 이름이 같은 곳은 위치가 있어야 알 수 있어 여기서는 빠진다). 많이 간 순 · 최근에 간 순.
     */
    fun toUpload(visits: List<Visit>, today: LocalDate = LocalDate.now()): List<CarePlaceItem> {
        val since = today.minusDays(WINDOW_DAYS)
        return visits
            .filter { it.role != "home" && !it.date.isBefore(since) }
            .groupBy { it.placeKey ?: it.placeName }
            .values
            .filter { it.size >= MIN_VISITS }
            .mapNotNull { group ->
                val amenities = VisitStore.CARE_AMENITIES.filter { a -> group.any { a in it.amenities } }
                if ("nursing_room" !in amenities) return@mapNotNull null
                val latest = group.maxBy { it.arrivedAt }
                CarePlaceItem(latest.placeKey.orEmpty(), latest.placeName.take(60), amenities, group.size, latest.date.toString())
            }
            .sortedWith(compareByDescending<CarePlaceItem> { it.visitCount }.thenByDescending { it.lastVisitedOn })
            .take(MAX_UPLOAD)
    }

    /**
     * 계정에서 받은 케어 장소의 확인된 시설을 이름이 같은 방문 기록에 더한다 (순수 함수, 빼지는 않는다).
     * 다른 차·웹에서 받은 방문 기록은 시설 정보가 없어서, 이렇게 해야 새 차에서도 '자주 가는 케어 장소'가 바로 보인다.
     * 바뀐 것이 없으면 같은 목록을 돌려준다.
     */
    fun withAmenities(visits: List<Visit>, places: List<CarePlaceItem>): List<Visit> {
        val byName = places.associateBy { it.placeName }
        var changed = false
        val next = visits.map { v ->
            val place = byName[v.placeName] ?: return@map v
            val merged = VisitStore.CARE_AMENITIES.filter { it in v.amenities || it in place.amenities }
            if (merged.toSet() == v.amenities.toSet()) v else v.copy(amenities = merged).also { changed = true }
        }
        return if (changed) next else visits
    }
}

/**
 * 자주 가는 케어 장소를 계정과 맞춘다: 차에서 고른 목록이 지난번에 올린 것과 다르면 올리고, 계정 목록을 받아 [cache]에 둔다.
 * 온보딩 O02는 [cached]로 "불러온 케어 장소"를 보여 준다. 서버가 아직 이 API를 모르면(404) 예외가 나고 다음 주기에 다시 한다.
 */
class CarePlaceSync(
    private val store: VisitStore,
    private val remote: CarePlaceRemote,
    private val uploaded: JsonFile<List<CarePlaceItem>>,
    private val cache: JsonFile<List<CarePlaceItem>>,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    private val lock = Mutex()

    fun cached(): List<CarePlaceItem> = cache.load()

    fun hasPendingUpload(): Boolean = CarePlaceRules.toUpload(store.all(), today()).let { it.isNotEmpty() && it != uploaded.load() }

    suspend fun reconcile() = lock.withLock {
        val mine = CarePlaceRules.toUpload(store.all(), today())
        val account = if (mine.isNotEmpty() && mine != uploaded.load()) {
            remote.put(mine).also { uploaded.save(mine) }
        } else {
            remote.list()
        }
        cache.save(account)
        val visits = store.all()
        val next = CarePlaceRules.withAmenities(visits, account)
        if (next !== visits) store.update { cur -> CarePlaceRules.withAmenities(cur, account) }
    }

    fun forget() {
        uploaded.clear()
        cache.clear()
    }
}

val carePlaceListSerializer = ListSerializer(CarePlaceItem.serializer())
