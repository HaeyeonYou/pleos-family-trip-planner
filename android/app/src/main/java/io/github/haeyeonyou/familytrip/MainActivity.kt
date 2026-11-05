package io.github.haeyeonyou.familytrip

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.haeyeonyou.familytrip.location.FUSED_LOCATION_PERMISSION
import io.github.haeyeonyou.familytrip.ui.CourseScreen
import io.github.haeyeonyou.familytrip.ui.DevScreen
import io.github.haeyeonyou.familytrip.ui.GuideScreen
import io.github.haeyeonyou.familytrip.ui.InputScreen
import io.github.haeyeonyou.familytrip.ui.NotificationOffBanner
import io.github.haeyeonyou.familytrip.ui.TripViewModel

private object Routes {
    const val INPUT = "input"
    const val COURSE = "course"
    const val GUIDE = "guide"
    const val DEV = "dev"
}

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as FamilyTripApp).container }

    /** 알림 권한 상태. 설정에서 바꾸고 돌아올 수 있어 onResume마다 다시 읽는다. */
    private val notificationsAllowed = mutableStateOf(true)

    private val notificationRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationsAllowed.value = granted
            // 두 번 거부하면 시스템이 더는 묻지 않으므로 앱 알림 설정으로 보낸다.
            if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                openNotificationSettings()
            }
        }

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            container.startLocationIfPermitted()
            notificationsAllowed.value = hasNotificationPermission()
        }

    override fun onResume() {
        super.onResume()
        notificationsAllowed.value = hasNotificationPermission()
    }

    private fun hasNotificationPermission() =
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        runCatching { startActivity(intent) }.recoverCatching { startActivity(fallback) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container.startLocationIfPermitted()
        // 위치(도착 판정), Android 위치(안내 중 포그라운드 서비스), 알림(내비가 앞에 있을 때 도착 안내)
        val missing = listOf(
            FUSED_LOCATION_PERMISSION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissionRequest.launch(missing.toTypedArray())

        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TripViewModel(container) as T
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                  Column {
                    NotificationOffBanner(!notificationsAllowed.value) {
                        notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    val vm: TripViewModel = viewModel(factory = factory)
                    val state by vm.state.collectAsStateWithLifecycle()
                    val vehicle by vm.vehicle.collectAsStateWithLifecycle()
                    val guide by vm.guide.collectAsStateWithLifecycle()
                    val nav = rememberNavController()
                    // 알림으로 앱을 다시 열었을 때 안내 중이면 안내 화면부터 (처음 한 번만 정한다)
                    val start = remember { if (vm.guide.value.active) Routes.GUIDE else Routes.INPUT }

                    NavHost(navController = nav, startDestination = start) {
                        composable(Routes.INPUT) {
                            InputScreen(
                                state = state,
                                isParked = vehicle.isParked,
                                onTextChange = vm::onRequestTextChange,
                                onParse = vm::parse,
                                onConditionsChange = vm::updateConditions,
                                onMakeCourse = { vm.requestCourse { nav.navigate(Routes.COURSE) } },
                                onOpenDev = { nav.navigate(Routes.DEV) },
                            )
                        }
                        composable(Routes.COURSE) {
                            state.course?.let { course ->
                                CourseScreen(
                                    course = course,
                                    isParked = vehicle.isParked,
                                    onStartGuide = {
                                        vm.startGuide()
                                        nav.navigate(Routes.GUIDE)
                                    },
                                    onBack = { nav.popBackStack() },
                                )
                            }
                        }
                        composable(Routes.GUIDE) {
                            GuideScreen(
                                state = guide,
                                isParked = vehicle.isParked,
                                onShowMap = container.navi::showNavigation,
                                onNext = vm::goToNext,
                                onMarkArrived = vm::markArrived,
                                onEnd = {
                                    vm.endGuide()
                                    if (!nav.popBackStack(Routes.INPUT, inclusive = false)) {
                                        nav.navigate(Routes.INPUT) { popUpTo(0) }
                                    }
                                },
                            )
                        }
                        composable(Routes.DEV) {
                            DevScreen(container, onBack = { nav.popBackStack() })
                        }
                    }
                  }
                }
            }
        }
    }
}
