package io.github.haeyeonyou.familytrip.navi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.haeyeonyou.familytrip.FamilyTripApp
import io.github.haeyeonyou.familytrip.MainActivity
import io.github.haeyeonyou.familytrip.care.CareKind
import io.github.haeyeonyou.familytrip.data.CarePlace
import io.github.haeyeonyou.familytrip.data.TripApi
import io.github.haeyeonyou.familytrip.location.LocationRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** 지연 알림에서 고를 수 있는 경유지 후보. [kind]는 어떤 종류로 찾았는지. */
data class DelayCandidate(val kind: CareKind, val place: CarePlace)

/**
 * 지연 알림 화면(HF06)에 그릴 내용.
 * [candidates]는 서버에서 찾아 오는 중이면 [loading]이 true이고, 못 찾았으면 비어 있다.
 */
data class DelayAlertUi(
    val delayMinutes: Int,
    val cause: DelayCause,
    val expectedArrival: String,
    val plannedArrival: String,
    val candidates: List<DelayCandidate> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
)

/**
 * 지연 알림 (#17, HF06). [NaviRepository]가 계산한 지연 상태가 "알림"이 되면
 *  1) 앱 화면에 지연 알림 패널을 띄우고 (경유지 후보는 서버 `/care/nearby`로 찾는다),
 *  2) 앱이 앞에 없을 때를 위해 헤드업 알림도 띄운다 (Pleos에는 다른 앱 위에 그리는 권한이 없어 알림을 쓴다. #38과 같은 이유).
 * 선택지는 "경유지 추가"(후보 누르기)와 "그대로 가기"다.
 */
class DelayAlertController(
    private val context: Context,
    private val navi: NaviRepository,
    private val location: LocationRepository,
    private val api: TripApi,
    /** 지금 안내 중인 곳(가는 길 기준으로 후보를 고르기 위해). 없으면 null. */
    private val destination: () -> Place?,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _ui = MutableStateFlow<DelayAlertUi?>(null)
    val ui: StateFlow<DelayAlertUi?> = _ui.asStateFlow()

    private val manager = context.getSystemService(NotificationManager::class.java)
    private var findJob: Job? = null

    init {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "지연 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "정체로 도착이 늦어질 때 알려요"
            },
        )
        scope.launch {
            navi.state.map { it.delay?.takeIf { d -> d.alert } to it.traffic }
                .distinctUntilChanged()
                .collect { (delay, traffic) -> onNavi(delay, traffic) }
        }
    }

    private fun onNavi(delay: DelayState?, traffic: TrafficSnapshot?) {
        if (delay == null) {
            if (_ui.value != null) clearUi()
            return
        }
        val now = clock()
        val first = _ui.value == null
        val cause = TrafficCause.describe(traffic, now)
        val minutes = (delay.delaySeconds + 30) / 60
        val base = DelayAlertUi(
            delayMinutes = minutes,
            cause = cause,
            expectedArrival = timeText(delay.expectedArrivalMillis),
            plannedArrival = timeText(delay.plannedArrivalMillis),
        )
        _ui.value = _ui.value?.copy(
            delayMinutes = base.delayMinutes,
            cause = base.cause,
            expectedArrival = base.expectedArrival,
            plannedArrival = base.plannedArrival,
        ) ?: base
        if (first) {
            findCandidates()
            notifyDelay(_ui.value!!)
        }
    }

    /** 경유지 후보: 수유실 1곳 + 잠깐 세울 곳 1곳 (HF06의 1·2번 카드). 가는 곳이 있으면 경로 위 우선. */
    private fun findCandidates() {
        findJob?.cancel()
        findJob = scope.launch {
            val here = here()
            if (here == null) {
                _ui.value = _ui.value?.copy(loading = false, message = "현재 위치를 몰라 근처 경유지를 찾지 못했어요")
                return@launch
            }
            val to = destination()
            val found = listOf(CareKind.NURSING, CareKind.REST).map { kind ->
                async {
                    try {
                        api.careNearby(kind.api, here.first, here.second, to?.latitude, to?.longitude, 2)
                            .places.firstOrNull()?.let { DelayCandidate(kind, it) }
                    } catch (e: Exception) {
                        // 요청 주소가 로그에 남지 않게 예외 종류만 적는다.
                        Log.w(TAG, "careNearby ${kind.api} failed: ${e.javaClass.simpleName}")
                        null
                    }
                }
            }.awaitAll().filterNotNull()
            _ui.value = _ui.value?.copy(
                candidates = found,
                loading = false,
                message = if (found.isEmpty()) "근처에서 경유지를 찾지 못했어요" else null,
            )
        }
    }

    /** 후보를 경유지로 넣는다. 안내 중인 경로에 끼워 넣고 내비 화면을 앞으로 가져온다. */
    fun addWaypoint(candidate: DelayCandidate) {
        val place = candidate.place
        navi.addWaypointOrRoute(Place(place.name, place.latitude, place.longitude))
        navi.resolveDelayAlert(addedWaypoint = true)
        navi.showNavigation()
        clearUi()
    }

    /** "그대로 가기" */
    fun keepGoing() {
        navi.resolveDelayAlert(addedWaypoint = false)
        clearUi()
    }

    private fun clearUi() {
        findJob?.cancel()
        _ui.value = null
        manager.cancel(NOTIFICATION_ID)
    }

    private fun here(): Pair<Double, Double>? {
        val loc = location.state.value
        if (loc.latitude != null && loc.longitude != null) return loc.latitude to loc.longitude
        val n = navi.state.value
        if (n.currentLatitude != null && n.currentLongitude != null) return n.currentLatitude to n.currentLongitude
        return null
    }

    private fun notifyDelay(ui: DelayAlertUi) {
        val open = PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val keep = PendingIntent.getBroadcast(
            context,
            11,
            Intent(context, DelayActionReceiver::class.java).setAction(ACTION_KEEP_GOING),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setContentTitle(headline(ui))
            .setContentText("${ui.cause.detail} · 도착 예정 ${ui.expectedArrival}")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "경유지 보기", open).build())
            .addAction(Notification.Action.Builder(null, "그대로 가기", keep).build())
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "DelayAlert"
        private const val CHANNEL_ID = "trip_delay"
        private const val NOTIFICATION_ID = 3
        const val ACTION_KEEP_GOING = "io.github.haeyeonyou.familytrip.action.DELAY_KEEP_GOING"
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun timeText(epochMillis: Long): String =
            Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(TIME)

        /** "정체로 도착이 7분 늦어져요" */
        fun headline(ui: DelayAlertUi): String =
            "${TrafficCause.withRo(ui.cause.reason)} 도착이 ${ui.delayMinutes}분 늦어져요"
    }
}

/** 알림의 "그대로 가기" 버튼. 앱 화면을 열지 않고 지연 알림을 닫는다. */
class DelayActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DelayAlertController.ACTION_KEEP_GOING) {
            (context.applicationContext as FamilyTripApp).container.delayAlert.keepGoing()
        }
    }
}
