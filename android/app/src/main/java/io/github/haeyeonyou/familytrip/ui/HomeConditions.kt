package io.github.haeyeonyou.familytrip.ui

import io.github.haeyeonyou.familytrip.data.CarePreferences
import io.github.haeyeonyou.familytrip.data.FamilyProfile
import io.github.haeyeonyou.familytrip.data.TripConditions
import io.github.haeyeonyou.familytrip.data.WeatherInfo

// 홈 추천 요청 조건을 만드는 규칙. 화면·뷰모델과 떼어 두어 단위 테스트로 확인한다.

/** 설정의 선호 조건을 홈 칩의 시작 상태로 한다. 실내·키즈메뉴·놀이방·주차 칩은 설정에 없어 그대로 둔다. */
fun HomeFilters.withPreferences(prefs: CarePreferences): HomeFilters =
    copy(nursingRoom = prefs.nursingRoomOnly, stroller = prefs.strollerOk, pet = prefs.petOnly)

/** 홈 추천 요청(`CourseRequest.conditions`)에 들어가는 조건: 가족 정보 + 지금 칩 상태. */
fun homeConditions(profile: FamilyProfile?, filters: HomeFilters): TripConditions =
    (profile?.applyTo(TripConditions()) ?: TripConditions())
        .copy(withPet = filters.pet, indoorOnly = filters.indoor, needs = filters.needs())

/** 날씨가 비·눈(곧 올 가능성 포함)이면 실내 칩을 자동으로 켤지. 설정의 '비·눈 오면 실내 우선'이 꺼져 있거나 칩을 직접 만졌으면 켜지 않는다. */
fun shouldPreferIndoor(prefs: CarePreferences, weather: WeatherInfo, indoorTouched: Boolean, filters: HomeFilters): Boolean =
    prefs.indoorOnRain && (weather.rainy || weather.rainLater) && !indoorTouched && !filters.indoor
