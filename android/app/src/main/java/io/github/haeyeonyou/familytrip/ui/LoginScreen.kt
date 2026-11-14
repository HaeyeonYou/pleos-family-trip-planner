package io.github.haeyeonyou.familytrip.ui

import io.github.haeyeonyou.familytrip.data.AppLog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.haeyeonyou.familytrip.data.Account
import io.github.haeyeonyou.familytrip.data.CarLoginPoll
import io.github.haeyeonyou.familytrip.data.CarLoginStart
import io.github.haeyeonyou.familytrip.data.CarLoginStarted
import io.github.haeyeonyou.familytrip.data.TripApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val TAG = "CarLogin"
private const val POLL_MS = 2_000L
private const val RETRY_MS = 5_000L

/**
 * 차 로그인 화면. 서버에 로그인 요청을 만들고 QR을 띄운 뒤, 폰에서 승인할 때까지 폴링한다.
 * QR이 만료되면 새 요청을 만든다. 차에서는 키보드를 쓰지 않는다 (가입·로그인은 폰에서).
 */
@Composable
fun LoginScreen(
    api: TripApi,
    isParked: Boolean,
    onLoggedIn: (Account) -> Unit,
    onSkip: () -> Unit,
) = DrivingAware(isParked) {
    var started by remember { mutableStateOf<CarLoginStarted?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                val request = api.startCarLogin(CarLoginStart(deviceName = "Pleos Connect 차량"))
                started = request
                error = null
                while (true) {
                    delay(POLL_MS)
                    val result = api.pollCarLogin(request.requestId, CarLoginPoll(request.pollSecret))
                    if (result.status == "approved" && result.token != null && result.user != null) {
                        onLoggedIn(Account(result.token, result.user))
                        return@LaunchedEffect
                    }
                    if (result.status != "pending") break // 만료되면 새 QR
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(TAG, "car login failed", e)
                started = null
                error = "서버에 연결하지 못했어요. 잠시 후 다시 시도할게요."
                delay(RETRY_MS)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("차곡", fontSize = 40.sp)
        Text(
            "폰 카메라로 QR을 찍어 회원가입하거나 로그인해 주세요.\n폰에서 '이 차에서 로그인'을 누르면 바로 시작돼요.",
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val request = started
        if (request == null) {
            if (error == null) CircularProgressIndicator() else Text(error!!, fontSize = 22.sp, color = MaterialTheme.colorScheme.error)
        } else {
            AsyncImage(model = request.qrUrl, contentDescription = "로그인 QR", modifier = Modifier.size(360.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("QR이 안 찍히면 폰에서 주소를 열어 주세요:", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(request.loginUrl, fontSize = 18.sp)
            }
        }
        TextButton(onClick = onSkip) { Text("로그인 없이 사용하기", fontSize = 20.sp) }
    }
}
