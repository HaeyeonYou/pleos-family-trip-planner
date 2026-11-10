package io.github.haeyeonyou.familytrip.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.time.OffsetDateTime

class FeedingSyncTest {
    private class FakeRemote : FeedingRemote {
        val puts = linkedMapOf<String, FeedingIn>()
        var failure: Exception? = null
        var lastFedAt: String? = null
        override suspend fun put(id: String, body: FeedingIn) {
            failure?.let { throw it }
            puts[id] = body
        }
        override suspend fun status(childId: String?) = FeedingStatus(lastFedAt = lastFedAt)
    }

    private val noon = OffsetDateTime.parse("2026-11-08T12:10:00+09:00").toInstant().toEpochMilli()

    private fun setup(owner: String? = "mom", now: Long = noon + 60_000, kind: String = "formula"): Triple<FeedingSync, FakeRemote, MemoryRoutine> {
        val remote = FakeRemote()
        val routine = MemoryRoutine()
        val queue = JsonFile(File(tempDir(), "q.json"), FeedingQueueData.serializer()) { FeedingQueueData() }
        // 옛 마지막 수유 이전 이관을 이미 끝낸 상태로 시작한다 (이관은 따로 시험)
        queue.save(FeedingQueueData(legacyMoved = true))
        return Triple(FeedingSync(remote, queue, routine, { kind }, { owner }, { now }), remote, routine)
    }

    @Test
    fun 방금_수유했어는_차에서_온_기록으로_같은_id로_올라간다() = runBlocking {
        val (sync, remote, _) = setup(kind = "breast")
        sync.record(noon)
        sync.flush()
        val body = remote.puts.getValue(FeedingSync.feedingId(noon))
        assertEquals("breast", body.kind)
        assertEquals("car", body.source)
        // 시간대를 포함한 ISO 8601이고 같은 순간이다
        assertTrue(body.fedAt.matches(Regex(""".*[+-]\d\d:\d\d$|.*Z$""")))
        assertEquals(noon, OffsetDateTime.parse(body.fedAt).toInstant().toEpochMilli())
        assertFalse(sync.hasPending())
    }

    @Test
    fun 같은_시각을_두_번_기록해도_하나만_올린다() = runBlocking {
        val (sync, remote, _) = setup()
        sync.record(noon); sync.record(noon)
        sync.flush()
        assertEquals(1, remote.puts.size)
    }

    @Test
    fun 오프라인이면_대기열에_남아_다음에_다시_올린다() = runBlocking {
        val (sync, remote, _) = setup()
        sync.record(noon)
        remote.failure = IOException("오프라인")
        assertTrue(runCatching { sync.flush() }.isFailure)
        assertTrue(sync.hasPending())
        remote.failure = httpError(503)
        assertTrue(runCatching { sync.flush() }.isFailure)
        assertTrue(sync.hasPending())
        remote.failure = null
        sync.flush()
        assertFalse(sync.hasPending())
        assertEquals(1, remote.puts.size)
    }

    @Test
    fun 서버가_거절한_기록은_버리고_로그인이_풀리면_그대로_던진다() = runBlocking {
        val (sync, remote, _) = setup()
        sync.record(noon)
        remote.failure = httpError(422)
        sync.flush()
        assertFalse(sync.hasPending())
        sync.record(noon + 1)
        remote.failure = httpError(401)
        assertTrue(runCatching { sync.flush() }.exceptionOrNull() is retrofit2.HttpException)
        assertTrue(sync.hasPending())
    }

    @Test
    fun 로그인_전에_한_기록은_쌓아_두었다가_로그인하면_올린다() = runBlocking {
        var owner: String? = null
        val remote = FakeRemote()
        val queue = JsonFile(File(tempDir(), "q.json"), FeedingQueueData.serializer()) { FeedingQueueData(legacyMoved = true) }
        val sync = FeedingSync(remote, queue, MemoryRoutine(), { "formula" }, { owner }, { noon + 1000 })
        sync.record(noon)
        sync.flush()
        assertTrue(remote.puts.isEmpty())
        assertTrue(sync.hasPending())
        owner = "mom"
        sync.flush()
        assertEquals(1, remote.puts.size)
    }

    @Test
    fun 서른날_가까이_지난_기록은_올리지_않고_버린다() = runBlocking {
        val (sync, remote, _) = setup(now = noon + 30L * 24 * 3_600_000)
        sync.record(noon)
        sync.flush()
        assertTrue(remote.puts.isEmpty())
        assertFalse(sync.hasPending())
    }

    @Test
    fun 다른_계정에서_기록한_대기_수유는_올리지_않는다() = runBlocking {
        val (sync, remote, _) = setup(owner = "mom")
        sync.record(noon)
        sync.onAccountChanged("dad")
        sync.flush()
        assertTrue(remote.puts.isEmpty())
    }

    @Test
    fun 서버의_마지막_수유를_차에_반영한다_웹에서_지우면_함께_비운다() = runBlocking {
        val (sync, remote, routine) = setup()
        remote.lastFedAt = "2026-11-08T12:10:00+09:00"
        sync.pull()
        assertEquals(noon, routine.routine.lastFedAt)
        remote.lastFedAt = null
        sync.pull()
        assertNull(routine.routine.lastFedAt)
    }

    @Test
    fun 아직_못_올린_수유가_더_늦으면_그것을_쓴다() = runBlocking {
        val (sync, remote, routine) = setup()
        remote.lastFedAt = "2026-11-08T12:10:00+09:00"
        sync.record(noon + 3_600_000)
        sync.pull()
        assertEquals(noon + 3_600_000, routine.routine.lastFedAt)
    }

    @Test
    fun 수유_간격은_받을_때_건드리지_않는다() = runBlocking {
        val (sync, remote, routine) = setup()
        routine.routine = FeedingRoutine(intervalMinutes = 240)
        remote.lastFedAt = "2026-11-08T12:10:00+09:00"
        sync.pull()
        assertEquals(240, routine.routine.intervalMinutes)
    }

    @Test
    fun 기능이_생기기_전_기기에만_있던_마지막_수유를_한_번_올린다() = runBlocking {
        val remote = FakeRemote()
        val routine = MemoryRoutine(FeedingRoutine(lastFedAt = noon))
        val queue = JsonFile(File(tempDir(), "q.json"), FeedingQueueData.serializer()) { FeedingQueueData() }
        val sync = FeedingSync(remote, queue, routine, { "formula" }, { "mom" }, { noon + 1000 })
        sync.flush()
        assertEquals(setOf(FeedingSync.feedingId(noon)), remote.puts.keys)
        remote.puts.clear()
        sync.flush()
        assertTrue(remote.puts.isEmpty())
    }

    @Test
    fun 수유_방식에서_기록_종류를_정한다() {
        assertEquals("breast", feedingKindOf("breast"))
        assertEquals("formula", feedingKindOf("mixed"))
        assertEquals("formula", feedingKindOf(null))
        assertEquals("solid", feedingKindOf("solid"))
    }
}
