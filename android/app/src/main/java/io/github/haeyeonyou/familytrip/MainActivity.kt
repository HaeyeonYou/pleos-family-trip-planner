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
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.haeyeonyou.familytrip.location.FUSED_LOCATION_PERMISSION
import io.github.haeyeonyou.familytrip.ui.AppShell
import io.github.haeyeonyou.familytrip.ui.CareScreen
import io.github.haeyeonyou.familytrip.ui.CourseEditActions
import io.github.haeyeonyou.familytrip.ui.FamilyTripTheme
import io.github.haeyeonyou.familytrip.ui.MainTab
import io.github.haeyeonyou.familytrip.ui.SettingsScreen
import io.github.haeyeonyou.familytrip.ui.CourseScreen
import io.github.haeyeonyou.familytrip.ui.DevScreen
import io.github.haeyeonyou.familytrip.ui.ExperienceScreen
import io.github.haeyeonyou.familytrip.ui.GuideScreen
import io.github.haeyeonyou.familytrip.ui.HomeActions
import io.github.haeyeonyou.familytrip.ui.HomeScreen
import io.github.haeyeonyou.familytrip.ui.InputScreen
import io.github.haeyeonyou.familytrip.ui.LoginScreen
import io.github.haeyeonyou.familytrip.ui.NotificationOffBanner
import io.github.haeyeonyou.familytrip.ui.ProfileScreen
import io.github.haeyeonyou.familytrip.ui.TripViewModel
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val INPUT = "input"
    const val COURSE = "course"
    const val GUIDE = "guide"
    const val DEV = "dev"
    const val PROFILE = "profile"
    const val EXPERIENCE = "experience"
    const val LOGIN = "login"
    const val CARE = "care"
    const val SETTINGS = "settings"
}

