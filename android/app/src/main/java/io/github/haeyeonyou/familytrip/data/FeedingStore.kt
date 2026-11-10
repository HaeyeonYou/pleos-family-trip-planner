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
 *
 * 아이별 수유 (C07, D4 "수유 타이머만 아이별"): 아이마다 루틴을 따로 둔다. 첫째 아이(목록 맨 앞)는 키가 null이고
 * 이 기능 전부터 쓰던 저장 키를 그대로 쓴다. 다른 아이는 서버 아이 id로 저장한다.
 * [load]/[save]/[routine]은 **지금 고른 아이**([selectedChildId]) 것이고, [firstChild]는 첫째 아이 것이다.
 */
class FeedingStore(context: Context) : FeedingRoutineStore {
    private val prefs = context.getSharedPreferences("feeding", Context.MODE_PRIVATE)
    private val _selected = MutableStateFlow(prefs.getString(KEY_SELECTED, null))

    /** 지금 고른 아이의 서버 id. null이면 첫째 아이. */
    val selectedChildId: StateFlow<String?> = _selected.asStateFlow()
    private val _routine = MutableStateFlow(read(_selected.value))
    val routine: StateFlow<FeedingRoutine> = _routine.asStateFlow()

    private fun lastKey(child: String?) = if (child == null) KEY_LAST else "$KEY_LAST:$child"
    private fun intervalKey(child: String?) = if (child == null) KEY_INTERVAL else "$KEY_INTERVAL:$child"

    private fun read(child: String?): FeedingRoutine = FeedingRoutine(
        lastFedAt = prefs.getLong(lastKey(child), 0L).takeIf { it > 0 },
        intervalMinutes = prefs.getInt(intervalKey(child), FeedingRoutine.DEFAULT_INTERVAL_MINUTES),
    )

    override fun load(): FeedingRoutine = _routine.value

    override fun save(routine: FeedingRoutine) = saveFor(_selected.value, routine)

    /** [child] 아이의 루틴 (null = 첫째). */
    fun loadFor(child: String?): FeedingRoutine = if (child == _selected.value) _routine.value else read(child)

    @Synchronized
    fun saveFor(child: String?, routine: FeedingRoutine) {
        prefs.edit()
            .putLong(lastKey(child), routine.lastFedAt ?: 0L)
            .putInt(intervalKey(child), routine.intervalMinutes)
            .apply()
        if (child == _selected.value) _routine.value = routine
    }

    /** 케어 대상 아이를 고른다 (null = 첫째). 홈·케어·음성이 모두 이 아이의 수유 타이머를 본다. */
    @Synchronized
    fun select(child: String?) {
        if (child == _selected.value) return
        prefs.edit().putString(KEY_SELECTED, child).apply()
        _selected.value = child
        _routine.value = read(child)
    }

    /** 첫째 아이의 루틴 (가족 정보 동기화가 첫째 아이 간격을 맞추는 데 쓴다). */
    val firstChild: FeedingRoutineStore = object : FeedingRoutineStore {
        override fun load() = loadFor(null)
        override fun save(routine: FeedingRoutine) = saveFor(null, routine)
    }

    private companion object {
        const val KEY_LAST = "last_fed_at"
        const val KEY_INTERVAL = "interval_minutes"
        const val KEY_SELECTED = "selected_child"
    }
}
