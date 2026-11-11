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
        val putChildren = mutableListOf<String?>()
        var list = listOf(PresetState("feeding_stop", "수유 정차"), PresetState("boarding", "탑승 준비"), PresetState("nap", "낮잠"))
        override suspend fun list() = list
        override suspend fun markApplied(id: String) {
            failure?.let { throw it }
            applied += id
        }
        override suspend fun put(slot: String, childId: String?, settings: ClimateSettings): PresetState {
            failure?.let { throw it }
            puts += slot to settings
            putChildren += childId
            return PresetState(slot, "저장됨", childId = childId, settings = settings)
        }
    }

    private fun outboxFile() = JsonFile(File(tempDir(), "o.json"), PresetOutbox.serializer()) { PresetOutbox() }
    private fun sync(remote: FakeRemote, outbox: JsonFile<PresetOutbox> = outboxFile()) = PresetSync(remote, outbox)

    @Test
    fun 슬롯은_탑승_낮잠_수유정차_순서로_고른다() = runBlocking {
        val list = presetsForChild(sync(FakeRemote()).fetch(), null)
        assertEquals(listOf("boarding", "nap", "feeding_stop"), list.map { it.slot })
    }

    // 아이별 케어 모드 (QO5, #192)

    private val warm = ClimateSettings(name = "도윤", temperature = 22f)
    private val all = listOf(
        PresetState("boarding", childId = null, settings = ClimateSettings(name = "가족", temperature = 24f)),
        PresetState("nap", childId = "seoa"),
        PresetState("boarding", childId = "seoa", settings = ClimateSettings(name = "서아", temperature = 25f)),
        PresetState("feeding_stop", childId = "seoa"),
        PresetState("boarding", childId = "doyun", settings = warm),
    )

    @Test
    fun 고른_아이의_모드만_슬롯_순서로_고르고_없으면_가족_기본값() {
        assertEquals(listOf("boarding", "nap", "feeding_stop"), presetsForChild(all, "seoa").map { it.slot })
        assertEquals(25f, presetsForChild(all, "seoa").first().settings.temperature)
        assertEquals(warm, presetsForChild(all, "doyun").single().settings)
        // 서버에 아직 없는 아이·서버와 안 맞춘 아이(null)는 가족 기본값
        assertEquals("가족", presetsForChild(all, "new-child").single().settings.name)
        assertEquals("가족", presetsForChild(all, null).single().settings.name)
    }

    @Test
    fun 모드_값의_주인은_고른_아이의_서버_id다() {
        val kids = listOf(Child(2025, 9, "서아", serverId = "seoa"), Child(2023, 3, "도윤", serverId = "doyun"))
        assertEquals("seoa", presetChildId(kids, null))
        assertEquals("doyun", presetChildId(kids, "doyun"))
        assertEquals("seoa", presetChildId(kids, "gone"))  // 지워진 아이 → 첫째
        assertEquals(null, presetChildId(listOf(Child(2025, 9, "서아")), null))  // 서버와 안 맞춘 첫째 → 가족 기본값
        assertEquals(null, presetChildId(emptyList(), null))
    }

    @Test
    fun 저장한_값은_같은_아이_슬롯만_바꾼다() {
        val saved = PresetState("boarding", childId = "doyun", settings = ClimateSettings(name = "새 값"))
        val next = replacePreset(all, saved)
        assertEquals("새 값", presetsForChild(next, "doyun").single().settings.name)
        assertEquals("서아", presetsForChild(next, "seoa").first().settings.name)
        assertEquals("가족", presetsForChild(next, null).single().settings.name)
    }

    @Test
    fun 아이별로_고친_값은_그_아이로_올라가고_예전_대기열_키는_가족_기본값이다() = runBlocking {
        assertEquals("doyun" to "nap", parseEditKey(editKey("doyun", "nap")))
        assertEquals(null to "nap", parseEditKey(editKey(null, "nap")))
        val remote = FakeRemote()
        val outbox = outboxFile()
        // 이 기능 전 앱이 남긴 대기열: 키가 슬롯 이름뿐
        outbox.save(PresetOutbox(edits = mapOf("nap" to warm)))
        val s = sync(remote, outbox)
        s.saveEdit("boarding", "doyun", warm)
        assertEquals(listOf(null, "doyun"), remote.putChildren)
        assertEquals(listOf("nap", "boarding"), remote.puts.map { it.first })
        assertFalse(s.hasPending())
    }

    @Test
    fun 서버가_준_아이별_묶음_JSON을_읽는다() {
        val text = """[{"child_id":null,"nickname":null,"presets":[{"slot":"boarding","child_id":null}]},{"child_id":"c1","nickname":"서아","presets":[{"slot":"nap","child_id":"c1","settings":{"temperature":22.5}}]}]"""
        val groups = Json { ignoreUnknownKeys = true }.decodeFromString<List<ChildPresets>>(text)
        assertEquals(listOf(null, "c1"), groups.map { it.childId })
        assertEquals(22.5f, groups[1].presets.single().settings.temperature)
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
        assertTrue(runCatching { s.saveEdit("nap", null, settings) }.isFailure)
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
