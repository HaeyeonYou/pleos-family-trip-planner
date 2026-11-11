package io.github.haeyeonyou.familytrip.guide

import android.content.Context
import io.github.haeyeonyou.familytrip.data.CourseStop
import io.github.haeyeonyou.familytrip.data.VisitStore
import io.github.haeyeonyou.familytrip.location.LocationRepository
import io.github.haeyeonyou.familytrip.navi.NaviRepository
import io.github.haeyeonyou.familytrip.navi.Place
import io.github.haeyeonyou.familytrip.ui.ROLE_LABELS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GuideState(
    val stops: List<CourseStop> = emptyList(),
    /** 안내 중인 stops 인덱스. null이면 안내 전. */
    val index: Int? = null,
    /** 현재 구간 목적지에 도착했는지. */
    val arrived: Boolean = false,
    /** 도착한(다녀온) 장소의 stops 인덱스. 방문 기록·H04 목록·웹 동기화에 쓴다. 건너뛰거나 바꾼 곳은 없다. */
    val visited: Set<Int> = emptySet(),
    /** 지금 장소에 도착한 시각(ms). 출발 시각·지연 계산에 쓴다. */
    val arrivedAtMillis: Long? = null,
    /** 코스를 끝낸 방법. 값이 있으면 H04 (Figma: [귀가]나 [여기서 코스 끝내기]를 고른 순간 끝난다). */
    val endedBy: CourseEnd? = null,
    /** 코스 출발 시각 "10:00" (진행 줄 첫 점) */
    val departureAt: String = "",
    /** "덕수궁 나들이 코스" */
    val title: String = "",
) {
    val active: Boolean get() = index != null

    /** 지금 가는(또는 도착한) 곳. 여기서 끝냈으면 갈 곳이 없다. */
    val current: CourseStop? get() = if (endedBy == CourseEnd.HERE) null else index?.let { stops.getOrNull(it) }
    val next: CourseStop? get() = index?.let { stops.getOrNull(it + 1) }

    /** 다음 '장소'(귀가 제외). 없으면 지금이 마지막 장소. */
    val nextVisit: CourseStop? get() = next?.takeIf { it.role != "home" }
    val finished: Boolean get() = endedBy != null
    val homeIndex: Int? get() = stops.indexOfLast { it.role == "home" }.takeIf { it >= 0 }
}

/**
 * 구간 순차 안내. 앱 화면이 뒤에 있어도(내비가 앞) 도착을 판정하고 알림으로 알리도록
 * 화면이 아니라 앱 단위로 둔다. 경유지 제한(최대 2개) 때문에 한 구간씩 목적지로 넘긴다.
 * 코스는 귀가(집 안내 시작)나 여기서 끝내기를 고른 순간 끝난다 (#218, Figma H04 정책).
 */
