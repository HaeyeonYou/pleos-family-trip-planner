package io.github.haeyeonyou.familytrip.data

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import retrofit2.HttpException
import java.util.UUID

/** 서버 가족 정보 API. 테스트에서는 가짜로 바꾼다. */
interface FamilyRemote {
    suspend fun family(): ServerFamily
    suspend fun putChild(id: String, body: ChildIn): ServerChild
    suspend fun deleteChild(id: String)
}

class TripApiFamilyRemote(private val api: TripApi) : FamilyRemote {
    override suspend fun family() = api.family()
    override suspend fun putChild(id: String, body: ChildIn) = api.putChild(id, body)
    override suspend fun deleteChild(id: String) {
        val res = api.deleteChild(id)
        if (!res.isSuccessful && res.code() != 404) throw HttpException(res)
    }
}

/** 차의 가족 정보를 담고 꺼내는 곳 ([ProfileStore]). */
interface ProfileRepo {
    fun load(): FamilyProfile?
    fun save(profile: FamilyProfile)
}

/** 한 번 맞춘 계획. 아이에게 서버 id를 붙인 차 프로필, 올릴 아이, 지울 아이. */
data class FamilyPlan(
    val local: FamilyProfile?,
    val puts: List<Pair<String, ChildIn>>,
    val deletes: List<String>,
)

const val SERVER_MAX_CHILDREN = 6
private const val NICKNAME_MAX = 30
private const val MIN_INTERVAL = 60
private const val MAX_INTERVAL = 360

fun Child.birthMonthKey(): String = "%04d-%02d".format(birthYear, birthMonth)

/** 서버 `birth_date` (YYYY-MM-DD). 일을 모르거나 없는 날이면 null. */
fun Child.birthDateKey(): String? = birthDate()?.toString()

/** 서버 아이를 그대로 다시 올릴 몸통. 차가 모르는 항목(성향·멀미 등)도 지운다고 보내지 않게 모두 옮긴다. */
fun ServerChild.toChildIn() = ChildIn(
    nickname = nickname,
    birthMonth = birthMonth,
    feedingType = feedingType,
    feedIntervalMinutes = feedIntervalMinutes,
    napCount = napCount,
    birthDate = birthDate,
    rearFacingSeat = rearFacingSeat,
    strollerUse = strollerUse,
    napTimes = napTimes,
    temperament = temperament,
    napInCar = napInCar,
    heatSensitivity = heatSensitivity,
    motionSickness = motionSickness,
    noiseSensitivity = noiseSensitivity,
)

/**
 * 차의 가족 정보 [local]을 서버 아이 목록 [server]과 맞출 계획 (순수 함수).
 *
 * 항목 대응 (docs/research/web-server-contract.md 6절 4번):
 * - 차 `name` ↔ 서버 `nickname`, 차 생년월 ↔ 서버 `birth_month`, 차 수유 간격(첫째 아이) ↔ `feed_interval_minutes`
 * - 서버에만 있는 수유 방식·낮잠 횟수는 차가 건드리지 않고 서버 값을 그대로 지킨다.
 * - 성인 수·반려견은 서버에 대응 항목이 없어 기기에만 둔다.
 *
 * 기준: 로그인 상태에서는 **서버가 기준**이다. 차에서 고친 것([snapshot](마지막으로 맞춘 서버 상태)과 다른 항목)만
 * 서버에 올리고, 나머지는 서버 값을 받는다. 고치지 않은 항목은 웹에서 바뀐 값이 이긴다.
 */
