package io.github.haeyeonyou.familytrip

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk
import android.content.pm.PackageManager
import io.github.haeyeonyou.familytrip.data.AccountStore
import io.github.haeyeonyou.familytrip.data.CourseStore
import io.github.haeyeonyou.familytrip.data.FeedingStore
import io.github.haeyeonyou.familytrip.data.ProfileStore
import io.github.haeyeonyou.familytrip.data.TripApi
import io.github.haeyeonyou.familytrip.data.VisitStore
import io.github.haeyeonyou.familytrip.guide.GuideNotifier
import io.github.haeyeonyou.familytrip.guide.GuideSession
import io.github.haeyeonyou.familytrip.location.FUSED_LOCATION_PERMISSION
import io.github.haeyeonyou.familytrip.location.LocationRepository
import io.github.haeyeonyou.familytrip.location.PleosLocationRepository
import io.github.haeyeonyou.familytrip.navi.NaviRepository
import io.github.haeyeonyou.familytrip.navi.PleosNaviRepository
import io.github.haeyeonyou.familytrip.vehicle.PleosVehicleRepository
import io.github.haeyeonyou.familytrip.vehicle.VehicleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val guide = GuideSession(app, navi, location, GuideNotifier(app), visits, scope)

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