class GuideSession(
    private val context: Context,
    private val navi: NaviRepository,
    private val location: LocationRepository,
    private val notifier: GuideNotifier,
    private val visits: VisitStore,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(GuideState())
    val state: StateFlow<GuideState> = _state.asStateFlow()

    init {
        scope.launch {
            location.state.collect { loc ->
                val s = _state.value
                val current = s.current ?: return@collect
                if (!s.arrived && loc.arrivedAt == current.place.name) onArrived()
            }
        }
        // 내비의 목적지 도착 이벤트도 도착으로 본다. 근접 알림과 먼저 오는 쪽을 쓴다.
        // 에뮬레이터에서는 내비 주행 시뮬레이션이 이 이벤트만 낸다 (FusedLocation은 그대로).
        scope.launch {
            navi.state.collect { naviState ->
                val s = _state.value
                val current = s.current ?: return@collect
                if (!s.arrived && naviState.arrivedAt == current.place.name) onArrived()
            }
        }
    }

    fun start(stops: List<CourseStop>, departureAt: String = "", title: String = courseTitle(stops)) {
        _state.value = GuideState(stops = stops, departureAt = departureAt, title = title)
        guideTo(0)
    }

    /** [다음 장소로]. 마지막 장소면 귀가([goHome])와 같다. */
    fun goToNext() {
        val s = _state.value
        if (s.finished) return
        val index = s.index ?: return
        if (s.nextVisit == null) goHome() else guideTo(index + 1)
    }

    /**
     * 장소 바꾸기·한 곳 더로 서버가 다시 계산한 코스를 받는다. 지나온 장소가 같을 때만 받고, [thenGoNext]면 바로 다음 장소로 안내한다.
     * 돌려준 값이 false면 받지 않은 것(다른 코스).
     */
    fun replaceStops(stops: List<CourseStop>, thenGoNext: Boolean): Boolean {
        val updated = withRescheduled(_state.value, stops) ?: return false
        _state.value = updated
        if (thenGoNext) goToNext()
        return true
    }

    /** [귀가]: 코스를 여기서 마치고 집 안내를 시작한다 (H04 "우리 집으로 안내 중"). */
    fun goHome() {
        val s = _state.value
        val home = s.homeIndex
        if (s.index == null || home == null) {
            endHere()
            return
        }
        _state.update { it.copy(endedBy = CourseEnd.HOME) }
        guideTo(home)
    }

    /** [여기서 코스 끝내기]: 안내를 취소하고 H04로 (귀가 안내 없음). */
    fun endHere() {
        if (_state.value.index == null) return
        visits.departOpen()
        GuideService.stop(context)
        navi.cancelRoute()
        location.clearArrival()
        notifier.clear()
        _state.update { it.copy(endedBy = CourseEnd.HERE, arrived = false) }
    }

    /** 근접 판정이 안 될 때를 위한 수동 도착. 앱 화면에서 누르므로 알림은 띄우지 않는다. */
    fun markArrived() {
        val s = _state.value
        if (s.current == null || s.arrived) return
        _state.update { it.copy(arrived = true, arrivedAtMillis = clock()) }
        recordArrival()
    }

    /**
     * 안내 상태를 비운다 (H04 [홈으로]·옛 [안내 끝내기]). 귀가를 골라 집으로 가는 중이면 내비 안내는 그대로 둔다.
     */
    fun end() {
        val homeward = _state.value.endedBy == CourseEnd.HOME
        visits.departOpen()
        GuideService.stop(context)
        if (!homeward) navi.cancelRoute()
        location.clearArrival()
        notifier.clear()
        _state.value = GuideState()
    }

    private fun onArrived() {
        _state.update { it.copy(arrived = true, arrivedAtMillis = clock()) }
        recordArrival()
        val s = _state.value
        val current = s.current ?: return
        if (current.role == "home") {
            GuideService.stop(context)
            notifier.notifyFinished()
        } else {
            GuideService.update(context, "${current.place.name} 도착 · 다음: ${s.nextVisit?.place?.name ?: "귀가"}")
            notifier.notifyArrived(current, s.next)
        }
    }

    /** 도착한 장소를 방문 기록에 남긴다 (경험 지도, #11). 귀가와 주유·충전(#215)은 기록하지 않는다. 다녀온 곳만 기록된다. */
    private fun recordArrival() {
        val s = _state.value
        val index = s.index ?: return
        val stop = s.current ?: return
        if (stop.role == "home" || index in s.visited) return
        if (stop.role != "fuel") visits.arrive(stop.place, stop.role)
        _state.update { it.copy(visited = it.visited + index) }
    }

    private fun guideTo(index: Int) {
        val stop = _state.value.stops.getOrNull(index) ?: return
        // 다음 장소로 떠나면 앞 장소의 머문 시간이 정해진다.
        visits.departOpen()
        notifier.clear()
        _state.update { it.copy(index = index, arrived = false, arrivedAtMillis = null) }
        // 앱이 뒤에 있어도 도착 판정이 돌도록 안내 중에는 포그라운드 서비스를 유지한다.
        val label = if (stop.role == "home") "귀가" else ROLE_LABELS[stop.role]
        GuideService.update(context, "$label · ${stop.place.name}(으)로 가는 중")
        val place = Place(stop.place.name, stop.place.latitude, stop.place.longitude)
        navi.requestRoute(place)
        // 안내 중에는 지도가 보여야 하므로 내장 내비를 앞으로 가져온다.
        // 귀가는 코스가 끝난 뒤라 H04(코스 요약)를 먼저 보이고, 안내는 내비가 뒤에서 이어 간다 ("Pleos 내비로 안내를 넘겼어요").
        if (stop.role != "home" || _state.value.endedBy == null) navi.showNavigation()
        location.watchArrival(place)
    }
}