/** 화면이 속한 왼쪽 메뉴. 로그인 화면은 메뉴 없이 보인다. */
private fun tabOf(route: String?): MainTab? = when (route) {
    null, Routes.LOGIN -> null
    Routes.CARE -> MainTab.CARE
    Routes.EXPERIENCE -> MainTab.GROW
    Routes.SETTINGS, Routes.PROFILE, Routes.DEV -> MainTab.SETTINGS
    else -> MainTab.HOME
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
            FamilyTripTheme {
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
                    val account by container.account.account.collectAsStateWithLifecycle()
                    val scope = rememberCoroutineScope()
                    val start = remember {
                        when {
                            vm.guide.value.active -> Routes.GUIDE
                            // 처음 실행·로그아웃 상태면 로그인 QR부터 (이번 실행에서 건너뛰면 묻지 않음)
                            container.account.account.value == null && !container.account.skipped -> Routes.LOGIN
                            else -> Routes.HOME
                        }
                    }
                    fun goHome() = nav.navigate(Routes.HOME) { popUpTo(0) }
                    val backStack by nav.currentBackStackEntryAsState()
                    val route = backStack?.destination?.route
                    fun openTab(tab: MainTab) {
                        val target = when (tab) {
                            MainTab.HOME -> if (guide.active) Routes.GUIDE else Routes.HOME
                            MainTab.CARE -> Routes.CARE
                            MainTab.GROW -> Routes.EXPERIENCE
                            MainTab.SETTINGS -> Routes.SETTINGS
                        }
                        // 그로우에서 연 코스 화면 등 그 탭의 하위 화면에 있으면 탭 화면까지 되돌아간다.
                        if (nav.popBackStack(target, inclusive = false)) return
                        nav.navigate(target) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }

                    AppShell(tab = tabOf(route ?: start), onTab = ::openTab) {
                    NavHost(navController = nav, startDestination = start) {
                        composable(Routes.LOGIN) {
                            LoginScreen(
                                api = container.api,
                                isParked = vehicle.isParked,
                                onLoggedIn = {
                                    container.account.save(it)
                                    vm.checkPendingPreset()
                                    goHome()
                                },
                                onSkip = {
                                    container.account.skipped = true
                                    goHome()
                                },
                            )
                        }
                        composable(Routes.HOME) {
                            HomeScreen(
                                state = state,
                                isParked = vehicle.isParked,
                                rangeMeters = vehicle.rangeRemainingMeters,
                                // 화면에 들어올 때마다 차 안 기록을 다시 읽는다.
                                visits = remember { container.visits.all() },
                                saved = remember { container.courses.all() },
                                actions = HomeActions(
                                    load = vm::loadHome,
                                    toggleFilter = vm::toggleFilter,
                                    retry = vm::retryHome,
                                    openOption = {
                                        vm.openOption(it)
                                        nav.navigate(Routes.COURSE)
                                    },
                                    changeConditions = { nav.navigate(Routes.INPUT) },
                                    openCare = { openTab(MainTab.CARE) },
                                    openGrow = { openTab(MainTab.GROW) },
                                    reopenCourse = {
                                        vm.openSavedCourse(it)
                                        nav.navigate(Routes.COURSE)
                                    },
                                    fed = vm::markFed,
                                    feedingInterval = vm::setFeedingInterval,
                                ),
                            )
                        }
                        composable(Routes.INPUT) {
                            InputScreen(
                                state = state,
                                isParked = vehicle.isParked,
                                onTextChange = vm::onRequestTextChange,
                                onParse = vm::parse,
                                onConditionsChange = vm::updateConditions,
                                onMakeCourse = { vm.requestCourse { nav.navigate(Routes.COURSE) } },
                                onEditProfile = { nav.navigate(Routes.PROFILE) },
                                onUseProfile = vm::conditionsFromProfile,
                                onApplyPreset = vm::applyPreset,
                                onDismissPreset = vm::dismissPreset,
                            )
                        }
                        composable(Routes.CARE) {
                            CareScreen(
                                state = state,
                                isParked = vehicle.isParked,
                                onApplyPreset = vm::applyPreset,
                                onDismissPreset = vm::dismissPreset,
                                onFed = vm::markFed,
                                onInterval = vm::setFeedingInterval,
                            )
                        }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                profile = state.profile,
                                account = account?.user,
                                isParked = vehicle.isParked,
                                onEditProfile = { nav.navigate(Routes.PROFILE) },
                                onLogin = { nav.navigate(Routes.LOGIN) },
                                onLogout = {
                                    scope.launch {
                                        // 서버 세션을 먼저 폐기하고, 연결이 안 돼도 차에서는 지운다.
                                        runCatching { container.api.logout() }
                                        container.account.clear()
                                        nav.navigate(Routes.LOGIN) { popUpTo(0) }
                                    }
                                },
                                onOpenDev = { nav.navigate(Routes.DEV) },
                            )
                        }
                        composable(Routes.EXPERIENCE) {
                            // 화면에 들어올 때마다 차 안 기록을 다시 읽는다.
                            ExperienceScreen(
                                visits = remember { container.visits.all() },
                                courses = remember { container.courses.all() },
                                childName = state.profile?.childName.orEmpty(),
                                isParked = vehicle.isParked,
                                onOpenCourse = {
                                    vm.openSavedCourse(it)
                                    nav.navigate(Routes.COURSE)
                                },
                                onPlan = ::goHome,
                            )
                        }
                        composable(Routes.PROFILE) {
                            ProfileScreen(
                                initial = state.profile,
                                isParked = vehicle.isParked,
                                onSave = {
                                    vm.saveProfile(it)
                                    nav.popBackStack()
                                },
                                onBack = { nav.popBackStack() },
                            )
                        }
                        composable(Routes.COURSE) {
                            state.course?.let { course ->
                                CourseScreen(
                                    course = course,
                                    isParked = vehicle.isParked,
                                    loading = state.loading,
                                    error = state.error,
                                    message = state.message,
                                    feeding = state.feeding,
                                    edit = CourseEditActions(
                                        swap = vm::swapPlace,
                                        move = vm::moveStop,
                                        remove = vm::removeStop,
                                        changeStay = vm::changeStay,
                                        add = vm::addStop,
                                    ),
                                    onSave = vm::saveCourse,
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
                                onShowRecord = { nav.navigate(Routes.EXPERIENCE) },
                                onEnd = {
                                    vm.endGuide()
                                    if (!nav.popBackStack(Routes.HOME, inclusive = false)) {
                                        nav.navigate(Routes.HOME) { popUpTo(0) }
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
}
