package io.github.haeyeonyou.familytrip.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
) {
    val date: LocalDate get() = LocalDateTime.parse(arrivedAt).toLocalDate()

    /** 머문 시간(분). 아직 떠나지 않았으면 null */
    fun stayMinutes(): Long? = departedAt?.let { Duration.between(LocalDateTime.parse(arrivedAt), LocalDateTime.parse(it)).toMinutes() }
}

/** 방문 기록을 차 안(앱 내부 파일)에만 저장한다. 서버로 보내지 않는다. */
class VisitStore(context: Context) {
    private val file = File(context.filesDir, "visits.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun all(): List<Visit> =
        runCatching { json.decodeFromString<List<Visit>>(file.readText()) }.getOrDefault(emptyList())

    /** 도착을 기록한다. 같은 장소에 이미 머무는 중이면 다시 쓰지 않는다 (근접 알림과 내비 도착이 겹칠 때). */
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
    fun departOpen(now: LocalDateTime = LocalDateTime.now()) {
        val visits = all()
        val last = visits.lastOrNull() ?: return
        if (last.departedAt != null) return
        write(visits.dropLast(1) + last.copy(departedAt = now.withNano(0).toString()))
    }

    private fun write(visits: List<Visit>) = file.writeText(json.encodeToString(visits.takeLast(MAX_VISITS)))

    private companion object {
        const val MAX_VISITS = 500
    }
}
