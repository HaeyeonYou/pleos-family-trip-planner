package io.github.haeyeonyou.familytrip.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import retrofit2.HttpException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** 아직 서버에 올리지 못한 수유 기록. 로그인 전·오프라인에서 한 기록도 여기 쌓였다가 올라간다. */
@Serializable
data class PendingFeeding(
    val id: String,
    val fedAtMs: Long,
    /** formula | breast | solid */
    val kind: String,
    /** 기록할 때 로그인해 있던 계정. 로그인 전이면 null (나중에 로그인한 계정에 올린다). */
    val owner: String? = null,
)

@Serializable
data class FeedingQueueData(
    val pending: List<PendingFeeding> = emptyList(),
    /** 이 기능이 생기기 전에 기기에만 있던 마지막 수유를 한 번 대기열에 옮겼는지 */
    val legacyMoved: Boolean = false,
)

/** 서버 수유 API. 테스트에서는 가짜로 바꾼다. */
interface FeedingRemote {
    suspend fun put(id: String, body: FeedingIn)
    suspend fun status(): FeedingStatus
}

class TripApiFeedingRemote(private val api: TripApi) : FeedingRemote {
    override suspend fun put(id: String, body: FeedingIn) {
        val res = api.putFeeding(id, body)
        if (!res.isSuccessful) throw HttpException(res)
    }

    override suspend fun status() = api.feedingStatus()
}

/** 마지막 수유 시각과 간격을 담고 꺼내는 곳 ([FeedingStore]). */
interface FeedingRoutineStore {
    fun load(): FeedingRoutine
    fun save(routine: FeedingRoutine)
}

/** 아이의 수유 방식(feeding_type)에서 기록 종류(kind)를 정한다. 혼합이나 모르면 분유. */
fun feedingKindOf(feedingType: String?): String = when (feedingType) {
    "breast" -> "breast"
    "solid" -> "solid"
    else -> "formula"
}

private val ISO_OFFSET: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

/** 서버는 30일 넘은 수유 시각을 거절한다. 그보다 넉넉히 앞서 대기열에서 버린다. */
private const val MAX_AGE_DAYS = 29L
private const val MAX_QUEUE = 100

/** 서버 응답 코드 중 다시 보내도 소용없는 것(요청 자체가 잘못됨). 401·408·429는 제외. */
internal fun isPermanentFailure(code: Int) = code in 400..499 && code != 401 && code != 408 && code != 429

/**
 * 수유 기록을 서버와 맞춘다.
 *
 * - 기록하면 먼저 차에 저장하고, 대기열에 넣어 서버로 올린다. 올리지 못하면 대기열에 남아 다음 주기에 다시 올린다.
 * - 같은 수유 시각은 같은 id([feedingId])라 여러 번 올려도 서버에 하나만 남는다.
 * - 서버에서 받을 때는 서버의 마지막 수유와 아직 못 올린 기록 중 더 늦은 시각을 쓴다.
 */
class FeedingSync(
    private val remote: FeedingRemote,
    private val queue: JsonFile<FeedingQueueData>,
    private val routine: FeedingRoutineStore,
    /** 기록할 때 쓸 수유 종류 (아이 정보의 수유 방식에서) */
    private val kind: () -> String,
    /** 지금 로그인한 계정 (없으면 null) */
    private val owner: () -> String?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val lock = Mutex()

    /** 방금 수유한 것을 기록한다 (차에 저장 + 대기열). 서버로 올리는 것은 [flush]가 한다. */
    fun record(fedAtMs: Long = now()) {
        val q = queue.load()
        val item = PendingFeeding(feedingId(fedAtMs), fedAtMs, kind(), owner())
        if (q.pending.none { it.id == item.id }) {
            queue.save(q.copy(pending = (q.pending + item).takeLast(MAX_QUEUE)))
        }
    }

    fun hasPending(): Boolean = queue.load().pending.isNotEmpty()

    /**
     * 대기열을 서버로 올린다. 401은 그대로 던진다. 네트워크·서버 오류가 나면 거기서 멈추고 예외를 던져
     * 나머지는 다음 주기에 다시 올린다. 서버가 거절한 기록(잘못된 요청)은 버린다.
     */
    suspend fun flush() = lock.withLock {
        val me = owner()
        migrateLegacy()
        for (item in queue.load().pending) {
            val expired = now() - item.fedAtMs > MAX_AGE_DAYS * 24 * 3_600_000L
            // 다른 계정에서 기록한 것은 이 계정에 올리지 않는다.
            if (expired || (me != null && item.owner != null && item.owner != me)) {
                drop(item.id)
                continue
            }
            if (me == null) return@withLock  // 로그인 전에는 쌓아 두기만 한다
            try {
                remote.put(item.id, FeedingIn(isoOf(item.fedAtMs), item.kind, null, "car"))
                drop(item.id)
            } catch (e: HttpException) {
                if (!isPermanentFailure(e.code())) throw e
                drop(item.id)
            }
        }
    }

    /**
     * 서버의 마지막 수유를 받아 차의 수유 루틴에 반영한다. 아직 못 올린 기록이 더 늦으면 그것을 쓴다.
     * 간격은 가족 정보([FamilySync])가 맞추므로 여기서는 건드리지 않는다.
     */
    suspend fun pull() = lock.withLock {
        val status = remote.status()
        val serverLast = status.lastFedAt?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() }
        val me = owner()
        val pendingLast = queue.load().pending.filter { it.owner == null || it.owner == me }.maxOfOrNull { it.fedAtMs }
        val last = listOfNotNull(serverLast, pendingLast).maxOrNull()
        val current = routine.load()
        if (current.lastFedAt != last) routine.save(current.copy(lastFedAt = last))
    }

    /** 기록이 생기기 전에 기기에만 있던 마지막 수유를 한 번 대기열로 옮긴다 (로그인할 때 서버에 올라가게). */
    private fun migrateLegacy() {
        val q = queue.load()
        if (q.legacyMoved) return
        val last = routine.load().lastFedAt
        val moved = if (last != null && now() - last <= MAX_AGE_DAYS * 24 * 3_600_000L && q.pending.none { it.fedAtMs == last }) {
            q.pending + PendingFeeding(feedingId(last), last, kind(), owner())
        } else {
            q.pending
        }
        queue.save(q.copy(pending = moved, legacyMoved = true))
    }

    private fun drop(id: String) {
        val q = queue.load()
        queue.save(q.copy(pending = q.pending.filterNot { it.id == id }))
    }

    /** 계정이 바뀌면 다른 계정에서 기록한 대기 기록을 정리한다 (로그인 전 기록은 남긴다). */
    fun onAccountChanged(newOwner: String?) {
        val q = queue.load()
        queue.save(q.copy(pending = q.pending.filter { it.owner == null || it.owner == newOwner }))
    }

    companion object {
        /** 수유 시각에서 만든 안정적인 id. 같은 기록을 다시 올려도 서버에 중복되지 않는다. */
        fun feedingId(fedAtMs: Long): String = UUID.nameUUIDFromBytes("feeding:$fedAtMs".toByteArray()).toString()

        fun isoOf(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toOffsetDateTime().withNano(0).format(ISO_OFFSET)
    }
}
