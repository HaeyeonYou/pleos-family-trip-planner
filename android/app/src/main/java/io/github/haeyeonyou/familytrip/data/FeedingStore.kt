package io.github.haeyeonyou.familytrip.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 수유 루틴 (#67): 마지막 수유 시각과 간격. */
data class FeedingRoutine(
    /** 마지막 수유 시각 (epoch ms). 아직 기록이 없으면 null */
    val lastFedAt: Long? = null,
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
) {
    /** 다음 수유 시각 (epoch ms) */
    val nextAt: Long? get() = lastFedAt?.plus(intervalMinutes * MS_PER_MINUTE)

    /** [now] 기준 다음 수유까지 남은 분. 지났으면 음수. */
    fun remainingMinutes(now: Long): Long? = nextAt?.let { Math.floorDiv(it - now, MS_PER_MINUTE) }

    /** 0(방금 수유)에서 1(다음 수유 시각)까지 진행률 */
    fun progress(now: Long): Float {
        val last = lastFedAt ?: return 0f
        return ((now - last).toFloat() / (intervalMinutes * MS_PER_MINUTE)).coerceIn(0f, 1f)
    }

    /** 코스 도착 시각 [arriveAt] (HH:mm)에 다음 수유까지 남는 여유(분). 도착 전에 수유 시각이면 음수. */
    fun slackAtArrival(arriveAt: String, now: Long): Long? {
        val next = nextAt ?: return null
        val zone = ZoneId.systemDefault()
        var arrival = LocalDate.now(zone).atTime(LocalTime.parse(arriveAt)).atZone(zone).toInstant().toEpochMilli()
        if (arrival < now - 12 * 60 * MS_PER_MINUTE) arrival += 24 * 60 * MS_PER_MINUTE  // 자정을 넘긴 도착
        return Math.floorDiv(next - arrival, MS_PER_MINUTE)
    }

    companion object {
        const val DEFAULT_INTERVAL_MINUTES = 180
        val INTERVAL_CHOICES = listOf(120, 150, 180, 240)
        const val MS_PER_MINUTE = 60_000L
    }
}

/** "2시간 45분", "45분", "3시간" */
fun durationLabel(minutes: Long): String {
    val m = kotlin.math.abs(minutes)
    return when {
        m < 60 -> "${m}분"
        m % 60 == 0L -> "${m / 60}시간"
        else -> "${m / 60}시간 ${m % 60}분"
    }
}

/** "9:30" 형태가 아니라 "09:30" */
fun clockLabel(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }

/**
 * 수유 루틴을 기기에 저장한다. 로그인했다면 [FeedingSync]·[FamilySync]가 서버와 맞춘다.
 * 바뀔 때마다 [routine] 흐름이 알려, 서버에서 받은 값도 홈·케어 화면에 바로 반영된다.
 */
class FeedingStore(context: Context) : FeedingRoutineStore {
    private val prefs = context.getSharedPreferences("feeding", Context.MODE_PRIVATE)
    private val _routine = MutableStateFlow(read())
    val routine: StateFlow<FeedingRoutine> = _routine.asStateFlow()

    private fun read(): FeedingRoutine = FeedingRoutine(
        lastFedAt = prefs.getLong(KEY_LAST, 0L).takeIf { it > 0 },
        intervalMinutes = prefs.getInt(KEY_INTERVAL, FeedingRoutine.DEFAULT_INTERVAL_MINUTES),
    )

    override fun load(): FeedingRoutine = _routine.value

    @Synchronized
    override fun save(routine: FeedingRoutine) {
        prefs.edit()
            .putLong(KEY_LAST, routine.lastFedAt ?: 0L)
            .putInt(KEY_INTERVAL, routine.intervalMinutes)
            .apply()
        _routine.value = routine
    }

    private companion object {
        const val KEY_LAST = "last_fed_at"
        const val KEY_INTERVAL = "interval_minutes"
    }
}
