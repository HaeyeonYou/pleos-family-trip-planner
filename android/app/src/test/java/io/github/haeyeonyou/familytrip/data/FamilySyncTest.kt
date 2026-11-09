package io.github.haeyeonyou.familytrip.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class FamilySyncTest {
    private fun sc(id: String, nick: String = "서아", birth: String = "2025-09", interval: Int? = null, type: String? = "mixed", nap: Int? = 2) =
        ServerChild(id, nick, birth, type, interval, nap, 14)

    private fun local(vararg c: Child) = FamilyProfile(children = c.toList(), adults = 3, petAlong = true)

    private var seq = 0
    private val newId = { "new-${++seq}" }

    // 순수 규칙

    @Test
    fun 차에서_새로_등록한_아이는_서버에_올린다() {
        val plan = planFamily(local(Child(2025, 9, "서아")), 180, emptyList(), emptyList(), newId)
        val (id, body) = plan.puts.single()
        assertEquals("new-1", id)
        assertEquals("서아", body.nickname)
        assertEquals("2025-09", body.birthMonth)
        assertNull(body.feedIntervalMinutes)   // 기본 간격은 보내지 않는다
        assertEquals("new-1", plan.local!!.children.single().serverId)
    }

    @Test
    fun 이름이_없으면_번호로_된_애칭을_올리고_수유_간격이_기본이_아니면_함께_올린다() {
        val plan = planFamily(local(Child(2025, 9)), 240, emptyList(), emptyList(), newId)
        assertEquals("아이 1", plan.puts.single().second.nickname)
        assertEquals(240, plan.puts.single().second.feedIntervalMinutes)
    }

    @Test
    fun 웹에서_먼저_등록한_같은_달_아이는_새로_만들지_않고_이어_붙인다() {
        val plan = planFamily(local(Child(2025, 9, "")), 180, emptyList(), listOf(sc("web-1")), newId)
        assertTrue(plan.puts.isEmpty())
        assertEquals("web-1", plan.local!!.children.single().serverId)
    }

    @Test
    fun 고친_항목만_올리고_서버에만_있는_항목은_서버_값을_지킨다() {
        val s = sc("a", interval = 150)
        val plan = planFamily(local(Child(2025, 9, "새이름", "a")), 150, listOf(s), listOf(s), newId)
        val body = plan.puts.single().second
        assertEquals("새이름", body.nickname)
        assertEquals("2025-09", body.birthMonth)
        assertEquals("mixed", body.feedingType)
        assertEquals(2, body.napCount)
        assertEquals(150, body.feedIntervalMinutes)
    }

    @Test
    fun 차에서_수유_간격만_바꿔도_첫째_아이에게_올라간다() {
        val s = sc("a")
        val plan = planFamily(local(Child(2025, 9, "서아", "a")), 240, listOf(s), listOf(s), newId)
        assertEquals(240, plan.puts.single().second.feedIntervalMinutes)
    }

    @Test
    fun 고치지_않은_항목은_웹에서_바뀐_값이_이긴다() {
        val snap = sc("a", nick = "서아")
        val web = sc("a", nick = "서아야", interval = 120)
        val plan = planFamily(local(Child(2025, 9, "서아", "a")), 180, listOf(snap), listOf(web), newId)
        assertTrue(plan.puts.isEmpty())
    }

    @Test
    fun 차에서_지운_아이는_서버에서도_지운다_웹에서_먼저_지운_아이는_건드리지_않는다() {
        val a = sc("a"); val b = sc("b", "하준", "2023-03")
        val plan = planFamily(local(Child(2025, 9, "서아", "a")), 180, listOf(a, b), listOf(a, b), newId)
        assertEquals(listOf("b"), plan.deletes)
        val webDeleted = planFamily(local(Child(2025, 9, "서아", "a")), 180, listOf(a, b), listOf(a), newId)
        assertTrue(webDeleted.deletes.isEmpty())
    }

    @Test
    fun 차_프로필이_없으면_올리지도_지우지도_않는다() {
        val plan = planFamily(null, 180, listOf(sc("a")), listOf(sc("a")), newId)
        assertTrue(plan.puts.isEmpty() && plan.deletes.isEmpty())
    }

    @Test
    fun 서버는_아이_여섯_명까지라_넘는_아이는_올리지_않는다() {
        val server = (1..6).map { sc("s$it", "아이$it", "2025-0${(it % 9) + 1}") }
        val plan = planFamily(local(Child(2020, 1, "일곱째")), 180, server, server, newId)
        assertTrue(plan.puts.isEmpty())
    }

    // 맞추는 흐름 (가짜 서버)

    private class FakeRemote(var children: MutableList<ServerChild>) : FamilyRemote {
        var failPut = false
        var putStatus: Int? = null
        val deleted = mutableListOf<String>()
        override suspend fun family() = ServerFamily(children.toList())
        override suspend fun putChild(id: String, body: ChildIn): ServerChild {
            if (failPut) throw IOException("오프라인")
            putStatus?.let { throw httpError(it) }
            val saved = ServerChild(id, body.nickname, body.birthMonth, body.feedingType, body.feedIntervalMinutes, body.napCount, 0)
            val at = children.indexOfFirst { it.id == id }
            if (at >= 0) children[at] = saved else children += saved
            return saved
        }
        override suspend fun deleteChild(id: String) {
            deleted += id
            children.removeAll { it.id == id }
        }
    }

    private fun sync(remote: FamilyRemote, profile: MemoryProfile, routine: MemoryRoutine) =
        FamilySync(remote, profile, routine, JsonFile(File(tempDir(), "snap.json"), serverChildrenSerializer) { emptyList() }, newId)

    @Test
    fun 웹에서_등록한_아이가_차_프로필로_들어오고_수유_간격도_따라온다() = runBlocking {
        val remote = FakeRemote(mutableListOf(sc("a", "서아", "2025-09", interval = 150)))
        val profile = MemoryProfile(); val routine = MemoryRoutine()
        sync(remote, profile, routine).reconcile()
        val child = profile.profile!!.children.single()
        assertEquals(Child(2025, 9, "서아", "a"), child)
        assertEquals(150, routine.routine.intervalMinutes)
    }

    @Test
    fun 웹에서_아이_정보를_고치면_차에_반영되고_성인_수와_반려견은_그대로다() = runBlocking {
        val remote = FakeRemote(mutableListOf(sc("a", "서아", "2025-09")))
        val profile = MemoryProfile(local(Child(2025, 9, "서아", "a"))); val routine = MemoryRoutine()
        val sync = sync(remote, profile, routine)
        sync.reconcile()
        remote.children[0] = sc("a", "서아공주", "2025-08")
        sync.reconcile()
        assertEquals(Child(2025, 8, "서아공주", "a"), profile.profile!!.children.single())
        assertEquals(3, profile.profile!!.adults)
        assertTrue(profile.profile!!.petAlong)
        assertTrue(remote.deleted.isEmpty())
    }

    @Test
    fun 웹에서_지운_아이는_차에서도_빠진다() = runBlocking {
        val remote = FakeRemote(mutableListOf(sc("a"), sc("b", "하준", "2023-03")))
        val profile = MemoryProfile(); val routine = MemoryRoutine()
        val sync = sync(remote, profile, routine)
        sync.reconcile()
        remote.children.removeAll { it.id == "b" }
        sync.reconcile()
        assertEquals(listOf("a"), profile.profile!!.children.map { it.serverId })
        assertTrue(remote.deleted.isEmpty())
    }

    @Test
    fun 차에서_고친_아이_정보가_서버에_올라간다() = runBlocking {
        val remote = FakeRemote(mutableListOf())
        val profile = MemoryProfile(local(Child(2025, 9, "서아")))
        val sync = sync(remote, profile, MemoryRoutine())
        sync.reconcile()
        val id = remote.children.single().id
        assertEquals("서아", remote.children.single().nickname)
        // 차에서 이름과 생월을 고침
        profile.profile = profile.profile!!.copy(children = listOf(Child(2025, 10, "서아언니", id)))
        assertTrue(sync.hasPendingUpload())
        sync.reconcile()
        assertEquals("서아언니", remote.children.single().nickname)
        assertEquals("2025-10", remote.children.single().birthMonth)
        assertTrue(!sync.hasPendingUpload())
    }

    @Test
    fun 올리기가_실패하면_서버_값으로_덮지_않고_다음에_다시_올린다() = runBlocking {
        val s = sc("a", "서아")
        val remote = FakeRemote(mutableListOf(s))
        val profile = MemoryProfile(local(Child(2025, 9, "서아", "a")))
        val sync = sync(remote, profile, MemoryRoutine())
        sync.reconcile()
        profile.profile = profile.profile!!.copy(children = listOf(Child(2025, 9, "고친이름", "a")))
        remote.failPut = true
        assertTrue(runCatching { sync.reconcile() }.isFailure)
        assertEquals("고친이름", profile.profile!!.children.single().name)   // 서버 값으로 덮이지 않았다
        remote.failPut = false
        sync.reconcile()
        assertEquals("고친이름", remote.children.single().nickname)
    }

    @Test
    fun 서버가_아이_정보를_거절하면_서버_값을_따른다() = runBlocking {
        val remote = FakeRemote(mutableListOf(sc("a", "서아")))
        val profile = MemoryProfile(local(Child(2025, 9, "서아", "a")))
        val sync = sync(remote, profile, MemoryRoutine())
        sync.reconcile()
        profile.profile = profile.profile!!.copy(children = listOf(Child(2025, 9, "고친이름", "a")))
        remote.putStatus = 422
        sync.reconcile()
        assertEquals("서아", profile.profile!!.children.single().name)
    }

    @Test
    fun 첫째_아이의_수유_방식을_수유_기록_종류에_쓴다() = runBlocking {
        val remote = FakeRemote(mutableListOf(sc("a", type = "breast")))
        val sync = sync(remote, MemoryProfile(), MemoryRoutine())
        assertNull(sync.firstChildFeedingType())
        sync.reconcile()
        assertEquals("breast", sync.firstChildFeedingType())
    }
}