fun planFamily(
    local: FamilyProfile?,
    localInterval: Int,
    snapshot: List<ServerChild>,
    server: List<ServerChild>,
    newId: () -> String = { UUID.randomUUID().toString() },
): FamilyPlan {
    // 차 프로필이 아직 없으면 올릴 것도 지울 것도 없다 (서버에서 받기만 한다).
    if (local == null) return FamilyPlan(null, emptyList(), emptyList())

    val serverById = server.associateBy { it.id }
    val snapshotById = snapshot.associateBy { it.id }
    val claimed = local.children.mapNotNull { it.serverId }.toMutableSet()
    val puts = mutableListOf<Pair<String, ChildIn>>()
    var serverCount = server.size

    val children = local.children.mapIndexed { index, child ->
        val id = child.serverId ?: run {
            // 서버에 같은 달에 태어난 아이가 이미 있으면 같은 아이로 보고 이어 붙인다 (웹에서 먼저 등록한 경우).
            val twin = server.filter { it.id !in claimed && it.birthMonth == child.birthMonthKey() }
                .let { c -> c.firstOrNull { it.nickname == child.name } ?: c.firstOrNull() }
            (twin?.id ?: newId()).also { claimed += it }
        }
        val srv = serverById[id]
        val snap = snapshotById[id]
        val first = index == 0
        when {
            srv != null && snap != null -> {
                val nameEdited = child.name.isNotBlank() && child.name != snap.nickname
                val monthEdited = child.birthMonthKey() != snap.birthMonth
                val dayEdited = child.birthDay != null && child.birthDateKey() != snap.birthDate
                val intervalEdited = first && localInterval != (snap.feedIntervalMinutes ?: FeedingRoutine.DEFAULT_INTERVAL_MINUTES)
                // 온보딩 항목: 차 값이 null이면 '모름'이라 고친 것으로 보지 않는다
                val strollerEdited = child.stroller != null && child.stroller != snap.strollerUse
                val seatEdited = child.rearFacingSeat != null && child.rearFacingSeat != snap.rearFacingSeat
                val napEdited = child.napTimes != null && child.napTimes != snap.napTimes
                if (nameEdited || monthEdited || dayEdited || intervalEdited || strollerEdited || seatEdited || napEdited) {
                    val birthEdited = monthEdited || dayEdited
                    puts += id to srv.toChildIn().copy(
                        nickname = if (nameEdited) child.name.take(NICKNAME_MAX) else srv.nickname,
                        birthMonth = if (birthEdited) child.birthMonthKey() else srv.birthMonth,
                        // 달만 고치면 서버가 그 달과 안 맞는 일을 버린다. 일을 알면 일까지 보낸다.
                        birthDate = if (birthEdited) child.birthDateKey() else srv.birthDate,
                        feedIntervalMinutes = if (intervalEdited) localInterval.coerceIn(MIN_INTERVAL, MAX_INTERVAL) else srv.feedIntervalMinutes,
                        strollerUse = if (strollerEdited) child.stroller else srv.strollerUse,
                        rearFacingSeat = if (seatEdited) child.rearFacingSeat else srv.rearFacingSeat,
                        napTimes = if (napEdited) child.napTimes else srv.napTimes,
                    )
                }
            }
            // 서버에 아직 없는 새 아이: 올린다 (서버는 6명까지).
            srv == null && snap == null -> if (serverCount < SERVER_MAX_CHILDREN) {
                serverCount++
                val interval = localInterval.takeIf { first && it != FeedingRoutine.DEFAULT_INTERVAL_MINUTES }
                puts += id to ChildIn(
                    nickname = child.name.ifBlank { "아이 ${index + 1}" }.take(NICKNAME_MAX),
                    birthMonth = child.birthMonthKey(),
                    feedingType = null,
                    feedIntervalMinutes = interval?.coerceIn(MIN_INTERVAL, MAX_INTERVAL),
                    napCount = null,
                    birthDate = child.birthDateKey(),
                    rearFacingSeat = child.rearFacingSeat,
                    strollerUse = child.stroller,
                    napTimes = child.napTimes,
                )
            }
            // 서버에는 있는데 처음 보는 아이(이어 붙인 아이): 서버 값을 받는다.
            // 서버에서 지워진 아이(snap만 있음): 받을 때 차에서도 빠진다.
        }
        child.copy(serverId = id)
    }

    val keptIds = children.mapNotNull { it.serverId }.toSet()
    // 차에서 지운 아이는 서버에서도 지운다 (이미 웹에서 지웠다면 서버 목록에 없으니 건드리지 않는다).
    val deletes = snapshot.map { it.id }.filter { it !in keptIds && it in serverById }
    return FamilyPlan(local.copy(children = children), puts, deletes)
}

