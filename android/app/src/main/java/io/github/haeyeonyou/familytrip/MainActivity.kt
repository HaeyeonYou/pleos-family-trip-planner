package io.github.haeyeonyou.familytrip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.haeyeonyou.familytrip.vehicle.GearState
import io.github.haeyeonyou.familytrip.vehicle.PleosVehicleRepository
import io.github.haeyeonyou.familytrip.vehicle.VehicleRepository
import io.github.haeyeonyou.familytrip.vehicle.VehicleState

class MainActivity : ComponentActivity() {

    private lateinit var vehicleRepository: VehicleRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vehicleRepository = PleosVehicleRepository(this)
        vehicleRepository.start()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by vehicleRepository.state.collectAsStateWithLifecycle()
                    VehicleStatusScreen(state)
                }
            }
        }
    }

    override fun onDestroy() {
        vehicleRepository.stop()
        super.onDestroy()
    }
}

@Composable
private fun VehicleStatusScreen(state: VehicleState) {
    Column(
        modifier = Modifier.padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("오늘, 어디 가지?", fontSize = 40.sp)
        Text("차량 상태 (Vehicle SDK 확인용)", fontSize = 24.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            StatusCard("배터리", state.stateOfChargePercent?.let { "$it%" } ?: "-")
            StatusCard("주행가능거리", state.rangeRemainingMeters?.let { "%.0f km".format(it / 1000f) } ?: "-")
            StatusCard("기어", state.gear.label())
            StatusCard("상태", if (state.isParked) "정차 중" else "주행 중")
        }
        state.lastError?.let { Text("오류: $it", color = MaterialTheme.colorScheme.error, fontSize = 20.sp) }
    }
}

@Composable
private fun StatusCard(title: String, value: String) {
    Card {
        Column(modifier = Modifier.padding(32.dp)) {
            Text(title, fontSize = 22.sp)
            Text(value, fontSize = 48.sp)
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
