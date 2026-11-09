package io.github.haeyeonyou.familytrip.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.util.UUID

/** 서버에 올라가 있다고 아는 방문 한 건의 상태. 서버에서 마지막으로 본 값이다. */
@Serializable
data class KnownVisit(
    val status: String = STATUS_CONFIRMED,
    val mood: String? = null,
    val stayedMinutes: Int? = null,
)

const val STATUS_CONFIRMED = "confirmed"

/** 서버가 받는 분야 값 (app/schemas.py Theme). 모르는 값을 보내면 요청 전체가 거절되어 걸러 낸다. */
private val SERVER_THEMES = setOf("nature", "experience", "exhibition", "play", "pet")
private const val MAX_BULK = 200

/** 차의 방문 [Visit]과 서버 방문 사이의 변환. */
object VisitMapper {
    /** 차에서 만든 방문의 서버 id. 같은 기록은 몇 번을 올려도 같은 id라 서버에 하나만 남는다. */
    fun uuid(localId: Long): String = UUID.nameUUIDFromBytes("visit:$localId".toByteArray()).toString()

    fun idOf(v: Visit): String = v.serverId ?: uuid(v.id)

    /** 서버에 올리는 것은 경험 기록이 되는 나들이 방문뿐이다 (식사·카페는 올리지 않는다). */
    fun syncs(v: Visit): Boolean = v.role == "outing"

    fun stayOf(v: Visit): Int? = v.stayMinutes()?.toInt()?.coerceIn(0, 1440)

    /** 좌표·주소·시각은 담지 않는다. 날짜(도착한 날)와 머문 분만 보낸다. */
    fun toServer(v: Visit, known: KnownVisit?): VisitBulkItem = VisitBulkItem(
        id = idOf(v),
        placeKey = v.placeKey.orEmpty().take(80),
        placeName = v.placeName.trim().take(60).ifEmpty { "이름 없는 장소" },
        theme = v.theme?.takeIf { it in SERVER_THEMES },
        visitedOn = v.date.toString(),
        stayedMinutes = stayOf(v),
        // 웹에서 확인·기분을 바꿨다면 그 값을 지키고 올린다 (PUT은 상태·기분도 덮어쓴다).
        status = known?.status ?: STATUS_CONFIRMED,
        mood = known?.mood,
    )

    /** 웹 등 다른 곳에서 확정된 방문을 차에 들인다. 서버에는 시각이 없어 그날 정오로 둔다. */
    fun fromServer(s: VisitOut, localId: Long): Visit {
        val arrived = java.time.LocalDate.parse(s.visitedOn).atTime(12, 0)
        return Visit(
            id = localId,
            placeName = s.placeName,
            role = "outing",
            theme = s.theme,
            placeKey = s.placeKey.ifBlank { null },
            arrivedAt = arrived.toString(),
            departedAt = s.stayedMinutes?.let { arrived.plusMinutes(it.toLong()).toString() },
            serverId = s.id,
        )
    }
}

/** 한 번 맞춘 결과. 차 기록에서 뺄 것, 들일 것, 서버에 올릴 것, 그리고 새로 기억할 서버 상태. */
data class VisitPlan(
    val removeLocal: Set<Long>,
    val addLocal: List<Visit>,
    val upload: List<Visit>,
    val known: Map<String, KnownVisit>,
)

/**
 * 차 기록 [local]과 서버 목록 [server]을 맞추는 규칙 (순수 함수).
 *
 * - 차와 서버에 모두 있다: 서버가 확정이 아니면(안 갔어요·확인 대기) 차 목록에서 뺀다. 머문 시간이 서버와 다르면 다시 올린다.
 * - 차에만 있다: 전에 서버에 올라간 적이 있다([known])면 **웹에서 지운 것**이라 차에서도 지운다 — 되살려 올리지 않는다.
 *   올라간 적이 없으면 올린다.
 * - 서버에만 있다: 확정이면 차에 들인다. 차 기록 상한 때문에 밀려난 옛 기록(이미 [known])은 다시 들이지 않는다.
 * - [serverComplete]가 false(목록이 상한만큼 와서 일부가 잘렸을 수 있음)면 "서버에 없다"는 판단을 하지 않는다.
 */
fun planVisits(
    local: List<Visit>,
    server: List<VisitOut>,
    known: Map<String, KnownVisit>,
    serverComplete: Boolean,
    newLocalId: () -> Long,
): VisitPlan {
    val serverById = server.associateBy { it.id.lowercase() }
    val nextKnown = known.toMutableMap()
    val removeLocal = mutableSetOf<Long>()
    val upload = mutableListOf<Visit>()
    val localUuids = mutableSetOf<String>()

    for (v in local.filter(VisitMapper::syncs)) {
        val uuid = VisitMapper.idOf(v).lowercase()
        localUuids += uuid
        val s = serverById[uuid]
        when {
            s != null -> {
                nextKnown[uuid] = KnownVisit(s.status, s.mood, s.stayedMinutes)
                if (s.status != STATUS_CONFIRMED) {
                    removeLocal += v.id
                } else {
                    val stay = VisitMapper.stayOf(v)
                    if (stay != null && stay != s.stayedMinutes) upload += v
                }
            }
            uuid in known -> if (serverComplete) {
                removeLocal += v.id
                nextKnown -= uuid
            }
            else -> upload += v
        }
    }

    val addLocal = mutableListOf<Visit>()
    for (s in server) {
        val uuid = s.id.lowercase()
        if (uuid in localUuids) continue
        val before = known[uuid]
        nextKnown[uuid] = KnownVisit(s.status, s.mood, s.stayedMinutes)
        // 차에 없는 확정 방문: 처음 보는 것(웹·다른 차)이거나, 확정이 아니었다가 확정된 것만 들인다.
        if (s.status == STATUS_CONFIRMED && (before == null || before.status != STATUS_CONFIRMED)) {
            addLocal += VisitMapper.fromServer(s, newLocalId())
        }
    }
    // 서버에서 사라진 것은 잊는다 (웹에서 지운 기록의 흔적이 남아 새 기록과 섞이지 않게).
    if (serverComplete) nextKnown.keys.retainAll(serverById.keys)
    return VisitPlan(removeLocal, addLocal, upload, nextKnown)
}

