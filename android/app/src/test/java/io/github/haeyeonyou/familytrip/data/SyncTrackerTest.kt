package io.github.haeyeonyou.familytrip.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTrackerTest {
    private fun marks(presets: String = "p1", feedings: String = "f1", all: String = "a1") =
        SyncMarks(presets = presets, feedings = feedings, visits = "v1", family = "m1", cars = "c1", all = all)

    @Test
    fun 처음_보는_표시는_모든_영역이_바뀐_것으로_본다() {
        val change = SyncTracker().diff(marks())
        assertTrue(change.presets && change.feedings && change.visits && change.family && change.cars)
    }

    @Test
    fun 같은_표시면_바뀐_것이_없다() {
        val tracker = SyncTracker().also { it.accept(marks()) }
        assertFalse(tracker.diff(marks()).any)
    }

    @Test
    fun 프리셋만_바뀌면_프리셋만_바뀐_것으로_본다() {
        val tracker = SyncTracker().also { it.accept(marks()) }
        val change = tracker.diff(marks(presets = "p2", all = "a2"))
        assertEquals(SyncChange(presets = true), change)
    }

    @Test
    fun 여러_영역이_함께_바뀌면_모두_알려_준다() {
        val tracker = SyncTracker().also { it.accept(marks()) }
        val change = tracker.diff(marks(presets = "p2", feedings = "f2", all = "a2"))
        assertEquals(SyncChange(presets = true, feedings = true), change)
    }

    @Test
    fun 반영하지_못하면_다음_주기에도_같은_변경이_잡힌다() {
        val tracker = SyncTracker().also { it.accept(marks()) }
        val next = marks(presets = "p2", all = "a2")
        assertTrue(tracker.diff(next).presets) // 다시 받기에 실패해 accept를 부르지 않음
        assertTrue(tracker.diff(next).presets) // 다음 주기에도 다시 시도한다
        tracker.accept(next)
        assertFalse(tracker.diff(next).any)
    }

    @Test
    fun 계정이_바뀌면_처음부터_다시_비교한다() {
        val tracker = SyncTracker().also { it.accept(marks()) }
        tracker.reset()
        assertTrue(tracker.diff(marks()).presets)
    }

    @Test
    fun 서버_응답을_읽는다_모르는_필드는_무시() {
        val json = Json { ignoreUnknownKeys = true }
        val parsed = json.decodeFromString<SyncMarks>(
            """{"presets":"aaa","feedings":"bbb","visits":"ccc","family":"ddd","cars":"eee","all":"fff","extra":1}""",
        )
        assertEquals("aaa", parsed.presets)
        assertEquals("fff", parsed.all)
    }
}
