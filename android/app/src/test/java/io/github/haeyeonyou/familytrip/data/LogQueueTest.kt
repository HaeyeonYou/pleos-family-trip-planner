package io.github.haeyeonyou.familytrip.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LogQueueTest {
    @get:Rule val tmp = TemporaryFolder()
    private var clock = 0L
    private fun queue(maxLines: Int = 300) = LogQueue(tmp.root.resolve("q.jsonl"), now = { clock }, timestamp = { "t$clock" }, maxLines = maxLines)

    @Test fun `반복되는 경고는 한 줄로 묶고 보내기 전에 묶은 수를 내보낸다`() {
        val q = queue()
        repeat(5) { clock += 1000; q.record("warn", "Sync", "sync failed: timeout", null) }
        assertEquals(1, q.size())
        q.flushRepeats()
        val events = q.peek(10)
        assertEquals(listOf(1, 4), events.map { it.count })
    }

    @Test fun `시간이 지나면 같은 경고도 다시 한 줄을 남기고 그동안 묶은 수를 함께 남긴다`() {
        val q = queue()
        q.record("warn", "Sync", "x", null)
        clock += 1000; q.record("warn", "Sync", "x", null)
        clock += 11 * 60_000L; q.record("warn", "Sync", "x", null)
        assertEquals(listOf(1, 1, 1), q.peek(10).map { it.count })
        assertEquals(3, q.size())
    }

    @Test fun `크래시는 묶지 않고 좌표는 지운다`() {
        val q = queue()
        q.record("crash", "thread:main", "boom at 37.56651,126.97800", "at A.b(37.12345)")
        q.record("crash", "thread:main", "boom at 37.56651,126.97800", null)
        val events = q.peek(10)
        assertEquals(2, events.size)
        assertFalse(events[0].message.contains("37.56651"))
        assertTrue(events[0].message.contains("<좌표>"))
        assertFalse(events[0].stack!!.contains("37.12345"))
    }

    @Test fun `보낸 만큼 지우고 오래된 줄부터 상한을 넘으면 버린다`() {
        val q = queue(maxLines = 3)
        (1..5).forEach { clock += 1; q.record("error", "T", "e$it", null) }
        assertEquals(listOf("e3", "e4", "e5"), q.peek(10).map { it.message })
        q.drop(2)
        assertEquals(listOf("e5"), q.peek(10).map { it.message })
    }
}
