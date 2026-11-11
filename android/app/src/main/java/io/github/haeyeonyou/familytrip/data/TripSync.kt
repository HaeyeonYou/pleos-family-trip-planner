package io.github.haeyeonyou.familytrip.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 서버 `PUT /me/trip`(app/trip.py, #218) 본문: 지금 코스. 좌표는 보내지 않는다. */
@Serializable
data class TripSnapshot(
    val title: String,
    @SerialName("started_on") val startedOn: String,
    @SerialName("departure_at") val departureAt: String,
    /** active · finished */
    val status: String,
    /** home · here · null */
    @SerialName("ended_by") val endedBy: String? = null,
    val stops: List<TripSnapshotStop>,
)

@Serializable
data class TripSnapshotStop(
    val name: String,
    val role: String,
    @SerialName("arrive_at") val arriveAt: String,
    @SerialName("stay_minutes") val stayMinutes: Int,
    /** visited · current · upcoming · skipped */
    val state: String,
)

/**
 * 코스가 바뀔 때마다(시작·도착·장소 바꾸기·한 곳 더·종료) 서버에 올려 폰 웹 홈에 바로 보이게 한다
 * (Figma H03 "바꾼 일정은 웹앱에도 바로 반영돼요"). 로그인하지 않았으면 올리지 않는다.
 * 실패해도 안내는 계속한다. 다음 변경 때 통째로 다시 올라간다.
 */
class TripSync(
    private val loggedIn: () -> Boolean,
    private val put: suspend (TripSnapshot) -> Unit,
) {
    fun start(snapshots: Flow<TripSnapshot?>, scope: CoroutineScope): Job = scope.launch {
        snapshots.distinctUntilChanged().collect { snap ->
            if (snap == null || !loggedIn()) return@collect
            runCatching { put(snap) }.onFailure { Log.w(TAG, "trip upload failed", it) }
        }
    }

    private companion object {
        const val TAG = "TripSync"
    }
}
