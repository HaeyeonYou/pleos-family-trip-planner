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
import androidx.compose.ui.Alignment
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
import io.github.haeyeonyou.familytrip.guide.ArrivalChoice
import io.github.haeyeonyou.familytrip.guide.classifyArrivalReply
import io.github.haeyeonyou.familytrip.guide.stageOf
import io.github.haeyeonyou.familytrip.ui.GuideActions
import io.github.haeyeonyou.familytrip.ui.GuidePicker
import io.github.haeyeonyou.familytrip.ui.GuideScreen
import io.github.haeyeonyou.familytrip.ui.HomeActions
import io.github.haeyeonyou.familytrip.ui.HomeScreen
import io.github.haeyeonyou.familytrip.ui.InputScreen
import io.github.haeyeonyou.familytrip.ui.LoginScreen
import io.github.haeyeonyou.familytrip.ui.NotificationOffBanner
import io.github.haeyeonyou.familytrip.ui.notificationBannerVisible
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.haeyeonyou.familytrip.onboarding.LocalVoiceEnabled
import io.github.haeyeonyou.familytrip.onboarding.OnboardingMode
import io.github.haeyeonyou.familytrip.onboarding.withChild
import io.github.haeyeonyou.familytrip.vehicle.isElectric

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
    /** 온보딩 O01~O06 (#184). 설정 > 아이 정보는 아이 단계(O04·O05)만 */
    const val ONBOARDING = "onboarding"
    const val ONBOARDING_CHILDREN = "onboarding_children"
}