/** 서버 방문 API. 테스트에서는 가짜로 바꾼다. */
interface VisitRemote {
    suspend fun list(): List<VisitOut>
    suspend fun put(id: String, body: VisitIn)
    suspend fun bulk(items: List<VisitBulkItem>)
}

class TripApiVisitRemote(private val api: TripApi) : VisitRemote {
    override suspend fun list() = api.visits()
    override suspend fun put(id: String, body: VisitIn) {
        api.putVisit(id, body)
    }

    override suspend fun bulk(items: List<VisitBulkItem>) {
        val res = api.bulkVisits(VisitBulk(items))
        if (!res.isSuccessful) throw retrofit2.HttpException(res)
    }
}

/**
 * 방문 기록을 서버와 맞춘다. 모든 맞춤은 [lock] 안에서 차례로 하므로, 올리는 도중에 받은 목록으로
 * "웹에서 지웠다"고 잘못 판단하는 일이 없다. 실패하면 상태를 남기지 않아 다음 주기에 같은 일이 다시 잡힌다.
 */
class VisitSync(
    private val store: VisitStore,
    private val remote: VisitRemote,
    private val state: JsonFile<Map<String, KnownVisit>>,
    private val newLocalId: () -> Long = { System.currentTimeMillis() + (counter++ % 1000) },
) {
    private val lock = Mutex()

    /** 서버에 올릴 것이 남았는지 (네트워크 없이 확인). 실패한 업로드를 다음 주기에 다시 잡는 데 쓴다. */
    fun hasPendingUpload(): Boolean = pending(store.all(), state.load()).isNotEmpty()

    /** 서버 목록을 받아 맞추고 올릴 것을 올린다. 실패하면 예외를 던진다 (호출한 쪽이 다음 주기에 다시 부른다). */
    suspend fun reconcile() = lock.withLock {
        val server = remote.list()
        val plan = planVisits(store.all(), server, state.load(), serverComplete = server.size < VISIT_LIST_LIMIT, newLocalId = newLocalId)
        if (plan.removeLocal.isNotEmpty() || plan.addLocal.isNotEmpty()) {
            store.update { cur ->
                (cur.filterNot { it.id in plan.removeLocal } + plan.addLocal).sortedBy { it.arrivedAt }
            }
        }
        state.save(plan.known)
        upload(plan.upload, plan.known)
    }

    private suspend fun upload(items: List<Visit>, known: Map<String, KnownVisit>) {
        if (items.isEmpty()) return
        val sent = known.toMutableMap()
        try {
            if (items.size == 1) {
                val item = VisitMapper.toServer(items.single(), known[VisitMapper.idOf(items.single()).lowercase()])
                remote.put(item.id, item.toIn())
                sent[item.id.lowercase()] = item.toKnown()
            } else {
                // 로그인 직후처럼 많이 쌓였을 때는 한꺼번에 (최대 200건씩)
                items.chunked(MAX_BULK).forEach { chunk ->
                    val bulk = chunk.map { VisitMapper.toServer(it, known[VisitMapper.idOf(it).lowercase()]) }
                    remote.bulk(bulk)
                    bulk.forEach { sent[it.id.lowercase()] = it.toKnown() }
                    state.save(sent)  // 앞 묶음이 성공했으면 기억해 둔다 (뒤 묶음이 실패해도 다시 올리지 않는다)
                }
            }
        } finally {
            state.save(sent)
        }
    }

    /** 계정이 바뀌었을 때 서버 쪽 기억을 지운다. */
    fun forget() = state.clear()

    private fun pending(local: List<Visit>, known: Map<String, KnownVisit>): List<Visit> =
        local.filter(VisitMapper::syncs).filter { v ->
            val k = known[VisitMapper.idOf(v).lowercase()]
            k == null || (VisitMapper.stayOf(v)?.let { it != k.stayedMinutes } == true && k.status == STATUS_CONFIRMED)
        }

    private companion object {
        var counter = 0L
    }
}

private fun VisitBulkItem.toIn() = VisitIn(placeKey, placeName, theme, visitedOn, stayedMinutes, status, mood)
private fun VisitBulkItem.toKnown() = KnownVisit(status, mood, stayedMinutes)

val knownVisitsSerializer = MapSerializer(String.serializer(), KnownVisit.serializer())
