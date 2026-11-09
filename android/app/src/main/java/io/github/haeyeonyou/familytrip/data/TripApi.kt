package io.github.haeyeonyou.familytrip.data

import io.github.haeyeonyou.familytrip.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

// 서버 app/schemas.py와 같은 구조. 필드 이름은 서버의 snake_case를 따른다.

@Serializable
data class TripConditions(
    @SerialName("child_ages") val childAges: List<Int> = emptyList(),
    /** 가족 프로필에서 계산한 개월 수. 있으면 childAges보다 정확하다. */
    @SerialName("child_age_months") val childAgeMonths: List<Int> = emptyList(),
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

/** 최근 방문 요약. 개인 정보 없이 장소 키·분야·날짜만 서버로 보낸다. */
@Serializable
data class VisitSummary(
    @SerialName("place_key") val placeKey: String = "",
    val theme: String? = null,
    /** yyyy-MM-dd */
    val date: String,
)

@Serializable
data class CourseRequest(
    val conditions: TripConditions,
    val origin: LatLng,
    val home: LatLng? = null,
    @SerialName("departure_time") val departureTime: String? = null,
    /** 최근 방문 요약. 다녀온 곳은 감점, 안 가 본 분야는 가점 */
    val visits: List<VisitSummary> = emptyList(),
    /** 이미 보여 준 나들이 장소 키. '다른 코스 보기'에서 뺀다 */
    @SerialName("exclude_place_keys") val excludePlaceKeys: List<String> = emptyList(),
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
    /** 나들이 장소의 경험 분야 (nature, exhibition, experience, play). 경험 지도에 쓴다. */
    val theme: String? = null,
)

/** 장소 근처 주차장 한 곳. 서버가 추천순으로 최대 3곳을 준다. 목록이 비어 있으면 '없음'이 아니라 '정보 없음'. */
@Serializable
data class StopParking(
    val name: String,
    /** 공영 / 민영 */
    val kind: String? = null,
    @SerialName("distance_m") val distanceMeters: Int = 0,
    /** 직선거리 기준으로 어림한 도보 분 */
    @SerialName("walk_minutes") val walkMinutes: Int = 1,
    val capacity: Int? = null,
    @SerialName("fee_summary") val feeSummary: String = "",
    val free: Boolean = false,
    val hours: String = "",
    /** 도착 시각에 운영하는지. null이면 운영시간을 모름 */
    @SerialName("open_at_arrival") val openAtArrival: Boolean? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
)

@Serializable
data class CourseStop(
    /** meal, outing, cafe, home */
    val role: String,
    val place: CoursePlace,
    /** 옛 저장본에는 없다 → 빈 목록 */
    val parking: List<StopParking> = emptyList(),
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
    /** 출발부터 귀가까지 지도에 그릴 실제 도로 좌표 */
    val path: List<LatLng> = emptyList(),
    val alternatives: Map<String, List<CoursePlace>> = emptyMap(),
    val warnings: List<String> = emptyList(),
)

// 홈 추천 (서버 app/course/planner.py suggest)

@Serializable
data class CourseOption(
    val name: String,
    /** 경유 요약 (A → B → C) */
    val summary: String,
    @SerialName("total_minutes") val totalMinutes: Int,
    @SerialName("first_drive_minutes") val firstDriveMinutes: Int,
    /** '지금 N대 가는 중' 집계 키 (나들이 장소의 source:source_id) */
    @SerialName("place_key") val placeKey: String,
    val indoor: Boolean = false,
    val course: CourseResponse,
)

@Serializable
data class SuggestResponse(val options: List<CourseOption>)

/** 기상청 실황 (서버 app/weather.py) */
@Serializable
data class WeatherInfo(
    val temperature: Double? = null,
    val condition: String = "",
    val rainy: Boolean = false,
    /** 앞으로 6시간 중 가장 높은 강수확률(%). 단기예보 */
    @SerialName("rain_chance") val rainChance: Int? = null,
    /** 지금은 아니어도 곧 비·눈이 올 가능성이 높음 */
    @SerialName("rain_later") val rainLater: Boolean = false,
    /** 시·군·구 이름 (예: 강남구). 못 구하면 빈 문자열 */
    val region: String = "",
)

@Serializable
data class StartBody(@SerialName("place_key") val placeKey: String)

// 계정 (서버 app/auth/api.py)

@Serializable
data class AccountUser(@SerialName("login_id") val loginId: String, @SerialName("display_name") val displayName: String)

@Serializable
data class CarLoginStart(@SerialName("device_name") val deviceName: String)

@Serializable
data class CarLoginStarted(
    @SerialName("request_id") val requestId: String,
    val code: String,
    @SerialName("login_url") val loginUrl: String,
    @SerialName("qr_url") val qrUrl: String,
    @SerialName("poll_secret") val pollSecret: String,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
data class CarLoginPoll(@SerialName("poll_secret") val pollSecret: String)

/** pending: 폰 승인 대기, approved: 로그인 완료(token은 이때 한 번만), expired: 만료 */
@Serializable
data class CarLoginStatus(val status: String, val token: String? = null, val user: AccountUser? = null)

// 공조 프리셋 (서버 app/presets.py)

@Serializable
data class ClimateSettings(
    val name: String = "우리 가족 설정",
    val power: Boolean = true,
    val temperature: Float = 23f,
    @SerialName("fan_speed") val fanSpeed: Int = 3,
    val ac: Boolean = false,
    @SerialName("air_cleaner") val airCleaner: Boolean = false,
    @SerialName("driver_seat_heat") val driverSeatHeat: Int = 0,
    @SerialName("passenger_seat_heat") val passengerSeatHeat: Int = 0,
    @SerialName("steering_wheel_heat") val steeringWheelHeat: Int = 0,
)

@Serializable
data class PendingPreset(val id: String, val settings: ClimateSettings, @SerialName("sent_at") val sentAt: String)

// 급한 케어 찾기 (서버 app/care.py)

@Serializable
data class CarePlace(
    val name: String,
    val address: String = "",
    val latitude: Double,
    val longitude: Double,
    @SerialName("distance_m") val distanceM: Int,
    @SerialName("drive_minutes") val driveMinutes: Int,
    val category: String = "",
    /** 가는 길에서 더 가야 하는 시간(분). 가는 곳을 모르면 null. */
    @SerialName("detour_minutes") val detourMinutes: Int? = null,
    @SerialName("on_route") val onRoute: Boolean = false,
    val note: String = "",
)

@Serializable
data class CareNearby(val kind: String, val places: List<CarePlace>)

// 음성 명령 해석 (서버 app/voice.py)

@Serializable
data class VoiceScreen(
    val tab: String = "home",
    @SerialName("has_course") val hasCourse: Boolean = false,
    val guiding: Boolean = false,
    @SerialName("baby_care") val babyCare: Boolean = false,
    @SerialName("sleep_mode") val sleepMode: Boolean = false,
    @SerialName("child_months") val childMonths: Int? = null,
    @SerialName("has_options") val hasOptions: Boolean = false,
    @SerialName("has_home_options") val hasHomeOptions: Boolean = false,
)

@Serializable
data class VoiceInterpretRequest(val text: String, val screen: VoiceScreen = VoiceScreen())

/** 서버가 해석한 동작 하나. [source]가 fallback이면 해석하지 못한 것이다. */
@Serializable
data class VoiceInterpretation(
    val action: String = "unknown",
    val target: String? = null,
    val on: Boolean? = null,
    val number: Int? = null,
    val temperature: Double? = null,
    @SerialName("temperature_delta") val temperatureDelta: Double? = null,
    val ac: Boolean? = null,
    @SerialName("driver_seat_heat") val driverSeatHeat: Int? = null,
    @SerialName("passenger_seat_heat") val passengerSeatHeat: Int? = null,
    @SerialName("steering_heat") val steeringHeat: Int? = null,
    val confidence: Double = 0.0,
    val reply: String = "",
    val source: String = "llm",
)

interface TripApi {
    @POST("parse")
    suspend fun parse(@Body request: ParseRequest): ParseResponse

    @POST("course")
    suspend fun course(@Body request: CourseRequest): CourseResponse

    @POST("course/suggest")
    suspend fun suggest(@Body request: CourseRequest): SuggestResponse

    /** 자연어 음성 명령을 앱 동작으로 해석한다 (서버가 3초 안에 못 받으면 unknown·fallback). */
    @POST("voice/interpret")
    suspend fun interpretVoice(@Body request: VoiceInterpretRequest): VoiceInterpretation

    @GET("weather")
    suspend fun weather(@Query("lat") latitude: Double, @Query("lng") longitude: Double): WeatherInfo

    /** 코스 시작을 서버가 센다 (장소 키만 보냄). */
    @POST("course/starts")
    suspend fun recordStart(@Body body: StartBody): Response<Unit>

    /** 장소 키별로 최근 1시간 안에 시작한 수 */
    @GET("course/going")
    suspend fun going(@Query("keys") keys: String): Map<String, Int>

    @POST("course/schedule")
    suspend fun schedule(@Body request: ScheduleRequest): CourseResponse

    @POST("auth/car-login")
    suspend fun startCarLogin(@Body request: CarLoginStart): CarLoginStarted

    @POST("auth/car-login/{id}/poll")
    suspend fun pollCarLogin(@Path("id") requestId: String, @Body request: CarLoginPoll): CarLoginStatus

    @GET("auth/me")
    suspend fun me(): AccountUser

    @POST("auth/logout")
    suspend fun logout()

    /** 영역별 변경 표시. 웹이 이 차의 연결을 끊으면 토큰이 만료되어 401이 온다. */
    @GET("me/sync")
    suspend fun sync(): SyncMarks

    /** 아직 적용하지 않은 프리셋. 없으면 204 (body null). */
    @GET("presets/pending")
    suspend fun pendingPreset(): Response<PendingPreset>

    /** 수유실(nursing)·기저귀 교환대(diaper)·잠깐 세울 곳(rest)을 현재 위치 근처에서 찾는다. 가는 곳을 주면 경로 위 우선. */
    @GET("care/nearby")
    suspend fun careNearby(
        @Query("kind") kind: String,
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("to_lat") toLat: Double? = null,
        @Query("to_lng") toLng: Double? = null,
        @Query("limit") limit: Int = 2,
    ): CareNearby

    @POST("presets/{id}/applied")
    suspend fun presetApplied(@Path("id") presetId: String): Response<Unit>

    companion object {
        /** [token]이 있으면 모든 요청에 차 세션 토큰을 붙인다 (로그인한 사용자 기능). */
        fun create(baseUrl: String = BuildConfig.SERVER_URL, token: () -> String? = { null }): TripApi {
            val json = Json { ignoreUnknownKeys = true }
            val http = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = token()?.let { chain.request().newBuilder().header("Authorization", "Bearer $it").build() }
                    chain.proceed(request ?: chain.request())
                }
                // Claude 파서 첫 호출이 8초 안팎이라 여유를 둔다.
                .readTimeout(60, TimeUnit.SECONDS)
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
