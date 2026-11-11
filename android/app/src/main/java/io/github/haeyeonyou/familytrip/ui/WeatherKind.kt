package io.github.haeyeonyou.familytrip.ui

/** 날씨 블록 아이콘 종류. */
enum class WeatherKind { SUN, PARTLY_CLOUDY, CLOUD, RAIN, SNOW }

/**
 * 날씨 설명 → 아이콘 종류. 설명은 서버 `weather.py`가 기상청 값을 옮긴 문구
 * (맑음·구름 많음·흐림·비·비/눈·눈·빗방울·빗방울/눈날림·눈날림·강수 없음)이고, 화면에서는 `흐림 · 구리시`처럼
 * 지역이 붙어 오므로 ` · ` 앞 첫 마디만 본다. 비와 눈이 섞이면 비로 본다(길이 젖는 쪽이 운전에 중요).
 * 모르는 문구·"강수 없음"(하늘 상태 조회 실패)은 맑음.
 */
fun weatherKindOf(description: String): WeatherKind {
    val condition = description.substringBefore(" · ").trim()
    return when {
        "비" in condition || "빗" in condition || "소나기" in condition -> WeatherKind.RAIN
        "눈" in condition -> WeatherKind.SNOW
        "구름" in condition -> WeatherKind.PARTLY_CLOUDY
        "흐림" in condition || "흐리" in condition -> WeatherKind.CLOUD
        else -> WeatherKind.SUN
    }
}