/** 서버 아이 목록을 차 프로필의 아이로 바꾼다. 서버 순서를 그대로 따른다 (첫째가 먼저). */
fun serverChildrenToLocal(server: List<ServerChild>): List<Child> = server.map {
    val (year, month) = it.birthMonth.split("-").let { p -> p[0].toInt() to p[1].toInt() }
    val day = it.birthDate?.takeIf { d -> d.startsWith(it.birthMonth) }?.substringAfterLast('-')?.toIntOrNull()
    Child(
        birthYear = year,
        birthMonth = month,
        name = it.nickname,
        serverId = it.id,
        birthDay = day,
        stroller = it.strollerUse,
        rearFacingSeat = it.rearFacingSeat,
        napTimes = it.napTimes,
    )
}

/**
 * 가족 정보(아이)를 서버와 맞춘다. 올리다 실패하면 서버에서 받지 않고 멈춘다 — 차에서 고친 것이
 * 올라가기 전에 서버 값으로 덮이지 않게 하기 위해서다. 다음 주기에 같은 차이가 다시 잡혀 재시도한다.
 */
class FamilySync(
    private val remote: FamilyRemote,
    private val profiles: ProfileRepo,
    private val routine: FeedingRoutineStore,
    private val snapshot: JsonFile<List<ServerChild>>,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    /** 첫째가 아닌 아이의 수유 간격을 차에 맞춘다 (서버 아이 id, 분). C07 아이별 수유. */
    private val childInterval: (String, Int) -> Unit = { _, _ -> },
) {
    private val lock = Mutex()

    /** 마지막으로 맞춘 서버의 첫째 아이 수유 방식 (수유 기록 종류를 정하는 데 쓴다). */
    fun firstChildFeedingType(): String? = snapshot.load().firstOrNull()?.feedingType

    /** 마지막으로 맞춘 서버의 아이들 (수유 방식·낮잠 횟수 등 차에 없는 항목 포함). */
    fun serverChildren(): List<ServerChild> = snapshot.load()

    /** [childId] 아이의 수유 방식 (null = 첫째). */
    fun feedingTypeOf(childId: String?): String? =
        if (childId == null) firstChildFeedingType() else snapshot.load().firstOrNull { it.id == childId }?.feedingType

    /** 차에서 고친 것이 아직 서버에 안 올라갔는지 (네트워크 없이 확인). */
    fun hasPendingUpload(): Boolean {
        val local = profiles.load() ?: return false
        val plan = planFamily(local, routine.load().intervalMinutes, snapshot.load(), snapshot.load(), newId = { "pending" })
        return plan.puts.isNotEmpty() || plan.deletes.isNotEmpty()
    }

    suspend fun reconcile() = lock.withLock {
        val server = remote.family().children
        val oldSnapshot = snapshot.load()
        val local = profiles.load()
        val plan = planFamily(local, routine.load().intervalMinutes, oldSnapshot, server, newId)
        // 붙인 서버 id를 먼저 저장한다. 올리다 끊겨도 다음에 같은 id로 다시 올려 중복되지 않는다.
        plan.local?.let { if (it != local) profiles.save(it) }

        val current = server.toMutableList()
        for ((id, body) in plan.puts) {
            try {
                val saved = remote.putChild(id, body)
                val at = current.indexOfFirst { it.id == id }
                if (at >= 0) current[at] = saved else current += saved
            } catch (e: HttpException) {
                if (!isPermanentFailure(e.code())) throw e
                Log.w(TAG, "서버가 아이 정보를 받지 않아 서버 값을 따릅니다: ${e.code()}")
            }
        }
        for (id in plan.deletes) {
            remote.deleteChild(id)
            current.removeAll { it.id == id }
        }

        // 서버 값을 차에 반영한다. 차에서 올릴 것은 위에서 모두 올라갔다.
        val pulled = serverChildrenToLocal(current)
        val base = plan.local ?: local
        if (base != null || pulled.isNotEmpty()) {
            val next = (base ?: FamilyProfile()).copy(children = pulled)
            if (next != profiles.load()) profiles.save(next)
        }
        current.firstOrNull()?.feedIntervalMinutes?.let { interval ->
            val r = routine.load()
            if (r.intervalMinutes != interval) routine.save(r.copy(intervalMinutes = interval))
        }
        current.drop(1).forEach { c -> c.feedIntervalMinutes?.let { childInterval(c.id, it) } }
        snapshot.save(current)
    }

    fun forget() = snapshot.clear()

    private companion object {
        const val TAG = "FamilySync"
    }
}

val serverChildrenSerializer = ListSerializer(ServerChild.serializer())