/** 화면이 속한 왼쪽 메뉴. 로그인 화면은 메뉴 없이 보인다. */
private fun tabOf(route: String?): MainTab? = when (route) {
    null, Routes.LOGIN, Routes.ONBOARDING, Routes.ONBOARDING_CHILDREN -> null
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

    private fun needsOnboarding() = io.github.haeyeonyou.familytrip.onboarding.OnboardingFlow.startsWithOnboarding(
        guideActive = container.guide.state.value.active,
        done = container.onboarding.done,
        loggedIn = container.account.account.value != null,
        profile = container.profile.load(),
    )

    /** 온보딩 O03 `다음`: 고른 항목에 필요한 권한을 묻고, 답이 오면(거절해도) 다음으로 간다. */
    private var onboardingPermissionDone: (() -> Unit)? = null
    private val onboardingPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            container.startLocationIfPermitted()
            notificationsAllowed.value = hasNotificationPermission()
            onboardingPermissionDone?.invoke()
            onboardingPermissionDone = null
        }

    private fun requestOnboardingPermissions(choices: io.github.haeyeonyou.familytrip.onboarding.LinkChoices, done: () -> Unit) {
        val wanted = buildList {
            // 필수: 차량 정보(에너지)·문/정차(주행)·위치, 도착 안내 알림
            add("pleos.car.permission.CAR_ENERGY")
            add("pleos.car.permission.CAR_DRIVE")
            add(FUSED_LOCATION_PERMISSION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.POST_NOTIFICATIONS)
            if (choices.climate) addAll(listOf("pleos.car.permission.CAR_CLIMATE", "pleos.car.permission.CONTROL_CAR_CLIMATE"))
            if (choices.voice) add(Manifest.permission.RECORD_AUDIO)
        }
        val missing = wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) return done()
        onboardingPermissionDone = done
        onboardingPermissionRequest.launch(missing.toTypedArray())
    }

    /** 아이 이름을 음성으로 한 번 듣는다 (마이크 권한이 없으면 먼저 묻는다). */
    private var pendingNameListen: ((String) -> Unit)? = null
    private val nameMicRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val cb = pendingNameListen
            pendingNameListen = null
            if (granted && cb != null) listenName(cb)
        }

    private fun listenName(onHeard: (String) -> Unit) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingNameListen = onHeard
            nameMicRequest.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        container.voiceIo.listen { result ->
            if (result is io.github.haeyeonyou.familytrip.voice.ListenResult.Heard) onHeard(result.text)
        }
    }

    private fun hasNotificationPermission() =
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        runCatching { startActivity(intent) }.recoverCatching { startActivity(fallback) }
    }

    /** 온보딩 화면 (#184). 저장은 기존 저장소(가족 정보·낮잠·선호 조건·케어 아이 선택)를 그대로 쓴다. */
    @Composable
    private fun OnboardingRoute(vm: TripViewModel, state: io.github.haeyeonyou.familytrip.ui.TripUiState, isParked: Boolean, mode: OnboardingMode, onDone: () -> Unit) {
        val care by container.care.state.collectAsStateWithLifecycle()
        val presets by container.serverSync.presets.collectAsStateWithLifecycle()
        val allPresets by container.serverSync.allPresets.collectAsStateWithLifecycle()
        val choices by container.onboarding.choices.collectAsStateWithLifecycle()
        val profile by container.profile.profile.collectAsStateWithLifecycle()
        var placesTick by remember { mutableStateOf(0) }
        io.github.haeyeonyou.familytrip.ui.onboarding.OnboardingScreen(
            mode = mode,
            api = container.api,
            profile = profile,
            isParked = isParked,
            presets = presets,
            allPresets = allPresets,
            carePlaceNames = remember(placesTick) { container.serverSync.carePlaces.cached().map { it.placeName } },
            selectedChildId = care.selectedChildId,
            choices = choices,
            firstRecommendation = state.options.firstOrNull()?.name,
            actions = io.github.haeyeonyou.familytrip.ui.onboarding.OnboardingActions(
                onLoggedIn = { account ->
                    container.account.save(account)
                    // 가족·수유·방문·프리셋을 지금 받아 O02 오른쪽 목록에 체크한다 (실패한 영역은 다음 주기에 다시)
                    container.serverSync.refresh(io.github.haeyeonyou.familytrip.data.SyncChange(presets = true, feedings = true, visits = true, family = true, cars = true))
                    runCatching { container.serverSync.carePlaces.reconcile() }
                    placesTick++
                    vm.syncNow()
                },
                climateSupported = { runCatching { container.vehicle.readClimate().supported }.getOrNull() },
                requestPermissions = ::requestOnboardingPermissions,
                saveChoices = container.onboarding::saveChoices,
                listenName = ::listenName,
                saveChild = { draft ->
                    vm.saveProfile(container.profile.load().withChild(draft))
                    draft.nap?.window?.let(vm::setNap)
                    if (io.github.haeyeonyou.familytrip.onboarding.TagAlong.STROLLER in draft.tags) vm.setPreference(io.github.haeyeonyou.familytrip.data.Preference.STROLLER, true)
                },
                selectChild = container.care::selectChild,
                saveModes = vm::saveChildPresetEdits,
                finish = {
                    container.onboarding.markDone()
                    onDone()
                },
                close = onDone,
            ),
        )
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
        // 첫 실행은 온보딩 O03에서 권한을 묻는다 (QO6)
        if (missing.isNotEmpty() && !needsOnboarding()) permissionRequest.launch(missing.toTypedArray())

        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TripViewModel(container) as T
        }

        setContent {
            FamilyTripTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                  Column {
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
                            // 처음 실행이면 온보딩부터 (끝냈거나 '나중에 하기'를 눌렀으면 홈, QO7)
                            needsOnboarding() -> Routes.ONBOARDING
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
                    // 코스 진행 (#218): 오른쪽 고르기 패널(장소 바꾸기·한 곳 더). 음성으로도 열고, 장소가 바뀌면 닫는다.
                    var guidePicker by remember { mutableStateOf<GuidePicker?>(null) }
                    LaunchedEffect(guide.index, guide.arrived, guide.finished) { guidePicker = null }
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
                                places = io.github.haeyeonyou.familytrip.grow.voicePlaceNames(growExperiences()),
                            )

                            // 그로우 음성 명령 (#185)
                            override fun growExperiences() = io.github.haeyeonyou.familytrip.grow.experiences(container.visits.all())

                            override fun showGrow(view: io.github.haeyeonyou.familytrip.grow.GrowView) {
                                io.github.haeyeonyou.familytrip.grow.GrowVoiceBus.show(view)
                                openTab(MainTab.GROW)
                            }

                            override fun answerArrival(text: String): String? {
                                val g = latestGuide
                                val choice = classifyArrivalReply(text, stageOf(g)) ?: return null
                                if (latestRoute != Routes.GUIDE) nav.navigate(Routes.GUIDE) { launchSingleTop = true }
                                return when (choice) {
                                    ArrivalChoice.NEXT -> "${g.nextVisit?.place?.name}(으)로 안내할게요.".also { vm.goToNext() }
                                    ArrivalChoice.HOME -> "오늘 코스를 마치고 우리 집으로 안내할게요.".also { vm.goHomeFromGuide() }
                                    ArrivalChoice.END_HERE -> "여기서 코스를 마칠게요.".also { vm.endCourseHere() }
                                    ArrivalChoice.SWAP -> "${g.nextVisit?.place?.name} 대신 갈 곳을 화면에 띄웠어요.".also { guidePicker = GuidePicker.SWAP }
                                    ArrivalChoice.MORE -> "근처 추천 장소를 화면에 띄웠어요.".also { guidePicker = GuidePicker.MORE }
                                }
                            }

                            override fun openCourseWith(placeName: String): Boolean {
                                val saved = container.courses.all().firstOrNull { c -> c.course.stops.any { it.place.name == placeName } } ?: return false
                                vm.openSavedCourse(saved)
                                nav.navigate(Routes.COURSE)
                                return true
                            }

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

                    val linkChoices by container.onboarding.choices.collectAsStateWithLifecycle()
                    CompositionLocalProvider(LocalVoiceEnabled provides linkChoices.voice) {
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
                        composable(Routes.ONBOARDING) { OnboardingRoute(vm, state, vehicle.isParked, OnboardingMode.FIRST_RUN, onDone = ::goHome) }
                        composable(Routes.ONBOARDING_CHILDREN) {
                            OnboardingRoute(vm, state, vehicle.isParked, OnboardingMode.EDIT_CHILDREN, onDone = { nav.popBackStack() })
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
                                    openChildInfo = { nav.navigate(Routes.ONBOARDING_CHILDREN) },
                                    // 연료 타일 (#215): 찾은 주유소·충전소는 안내 중이면 경유지, 아니면 목적지로
                                    fuel = io.github.haeyeonyou.familytrip.ui.FuelActions(
                                        find = vm::findFuel,
                                        go = { st -> container.navi.addWaypointOrRoute(io.github.haeyeonyou.familytrip.navi.Place(st.place.name, st.place.latitude, st.place.longitude)) },
                                    ),
                                ),
                                electric = vehicle.isElectric(),
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
                                    onSelectChild = care::selectChild,
                                    childRoutine = care::routineOf,
                                    serverChildren = { container.serverSync.family.serverChildren() },
                                    allPresets = { container.serverSync.allPresets.value },
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
                                    // 유아기 화면 (#217): 가까운 화장실, 공조 직접 조작, 정차 모드
                                    onFindNearby = { kind, limit -> scope.launch { care.findNearby(kind, limit) } },
                                    onAdjustBabyCare = { settings ->
                                        scope.launch { care.adjustBabyCare(settings) }
                                        vm.savePresetEdits(mapOf("boarding" to settings)) { }
                                    },
                                    onStartRestStop = { care.startFeedingStop(feeding = false) },
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
                                    // 설정 > 아이 정보: 온보딩 O04·O05로 (QO7)
                                    onEditProfile = { nav.navigate(Routes.ONBOARDING_CHILDREN) },
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
                                // 안가봄 줄: 빈 분야 근처 장소 검색 1회 (#216, QF1 ③)
                                searchUnvisited = { targets, visited ->
                                    val loc = container.location.state.value
                                    val origin = if (loc.latitude != null && loc.longitude != null) io.github.haeyeonyou.familytrip.data.LatLng(loc.latitude, loc.longitude) else io.github.haeyeonyou.familytrip.ui.DEFAULT_ORIGIN
                                    io.github.haeyeonyou.familytrip.grow.searchUnvisited(container.api, origin, state.profile, targets, visited)
                                },
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
                                    showFeeding = io.github.haeyeonyou.familytrip.care.anyInfant(state.profile?.children.orEmpty()),
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
                            val navi by container.navi.state.collectAsStateWithLifecycle()
                            fun leaveGuide() {
                                if (!nav.popBackStack(Routes.HOME, inclusive = false)) nav.navigate(Routes.HOME) { popUpTo(0) }
                            }
                            GuideScreen(
                                state = guide,
                                course = state.course,
                                picker = guidePicker,
                                loading = state.loading,
                                error = state.error,
                                weather = state.weather,
                                origin = state.courseOrigin,
                                homeRemainingSeconds = navi.remainingSeconds.takeIf { navi.routing },
                                actions = GuideActions(
                                    onBack = ::leaveGuide,
                                    onShowMap = container.navi::showNavigation,
                                    onMarkArrived = vm::markArrived,
                                    onNext = vm::goToNext,
                                    onSwap = vm::swapNextDuringGuide,
                                    onAddExtra = vm::addExtraDuringGuide,
                                    onGoHome = vm::goHomeFromGuide,
                                    onEndHere = vm::endCourseHere,
                                    onShowJelly = {
                                        vm.endGuide()
                                        openTab(MainTab.GROW)
                                    },
                                    onHome = {
                                        vm.endGuide()
                                        leaveGuide()
                                    },
                                    onPicker = { guidePicker = it },
                                ),
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
                    // 첫 실행 온보딩 동안은 띄우지 않고, 앱 프레임을 줄이지 않게 위쪽(음성 바 안내 문구 자리)에 떠 있게 그린다 (O-1).
                    NotificationOffBanner(
                        visible = notificationBannerVisible(notificationsAllowed.value, onboarding = (route ?: start) == Routes.ONBOARDING || route == Routes.ONBOARDING_CHILDREN),
                        onAllow = { notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.align(Alignment.TopCenter),
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
}
