package io.github.haeyeonyou.familytrip.ui

import java.time.LocalTime
import io.github.haeyeonyou.familytrip.data.CarePreferences
import io.github.haeyeonyou.familytrip.data.FamilyProfile
import io.github.haeyeonyou.familytrip.data.TripConditions
import io.github.haeyeonyou.familytrip.data.WeatherInfo

// 홈 추천 요청 조건을 만드는 규칙. 화면·뷰모델과 떼어 두어 단위 테스트로 확인한다.

/** 설정의 선호 조건을 홈 칩의 시작 상태로 한다. 실내·키즈메뉴·놀이방·주차 칩은 설정에 없어 그대로 둔다. */
fun HomeFilters.withPreferences(prefs: CarePreferences): HomeFilters =
    copy(nursingRoom = prefs.nursingRoomOnly, stroller = prefs.strollerOk, pet = prefs.petOnly)

/** 홈 추천 요청(`CourseRequest.conditions`)에 들어가는 조건: 가족 정보 + 지금 칩 상태. */
fun homeConditions(profile: FamilyProfile?, filters: HomeFilters, nowHour: Int = LocalTime.now().hour): TripConditions =
    (profile?.applyTo(TripConditions()) ?: TripConditions())
        .copy(
            withPet = filters.pet,
            indoorOnly = filters.indoor,
            needs = filters.needs(),
            returnByHour = filters.duration?.returnByHour(nowHour),
            maxDriveMinutes = driveMinutesForRadius(filters.radiusKm),
        )

/** 코스 길이 칩 (H01). 반나절은 지금부터 5시간 안에 귀가, 하루는 귀가 시각 제한 없음. */
enum class HomeDuration(val label: String, private val hours: Int?) {
    Half("반나절 코스", 5),
    Full("하루 코스", null),
    ;

    fun returnByHour(nowHour: Int): Int? = hours?.let { (nowHour + it).coerceAtMost(23) }
}

const val HOME_RADIUS_DEFAULT_KM = 10
val HOME_RADIUS_STEPS_KM = listOf(5, 10, 20)

/** 서버는 반경을 이동 시간으로 받는다 (planner의 METERS_PER_DRIVE_MINUTE = 500m/분 가정과 같은 값) */
fun driveMinutesForRadius(radiusKm: Int): Int = radiusKm * 1000 / 500

/** 날씨가 비·눈(곧 올 가능성 포함)이면 실내 칩을 자동으로 켤지. 설정의 '비·눈 오면 실내 우선'이 꺼져 있거나 칩을 직접 만졌으면 켜지 않는다. */
fun shouldPreferIndoor(prefs: CarePreferences, weather: WeatherInfo, indoorTouched: Boolean, filters: HomeFilters): Boolean =
    prefs.indoorOnRain && (weather.rainy || weather.rainLater) && !indoorTouched && !filters.indoor

/**
 * 홈 코스 카드 제목. 서버 이름은 "자연 산책 코스 · 능골공원"(분야 · 나들이 장소)이라 카드 폭에서 장소가 잘린다.
 * Figma 카드("OO동물원 나들이", "아차산 숲길 반나절")처럼 장소를 앞에, 분야는 '코스'를 떼고 뒤에 붙인다: "능골공원 자연 산책".
 * Figma의 "[테마N]" 접두는 샘플 번호라 붙이지 않는다. 형식이 다르면 서버 이름 그대로.
 */
fun homeCourseTitle(serverName: String): String {
    val parts = serverName.split(" · ", limit = 2)
    if (parts.size != 2 || parts.any { it.isBlank() }) return serverName
    val theme = parts[0].trim().removeSuffix("코스").trim()
    val place = parts[1].trim()
    return if (theme.isEmpty()) place else "$place $theme"
}

/**
 * 코스 카드 편의 태그 (Figma Course Item: ✓수유실 ✓유모차 ✓반려동물). 나들이뿐 아니라 경유지 전체에서 한 곳이라도 있으면 체크.
 * 수유실·유모차는 정보가 없으면 '확인 필요'(빼기 표시)로 늘 보이고, 반려동물은 되는 곳이 있을 때만.
 */
fun homeCourseTags(course: io.github.haeyeonyou.familytrip.data.CourseResponse): List<Pair<String, Boolean>> {
    val places = course.stops.filter { it.role != "home" }.map { it.place }
    fun any(key: String) = places.any { it.amenities[key] == "available" }
    return buildList {
        add((if (any("nursing_room")) "수유실" else "수유실 확인") to any("nursing_room"))
        add((if (any("stroller")) "유모차" else "유모차 확인") to any("stroller"))
        if (any("pet")) add("반려동물" to true)
    }
}
