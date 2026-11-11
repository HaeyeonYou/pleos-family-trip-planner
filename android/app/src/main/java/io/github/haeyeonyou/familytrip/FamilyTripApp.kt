package io.github.haeyeonyou.familytrip

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk
import android.content.pm.PackageManager
import io.github.haeyeonyou.familytrip.care.CareController
import io.github.haeyeonyou.familytrip.data.AccountStore
import io.github.haeyeonyou.familytrip.data.CourseStore
import io.github.haeyeonyou.familytrip.data.FeedingStore
import io.github.haeyeonyou.familytrip.data.ProfileStore
import io.github.haeyeonyou.familytrip.data.ServerSync
import io.github.haeyeonyou.familytrip.data.SettingsStore
import io.github.haeyeonyou.familytrip.data.TripApi
import io.github.haeyeonyou.familytrip.data.VisitStore
import io.github.haeyeonyou.familytrip.guide.GuideNotifier
import io.github.haeyeonyou.familytrip.guide.GuideSession
import io.github.haeyeonyou.familytrip.location.FUSED_LOCATION_PERMISSION
import io.github.haeyeonyou.familytrip.location.LocationRepository
import io.github.haeyeonyou.familytrip.location.PleosLocationRepository
import io.github.haeyeonyou.familytrip.navi.DelayAlertController
import io.github.haeyeonyou.familytrip.navi.NaviRepository
import io.github.haeyeonyou.familytrip.navi.Place
import io.github.haeyeonyou.familytrip.navi.PleosNaviRepository
import io.github.haeyeonyou.familytrip.voice.VoiceController
import io.github.haeyeonyou.familytrip.voice.VoiceIo
import io.github.haeyeonyou.familytrip.vehicle.PleosVehicleRepository
import io.github.haeyeonyou.familytrip.vehicle.VehicleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map

/**
 * 앱 전체에서 하나씩 쓰는 의존성. 차량·내비 연결은 프로세스가 살아 있는 동안 유지한다.
 * 데모 모드(#21)에서는 여기서 시뮬레이션 구현으로 바꿔 끼운다.
 */
class AppContainer(private val app: Application) {
    val vehicle: VehicleRepository = PleosVehicleRepository(app)
    val navi: NaviRepository = PleosNaviRepository(app)
    val location: LocationRepository = PleosLocationRepository(app)
    val account = AccountStore(app)
    val api: TripApi = TripApi.create(token = { account.token })
    val courses = CourseStore(app)
    val profile = ProfileStore(app)
    val visits = VisitStore(app)
    val feeding = FeedingStore(app)
    val settings = SettingsStore(app)
    /** 온보딩 완료 여부·O03 선택 항목 (#184) */
    val onboarding = io.github.haeyeonyou.familytrip.onboarding.OnboardingStore(app)
    /** 그로우 캐릭터 탄생을 축하했는지 (#164) */
    val births = io.github.haeyeonyou.familytrip.grow.BirthStore(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val notifier = GuideNotifier(app)
    val guide = GuideSession(app, navi, location, notifier, visits, scope)
    /** 진행 중 코스를 계정에 올려 폰 웹 홈에 바로 보이게 한다 (#218, 로그인했을 때만). */
    private val tripSync = io.github.haeyeonyou.familytrip.data.TripSync({ account.token != null }, { api.putTrip(it) }).also {
        it.start(guide.state.map { g -> io.github.haeyeonyou.familytrip.guide.tripSnapshot(g, java.time.LocalDate.now()) }, scope)
    }
    /** 차 앱 ↔ 서버 동기화 (로그인했을 때만 동작). 도착 기록 같은 차의 변경을 바로 올린다. */
    val serverSync = ServerSync(app, api, account, visits, profile, feeding, scope).also { it.start() }
    val care = CareController(app, vehicle, navi, location, api, guide, notifier, feeding, serverSync, visits, scope).also { c ->
        // 고른 아이가 12개월 이하인지 (#217): 수유 정차·자동 적용의 기준
        c.infant = {
            val children = profile.load()?.children.orEmpty()
            io.github.haeyeonyou.familytrip.care.isInfant(children.getOrNull(io.github.haeyeonyou.familytrip.care.selectedChildIndex(children, c.state.value.selectedChildId)))
        }
    }
    val delayAlert = DelayAlertController(app, navi, location, api, { guide.state.value.current?.place?.let { Place(it.name, it.latitude, it.longitude) } }, scope)
    val voiceIo = VoiceIo(app)
    val voice = VoiceController(voiceIo, care, vehicle, navi, api, scope)

    private var locationStarted = false

    fun startConnections() {
        vehicle.start()
        navi.start()
        startLocationIfPermitted()
    }

    /** FUSED_LOCATION은 런타임 권한이라 허용된 뒤에 시작한다. */
    fun startLocationIfPermitted() {
        if (locationStarted) return
        if (app.checkSelfPermission(FUSED_LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            location.start()
            locationStarted = true
        }
    }
}

class FamilyTripApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // 카카오맵은 키가 있을 때만 켠다. 없으면 코스 지도는 간이 지도로 보인다.
        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()) KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        container = AppContainer(this).also { it.startConnections() }
    }
}
