package io.github.haeyeonyou.familytrip.voice

import io.github.haeyeonyou.familytrip.data.WeatherInfo
import io.github.haeyeonyou.familytrip.guide.GuideState
import io.github.haeyeonyou.familytrip.navi.NaviState
import java.time.LocalTime

/**
 * 앱이 가진 정보로 하는 답변. 없는 정보는 지어내지 않고 없다고 답한다.
 * [now]는 시각 계산용(테스트에서 고정).
 */
fun etaText(navi: NaviState, guide: GuideState, now: LocalTime = LocalTime.now()): String {
    val seconds = navi.remainingSeconds
    if (navi.routing && seconds != null) {
        val minutes = (seconds + 59) / 60
        val arrive = now.plusSeconds(seconds.toLong())
        val place = guide.current?.place?.name?.let { name -> "${name}에" } ?: "목적지에"
        return "$place 약 ${minutes}분 뒤, %02d시 %02d분쯤 도착해요.".format(arrive.hour, arrive.minute)
    }
    guide.current?.let { return "${it.place.name} 도착 예정은 ${it.arriveAt}이에요. (코스 계획 기준)" }
    return "안내 중인 경로가 없어요."
}

fun weatherText(w: WeatherInfo?): String {
    if (w == null) return "날씨 정보를 아직 받지 못했어요."
    val temp = w.temperature?.let { "${it.toInt()}도" }.orEmpty()
    val rain = when {
        w.rainy -> " 지금 비가 와요."
        w.rainLater -> " 곧 비가 올 수 있어요."
        else -> ""
    }
    return "${w.region.ifBlank { "지금 위치" }} ${w.condition} $temp.$rain".replace("  ", " ").trim()
}
