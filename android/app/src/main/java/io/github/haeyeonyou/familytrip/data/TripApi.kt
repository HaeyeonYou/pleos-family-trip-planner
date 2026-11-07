package io.github.haeyeonyou.familytrip.data

import io.github.haeyeonyou.familytrip.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// 서버 app/schemas.py와 같은 구조. 필드 이름은 서버의 snake_case를 따른다.

@Serializable
data class TripConditions(
    @SerialName("child_ages") val childAges: List<Int> = emptyList(),
    val adults: Int? = null,
    @SerialName("with_pet") val withPet: Boolean = false,
    val themes: List<String> = emptyList(),
    @SerialName("indoor_only") val indoorOnly: Boolean = false,
    @SerialName("return_by_hour") val returnByHour: Int? = null,
    @SerialName("max_drive_minutes") val maxDriveMinutes: Int? = null,
    val needs: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
)

@Serializable
data class ParseRequest(val text: String)

@Serializable
data class ParseResponse(val conditions: TripConditions, val parser: String)

@Serializable
data class LatLng(val latitude: Double, val longitude: Double)

@Serializable
data class CourseRequest(
    val conditions: TripConditions,
    val origin: LatLng,
    val home: LatLng? = null,
    @SerialName("departure_time") val departureTime: String? = null,
)

@Serializable
data class CoursePlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
    val category: String = "",
    val source: String,
    @SerialName("source_id") val sourceId: String,
    @SerialName("image_url") val imageUrl: String = "",
    /** stroller, nursing_room, kids_chair → available / unknown */
    val amenities: Map<String, String> = emptyMap(),
    @SerialName("amenity_notes") val amenityNotes: Map<String, String> = emptyMap(),
)

@Serializable
data class CourseStop(
    /** meal, outing, cafe, home */
    val role: String,
    val place: CoursePlace,
    @SerialName("arrive_at") val arriveAt: String,
    @SerialName("depart_at") val departAt: String? = null,
    @SerialName("stay_minutes") val stayMinutes: Int = 0,
    @SerialName("drive_minutes") val driveMinutes: Int,
    @SerialName("drive_distance_m") val driveDistanceMeters: Int,
)

@Serializable
data class ScheduleStop(
    val role: String,
    val place: CoursePlace,
    /** 사용자가 바꾼 체류 시간. 없으면 서버가 역할 기본값을 쓴다. */
    @SerialName("stay_minutes") val stayMinutes: Int? = null,
)

/** 장소를 바꾼 코스의 시간표만 다시 계산한다 (서버가 장소를 다시 찾지 않음). */
@Serializable
data class ScheduleRequest(
    val conditions: TripConditions,
    val origin: LatLng,
    val home: LatLng? = null,
    @SerialName("departure_time") val departureTime: String? = null,
    val stops: List<ScheduleStop>,
    /** 나들이 장소 근처 식사·카페 후보를 새로 찾아 받는다 (나들이를 바꿨을 때). */
    @SerialName("suggest_nearby") val suggestNearby: Boolean = false,
)

@Serializable
data class CourseResponse(
    @SerialName("departure_at") val departureAt: String = "",
    val stops: List<CourseStop>,
    @SerialName("total_drive_minutes") val totalDriveMinutes: Int,
    @SerialName("return_at") val returnAt: String,
    @SerialName("route_source") val routeSource: String,
    val alternatives: Map<String, List<CoursePlace>> = emptyMap(),
    val warnings: List<String> = emptyList(),
)

interface TripApi {
    @POST("parse")
    suspend fun parse(@Body request: ParseRequest): ParseResponse

    @POST("course")
    suspend fun course(@Body request: CourseRequest): CourseResponse

    @POST("course/schedule")
    suspend fun schedule(@Body request: ScheduleRequest): CourseResponse

    companion object {
        fun create(baseUrl: String = BuildConfig.SERVER_URL): TripApi {
            val json = Json { ignoreUnknownKeys = true }
            val http = OkHttpClient.Builder()
                // Claude 파서 첫 호출이 8초 안팎이라 여유를 둔다.
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(http)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(TripApi::class.java)
        }
    }
}
