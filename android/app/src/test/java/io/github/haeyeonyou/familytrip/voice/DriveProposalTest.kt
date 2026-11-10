package io.github.haeyeonyou.familytrip.voice

import io.github.haeyeonyou.familytrip.care.CareKind
import io.github.haeyeonyou.familytrip.data.CarePlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveProposalTest {
    private fun place(name: String, detour: Int?, onRoute: Boolean = false, minutes: Int = 5, meters: Int = 1000) =
        CarePlace(name = name, latitude = 37.0, longitude = 127.0, distanceM = meters, driveMinutes = minutes, detourMinutes = detour, onRoute = onRoute)

    @Test
    fun picksLeastDetour() {
        val best = pickBestPlace(listOf(place("A", 6), place("B", 3), place("C", 9)))
        assertEquals("B", best?.name)
    }

    @Test
    fun onRouteBeatsDetour() {
        val best = pickBestPlace(listOf(place("A", 1), place("B", 4, onRoute = true)))
        assertEquals("B", best?.name)
    }

    @Test
    fun unknownDetourGoesLastThenNearest() {
        assertEquals("B", pickBestPlace(listOf(place("A", null, minutes = 2), place("B", 8)))?.name)
        assertEquals("N", pickBestPlace(listOf(place("F", null, minutes = 9), place("N", null, minutes = 3)))?.name)
        assertEquals("N", pickBestPlace(listOf(place("F", 3, meters = 2000), place("N", 3, meters = 800)))?.name)
    }

    @Test
    fun emptyHasNoPick() = assertNull(pickBestPlace(emptyList()))

    @Test
    fun messageMatchesDesignWording() {
        assertEquals(
            "가장 가까운 OO몰 2F 수유실이에요. 경로에서 3분 더 걸려요. 경유지로 추가할까요?",
            proposalMessage(place("OO몰 2F 수유실", 3)),
        )
        assertTrue(proposalMessage(place("X", 0, onRoute = true)).contains("경로 위에 있어요"))
        assertTrue(proposalMessage(place("X", null, minutes = 7)).contains("7분 거리"))
        assertTrue(noProposalMessage(CareKind.NURSING, failed = false).contains("수유실"))
    }

    @Test
    fun acceptWords() {
        listOf("좋아", "좋아요", "좋아, 추가", "추가해줘", "응", "네", "첫 번째", "1번으로 가줘", "그래").forEach {
            assertEquals(it, ProposalReply.ACCEPT, classifyProposalReply(it))
        }
    }

    @Test
    fun declineWords() {
        listOf("아니요", "아니야", "싫어", "그대로 가기", "괜찮아", "좋아 아니야").forEach {
            assertEquals(it, ProposalReply.DECLINE, classifyProposalReply(it))
        }
    }

    @Test
    fun otherWordsAreIgnored() {
        listOf("", "  ", "언제 도착해", "응가했어", "2번으로 가줘").forEach {
            assertEquals(it, ProposalReply.OTHER, classifyProposalReply(it))
        }
    }

    private class FakeClock : TimerClock {
        var now = 0L
        private val queue = mutableListOf<Pair<Long, Runnable>>()
        override fun postDelayed(task: Runnable, delayMs: Long) { queue += now + delayMs to task }
        override fun cancel(task: Runnable) { queue.removeAll { it.second === task } }
        fun advance(ms: Long) {
            now += ms
            queue.filter { it.first <= now }.also { queue.removeAll(it) }.forEach { it.second.run() }
        }
    }

    @Test
    fun closesAfterEightSecondsOfSilence() {
        val clock = FakeClock()
        val timer = AutoCloseTimer(clock)
        var closed = 0
        timer.start(PROPOSAL_IDLE_CLOSE_MS) { closed++ }
        clock.advance(7_999)
        assertEquals(0, closed)
        assertTrue(timer.pending)
        clock.advance(1)
        assertEquals(1, closed)
        assertFalse(timer.pending)
    }

    @Test
    fun answerCancelsClose() {
        val clock = FakeClock()
        val timer = AutoCloseTimer(clock)
        var closed = 0
        timer.start(PROPOSAL_IDLE_CLOSE_MS) { closed++ }
        clock.advance(5_000)
        timer.cancel()
        clock.advance(10_000)
        assertEquals(0, closed)
    }

    @Test
    fun restartResetsTheCount() {
        val clock = FakeClock()
        val timer = AutoCloseTimer(clock)
        var closed = 0
        timer.start(PROPOSAL_SAFETY_CLOSE_MS) { closed++ }
        clock.advance(6_000) // 제안 말이 끝나는 때 8초로 다시 센다
        timer.start(PROPOSAL_IDLE_CLOSE_MS) { closed++ }
        clock.advance(7_999)
        assertEquals(0, closed)
        clock.advance(1)
        assertEquals(1, closed)
    }
}
