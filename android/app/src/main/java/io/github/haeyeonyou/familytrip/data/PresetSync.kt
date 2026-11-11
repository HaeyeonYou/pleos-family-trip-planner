package io.github.haeyeonyou.familytrip.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import retrofit2.HttpException

/** 서버 프리셋 API. 테스트에서는 가짜로 바꾼다. */
interface PresetRemote {
    /** 가족 기본값과 아이마다의 슬롯을 한 목록으로 (각 항목의 [PresetState.childId]로 구분). */
    suspend fun list(): List<PresetState>
    suspend fun markApplied(id: String)
    suspend fun put(slot: String, childId: String?, settings: ClimateSettings): PresetState
}

class TripApiPresetRemote(private val api: TripApi) : PresetRemote {
    /** `GET /presets/children`을 펼친다. 이 API가 없는 예전 서버(404)는 가족 공통 3개(`GET /presets`)를 쓴다. */
    override suspend fun list(): List<PresetState> = try {
        api.childPresets().flatMap { group -> group.presets.map { it.copy(childId = group.childId).withApplyAt() } }
    } catch (e: HttpException) {
        if (e.code() != 404) throw e
        api.presets().map { it.withApplyAt() }
    }

    override suspend fun markApplied(id: String) {
        val res = api.presetApplied(id)
        if (!res.isSuccessful) throw HttpException(res)
    }

    override suspend fun put(slot: String, childId: String?, settings: ClimateSettings) =
        api.putPreset(slot, settings, appliedOnCar = true, childId = childId).withApplyAt()
}

/** 서버가 따로 주는 적용 시점을 설정 묶음에도 넣는다 (편집 시트의 바뀐 값 비교·저장 대기열이 한 묶음으로 다루게, #168). */
fun PresetState.withApplyAt(): PresetState = if (applyAt == null) this else copy(settings = settings.copy(applyAt = applyAt))

/**
 * 서버에 아직 못 알린 것: 차에서 적용한 프리셋 id, 차에서 고쳐 저장한 슬롯.
 * [edits]의 키는 [editKey] ("nap"은 가족 기본값, "<아이 id>/nap"은 그 아이). 예전 대기열의 키("nap")도 그대로 읽힌다.
 */
@Serializable
data class PresetOutbox(
    val applied: List<String> = emptyList(),
    val edits: Map<String, ClimateSettings> = emptyMap(),
)

fun editKey(childId: String?, slot: String): String = if (childId == null) slot else "$childId/$slot"

/** [editKey]를 (아이 id, 슬롯)으로. */
fun parseEditKey(key: String): Pair<String?, String> =
    key.lastIndexOf('/').let { i -> if (i < 0) null to key else key.substring(0, i) to key.substring(i + 1) }

/** 슬롯 순서(탑승 → 낮잠 → 수유 정차)로 정렬한다. 모르는 슬롯은 뒤로. */
fun orderedPresets(list: List<PresetState>): List<PresetState> =
    list.sortedBy { PRESET_SLOTS.indexOf(it.slot).let { i -> if (i < 0) PRESET_SLOTS.size else i } }

/**
 * [childId] 아이의 모드 3개 (QO5: 케어 모드 값은 아이별). 서버에 그 아이 값이 없으면(예전 서버, 서버와 아직 안 맞춘 아이)
 * 가족 기본값을 쓴다.
 */
fun presetsForChild(all: List<PresetState>, childId: String?): List<PresetState> {
    val own = if (childId == null) emptyList() else all.filter { it.childId == childId }
    return orderedPresets(own.ifEmpty { all.filter { it.childId == null } })
}

/**
 * 케어 대상 아이(아이 선택 키 [selected]: null = 첫째, 그 밖에는 서버 아이 id)의 서버 id. 모드 값의 주인이다.
 * 고른 아이가 목록에 없으면 첫째 (`care.selectedChildIndex`와 같은 규칙). 서버와 아직 안 맞춘 아이면 null.
 */
fun presetChildId(children: List<Child>, selected: String?): String? =
    selected?.takeIf { s -> children.drop(1).any { it.serverId == s } } ?: children.firstOrNull()?.serverId

/** [saved]로 같은 아이·슬롯 항목을 바꾼다. */
fun replacePreset(all: List<PresetState>, saved: PresetState): List<PresetState> =
    if (all.any { it.childId == saved.childId && it.slot == saved.slot }) {
        all.map { if (it.childId == saved.childId && it.slot == saved.slot) saved else it }
    } else {
        all + saved
    }

/**
 * 공조 프리셋(가족 기본값·아이별 3개)을 서버와 맞춘다. 차에서 적용했다는 알림과 정차 중 고친 설정은 먼저 [outbox]에 적고 올린다.
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

    fun queueEdit(slot: String, childId: String?, settings: ClimateSettings) {
        val o = outbox.load()
        outbox.save(o.copy(edits = o.edits + (editKey(childId, slot) to settings)))
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
        for ((key, settings) in outbox.load().edits) {
            val (childId, slot) = parseEditKey(key)
            try {
                remote.put(slot, childId, settings)
            } catch (e: HttpException) {
                // 지워진 아이(404)나 잘못된 요청은 버린다
                if (!isPermanentFailure(e.code())) throw e
            }
            outbox.load().let { outbox.save(it.copy(edits = it.edits - key)) }
        }
    }

    /** 가족 기본값과 아이별 슬롯의 현재 상태를 받는다. 못 알린 것이 있으면 먼저 올린다 (그래야 방금 적용한 것이 대기로 되돌아와 보이지 않는다). */
    suspend fun fetch(): List<PresetState> {
        flush()
        return remote.list()
    }

    /** 정차 중 고친 설정을 그 아이의 슬롯에 저장한다. 실패하면 대기열에 남겨 두고 예외를 던진다. 저장 뒤 전체 목록을 돌려준다. */
    suspend fun saveEdit(slot: String, childId: String?, settings: ClimateSettings): List<PresetState> {
        queueEdit(slot, childId, settings)
        flush()
        return remote.list()
    }

    fun forget() = outbox.clear()
}
