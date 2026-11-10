package io.github.haeyeonyou.familytrip.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class CarePlaceSyncTest {
    private val today = LocalDate.of(2026, 11, 11)

    private fun visit(id: Long, name: String, day: String, amenities: List<String> = emptyList(), role: String = "outing", key: String? = "kakao:$name") =
        Visit(id = id, placeName = name, role = role, placeKey = key, arrivedAt = "${day}T10:00:00", amenities = amenities)

    @Test
    fun 수유실이_확인된_곳을_두_번_이상_가면_올린다() {
        val visits = listOf(
            visit(1, "육아종합지원센터", "2026-11-01", listOf("nursing_room")),
            visit(2, "육아종합지원센터", "2026-11-05", listOf("stroller")),
            visit(3, "키즈카페", "2026-11-02", listOf("nursing_room")),  // 한 번뿐
            visit(4, "공원", "2026-11-02"), visit(5, "공원", "2026-11-03"),  // 수유실 모름
            visit(6, "집", "2026-11-02", listOf("nursing_room"), role = "home"), visit(7, "집", "2026-11-03", listOf("nursing_room"), role = "home"),
            visit(8, "옛 센터", "2026-06-01", listOf("nursing_room")), visit(9, "옛 센터", "2026-06-02", listOf("nursing_room")),  // 90일 밖
        )
        val up = CarePlaceRules.toUpload(visits, today)
        assertEquals(listOf(CarePlaceItem("kakao:육아종합지원센터", "육아종합지원센터", listOf("nursing_room", "stroller"), 2, "2026-11-05")), up)
    }

    @Test
    fun 받은_시설은_같은_이름_방문에_더하기만_한다() {
        val visits = listOf(visit(1, "육아종합지원센터", "2026-11-01", listOf("pet")), visit(2, "공원", "2026-11-01"))
        val places = listOf(CarePlaceItem(placeName = "육아종합지원센터", amenities = listOf("nursing_room"), visitCount = 3))
        val next = CarePlaceRules.withAmenities(visits, places)
        assertEquals(listOf("nursing_room", "pet"), next[0].amenities)
        assertEquals(emptyList<String>(), next[1].amenities)
        assertSame(next, CarePlaceRules.withAmenities(next, places))  // 바뀐 게 없으면 같은 목록
    }

    private class FakeRemote(var account: List<CarePlaceItem> = emptyList()) : CarePlaceRemote {
        val puts = mutableListOf<List<CarePlaceItem>>()
        override suspend fun list() = account
        override suspend fun put(places: List<CarePlaceItem>): List<CarePlaceItem> {
            puts += places
            account = (account + places).distinctBy { it.placeName }
            return account
        }
    }

    @Test
    fun 바뀐_때만_올리고_계정_목록을_받아_둔다() = runBlocking {
        val dir = tempDir()
        val store = VisitStore(File(dir, "visits.json"))
        store.update { listOf(visit(1, "센터", "2026-11-01", listOf("nursing_room")), visit(2, "센터", "2026-11-03"), visit(3, "다른 차가 간 곳", "2026-11-04")) }
        val remote = FakeRemote(listOf(CarePlaceItem(placeName = "다른 차가 간 곳", amenities = listOf("nursing_room"), visitCount = 4)))
        val sync = CarePlaceSync(
            store, remote,
            JsonFile(File(dir, "up.json"), carePlaceListSerializer) { emptyList() },
            JsonFile(File(dir, "cache.json"), carePlaceListSerializer) { emptyList() },
            today = { today },
        )
        assertTrue(sync.hasPendingUpload())
        sync.reconcile()
        assertEquals(1, remote.puts.size)
        assertEquals(listOf("다른 차가 간 곳", "센터"), sync.cached().map { it.placeName }.sorted())
        assertEquals(listOf("nursing_room"), store.all().first { it.placeName == "다른 차가 간 곳" }.amenities)
        sync.reconcile()
        assertEquals(1, remote.puts.size)  // 같은 목록은 다시 올리지 않는다
    }
}
