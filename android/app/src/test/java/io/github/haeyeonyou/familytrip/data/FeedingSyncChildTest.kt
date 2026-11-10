package io.github.haeyeonyou.familytrip.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.OffsetDateTime

/** C07 아이별 수유: 고른 아이의 id로 올리고, 그 아이의 상태만 받는다. */
class FeedingSyncChildTest {
    private class ChildRemote : FeedingRemote {
        val puts = linkedMapOf<String, FeedingIn>()
        val statusAsked = mutableListOf<String?>()
        val lastByChild = mutableMapOf<String?, String?>()
        override suspend fun put(id: String, body: FeedingIn) { puts[id] = body }
        override suspend fun status(childId: String?): FeedingStatus {
            statusAsked += childId
            return FeedingStatus(childId = childId, lastFedAt = lastByChild[childId])
        }
    }

    private val noon = OffsetDateTime.parse("2026-11-08T12:10:00+09:00").toInstant().toEpochMilli()

    @Test
    fun 고른_아이의_id로_기록하고_그_아이_상태를_받는다() = runBlocking {
        val remote = ChildRemote()
        val routine = MemoryRoutine()
        val queue = JsonFile(File(tempDir(), "q.json"), FeedingQueueData.serializer()) { FeedingQueueData(legacyMoved = true) }
        var child: String? = "doyun"
        val sync = FeedingSync(remote, queue, routine, { "solid" }, { "mom" }, { noon + 1000 }, childId = { child })

        sync.record(noon)
        sync.flush()
        val body = remote.puts.getValue(FeedingSync.feedingId(noon, "doyun"))
        assertEquals("doyun", body.childId)
        // 같은 시각이라도 아이가 다르면 다른 기록
        assertNotEquals(FeedingSync.feedingId(noon), FeedingSync.feedingId(noon, "doyun"))

        remote.lastByChild["doyun"] = "2026-11-08T11:00:00+09:00"
        sync.pull()
        assertEquals("doyun", remote.statusAsked.last())
        assertEquals(OffsetDateTime.parse("2026-11-08T11:00:00+09:00").toInstant().toEpochMilli(), routine.load().lastFedAt)

        // 첫째(null)로 바꾸면 첫째 상태만 본다 (둘째의 대기 기록은 섞지 않는다)
        child = null
        sync.record(noon + 5_000)  // 첫째 기록은 아직 안 올림
        routine.save(FeedingRoutine())
        remote.lastByChild[null] = null
        queue.save(queue.load().copy(pending = queue.load().pending.filter { it.childId == null } + PendingFeeding("x", noon - 1000, "solid", "mom", "doyun")))
        sync.pull()
        assertNull(remote.statusAsked.last())
        assertEquals(noon + 5_000, routine.load().lastFedAt)
    }
}
