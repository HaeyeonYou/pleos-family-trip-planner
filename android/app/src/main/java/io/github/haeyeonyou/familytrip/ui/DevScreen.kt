package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.haeyeonyou.familytrip.location.LocationRepository
import io.github.haeyeonyou.familytrip.location.LocationState
import io.github.haeyeonyou.familytrip.navi.NaviRepository
import io.github.haeyeonyou.familytrip.navi.NaviState
import io.github.haeyeonyou.familytrip.navi.Place
import io.github.haeyeonyou.familytrip.vehicle.GearState
import io.github.haeyeonyou.familytrip.vehicle.VehicleState

import io.github.haeyeonyou.familytrip.AppContainer
import androidx.compose.material3.TextButton

private val COEX = Place("코엑스", 37.5115, 127.0595)

/** SDK 동작 확인용 화면 (S2·S3·FusedLocation 스파이크). */
@Composable
fun DevScreen(container: AppContainer, onBack: () -> Unit) {
    val vehicleState by container.vehicle.state.collectAsStateWithLifecycle()
    val naviState by container.navi.state.collectAsStateWithLifecycle()
    val locationState by container.location.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .padding(48.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 돌아가기", fontSize = 20.sp) }
        Text("개발자 화면", fontSize = 40.sp)
        VehicleStatusSection(vehicleState)
        NaviSection(naviState, container.navi, locationState, container.location)
        LocationSection(locationState)
    }
}

@Composable
private fun VehicleStatusSection(state: VehicleState) {
    Text("차량 상태 (Vehicle SDK 확인용)", fontSize = 24.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        StatusCard("배터리", state.stateOfChargePercent?.let { "$it%" } ?: "-")
        StatusCard("주행가능거리", state.rangeRemainingMeters?.let { "%.0f km".format(it / 1000f) } ?: "-")
        StatusCard("기어", state.gear.label())
        StatusCard("상태", if (state.isParked) "정차 중" else "주행 중")
    }
    state.lastError?.let { Text("오류: $it", color = MaterialTheme.colorScheme.error, fontSize = 20.sp) }
}

@Composable
private fun NaviSection(
    state: NaviState,
    navi: NaviRepository,
    locationState: LocationState,
    location: LocationRepository,
) {
    fun guideTo(place: Place) {
        navi.requestRoute(place)
        location.watchArrival(place)
    }

    Text("내비 (NaviHelper 확인용)", fontSize = 24.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        StatusCard("연결", if (state.connected) "됨" else "안 됨")
        StatusCard("안내", if (state.routing) "안내 중" else "-")
        StatusCard("남은 거리", state.remainingDistanceMeters?.let { "%.1f km".format(it / 1000f) } ?: "-")
        StatusCard("남은 시간", state.remainingSeconds?.let { "${it / 60}분" } ?: "-")
        StatusCard("도착", state.arrivedAt ?: "-")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = { guideTo(COEX) }) { Text("코엑스로 안내", fontSize = 20.sp) }
        // Prefer FusedLocation: the built-in navigation's position is fixed on the emulator.
        val lat = locationState.latitude ?: state.currentLatitude
        val lon = locationState.longitude ?: state.currentLongitude
        Button(
            enabled = lat != null && lon != null,
            onClick = { guideTo(Place("근처 테스트 지점", lat!! + 0.005, lon!!)) },
        ) { Text("500m 북쪽으로 안내", fontSize = 20.sp) }
        OutlinedButton(onClick = {
            navi.cancelRoute()
            location.clearArrival()
        }) { Text("안내 취소", fontSize = 20.sp) }
        OutlinedButton(onClick = { navi.refreshCurrentLocation() }) { Text("현재 위치", fontSize = 20.sp) }
    }
    Card {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("이벤트 로그", fontSize = 20.sp)
            if (state.events.isEmpty()) Text("-", fontSize = 18.sp)
            state.events.forEach { Text(it, fontSize = 18.sp) }
        }
    }
}

@Composable
private fun LocationSection(state: LocationState) {
    Text("위치·도착 판정 (FusedLocation 확인용)", fontSize = 24.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        StatusCard(
            "서비스",
            when (state.supported) {
                null -> "-"
                true -> "사용 가능"
                false -> "없음"
            },
        )
        StatusCard(
            "현재 위치",
            if (state.latitude != null && state.longitude != null) {
                "%.5f, %.5f".format(state.latitude, state.longitude)
            } else {
                "-"
            },
        )
        StatusCard("도착 감시", state.watching?.name ?: "-")
        StatusCard("도착", state.arrivedAt ?: "-")
    }
    Card {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("위치 이벤트 로그", fontSize = 20.sp)
            if (state.events.isEmpty()) Text("-", fontSize = 18.sp)
            state.events.forEach { Text(it, fontSize = 18.sp) }
        }
    }
}

@Composable
internal fun StatusCard(title: String, value: String) {
    Card {
        Column(modifier = Modifier.padding(32.dp)) {
            Text(title, fontSize = 22.sp)
            Text(value, fontSize = 40.sp)
        }
    }
}

private fun GearState.label() = when (this) {
    GearState.PARK -> "P"
    GearState.DRIVE -> "D"
    GearState.REVERSE -> "R"
    GearState.NEUTRAL -> "N"
    GearState.UNKNOWN -> "?"
}
