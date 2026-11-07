package io.github.haeyeonyou.familytrip.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceIntentTest {
    private fun situation(text: String) = (parseUtterance(text) as? Utterance.Situation)?.situation

    @Test
    fun sleep() {
        listOf("애 잔다", "아기 재웠어", "잠들었어", "애가 잠들었어요", "아기 자요").forEach {
            assertEquals(it, CareSituation.SLEEP, situation(it))
        }
    }

    @Test
    fun diaper() {
        listOf("애 쌌다", "똥 쌌어", "기저귀 갈아야 해", "아기 응가했어").forEach {
            assertEquals(it, CareSituation.DIAPER, situation(it))
        }
    }

    @Test
    fun feed() {
        listOf("애 배고프다", "아기 밥 줘야 해", "수유해야 돼", "분유 먹일 곳").forEach {
            assertEquals(it, CareSituation.FEED, situation(it))
        }
    }

    @Test
    fun soothe() {
        listOf("애 운다", "아기가 보챈다", "칭얼대요", "애가 울어요").forEach {
            assertEquals(it, CareSituation.SOOTHE, situation(it))
        }
    }

    @Test
    fun diaperComesBeforeFeedWhenBothAreSaid() {
        assertEquals(CareSituation.DIAPER, situation("애 쌌고 배고파해"))
    }

    @Test
    fun choose() {
        assertEquals(Utterance.Choose(1), parseUtterance("1번으로 가줘"))
        assertEquals(Utterance.Choose(2), parseUtterance("2번으로 가줘"))
        assertEquals(Utterance.Choose(1), parseUtterance("일번 가자"))
        assertEquals(Utterance.Choose(2), parseUtterance("두 번째로 가줘"))
    }

    @Test
    fun cancelAndKeepGoing() {
        assertEquals(Utterance.Cancel, parseUtterance("아니야"))
        assertEquals(Utterance.Cancel, parseUtterance("아니야 그게 아니야"))
        assertEquals(Utterance.KeepGoing, parseUtterance("그대로 가기"))
    }

    @Test
    fun unknown() {
        assertEquals(Utterance.Unknown, parseUtterance(""))
        assertEquals(Utterance.Unknown, parseUtterance("오늘 날씨 어때"))
    }
}
