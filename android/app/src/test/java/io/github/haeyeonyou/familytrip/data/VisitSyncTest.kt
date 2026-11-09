package io.github.haeyeonyou.familytrip.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VisitSyncTest {
    private fun serverVisit(v: Visit, status: String = "confirmed", mood: String? = null, stay: Int? = null) = VisitOut(
        id = VisitMapper.idOf(v), placeKey = v.placeKey.orEmpty(), placeName = v.placeName, theme = v.theme,
        visitedOn = v.date.toString(), stayedMinutes = stay, status = status, mood = mood,
    )

    private fun plan(local: List<Visit>, server: List<VisitOut>, known: Map<String, KnownVisit> = emptyMap(), complete: Boolean = true) =
        planVisits(local, server, known, complete) { 9_000L }

    // 변환 규칙

    @Test
    fun 방문을_서버_모양으로_바꾼다() {
        val v = outing(1700000000000, depart = "2026-11-05T13:00:00")
        val item = VisitMapper.toServer(v, null)
        assertEquals("서울숲", item.placeName)
        assertEquals("tour:1", item.placeKey)
        assertEquals("nature", item.theme)
        assertEquals("2026-11-05", item.visitedOn)
        assertEquals(180, item.stayedMinutes)
        assertEquals("confirmed", item.status)
        assertNull(item.mood)
    }

    @Test
    fun 서버_id는_기기_id에서_만든_같은_UUID다() {
        assertEquals(VisitMapper.uuid(42), VisitMapper.uuid(42))
        assertNotEquals(VisitMapper.uuid(42), VisitMapper.uuid(43))
        assertEquals(java.util.UUID.nameUUIDFromBytes("visit:42".toByteArray()).toString(), VisitMapper.uuid(42))
    }

    @Test
    fun 좌표_주소_시각은_올리지_않는다() {
        val body = Json.encodeToString(VisitMapper.toServer(outing(1, depart = "2026-11-05T11:00:00"), null))
        assertFalse(body.contains("비밀주소"))
        assertFalse(body.contains("10:00"))
        assertFalse(body.contains("address"))
        assertFalse(body.contains("latitude"))
    }

    @Test
    fun 떠나지_않은_방문은_머문_분이_없다_모르는_분야는_뺀다() {
        val item = VisitMapper.toServer(outing(1, theme = "shop"), null)
        assertNull(item.stayedMinutes)
        assertNull(item.theme)
    }

    @Test
    fun 식사와_카페는_올리지_않는다() {
        assertFalse(VisitMapper.syncs(outing(1).copy(role = "meal")))
        assertTrue(VisitMapper.syncs(outing(1)))
        val p = plan(listOf(outing(1).copy(role = "cafe")), emptyList())
        assertTrue(p.upload.isEmpty())
    }

    @Test
    fun 웹에서_확인하거나_정한_상태와_기분은_다시_올릴_때_지킨다() {
        val item = VisitMapper.toServer(outing(1), KnownVisit("confirmed", "excited", null))
        assertEquals("excited", item.mood)
    }

    // 맞추는 규칙

    @Test
    fun 올린_적_없는_방문은_올린다() {
        val v = outing(1)
        val p = plan(listOf(v), emptyList())
        assertEquals(listOf(v), p.upload)
        assertTrue(p.removeLocal.isEmpty())
    }

    @Test
    fun 웹에서_지운_방문은_차에서도_지우고_되살리지_않는다() {
        val v = outing(1)
        val known = mapOf(VisitMapper.idOf(v) to KnownVisit())
        val p = plan(listOf(v), emptyList(), known)
        assertEquals(setOf(1L), p.removeLocal)
        assertTrue(p.upload.isEmpty())
        assertTrue(p.known.isEmpty())
    }

    @Test
    fun 서버_목록이_상한만큼_와서_잘렸을_수_있으면_지웠다고_판단하지_않는다() {
        val v = outing(1)
        val known = mapOf(VisitMapper.idOf(v) to KnownVisit())
        val p = plan(listOf(v), emptyList(), known, complete = false)
        assertTrue(p.removeLocal.isEmpty())
        assertTrue(p.upload.isEmpty())
    }

    @Test
    fun 웹에서_안_갔어요로_바꾼_방문은_차_목록에서_뺀다() {
        val v = outing(1)
        val p = plan(listOf(v), listOf(serverVisit(v, status = "dismissed")))
        assertEquals(setOf(1L), p.removeLocal)
        assertTrue(p.upload.isEmpty())
    }

    @Test
    fun 웹에서_확인대기를_확정하면_차에_들인다() {
        val web = VisitOut(id = "aaaaaaaa-0000-0000-0000-000000000001", placeKey = "tour:9", placeName = "과천과학관", theme = "exhibition", visitedOn = "2026-11-03", stayedMinutes = 120)
        val pendingKnown = mapOf(web.id to KnownVisit(status = "pending"))
        val added = plan(emptyList(), listOf(web), pendingKnown).addLocal.single()
        assertEquals("과천과학관", added.placeName)
        assertEquals(web.id, added.serverId)
        assertEquals("outing", added.role)
        assertEquals(120L, added.stayMinutes())
        assertEquals("2026-11-03", added.date.toString())
        // 확인 대기인 동안에는 들이지 않는다
        assertTrue(plan(emptyList(), listOf(web.copy(status = "pending"))).addLocal.isEmpty())
    }

    @Test
    fun 차_기록_상한으로_밀려난_옛_기록은_다시_들이지_않는다() {
        val old = outing(1)
        val known = mapOf(VisitMapper.idOf(old) to KnownVisit())
        assertTrue(plan(emptyList(), listOf(serverVisit(old)), known).addLocal.isEmpty())
    }

    @Test
    fun 머문_시간이_달라지면_다시_올린다() {
        val v = outing(1, depart = "2026-11-05T11:00:00")
        val p = plan(listOf(v), listOf(serverVisit(v, stay = null)))
        assertEquals(listOf(v), p.upload)
        assertTrue(plan(listOf(v), listOf(serverVisit(v, stay = 60))).upload.isEmpty())
    }

    // 업로드 흐름 (가짜 서버)

    private class FakeRemote : VisitRemote {
        val server = linkedMapOf<String, VisitOut>()
        var failNext = 0
        val puts = mutableListOf<String>()
        val bulks = mutableListOf<Int>()
        override suspend fun list(): List<VisitOut> {
            if (failNext > 0) { failNext--; throw java.io.IOException("오프라인") }
            return server.values.toList()
        }
        override suspend fun put(id: String, body: VisitIn) {
            if (failNext > 0) { failNext--; throw java.io.IOException("오프라인") }
            puts += id
            server[id] = VisitOut(id, body.placeKey, body.placeName, body.theme, body.visitedOn, body.stayedMinutes, body.status, body.mood)
        }
        override suspend fun bulk(items: List<VisitBulkItem>) {
            if (failNext > 0) { failNext--; throw java.io.IOException("오프라인") }
            bulks += items.size
            items.forEach { server[it.id] = VisitOut(it.id, it.placeKey, it.placeName, it.theme, it.visitedOn, it.stayedMinutes, it.status, it.mood) }
        }
    }

    private fun setup(): Triple<VisitStore, FakeRemote, VisitSync> {
        val dir = tempDir()
        val store = VisitStore(File(dir, "visits.json"))
        val remote = FakeRemote()
        val sync = VisitSync(store, remote, JsonFile(File(dir, "known.json"), knownVisitsSerializer) { emptyMap() }) { System.nanoTime() }
        return Triple(store, remote, sync)
    }

    private fun VisitStore.add(v: Visit) = update { it + v }

    @Test
    fun 로그인하면_쌓인_방문을_bulk로_올리고_하나뿐이면_PUT으로_올린다() = runBlocking {
        val (store, remote, sync) = setup()
        store.add(outing(1)); store.add(outing(2, "코엑스", "tour:2", "experience"))
        sync.reconcile()
        assertEquals(listOf(2), remote.bulks)
        assertEquals(2, remote.server.size)
        store.add(outing(3, "어린이대공원", "tour:3", "nature"))
        sync.reconcile()
        assertEquals(1, remote.puts.size)
        assertEquals(3, remote.server.size)
    }

    @Test
    fun 떠남이_기록되면_머문_시간을_PUT으로_다시_올린다() = runBlocking {
        val (store, remote, sync) = setup()
        store.add(outing(1))
        sync.reconcile()
        assertNull(remote.server.values.single().stayedMinutes)
        store.update { it.map { v -> v.copy(departedAt = "2026-11-05T12:30:00") } }
        assertTrue(sync.hasPendingUpload())
        sync.reconcile()
        assertEquals(150, remote.server.values.single().stayedMinutes)
        assertFalse(sync.hasPendingUpload())
    }

    @Test
    fun 올리다_실패하면_다음에_다시_올린다() = runBlocking {
        val (store, remote, sync) = setup()
        store.add(outing(1))
        remote.failNext = 1
        runCatching { sync.reconcile() }
        assertTrue(remote.server.isEmpty())
        assertTrue(sync.hasPendingUpload())
        // 목록은 받았지만 올리기가 실패한 경우도 남는다
        remote.failNext = 0
        sync.reconcile()
        assertEquals(1, remote.server.size)
        assertFalse(sync.hasPendingUpload())
    }

    @Test
    fun 올리기만_실패해도_표시를_남기지_않아_다시_올린다() = runBlocking {
        val (store, remote, sync) = setup()
        store.add(outing(1))
        // list는 성공하고 put에서 실패하도록 한 번 지나간 뒤 실패를 건다
        val flaky = object : VisitRemote by remote {
            var first = true
            override suspend fun put(id: String, body: VisitIn) {
                if (first) { first = false; throw java.io.IOException("끊김") }
                remote.put(id, body)
            }
        }
        val dir = tempDir()
        val sync2 = VisitSync(store, flaky, JsonFile(File(dir, "k.json"), knownVisitsSerializer) { emptyMap() })
        runCatching { sync2.reconcile() }
        assertTrue(sync2.hasPendingUpload())
        sync2.reconcile()
        assertEquals(1, remote.server.size)
    }

    @Test
    fun 웹에서_지운_방문은_다시_올리지_않고_차에서도_사라진다() = runBlocking {
        val (store, remote, sync) = setup()
        store.add(outing(1)); store.add(outing(2, "코엑스", "tour:2", "experience"))
        sync.reconcile()
        remote.server.remove(VisitMapper.uuid(1))   // 웹에서 지움
        sync.reconcile()
        assertEquals(listOf(2L), store.all().map { it.id })
        assertFalse(remote.server.containsKey(VisitMapper.uuid(1)))   // 되살려 올리지 않았다
        sync.reconcile()
        assertFalse(remote.server.containsKey(VisitMapper.uuid(1)))
    }

    @Test
    fun 웹에서_전체_삭제해도_차_기록은_되살아나지_않는다() = runBlocking {
        val (store, remote, sync) = setup()
        store.add(outing(1)); store.add(outing(2, "코엑스", "tour:2", "experience"))
        sync.reconcile()
        remote.server.clear()
        sync.reconcile()
        assertTrue(store.all().isEmpty())
        assertTrue(remote.server.isEmpty())
    }

    @Test
    fun 웹에서_확인한_방문이_차에_들어오고_웹에서_안_갔어요면_빠진다() = runBlocking {
        val (store, remote, sync) = setup()
        store.add(outing(1))
        sync.reconcile()
        remote.server["bbbbbbbb-0000-0000-0000-000000000002"] =
            VisitOut("bbbbbbbb-0000-0000-0000-000000000002", "tour:5", "남산", "nature", "2026-11-04", 90, "confirmed", "excited")
        remote.server[VisitMapper.uuid(1)] = remote.server.getValue(VisitMapper.uuid(1)).copy(status = "dismissed")
        sync.reconcile()
        assertEquals(listOf("남산"), store.all().map { it.placeName })
        // 한 번 반영한 뒤에는 흔들리지 않는다
        sync.reconcile()
        assertEquals(listOf("남산"), store.all().map { it.placeName })
    }

    @Test
    fun 웹에서_정한_기분은_머문_시간을_다시_올려도_지켜진다() = runBlocking {
        val (store, remote, sync) = setup()
        store.add(outing(1))
        sync.reconcile()
        remote.server[VisitMapper.uuid(1)] = remote.server.getValue(VisitMapper.uuid(1)).copy(mood = "excited")
        store.update { it.map { v -> v.copy(departedAt = "2026-11-05T11:00:00") } }
        sync.reconcile()
        assertEquals("excited", remote.server.getValue(VisitMapper.uuid(1)).mood)
        assertEquals(60, remote.server.getValue(VisitMapper.uuid(1)).stayedMinutes)
    }

    @Test
    fun 기록이_바뀌면_revision이_올라간다() {
        val (store, _, _) = setup()
        val before = store.revision.value
        store.add(outing(1))
        assertEquals(before + 1, store.revision.value)
        store.update { it }   // 달라진 것이 없으면 올리지 않는다
        assertEquals(before + 1, store.revision.value)
    }
}
