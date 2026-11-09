package io.github.haeyeonyou.familytrip.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

// 서버 계약(docs/research/web-server-contract.md)의 방문 기록·수유·가족·프리셋 모양.
// 요청용 모델은 기본값 없이 만들어 모든 필드가 항상 실려 간다.

/** 방문 한 건을 올릴 때. 좌표·주소·시각은 담지 않는다. */
@Serializable
data class VisitIn(
    @SerialName("place_key") val placeKey: String,
    @SerialName("place_name") val placeName: String,
    val theme: String?,
    /** yyyy-MM-dd */
    @SerialName("visited_on") val visitedOn: String,
    @SerialName("stayed_minutes") val stayedMinutes: Int?,
    /** confirmed | pending | dismissed */
    val status: String,
    val mood: String?,
)

@Serializable
data class VisitBulkItem(
    val id: String,
    @SerialName("place_key") val placeKey: String,
    @SerialName("place_name") val placeName: String,
    val theme: String?,
    @SerialName("visited_on") val visitedOn: String,
    @SerialName("stayed_minutes") val stayedMinutes: Int?,
    val status: String,
    val mood: String?,
)

@Serializable
data class VisitBulk(val visits: List<VisitBulkItem>)

@Serializable
data class VisitOut(
    val id: String,
    @SerialName("place_key") val placeKey: String = "",
    @SerialName("place_name") val placeName: String = "",
    val theme: String? = null,
    @SerialName("visited_on") val visitedOn: String,
    @SerialName("stayed_minutes") val stayedMinutes: Int? = null,
    val status: String = "confirmed",
    val mood: String? = null,
)

@Serializable
data class FeedingIn(
    @SerialName("fed_at") val fedAt: String,
    val kind: String,
    @SerialName("amount_ml") val amountMl: Int?,
    val source: String,
)

@Serializable
data class FeedingStatus(
    @SerialName("child_id") val childId: String? = null,
    val nickname: String? = null,
    @SerialName("interval_minutes") val intervalMinutes: Int = 180,
    @SerialName("last_fed_at") val lastFedAt: String? = null,
    @SerialName("next_due_at") val nextDueAt: String? = null,
    @SerialName("minutes_left") val minutesLeft: Int? = null,
    val overdue: Boolean = false,
)

/** 서버의 아이 한 명. 차에 없는 항목(수유 방식·낮잠)도 그대로 들고 있다가 고쳐 올릴 때 지키려고 함께 읽는다. */
@Serializable
data class ServerChild(
    val id: String,
    val nickname: String,
    /** YYYY-MM */
    @SerialName("birth_month") val birthMonth: String,
    @SerialName("feeding_type") val feedingType: String? = null,
    @SerialName("feed_interval_minutes") val feedIntervalMinutes: Int? = null,
    @SerialName("nap_count") val napCount: Int? = null,
    @SerialName("age_months") val ageMonths: Int = 0,
)

@Serializable
data class ChildIn(
    val nickname: String,
    @SerialName("birth_month") val birthMonth: String,
    @SerialName("feeding_type") val feedingType: String?,
    @SerialName("feed_interval_minutes") val feedIntervalMinutes: Int?,
    @SerialName("nap_count") val napCount: Int?,
)

/** `GET /me/family`. 멤버(호칭)는 차 앱이 쓰지 않아 읽지 않는다. */
@Serializable
data class ServerFamily(val children: List<ServerChild> = emptyList())

/** `GET /presets`의 슬롯 하나 (boarding · nap · feeding_stop). */
@Serializable
data class PresetState(
    val slot: String,
    val label: String = "",
    @SerialName("when") val whenLabel: String = "",
    val sent: Boolean = false,
    val id: String? = null,
    val settings: ClimateSettings = ClimateSettings(),
    @SerialName("sent_at") val sentAt: String? = null,
    @SerialName("applied_at") val appliedAt: String? = null,
    /** 폰에서 보냈는데 아직 차에서 적용하지 않음 */
    val pending: Boolean = false,
)

/** 서버가 정한 슬롯 순서. */
val PRESET_SLOTS = listOf("boarding", "nap", "feeding_stop")

/** 앱 안에서 쓰는 작은 JSON 파일 저장소. 쓰다가 끊겨도 깨지지 않게 임시 파일에 쓴 뒤 바꿔 넣는다. */
class JsonFile<T>(private val file: File, private val serializer: KSerializer<T>, private val empty: () -> T) {
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun load(): T = runCatching { json.decodeFromString(serializer, file.readText()) }.getOrElse { empty() }

    @Synchronized
    fun save(value: T) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(serializer, value))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
    }
}
