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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.haeyeonyou.familytrip.location.FUSED_LOCATION_PERMISSION
import io.github.haeyeonyou.familytrip.ui.AppShell
import io.github.haeyeonyou.familytrip.ui.DriveMode
import io.github.haeyeonyou.familytrip.ui.DelayAlertPanel
import io.github.haeyeonyou.familytrip.care.CareKind
import io.github.haeyeonyou.familytrip.ui.CareActions
import io.github.haeyeonyou.familytrip.ui.CareScreen
import io.github.haeyeonyou.familytrip.ui.CourseEditActions
import io.github.haeyeonyou.familytrip.ui.FamilyTripTheme
import io.github.haeyeonyou.familytrip.ui.MainTab
import io.github.haeyeonyou.familytrip.ui.SettingsActions
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
import io.github.haeyeonyou.familytrip.ui.VoicePanel
import io.github.haeyeonyou.familytrip.data.VoiceScreen
import io.github.haeyeonyou.familytrip.voice.VoiceFilter
import io.github.haeyeonyou.familytrip.voice.VoiceHost
import io.github.haeyeonyou.familytrip.voice.VoiceTab
import io.github.haeyeonyou.familytrip.voice.etaText
import io.github.haeyeonyou.familytrip.voice.weatherText
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

    /** 마이크: 허용되면 바로 듣기를 시작한다. 거부돼도 문장 버튼으로 쓸 수 있어 패널은 연다. */
    private val micRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { container.voice.startListening() }

    private fun onMicClick() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) container.voice.startListening()
        else micRequest.launch(Manifest.permission.RECORD_AUDIO)
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
                    // 앱이 화면에 보이는 동안만 웹(폰)의 변경을 주기적으로 확인한다. 웹이 차 연결을 끊으면(401) 로그인 화면으로.
                    val lifecycleOwner = LocalLifecycleOwner.current
                    LaunchedEffect(lifecycleOwner) {
                        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            vm.runSyncLoop(onUnauthorized = { nav.navigate(Routes.LOGIN) { popUpTo(0) } })
                        }
                    }
                    val visitRevision by container.visits.revision.collectAsStateWithLifecycle()
                    // 그로우 캐릭터 탄생 (#164): 주행 중에는 화면 없이 음성 1회(B안), 정차 중 축하 시트는 그로우 화면이 띄운다.
                    var celebrated by remember { mutableStateOf(container.births.celebrated()) }
                    LaunchedEffect(visitRevision, vehicle.gear) {
                        // 기어를 아직 모르면(앱 시작 직후) 주행으로 보지 않는다. 정차면 그로우 화면의 시트가 알린다.
                        if (vehicle.isParked || vehicle.gear == io.github.haeyeonyou.familytrip.vehicle.GearState.UNKNOWN) return@LaunchedEffect
                        val born = io.github.haeyeonyou.familytrip.grow.birthsIn(container.visits.all(), celebrated)
                        if (born.isEmpty()) return@LaunchedEffect
                        container.births.markCelebrated(born)
                        celebrated = container.births.celebrated()
                        container.voiceIo.speak(io.github.haeyeonyou.familytrip.grow.birthSpeech(state.profile?.childName.orEmpty(), born))
                    }
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

                    val voice by container.voice.state.collectAsStateWithLifecycle()
                    // 음성 명령이 화면 기능(탭 이동·코스 조건 등)을 부르는 통로
                    val latestState by rememberUpdatedState(state)
                    val latestVehicle by rememberUpdatedState(vehicle)
                    val latestGuide by rememberUpdatedState(guide)
                    val latestRoute by rememberUpdatedState(route ?: start)
                    DisposableEffect(vm, nav) {
                        container.voice.host = object : VoiceHost {
                            override val driving get() = !latestVehicle.isParked

                            override fun screen() = VoiceScreen(
                                tab = when (tabOf(latestRoute)) {
                                    MainTab.CARE -> "care"
                                    MainTab.GROW -> "grow"
                                    MainTab.SETTINGS -> "settings"
                                    else -> "home"
                                },
                                hasCourse = latestState.course != null,
                                guiding = latestGuide.active,
                                babyCare = container.care.state.value.babyCare,
                                sleepMode = container.care.state.value.sleepMode,
                                childMonths = latestState.profile?.ageMonths()?.minOrNull(),
                                hasHomeOptions = latestState.options.isNotEmpty(),
                            )

                            override fun goTab(tab: VoiceTab) = openTab(
                                when (tab) {
                                    VoiceTab.HOME -> MainTab.HOME
                                    VoiceTab.CARE -> MainTab.CARE
                                    VoiceTab.GROW -> MainTab.GROW
                                    VoiceTab.SETTINGS -> MainTab.SETTINGS
                                },
                            )

                            override fun setFilter(filter: VoiceFilter, on: Boolean) {
                                openTab(MainTab.HOME)
                                vm.toggleFilter {
                                    when (filter) {
                                        VoiceFilter.PET -> it.copy(pet = on)
                                        VoiceFilter.INDOOR -> it.copy(indoor = on)
                                        VoiceFilter.NURSING_ROOM -> it.copy(nursingRoom = on)
                                        VoiceFilter.STROLLER -> it.copy(stroller = on)
                                        VoiceFilter.KIDS_MENU -> it.copy(kidsMenu = on)
                                        VoiceFilter.PLAYROOM -> it.copy(playroom = on)
                                        VoiceFilter.PARKING -> it.copy(parking = on)
                                    }
                                }
                            }

                            override fun anotherCourses() {
                                openTab(MainTab.HOME)
                                vm.anotherOptions()
                            }

                            override fun openCourse(number: Int): Boolean {
                                val option = latestState.options.getOrNull(number - 1) ?: return false
                                vm.openOption(option)
                                nav.navigate(Routes.COURSE)
                                return true
                            }

                            override fun startCourse(): Boolean {
                                if (latestState.course == null) return false
                                vm.startGuide()
                                nav.navigate(Routes.GUIDE)
                                return true
                            }

                            override fun etaAnswer(): String = etaText(container.navi.state.value, latestGuide)

                            override fun weatherAnswer(): String = weatherText(latestState.weather)
                        }
                        onDispose { container.voice.host = null }
                    }

                    AppShell(tab = tabOf(route ?: start), onTab = ::openTab, onMic = ::onMicClick, drive = DriveMode.from(vehicle)) {
                    Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(navController = nav, startDestination = start) {
                        composable(Routes.LOGIN) {
                            LoginScreen(
                                api = container.api,
                                isParked = vehicle.isParked,
                                onLoggedIn = {
                                    container.account.save(it)
                                    vm.syncNow()
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
                                // 서버에서 받은 변경으로 기록이 바뀌면 다시 읽는다.
                                visits = remember(visitRevision) { container.visits.all() },
                                saved = remember { container.courses.all() },
                                actions = HomeActions(
                                    load = vm::loadHome,
                                    toggleFilter = vm::toggleFilter,
                                    retry = vm::retryHome,
                                    another = vm::anotherOptions,
                                    openOption = {
                                        vm.openOption(it)
                                        nav.navigate(Routes.COURSE)
                                    },
                                    startOption = {
                                        vm.openOption(it)
                                        vm.startGuide()
                                        nav.navigate(Routes.GUIDE)
                                    },
                                    onMic = ::onMicClick,
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
                                onDismissPreset = { vm.dismissPreset(it) },
                            )
                        }
                        composable(Routes.CARE) {
                            val care = container.care
                            val careState by care.state.collectAsStateWithLifecycle()
                            val careDelay by container.delayAlert.ui.collectAsStateWithLifecycle()
                            CareScreen(
                                state = state,
                                care = careState,
                                delayAlert = careDelay,
                                isParked = vehicle.isParked,
                                actions = CareActions(
                                    // 폰에서 보낸 공조 프리셋이 있으면 그 값으로 베이비케어를 맞춘다.
                                    onBabyCare = { on -> care.setBabyCare(on, state.babyCarePreset, onApplied = { vm.dismissPreset("boarding") }) },
                                    onFedAt = { at -> care.markFed(at) },
                                    onFind = { kind -> scope.launch { care.findNearby(kind) } },
                                    onOpenUrgent = { kind -> care.openUrgent(kind) },
                                    onCloseUrgent = care::closeUrgent,
                                    onAddWaypoint = { place -> care.addWaypoint(place, showNavigation = false) },
                                    onUndoWaypoint = care::undoWaypoint,
                                    onContinueGuidance = care::continueGuidance,
                                    onStartFeedingStop = { care.startFeedingStop() },
                                    onFinishFeedingStop = { care.finishFeedingStop() },
                                    onDismissFeedingStopAsk = care::dismissFeedingStopAsk,
                                    onSleepMode = care::setSleepMode,
                                    onDelayAddWaypoint = container.delayAlert::addWaypoint,
                                    onDelayKeepGoing = container.delayAlert::keepGoing,
                                    onApplyPreset = vm::applyPreset,
                                    onSavePreset = vm::savePreset,
                                    onSaveModes = vm::savePresetEdits,
                                    onDismissPreset = { vm.dismissPreset(it) },
                                    onEnter = {
                                        care.refreshClimate()
                                        care.refreshFrequent()
                                        scope.launch { CareKind.cards.forEach { care.findNearby(it) } }
                                    },
                                ),
                            )
                        }
                        composable(Routes.SETTINGS) {
                            // 수유 간격은 케어 화면과 같은 값이라 케어 상태를 읽고 바꾼다.
                            val care = container.care
                            val careState by care.state.collectAsStateWithLifecycle()
                            SettingsScreen(
                                profile = state.profile,
                                account = account?.user,
                                isParked = vehicle.isParked,
                                prefs = state.prefs,
                                feeding = careState.feeding,
                                nap = state.nap,
                                actions = SettingsActions(
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
                                    onPreference = vm::setPreference,
                                    onInterval = care::setFeedingInterval,
                                    onNap = vm::setNap,
                                    onWipe = vm::wipeData,
                                ),
                            )
                        }
                        composable(Routes.EXPERIENCE) {
                            // 도착·떠남이나 서버(웹)에서 받은 변경으로 기록이 바뀌면 다시 읽는다.
                            ExperienceScreen(
                                visits = remember(visitRevision) { container.visits.all() },
                                courses = remember { container.courses.all() },
                                children = state.profile?.children.orEmpty(),
                                weather = state.weather,
                                celebrated = celebrated,
                                onCelebrated = {
                                    container.births.markCelebrated(it)
                                    celebrated = container.births.celebrated()
                                },
                                onOpenCourse = {
                                    vm.openSavedCourse(it)
                                    nav.navigate(Routes.COURSE)
                                },
                                onRoute = { name, lat, lon -> container.navi.requestRoute(io.github.haeyeonyou.familytrip.navi.Place(name, lat, lon)) },
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
                                    weather = state.weather,
                                    origin = state.courseOrigin,
                                    going = course.stops.firstOrNull { it.role == "outing" }?.place?.let { state.going["${it.source}:${it.sourceId}"] } ?: 0,
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
                    val delayAlert by container.delayAlert.ui.collectAsStateWithLifecycle()
                    DelayAlertPanel(
                        // 정차 중에 케어 화면을 보고 있으면 오른쪽 슬롯 카드가 알려 주므로 전체 화면으로 덮지 않는다 (주행 중에는 그대로).
                        alert = delayAlert.takeUnless { vehicle.isParked && route == Routes.CARE },
                        onAddWaypoint = container.delayAlert::addWaypoint,
                        onKeepGoing = container.delayAlert::keepGoing,
                    )
                    VoicePanel(
                        state = voice,
                        isParked = vehicle.isParked,
                        onSubmit = container.voice::submit,
                        onListen = ::onMicClick,
                        onClose = container.voice::close,
                    )
                    }
                    }
                  }
                }
            }
        }
    }
}
