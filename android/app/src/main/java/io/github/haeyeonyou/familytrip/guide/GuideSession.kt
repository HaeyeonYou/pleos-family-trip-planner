package io.github.haeyeonyou.familytrip.guide

import android.content.Context
import io.github.haeyeonyou.familytrip.data.CourseStop
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
) {
    val active: Boolean get() = index != null
    val current: CourseStop? get() = index?.let { stops.getOrNull(it) }
    val next: CourseStop? get() = index?.let { stops.getOrNull(it + 1) }
    val finished: Boolean get() = current?.role == "home" && arrived
}

/**
 * 구간 순차 안내. 앱 화면이 뒤에 있어도(내비가 앞) 도착을 판정하고 알림으로 알리도록
 * 화면이 아니라 앱 단위로 둔다. 경유지 제한(최대 2개) 때문에 한 구간씩 목적지로 넘긴다.
 */
class GuideSession(
    private val context: Context,
    private val navi: NaviRepository,
    private val location: LocationRepository,
    private val notifier: GuideNotifier,
    scope: CoroutineScope,
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

    fun start(stops: List<CourseStop>) {
        _state.value = GuideState(stops = stops)
        guideTo(0)
    }

    fun goToNext() {
        val index = _state.value.index ?: return
        guideTo(index + 1)
    }

    /** 근접 판정이 안 될 때를 위한 수동 도착. 앱 화면에서 누르므로 알림은 띄우지 않는다. */
    fun markArrived() = _state.update { it.copy(arrived = true) }

    fun end() {
        GuideService.stop(context)
        navi.cancelRoute()
        location.clearArrival()
        notifier.clear()
        _state.value = GuideState()
    }

    private fun onArrived() {
        _state.update { it.copy(arrived = true) }
        val s = _state.value
        val current = s.current ?: return
        if (s.finished) {
            GuideService.stop(context)
            notifier.notifyFinished()
        } else {
            GuideService.update(context, "${current.place.name} 도착 · 다음: ${s.next?.place?.name ?: "-"}")
            notifier.notifyArrived(current, s.next)
        }
    }

    private fun guideTo(index: Int) {
        val stop = _state.value.stops.getOrNull(index) ?: return
        notifier.clear()
        _state.update { it.copy(index = index, arrived = false) }
        // 앱이 뒤에 있어도 도착 판정이 돌도록 안내 중에는 포그라운드 서비스를 유지한다.
        GuideService.update(context, "${ROLE_LABELS[stop.role]} · ${stop.place.name}(으)로 가는 중")
        val place = Place(stop.place.name, stop.place.latitude, stop.place.longitude)
        navi.requestRoute(place)
        // 안내 중에는 지도가 보여야 하므로 내장 내비를 앞으로 가져온다.
        navi.showNavigation()
        location.watchArrival(place)
    }
}
