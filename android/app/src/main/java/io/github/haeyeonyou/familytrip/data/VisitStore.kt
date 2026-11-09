package io.github.haeyeonyou.familytrip.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 도착으로 남는 방문 한 건 (지수 덱 '기록', 혜연 P07 '장소별 체크인').
 * 시각은 기기 현지 시각(yyyy-MM-ddTHH:mm:ss)으로 둔다.
 */
@Serializable
data class Visit(
    val id: Long,
    val placeName: String,
    /** meal, outing, cafe */
    val role: String,
    /** 나들이 장소의 경험 분야. 식사·카페는 없음 */
    val theme: String? = null,
    val address: String = "",
    /** 서버 장소 키 (source:source_id). 추천에서 최근 다녀온 곳을 빼는 데 쓴다. 예전 기록에는 없다. */
    val placeKey: String? = null,
    val arrivedAt: String,
    val departedAt: String? = null,
    /** 서버가 정한 방문 id. 웹 등 다른 곳에서 받아온 기록에만 있고, 차에서 만든 기록은 [id]에서 만든 UUID를 쓴다. */
    val serverId: String? = null,
) {
    val date: LocalDate get() = LocalDateTime.parse(arrivedAt).toLocalDate()

    /** 머문 시간(분). 아직 떠나지 않았으면 null */
    fun stayMinutes(): Long? = departedAt?.let { Duration.between(LocalDateTime.parse(arrivedAt), LocalDateTime.parse(it)).toMinutes() }
}

/**
 * 방문 기록을 차 안(앱 내부 파일)에 저장한다. 로그인했다면 [VisitSync]가 나들이 방문만 서버에도 올리고 맞춘다.
 * 기록이 바뀔 때마다 [revision]이 올라 화면이 다시 읽을 수 있다.
 */
class VisitStore(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "visits.json"))

    private val json = Json { ignoreUnknownKeys = true }
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    @Synchronized
    fun all(): List<Visit> =
        runCatching { json.decodeFromString<List<Visit>>(file.readText()) }.getOrDefault(emptyList())

    /** 기록 전체를 [transform]으로 바꾼다 (읽고 쓰는 사이에 다른 기록이 끼어들지 않는다). 달라졌을 때만 [revision]을 올린다. */
    @Synchronized
    fun update(transform: (List<Visit>) -> List<Visit>) {
        val before = all()
        val after = transform(before)
        if (after != before) write(after)
    }

    /** 도착을 기록한다. 같은 장소에 이미 머무는 중이면 다시 쓰지 않는다 (근접 알림과 내비 도착이 겹칠 때). */
    @Synchronized
    fun arrive(place: CoursePlace, role: String, now: LocalDateTime = LocalDateTime.now()) {
        val visits = all()
        if (visits.lastOrNull()?.let { it.placeName == place.name && it.departedAt == null } == true) return
        val visit = Visit(
            id = System.currentTimeMillis(),
            placeName = place.name,
            role = role,
            theme = place.theme,
            address = place.address,
            placeKey = "${place.source}:${place.sourceId}",
            arrivedAt = now.withNano(0).toString(),
        )
        write(visits + visit)
    }

    /** 머무는 중인 방문을 떠난 것으로 닫는다 (다음 안내 시작, 안내 종료). */
    @Synchronized
    fun departOpen(now: LocalDateTime = LocalDateTime.now()) {
        val visits = all()
        val last = visits.lastOrNull() ?: return
        if (last.departedAt != null) return
        write(visits.dropLast(1) + last.copy(departedAt = now.withNano(0).toString()))
    }

    /** 데이터 전체 삭제: 방문 기록을 모두 지운다. */
    @Synchronized
    fun clear() {
        file.delete()
        _revision.value += 1
    }

    private fun write(visits: List<Visit>) {
        file.writeText(json.encodeToString(visits.takeLast(MAX_VISITS)))
        _revision.value += 1
    }

    companion object {
        /** 차에 두는 방문 기록 상한. 서버 목록 한 번 조회 상한([VISIT_LIST_LIMIT])과 같다. */
        const val MAX_VISITS = VISIT_LIST_LIMIT
    }
}
