package io.github.haeyeonyou.familytrip.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import retrofit2.HttpException

/** 서버 프리셋 API. 테스트에서는 가짜로 바꾼다. */
interface PresetRemote {
    suspend fun list(): List<PresetState>
    suspend fun markApplied(id: String)
    suspend fun put(slot: String, settings: ClimateSettings): PresetState
}

class TripApiPresetRemote(private val api: TripApi) : PresetRemote {
    override suspend fun list() = api.presets()

    override suspend fun markApplied(id: String) {
        val res = api.presetApplied(id)
        if (!res.isSuccessful) throw HttpException(res)
    }

    override suspend fun put(slot: String, settings: ClimateSettings) = api.putPreset(slot, settings, appliedOnCar = true)
}

/** 서버에 아직 못 알린 것: 차에서 적용한 프리셋 id, 차에서 고쳐 저장한 슬롯. */
@Serializable
data class PresetOutbox(
    val applied: List<String> = emptyList(),
    val edits: Map<String, ClimateSettings> = emptyMap(),
)

/** 슬롯 순서(탑승 → 낮잠 → 수유 정차)로 정렬한다. 모르는 슬롯은 뒤로. */
fun orderedPresets(list: List<PresetState>): List<PresetState> =
    list.sortedBy { PRESET_SLOTS.indexOf(it.slot).let { i -> if (i < 0) PRESET_SLOTS.size else i } }

/**
 * 공조 프리셋 3개를 서버와 맞춘다. 차에서 적용했다는 알림과 정차 중 고친 설정은 먼저 [outbox]에 적고 올린다.
 * 올리지 못하면 남아 있다가 다음 주기에 다시 올린다.
 */
class PresetSync(
    private val remote: PresetRemote,
    private val outbox: JsonFile<PresetOutbox>,
) {
    private val lock = Mutex()

    fun queueApplied(id: String) {
        val o = outbox.load()
        if (id !in o.applied) outbox.save(o.copy(applied = o.applied + id))
    }

    fun queueEdit(slot: String, settings: ClimateSettings) {
        val o = outbox.load()
        outbox.save(o.copy(edits = o.edits + (slot to settings)))
    }

    fun hasPending(): Boolean = outbox.load().let { it.applied.isNotEmpty() || it.edits.isNotEmpty() }

    /** 남은 알림을 올린다. 이미 바뀐 프리셋(404)이나 잘못된 요청은 버리고, 그 밖의 실패는 던져 다음 주기에 다시 한다. */
    suspend fun flush() = lock.withLock {
        for (id in outbox.load().applied) {
            try {
                remote.markApplied(id)
            } catch (e: HttpException) {
                if (e.code() != 404 && !isPermanentFailure(e.code())) throw e
            }
            outbox.load().let { outbox.save(it.copy(applied = it.applied - id)) }
        }
        for ((slot, settings) in outbox.load().edits) {
            try {
                remote.put(slot, settings)
            } catch (e: HttpException) {
                if (!isPermanentFailure(e.code())) throw e
            }
            outbox.load().let { outbox.save(it.copy(edits = it.edits - slot)) }
        }
    }

    /** 3개 슬롯의 현재 상태를 받는다. 못 알린 것이 있으면 먼저 올린다 (그래야 방금 적용한 것이 대기로 되돌아와 보이지 않는다). */
    suspend fun fetch(): List<PresetState> {
        flush()
        return orderedPresets(remote.list())
    }

    /** 정차 중 고친 설정을 슬롯에 저장한다. 실패하면 대기열에 남겨 두고 예외를 던진다. */
    suspend fun saveEdit(slot: String, settings: ClimateSettings): PresetState {
        queueEdit(slot, settings)
        flush()
        return orderedPresets(remote.list()).first { it.slot == slot }
    }

    fun forget() = outbox.clear()
}
