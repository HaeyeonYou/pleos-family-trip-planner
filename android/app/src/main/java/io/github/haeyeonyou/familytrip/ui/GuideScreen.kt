package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haeyeonyou.familytrip.data.CourseStop
import io.github.haeyeonyou.familytrip.guide.GuideState

/**
 * 구간 안내 상태 화면. 안내 중에는 내장 내비가 앞에 있고, 도착하면 헤드업 알림이 뜬다.
 * 이 화면은 알림이나 앱 아이콘으로 돌아왔을 때 보인다. 주행 중에도 조수석 조작을 위해 버튼은 그대로 둔다.
 */
@Composable
fun GuideScreen(
    state: GuideState,
    isParked: Boolean,
    onShowMap: () -> Unit,
    onNext: () -> Unit,
    onMarkArrived: () -> Unit,
    onEnd: () -> Unit,
    onShowRecord: () -> Unit,
) {
    val current = state.current ?: return
    DrivingAware(isParked) {
        Column(
            modifier = Modifier.fillMaxSize().padding(40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            when {
                state.finished -> Finished(onEnd, onShowRecord)
                state.arrived -> Arrived(current, state.next, onNext)
                else -> Heading(current, onShowMap, onMarkArrived)
            }
            if (!state.finished) {
                BigOutlinedButton("안내 끝내기", onEnd)
            }
        }
    }
}

@Composable
private fun Heading(stop: CourseStop, onShowMap: () -> Unit, onMarkArrived: () -> Unit) {
    Text("${ROLE_LABELS[stop.role]}(으)로 가는 중", fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stop.place.name, fontSize = 48.sp)
            Text("${stop.arriveAt} 도착 예정 · ${stop.driveMinutes}분", fontSize = 28.sp)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        BigButton("지도 보기", onShowMap)
        // 근접 판정이 안 될 때를 위한 수동 도착
        BigOutlinedButton("도착했어요", onMarkArrived)
    }
}

@Composable
private fun Arrived(stop: CourseStop, next: CourseStop?, onNext: () -> Unit) {
    Text("${stop.place.name}에 도착했어요", fontSize = 40.sp)
    if (stop.stayMinutes > 0) {
        Text("${stop.stayMinutes}분 정도 머물 예정이에요", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    next ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(32.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("다음: ${ROLE_LABELS[next.role]}", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                Text(next.place.name, fontSize = 36.sp)
                Text("이동 ${next.driveMinutes}분", fontSize = 24.sp)
            }
        }
    }
    BigButton("다음 장소 안내 시작", onNext, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Finished(onEnd: () -> Unit, onShowRecord: () -> Unit) {
    Text("오늘 나들이 끝!", fontSize = 48.sp)
    Text("수고하셨어요. 집에 도착했어요.", fontSize = 28.sp)
    BigButton("오늘의 기록 보기", onShowRecord)
    BigOutlinedButton("처음으로", onEnd)
}
