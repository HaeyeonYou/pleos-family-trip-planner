package io.github.haeyeonyou.familytrip.navi

/**
 * 지연 알림 기준 (#17, HF06).
 *
 * - [thresholdSeconds]: 이만큼 이상 늦어지면 알린다 (5분).
 * - [clearBelowSeconds]: 알림이 떠 있다가 이 밑으로 내려오면 스스로 거둔다. 문턱 바로 위아래에서 알림이 깜빡이지 않게 문턱보다 낮다 (3분).
 * - [sustainMillis]: 문턱을 넘은 상태가 이만큼 이어져야 알린다. 남은 시간이 잠깐 튀는 것을 거른다 (30초).
 * - [minRepeatMillis]: 사용자가 "그대로 가기"를 누르거나 알림이 스스로 거두어진 뒤 다시 알리기까지의 최소 간격 (10분).
 * - [escalationSeconds]: "그대로 가기" 뒤에는 그때보다 이만큼 더 늦어져야 다시 알린다 (5분).
 * - [departMeters]: 출발 전에는 지연을 기준 시각과 비교하지 않는다. 길이 이만큼 줄어들어야 출발한 것으로 본다 (200m).
 * - [rebaseGraceMillis]: 경유지를 넣는 등 경로가 바뀐 직후, 내비가 새 남은 시간을 계산하는 동안 기준을 따라 움직인다 (60초).
 */
data class DelayConfig(
    val thresholdSeconds: Int = 300,
    val clearBelowSeconds: Int = 180,
    val sustainMillis: Long = 30_000,
    val minRepeatMillis: Long = 10 * 60_000,
    val escalationSeconds: Int = 300,
    val departMeters: Int = 200,
    val rebaseGraceMillis: Long = 60_000,
)

/**
 * 지금 지연 상태. 음수는 예상보다 빨리 가고 있다는 뜻.
 * 시각은 모두 epoch 밀리초.
 */
data class DelayState(
    val delaySeconds: Int,
    val plannedArrivalMillis: Long,
    val expectedArrivalMillis: Long,
    /** 알림을 띄워야 하는 상태인지 */
    val alert: Boolean,
)

/**
 * 출발 때 내비가 알려 준 예상 도착 시각(`RouteStartInfo.duration`)과, 안내 중 내비가 알려 주는 남은 시간
 * (`DrivingInfo.destination.duration`)으로 늦어진 시간을 계산하고, 알림을 띄우고 거두는 시점을 정한다.
 *
 * 지연 = (지금 + 남은 시간) − (출발 시각 + 출발 때 예상 시간). 걸린 시간을 직접 재지 않고 내비의 도착 예상 시각이
 * 처음보다 얼마나 밀렸는지를 보므로, 내비가 정체를 반영해 남은 시간을 늘리면 그만큼 지연으로 나타난다.
 * 시간을 받는 모든 함수는 시계를 직접 읽지 않고 [nowMillis]를 받는다 (테스트에서 시간을 움직이기 위해).
 */
class DelayTracker(private val config: DelayConfig = DelayConfig()) {

    private var started = false
    private var plannedSeconds = 0
    private var startMeters = 0
    private var plannedArrivalMillis = 0L
    private var departed = false
    private var graceUntilMillis = 0L

    private var aboveSince: Long? = null
    private var belowSince: Long? = null
    private var active = false

    /** 이 시각 전에는 알리지 않는다 */
    private var quietUntilMillis = 0L

    /** 알림은 지연이 이 값 이상일 때만 (다시 알림의 기준) */
    private var alertAtLeastSeconds = config.thresholdSeconds

    /** 안내를 시작했다. [plannedSeconds]와 [meters]는 `RouteStartInfo`의 예상 시간(초)과 거리(m). */
    fun start(plannedSeconds: Int, meters: Int, nowMillis: Long) {
        reset()
        started = true
        this.plannedSeconds = plannedSeconds
        startMeters = meters
        plannedArrivalMillis = nowMillis + plannedSeconds * 1000L
    }

    /**
     * 경유지를 넣거나 빼서 경로가 바뀌었다. 바뀐 뒤의 남은 시간을 새 기준으로 삼고 알림을 거둔다.
     * 내비가 새 남은 시간을 계산하는 동안에는 기준이 남은 시간을 따라간다.
     */
    fun rebase(nowMillis: Long) {
        if (!started) return
        departed = true
        graceUntilMillis = nowMillis + config.rebaseGraceMillis
        aboveSince = null
        belowSince = null
        active = false
        quietUntilMillis = 0
        alertAtLeastSeconds = config.thresholdSeconds
    }

    /** 안내가 끝났다(취소·도착·내비 종료). */
    fun reset() {
        started = false
        departed = false
        graceUntilMillis = 0
        aboveSince = null
        belowSince = null
        active = false
        quietUntilMillis = 0
        alertAtLeastSeconds = config.thresholdSeconds
    }

    /** 사용자가 "그대로 가기"를 눌렀다. 한동안, 그리고 더 늦어지기 전에는 다시 알리지 않는다. */
    fun dismiss(nowMillis: Long, currentDelaySeconds: Int) {
        if (!active) return
        active = false
        aboveSince = null
        belowSince = null
        quietUntilMillis = nowMillis + config.minRepeatMillis
        alertAtLeastSeconds = currentDelaySeconds + config.escalationSeconds
    }

    /**
     * 내비가 남은 시간([remainingSeconds])과 남은 거리([remainingMeters])를 알려 줄 때마다 부른다.
     * 안내 중이 아니면 null.
     */
    fun update(remainingSeconds: Int, remainingMeters: Int, nowMillis: Long): DelayState? {
        if (!started) return null
        val expected = nowMillis + remainingSeconds * 1000L

        if (!departed && startMeters - remainingMeters >= config.departMeters) departed = true
        when {
            // 아직 출발 전: 기다린 시간은 지연이 아니므로 기준을 지금 기준으로 민다.
            !departed -> plannedArrivalMillis = nowMillis + plannedSeconds * 1000L
            // 경로가 막 바뀜: 새 남은 시간을 기준으로 삼는다.
            nowMillis < graceUntilMillis -> plannedArrivalMillis = expected
        }
        val delay = ((expected - plannedArrivalMillis) / 1000).toInt()
        val inGrace = nowMillis < graceUntilMillis

        if (!inGrace) {
            if (active) {
                if (delay < config.clearBelowSeconds) {
                    val since = belowSince ?: nowMillis.also { belowSince = it }
                    if (nowMillis - since >= config.sustainMillis) {
                        // 지연이 풀렸다: 알림을 거두고, 다시 지연되더라도 한동안은 조용히 둔다.
                        active = false
                        belowSince = null
                        quietUntilMillis = nowMillis + config.minRepeatMillis
                        alertAtLeastSeconds = config.thresholdSeconds
                    }
                } else {
                    belowSince = null
                }
            } else if (delay >= config.thresholdSeconds && delay >= alertAtLeastSeconds && nowMillis >= quietUntilMillis) {
                val since = aboveSince ?: nowMillis.also { aboveSince = it }
                if (nowMillis - since >= config.sustainMillis) {
                    active = true
                    aboveSince = null
                    belowSince = null
                }
            } else {
                aboveSince = null
            }
        }
        return DelayState(delay, plannedArrivalMillis, expected, alert = active)
    }
}
