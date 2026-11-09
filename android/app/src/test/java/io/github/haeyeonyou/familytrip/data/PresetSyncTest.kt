package io.github.haeyeonyou.familytrip.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class PresetSyncTest {
    private class FakeRemote : PresetRemote {
        var failure: Exception? = null
        val applied = mutableListOf<String>()
        val puts = mutableListOf<Pair<String, ClimateSettings>>()
        var list = listOf(PresetState("feeding_stop", "수유 정차"), PresetState("boarding", "탑승 준비"), PresetState("nap", "낮잠"))
        override suspend fun list() = list
        override suspend fun markApplied(id: String) {
            failure?.let { throw it }
            applied += id
        }
        override suspend fun put(slot: String, settings: ClimateSettings): PresetState {
            failure?.let { throw it }
            puts += slot to settings
            return PresetState(slot, "저장됨", settings = settings)
        }
    }

    private fun sync(remote: FakeRemote) = PresetSync(remote, JsonFile(File(tempDir(), "o.json"), PresetOutbox.serializer()) { PresetOutbox() })

    @Test
    fun 슬롯은_탑승_낮잠_수유정차_순서로_받는다() = runBlocking {
        val list = sync(FakeRemote()).fetch()
        assertEquals(listOf("boarding", "nap", "feeding_stop"), list.map { it.slot })
    }

    @Test
    fun 적용했다는_알림은_실패하면_남았다가_다음에_올라간다() = runBlocking {
        val remote = FakeRemote()
        val s = sync(remote)
        s.queueApplied("p1")
        remote.failure = IOException("오프라인")
        assertTrue(runCatching { s.flush() }.isFailure)
        assertTrue(s.hasPending())
        remote.failure = null
        s.flush()
        assertEquals(listOf("p1"), remote.applied)
        assertFalse(s.hasPending())
    }

    @Test
    fun 웹에서_다시_보내_없어진_프리셋_id는_버린다() = runBlocking {
        val remote = FakeRemote()
        val s = sync(remote)
        s.queueApplied("old")
        remote.failure = httpError(404)
        s.flush()
        assertFalse(s.hasPending())
    }

    @Test
    fun 정차_중_고친_설정을_저장하고_실패하면_대기열에_남긴다() = runBlocking {
        val remote = FakeRemote()
        val s = sync(remote)
        val settings = ClimateSettings(name = "낮잠", temperature = 22.5f, fanSpeed = 1, mediaVolume = 20)
        remote.failure = IOException("오프라인")
        assertTrue(runCatching { s.saveEdit("nap", settings) }.isFailure)
        assertTrue(s.hasPending())
        remote.failure = null
        s.flush()
        assertEquals("nap" to settings, remote.puts.single())
        assertFalse(s.hasPending())
    }

    @Test
    fun 가져오기_전에_남은_알림을_먼저_올린다() = runBlocking {
        val remote = FakeRemote()
        val s = sync(remote)
        s.queueApplied("p9")
        s.fetch()
        assertEquals(listOf("p9"), remote.applied)
    }

    @Test
    fun 서버에_보내는_설정에는_음량과_기본값도_항상_실린다() {
        val json = Json.encodeToString(ClimateSettings(name = "낮잠", mediaVolume = 20))
        assertTrue(json, json.contains("\"media_volume\":20"))
        assertTrue(json, json.contains("\"fan_speed\":3"))
        assertTrue(json, json.contains("\"power\":true"))
        assertTrue(Json.encodeToString(ClimateSettings()).contains("\"media_volume\":null"))
    }

    @Test
    fun 서버가_준_슬롯_JSON을_읽는다_모르는_필드는_무시한다() {
        val text = """{"slot":"nap","label":"낮잠","when":"수면 모드와 함께","sent":true,"id":"x","settings":{"name":"낮잠","temperature":23.0,"fan_speed":1,"media_volume":20,"extra":1},"sent_at":"2026-11-08T12:00:00+09:00","applied_at":null,"pending":true}"""
        val p = Json { ignoreUnknownKeys = true }.decodeFromString<PresetState>(text)
        assertEquals("수면 모드와 함께", p.whenLabel)
        assertEquals(20, p.settings.mediaVolume)
        assertTrue(p.pending)
    }
}
