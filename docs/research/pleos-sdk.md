# Pleos Connect SDK 조사 노트

> 조사일: 2026-11-05. 출처: document.pleos.ai 공식 문서. 확인 못 한 항목은 **[미확인]**으로 표시했다.

## 1. 개발 환경 / 2. 에뮬레이터 설치

출처: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator

- Android Studio는 "최신 버전"을 쓰라고만 되어 있고 버전 번호는 없다.
- JDK: **[미확인]**(이 페이지에는 없음)
- Gradle은 8.0 이상. AGP 버전: **[미확인]**
- minSdk는 **API 28 이상**. 차량 사이버보안 요건(서명 키 로테이션/lineage) 때문이다.
- compileSdk/targetSdk: 이 페이지에 없음.
- 에뮬레이터 시스템 이미지는 **API 34**, Pleos Connect **v2.1.0**이다. Virtual Console을 쓰려면 2.1.0 이상이어야 한다.
- 이미지 설치: Android Studio SDK Manager > SDK Update Sites에서 `+`를 눌러 이름 "Pleos Connect System Image"로 추가한다.
  - **Mac: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`** (arm64 전용 이미지가 따로 있다)
  - Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
  - 이어서 SDK Platforms 탭에서 Show Package Details를 켜고 API 34 아래의 Pleos Connect system image를 설치한다.
- AVD 설정: Automotive 폼팩터, Pleos Connect v2.1.0, API 34, RAM 4GB, Cold boot.
- 이 페이지에는 계정이나 신청 요구가 없다. 다만 앞 단계로 "프로젝트 생성 및 SDK 사용 신청"(/docs/pleos-only/create-my-project) 페이지가 따로 있다.
- Apple Silicon: Mac용 이미지 URL이 `arm64`라서 Apple Silicon Mac을 지원하는 것으로 보인다. "지원한다"는 명시적 문구는 없다.

## SDK 구성 개요

출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/

- 공개된 SDK는 5개다: **Vehicle**(차량 상태 조회/제어), **NaviHelper**(경로 안내 제어·내비 정보 조회), **TextToSpeech**, **SpeechToText**, **FusedLocation**(차량 위치).
- 권한 규칙
  - API별로 필요한 권한을 선언해야 한다.
  - Pleos App Market과 합의·승인된 범위 안에서만 쓸 수 있다.
  - 승인되지 않은 권한을 선언하거나 관련 API를 호출하면 앱 심사에서 탈락한다.
- 사이드바에 "Gleo Interface 구현 가이드" 메뉴가 있다(7번 항목 참고).
- 공모전 전용이나 비공개 SDK 언급: 이 페이지에는 없다.

## 3. Vehicle SDK 개요

출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro

- 버전은 **Vehicle SDK v2.3.2**이고, Vehicle Service v2.3.21 기준이다.
- 사용 패턴
  - `Vehicle` 진입점 인스턴스에서 `initialize()`로 연결하고, 끝나면 `release()`를 호출한다.
  - 도메인 객체는 직접 만들지 않고 `getDoor()`, `getBrake()` 같은 `getXxx()`로 얻는다.
- 호출은 IPC 비동기 방식이다. 결과는 `onComplete`/`onFailed` 람다로 받으며, 콜백이 끝나기 전에 `release()`를 부르면 안 된다.
- 연산 종류: get(현재값), set(제어), callback(상태 변화 리스너), `checkXXXCapabilities`(지원 여부와 허용 범위).
- 도메인
  - Fuel(연비 등), EvBattery(EV 배터리·충전 상태·효율), HVAC(온도·풍량·습도), Light(상태·앰비언트 제어)
  - Window, Wiper, Door, DrivingMode(주행모드·크루즈), Odometer(총거리·트립)
  - Brake, CarInfo, Display, Safety, Seat, SideMirror, Steeringwheel, Tire, Toll, TurnSignal
- 기어, 속도, Range, SoC는 intro의 도메인 목록에 별도 항목이 없다. 어떤 도메인에 들어 있는지는 아래 API Reference 조사에서 확인한다.

### Vehicle SDK 의존성 추가

출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup

- settings.gradle(.kts)의 `dependencyResolutionManagement.repositories`에 아래 저장소를 추가한다.
  `maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }`
- 앱 모듈에 `implementation("ai.pleos.playground:Vehicle:2.3.2")`를 추가한다.
- 이 페이지에는 저장소 인증정보(토큰) 요구가 없다.
- compileSdk/targetSdk/JDK/AGP/Kotlin 버전: **[미확인]**

### Vehicle 권한

출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission

- 읽기 권한은 대표 권한 기준이다. API마다 `@RequiresPermission`이 다를 수 있으니 개별로 확인해야 한다.
  - Fuel, EvBattery: `pleos.car.permission.CAR_ENERGY`
  - HVAC: `pleos.car.permission.CAR_CLIMATE`
  - Light: `pleos.car.permission.CAR_LIGHTS`
  - Window, Wiper: `pleos.car.permission.CAR_WINDOWS`
  - DrivingMode, Brake, Odometer: `pleos.car.permission.CAR_DRIVE`
- 제어(set) 권한은 `CONTROL_CAR_*` 형식이다(예: `CONTROL_CAR_DOORS`). 도메인별 전체 목록은 이 페이지에 없다.
- 기어/속도/주행상태 권한은 이 페이지에 개별 명시가 없다. **[미확인]**
- 보호 수준(dangerous/signature): **[미확인]**

### API 정책

출처: https://document.pleos.ai/api-reference/connect-sdk-pleos (브라우저로 열면 /docs/pleos-only/connect-sdk-pleos/ 로 리다이렉트된다)

- AAOS에 같거나 비슷한 API가 있으면 Android Automotive에 맞추는 것이 원칙이다.
- Vehicle SDK 기능이 나중에 AAOS로 흡수되더라도 최소 2개 major 버전 동안 하위호환을 보장한다.
- `/api-reference/connect-sdk-pleos` 경로에 별도 API Reference 본문은 없다(리다이렉트만 된다).

### Vehicle API Reference: 패키지와 도메인

출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/entry , https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/entry

- 패키지: `ai.pleos.playground.vehicle`, `.api`, `.api.model.{area,capability,common,telltale}`, `.callback.exception`, `.constant(.area/.control/.telltale/.unit)`, `.listener`
- `.api` 패키지의 도메인 인터페이스: Brake, CarInfo, Display, Door, DrivingMode, EvBattery, Fuel, HVAC, Light, Odometer, Safety, Seat, SideMirror, SteeringWheel, Tire, Toll, TurnSignal, Window, Wiper
- **Gear, Speed, 주행상태(Powertrain) 도메인은 목록에 없다.**

#### Fuel

출처: .../ai.pleos.playground.vehicle.api/fuel/entry

- 연료량은 `getFuelPercent` / `registerFuelPercent` / `unregisterFuelPercent` / `checkFuelPercentCapabilities`로 다룬다.
- 연비는 Cumulative/Current/Instantaneous/SinceCharge 네 종류이고, 각각 get/register/unregister/check가 있다.
- set 메서드는 없다.
- **주행가능거리(Range)와 저연료 경고는 Fuel에 없다.**
- 레퍼런스에 메서드별 `@RequiresPermission`이 표시되어 있지 않다.

#### EvBattery

출처: .../ai.pleos.playground.vehicle.api/ev-battery/entry

- SoC: `getEvBatteryStateOfCharge`, `registerEvBatteryStateOfCharge`/`unregister…`, `checkEvBatteryStateOfChargeCapabilities`
- 충전 스위치: `getEvChargeSwitchState`, `setEvChargeSwitch`, `checkEvChargeSwitchCapabilities`
- 충전구: `setEvChargePortOpen`, `checkEvChargePortOpenCapabilities`
- 충전 한도: `get/setEvChargeCurrentDrawLimit`, `setEvChargePercentLimit`, 각각의 check
- 효율: Cumulative/Current/Instantaneous/SinceCharge 각각 get/register/unregister/check
- **Range(주행가능거리)와 남은 충전 시간 메서드는 EvBattery에 없다.**
- "충전 중" 상태를 직접 주는 메서드는 문서에서 찾지 못했다. ChargeSwitchState만 있다. **[미확인]**

#### DrivingMode / CarInfo

출처: .../ai.pleos.playground.vehicle.api/driving-mode/entry , .../car-info/entry

- DrivingMode
  - 주행모드: `getCurrentDrivingModeState`, `registerDrivingModeState`, `unregisterDrivingModeState`, `checkDrivingModeStateCapabilities`
  - 크루즈: `getCruiseControlState`, `getCruiseControlTargetSpeed`, 각각의 register/unregister/check
- CarInfo: `getVINInfo`, `getCRNInfo`, 각각의 check
- **결론: Pleos Vehicle SDK 레퍼런스에는 기어, 현재 차속, 주차/주행 여부, Range API가 없다.** 크루즈 목표속도만 있다.
  - 추론(문서 근거 아님): API 정책상 "AAOS와 같은 기능은 AAOS에 맞춘다"고 했으므로, 이 값들은 표준 AAOS `CarPropertyManager`(PERF_VEHICLE_SPEED, GEAR_SELECTION, RANGE_REMAINING 등)를 써야 할 가능성이 있다. Pleos 문서에서 직접 확인하지는 못했다. **[미확인]**

## 2-1. Virtual Console

출처: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator

- 별도 설치 프로그램이 없다. **에뮬레이터 안에 웹 UI로 들어 있다**(에뮬레이터 v2.1.0 이상).
- 사용법: 에뮬레이터를 켠 뒤 `adb forward tcp:8088 tcp:8088`을 실행하고, PC 브라우저에서 `http://127.0.0.1:8088/`을 연다. 웹 UI이므로 호스트 OS와 무관하게 쓸 수 있을 것으로 보인다.
- 셋업 절차에 로그인 언급은 없다.
- 변경 가능한 항목
  - Motion: **기어**, 브레이크, 조향각, 주행거리, 핸들 열선
  - ADAS: 크루즈 준비/활성, 목표속도
  - Convenience: **도어**, **창문**, 트렁크, 시트, **라이트**, **와이퍼**, ETCS 시나리오, 글로브박스
  - Safety: 경고등(주차브레이크, 안전벨트, ABS) off/on/blink
  - Energy: **주행가능거리(Range)**, **배터리 용량**, **충전 상태**, 충전 전류, 목표 충전량, 충전구 상태
  - Climate: **HVAC** 전원, 온도, 풍량, 풍향, 내외기, 제상, 시트 열선/통풍, 공기청정
  - Vehicle: 알림 표시 상태
- **연료(Fuel)** 항목은 이 페이지에 없다. **[미확인]**
- **속도**는 소개 문구에만 언급되고 구체적인 컨트롤 설명은 없다. 아래 상세 가이드에서 확인한다.
- 위치(GPS) 변경: 이 페이지에는 없다.
- 상세 가이드: /docs/pleos-only/app-test-guide/virtual-console-guide

### Virtual Console 상세

출처: https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide

- **Battery SOC**: 0~100%, 1% 단위로 바꿀 수 있다.
- **Gear Selection**: P/R/N/D 변경이 가능하다.
- **실제 차속**: 컨트롤이 없다. Cruise Target Speed(5.556~27.778 m/s)만 있다.
- **연료량(Fuel)**: 컨트롤이 없다.
- 시나리오는 ETCS(하이패스 카드 삽입/제거, 톨게이트 진입/진출)뿐이고, 일반 주행 시나리오는 없다.
- 위치/GPS 경로 시뮬레이션: 언급이 없다. 표준 에뮬레이터 Extended Controls 사용 여부는 **[미확인]**
- 연동 동작: Exterior Lights Mode를 바꾸면 테일램프와 로우빔이 같이 바뀌고, Hazard는 스위치와 실제 상태가 같이 바뀐다.
- 어느 SDK API가 어느 컨트롤에 반응하는지는 문서에 없다. 직접 테스트해야 한다.

## 4. NaviHelper SDK

출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro

- 버전은 **NaviHelper SDK v3.0.5**이다.
- 사용 흐름: `NaviHelper` 생성 → `initialize()` → `addListener()`(NaviHelperEventListener) → API 호출 → 콜백으로 결과 처리 → `removeListener()` → `release()`
- 기능
  - 목적지 설정/확정/취소
  - 경로 옵션 변경과 재탐색
  - **경유지 추가/조회/삭제**, 경유지를 포함한 경로 요청
  - 내비 정보 조회, 내비 화면 요청
- 경유지 최대 개수는 intro에 없다. 아래 하위 페이지에서 확인한다.
- **이벤트는 Navigation 앱 구현에 따라 실제 전달 여부가 달라질 수 있다고 명시되어 있다.**

### 경유지

출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/waypoint-settings

- 추가는 `addWaypoint(RequestWaypointInfo)`, 조회는 `getWaypointInfo()`이고 결과는 `onWaypointInfo`/`onWayPointInfo(List<WaypointInfo>)`로 온다.
- 삭제는 `removeWaypoint(WaypointIndex.FIRST | SECOND | ALL)`이다.
- **경유지는 최대 2개다**(`WaypointIndex.FIRST`, `SECOND`).
- 예제는 `requestRoute()`로 목적지를 먼저 설정한 뒤 경유지를 추가한다. 이 순서가 필수인지는 명시되어 있지 않다.
- 2개를 초과할 때의 에러코드는 문서에 없다.

### 이벤트 리스너(NaviHelperEventListener)

출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners

- 경로: `onRouteStarted`, `onRoutingCompleted`, `onRouteCancelled`, `onReRouteEnded`, `onRouteOptionChanged`
- **도착: `onDestinationArrived`(목적지 도착), `onWayPointArrived`(경유지 도착). 둘 다 있다.**
- 변경: `onDestinationChanged`, `onWaypointChanged`
- 정보
  - `onBookmarkInfo`, `onBookMarkChanged`, `onRecentDestinationInfo`, `onCurrentLocationInfo`, `onDestinationInfo`, `onWayPointInfo`
  - `onRouteStateInfo`, `onTBTInfo`, `onChargerOperatorInfo`, `onTripInfo`, `onNavigationStatus`, `onDrivingInfo`
  - `onCurrentRoadSpeedLimit`, `onCameraAlert`, `onCautionPointInfo`
- 오류: `onError`
- 실제로 전달되는 이벤트는 Navigation 앱 구현 시나리오에 따라 다르다고 명시되어 있다.

### NaviHelper 의존성과 권한

출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk , https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions

- 의존성: `implementation("ai.pleos.playground:NaviHelper:3.0.5")`. 저장소는 Vehicle과 같은 nexus-playground maven-releases다.
- 권한
  - `pleos.car.permission.NAVI_ROUTE`: `get*Info()`, `requestTrip()`
  - `pleos.car.permission.NAVI_ROUTE_SEARCH`: `requestRoute()`, `requestRouteWithWaypoints()`, `cancelRoute()`, `requestReRoute()`, `addWaypoint()`, `removeWaypoint()`, `changeRouteOption()`
  - `pleos.car.permission.NAVI_CUSTOM_MAP`: `requestNavigationScreen()`

### 경로 요청과 경로 정보

출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/routes , https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/entry

- `requestRoute(RouteInfo)`로 경로를 요청한다.
  - RouteInfo 필드: latitude, longitude, displayLatitude, displayLongitude, poiName, poiId, poiSubId, address(+routeOption)
  - **Java 생성자는 longitude가 latitude보다 앞이다.**
  - 경로 옵션은 `RouteOption`으로 주거나 `changeRouteOption()`으로 바꾼다.
- 데이터 클래스(navi.data):
  - TripInfo, TripSummary, TripRequest
  - DrivingInfo, DrivingState, RouteStateInfo, RouteStartInfo
  - DestinationArrivedInfo, WaypointArrivedInfo
  - RouteWithWaypointsRequest, TBTInfo
  - ChargingPlan, ChargerOperator, TrafficInfo 등 모두 31개
- **ETA/남은 거리 필드 이름**: 문서 페이지 본문은 비어 있었지만, 내려받은 SDK 소스(`navi-data-3.0.4-sources.jar`)와 에뮬레이터 로그로 2026-11-08에 확인했다. 아래 "ETA·지연·교통 정보 검증 (#17)" 절 참고.

### 목적지와 경유지를 한 번에 요청

출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/route-with-waypoints

- `requestRouteWithWaypoints(RouteWithWaypointsRequest(destination: RouteInfo, waypoints: List<RequestWaypointInfo>))`
- RequestWaypointInfo 필드: latitude, longitude, poiName, `waypointIndex`(WaypointIndex.FIRST 등), poiId, address, poiSubId
- 권한은 NAVI_ROUTE_SEARCH다. 결과는 `onRouteStarted` / `onRoutingCompleted` / `onError`로 온다.
- 이 페이지에는 최대 개수가 적혀 있지 않다. 다만 WaypointIndex가 FIRST/SECOND뿐이므로 **실질 최대는 2개**다(waypoint-settings 페이지 근거).

### 목적지 API와 NaviHelper 메서드 목록

출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations , https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/entry

- 목적지 설정/변경은 `requestRoute(RouteInfo)`, 확인은 `getDestinationInfo()`(결과는 `onDestinationInfo` 콜백), 취소는 `cancelRoute()`다.
- `onDestinationChanged`는 목적지를 설정하거나 삭제할 때 온다.
- **`requestRoute()` 호출이 성공했다고 경로 탐색이 끝난 것은 아니다.** 완료는 `onRoutingCompleted`로 확인해야 한다.
- NaviHelper 메서드 전체
  - 수명주기: initialize, release, addListener, removeListener
  - 경로: requestRoute, requestRouteWithWaypoints, requestTrip, requestReRoute, cancelRoute, changeRouteOption
  - 경유지: addWaypoint, removeWaypoint, getWaypointInfo
  - 화면: requestNavigationScreen
  - 조회: getBookmarkInfo, getChargerOperatorInfo, getCurrentLocationInfo, getDestinationInfo, getRecentDestinationInfo, (getRouteStateInfo, getTBTInfo)
- 개별 메서드 페이지와 데이터 클래스 페이지는 본문이 렌더링되지 않았다. ETA와 남은 거리 필드는 SDK 소스와 에뮬레이터로 확인했다("ETA·지연·교통 정보 검증 (#17)" 절).

## 5. TextToSpeech / SpeechToText

출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro , https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro

- 둘 다 **v3.3.1**이다.
- 공통 모드(`Mode`)
  - `HYBRID`(기본): 서버를 먼저 쓰고, 실패하면 온디바이스로 폴백한다.
  - `SERVER`: 서버만 쓰고 폴백이 없다.
  - `ON_DEVICE`: 네트워크와 앱 등록이 필요 없다.
- **HYBRID/SERVER 모드는 "앱 등록(app-registration)" 절차가 필요하다.**
- TTS 사용 흐름
  - `ai.pleos.playground.tts.TextToSpeech(context, mode)` 생성 → `initialize()` → `addEventListener()` → (`setLocale()`)
  - 서버 모드에서는 `OnServerConnectionListener.onConnected()`를 기다린 뒤 `speak()`를 호출한다.
  - 끝낼 때는 `stop()` → `removeEventListener()` → `release()`
  - 차량 오디오 시스템으로 출력된다.
- STT 사용 흐름
  - `ai.pleos.playground.stt.SpeechToText(context, mode)` 생성 → `initialize()` → `addListener(ResultListener)` → (`setLocale()`)
  - 인식 시작은 `request()`, 종료는 `stop()`, 정리는 `removeListener()` → `release()`
  - 오디오 데이터를 직접 보내는 기능(sending-audio-data)도 있다.
- 에뮬레이터에서 마이크를 쓰려면 별도 설정이 필요하다(intro에 언급).

### TTS/STT 권한과 언어

출처: .../SpeechToText/declaring-permissions , .../SpeechToText/set-locale , .../TextToSpeech/declaring-permissions , .../TextToSpeech/set-locale (모두 https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/ 아래)

- STT 권한은 `pleos.car.permission.STT_SERVICE`, TTS 권한은 `pleos.car.permission.TTS_SERVICE`다. 없으면 `PERMISSION_DENIED`가 난다.
  - STT 페이지에는 `RECORD_AUDIO` 언급이 없다.
- **한국어(`ko-KR`)를 지원한다.** TTS와 STT 모두 ko-KR, en-US, fr-CA, es-US, es-MX를 지원한다.
- 기본 locale은 "같은 앱이 이전에 설정한 값"이고, 없으면 시스템 locale을 쓴다.
- TTS 음성(voice) 옵션 문서는 찾지 못했다. **[미확인]**

## 5-1. FusedLocation

출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro , https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/project-setup

- 버전은 **v1.1.3**이고, 의존성은 `implementation("ai.pleos.playground:FusedLocation:1.1.3")`이다.
- 사용 흐름
  - `FusedLocation` 생성 → `isSupported()` 확인 → `initialize()`
  - 위치는 `getLastKnownLocation()` 또는 `registerFusedLocationCallback()`로 받고, `unregisterFusedLocationCallback()` → `release()`로 정리한다.
- 기타 메서드: `showLocationAccessBlockedPopup()`, 지오펜스형 진입/이탈 알림인 `addProximityAlert()` / `removeProximityAlert()`
- `getLastKnownLocation()`은 새로 계산하지 않고 마지막 값을 준다. 위치가 없으면 null이 올 수 있다.
- `LocationData` 필드: 위도, 경도, 고도, heading, 정확도/신뢰도, timestamp
- 권한은 `pleos.car.permission.FUSED_LOCATION`이다.
- 업데이트 주기와 Android LocationManager와의 관계: **[미확인]**

## SDK 의존성 요약

- 저장소는 모두 `https://nexus-playground.pleos.ai/repository/maven-releases/`다(인증 요구 언급 없음).
- 좌표
  - `ai.pleos.playground:Vehicle:2.3.2`
  - `ai.pleos.playground:NaviHelper:3.0.5`
  - `ai.pleos.playground:TextToSpeech:3.3.1` (출처: .../TextToSpeech/importing-sdk)
  - `ai.pleos.playground:SpeechToText:3.3.1`: TTS와 같은 패턴과 버전으로 추정된다. importing 페이지는 직접 확인하지 않았다. **[미확인]**
  - `ai.pleos.playground:FusedLocation:1.1.3`

## 6. 주행 중 UX 제한

출처: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide , https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/intro

- 앱은 플랫폼의 `CarUxRestrictions` 상태에 맞춰 주행 중 UX 제한을 적용해야 한다.
- 표준 AAOS 방식을 쓴다.
  - `CarUxRestrictionsManager.registerListener()`로 등록하고 `onUxRestrictionsChanged()`로 변경을 받는다.
  - `isRequiresDistractionOptimization()`과 `ActiveRestrictions` 플래그로 제한 여부를 판단한다.
- 지역 법규에 맞는 `ActiveRestrictions`를 플랫폼이 내려주므로, 앱은 받은 값만 적용하면 된다.
- 예시: 주행 중 동영상(DMB 등)은 `UX_RESTRICTIONS_NO_VIDEO`로 제한된다.
- 키보드, 검색, 긴 텍스트, 리스트 길이 제한
  - 이 페이지에는 구체적 기준이 없고 "지역별 주행 규제 정책" 페이지를 참조하라고 되어 있다.
  - **UX 스펙은 비공개라고 명시**되어 있다.
- manifest의 `distractionOptimized` 메타데이터 언급이 없다. **[미확인]**
- ADB 명령 레퍼런스(/docs/pleos-only/vehicle-app-developer-guide/adb-command-reference-guide)에 "주행 규제 제어" 항목이 있다(세부 내용은 미확인).
- 기타 개발 가이드: Audio Focus, Suspend Mode Audio Focus, CCP 로터리, 내비 앱, 음악 앱, TTS 프리롤 무음, IME 리사이즈, FAQ

## 7. Gleo

출처: https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts

- **Pleos 문서에 있다.** 사이드바 메뉴 이름은 "Gleo Interface 구현 가이드"다.
- Gleo Interface는 **Gleo AI(차량 내 LLM 기반 음성 명령 처리)와 서드파티 앱의 연동 규격**이다.
- **function calling 방식**이다. 문서는 OpenAI function calling과 비슷하며 Android 앱용으로 확장했다고 설명한다.
  1. 앱이 호출 가능한 함수를 **App Schema(JSON)**로 등록한다.
  2. Gleo AI가 **Gleo Action**으로 앱을 호출한다.
  3. 앱이 **Callback**으로 응답을 돌려준다.
  4. LLM이 그 응답으로 최종 답변을 만든다.
- 하위 페이지: guide/app-capability(App Schema 작성), guide/action_callback(Callback Schema), guide/examples, guide/deliver-schema(Gleo에 스키마 전달), use-cases/radio-app
- 권한, 공개 범위, 에뮬레이터 지원 여부: 이 페이지에 없다. **[미확인]**
- 문서에 "개발 진행에 따라 가이드가 계속 보완된다"고 적혀 있다.

### Gleo 스키마 전달

출처: https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/deliver-schema

- 스키마 파일은 앱의 `assets/schema.json`에 둔다.
- 함수마다 `AndroidManifest.xml`에 intent action 필터를 선언한다(예: `ADD_REMINDER`).
- Gleo AI는 부팅 때 한 번 `assets/`의 `schema.json`을 스캔해 DB에 저장한다. 앱을 설치하거나 삭제하면 갱신된다.
- **스키마는 Gleo 팀에 보내 가이드라인 검토를 받아야 하고, 승인되면 연동이 자동으로 활성화된다.**
- 에뮬레이터 지원과 공개 범위 언급은 없다. **[미확인]**

## 1-1. 개발 환경 기준값 (Vehicle SDK Sample App)

출처: https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk (브라우저로 열면 /docs/pleos-only/tutorial/vehicle-sdk-sample-app 으로 리다이렉트된다)

- 문서 전체에서 유일하게 툴체인 버전을 명시한 곳이다. 샘플앱 release 기준이다.
  - **JDK 17**, **Gradle wrapper 8.10.2**, **AGP 8.8.2**, **Kotlin 2.0.0**
  - **compileSdk / targetSdk 35**
  - Sample App 1.0.0-sdk35, Vehicle SDK 2.3.2, Vehicle Service 2.3.22
  - AVD는 Pleos_Connect_v2.1(Android 14)
- 샘플앱 자체의 minSdk는 23이다. 다만 **Pleos Playground에 배포할 제품 앱은 API 28 이상**이어야 한다(signing key rotation/lineage).
- 일반 에뮬레이터 이미지가 아니라 **Pleos Vehicle Service가 들어 있는 전용 AVD**가 필요하다.
- Door Position 예제의 API 흐름: `checkDoorPositionCapabilities` → `getCurrentDoorPosition` → `registerDoorPosition` → `setDoorPosition` → `unregisterDoorPosition`
- 주의사항
  - set 완료 콜백의 값은 "요청한 값"이다. 실제로 바뀌었는지는 다시 조회하거나 변경 알림으로 확인해야 한다.
  - Unsupported에는 지원 확인 요청이 실패한 경우도 포함될 수 있다.
  - 지원 범위(capability)는 AVD에 따라 다르다.
- 샘플앱 소스 저장소 링크는 페이지에서 찾지 못했다. **[미확인]**

## 8. 계정, SDK 사용 신청, 미공개 SDK

출처: https://document.pleos.ai/docs/pleos-only/create-my-project

- 프로젝트를 만들려면 **Pleos Playground 계정과 로그인이 필요하다**(developers.pleos.ai 가입, 기존 Pleos 계정 사용 가능).
- 프로젝트를 만들면 **Project ID(Key)**가 발급된다. App Market Console에서 심사를 요청할 때 입력한다.
- 서버 연동 SDK(TTS, STT)는 **Client ID / Client Secret**이 발급된다. 문서의 "앱 등록" 절차와 관련된다.
- SDK 상태 구분
  - "사용 가능": **Vehicle State, NaviHelper**는 신청 없이 쓸 수 있다.
  - "신청 필요": 신청 후 승인을 받아야 한다. 승인 소요 시간은 명시되지 않았다.
  - **"Coming Soon(준비 중)": FusedLocation, TextToSpeech, SpeechToText, Vehicle Control SDK.** 아직 신청할 수 없다.
  - "제휴 계약 필요": 웹 신청이 없고 서비스 계약 담당자에게 문의해야 한다.
- 정리하면 문서상 Vehicle은 **State(조회)**와 **Control(제어)**로 나뉘고, Control은 Coming Soon 상태다. 그런데 SDK 레퍼런스에는 set 메서드와 문서가 이미 공개되어 있다. 이 불일치는 페이지 갱신 시점 차이일 수 있다. **[미확인]**
- "공모전" 언급은 이 페이지에 없다. Gleo 언급도 없다.

## 에뮬레이터 릴리스 노트

출처: https://document.pleos.ai/docs/pleos-only/release-note/emulator-release-note

- **v2.1.0(2026-10-19, Stable)**
  - 새 기능: Virtual Console, 폼팩터 테스트 기능
  - 개선: 번들 SDK 버전을 올려 API 지원을 확대했다.
  - **제거: LLM SDK, CRN, Bluetooth 동글 연동**
  - 예전 에뮬레이터에 "LLM SDK"가 있었으나 2.1.0에서 빠졌다는 뜻이다. 공모전과의 관련성은 문서에 없다.
- Apple Silicon/macOS 관련 알려진 이슈는 언급이 없다.

## 시작하기(Getting started)

출처: https://document.pleos.ai/docs/pleos-only/getting-started

- 시작 절차
  1. Android Studio에서 **Car/Automotive 템플릿** → Empty Activity → Kotlin, minSdk 28 이상으로 프로젝트를 만든다.
  2. 에뮬레이터 이미지를 설치한다.
  3. 저장소와 의존성을 추가한다.
  4. 권한을 선언한다.
  5. `Vehicle().initialize()` → door 도메인 → `getCurrentDoorPosition()`을 호출하고, 끝나면 `release()`를 호출한다.
- 이 페이지 예제는 **`Vehicle:2.3.1`**을 쓴다. Vehicle intro와 project-setup은 2.3.2다. 문서 간에 버전이 다르다.
- Door 권한: `pleos.car.permission.CAR_DOORS`
- 공모전, Gleo, LLM SDK, Apple Silicon 언급은 없다.

## 접근하지 못했거나 본문이 비어 있던 페이지

- https://document.pleos.ai/api-reference/connect-sdk-pleos : SDK Overview로 리다이렉트된다. 별도 레퍼런스가 없다.
- NaviHelper API Reference의 클래스/메서드 상세 페이지(예: .../navi.data/driving-info/entry, .../route-state-info/entry, .../navi-helper/request-trip): WebFetch와 브라우저 렌더링 모두 본문이 비어 있었다. 그래서 ETA/남은거리 필드명은 SDK 소스 jar로 확인했다(2026-11-08, "ETA·지연·교통 정보 검증 (#17)" 절).
- Vehicle 도메인 메서드별 `@RequiresPermission` 값: 레퍼런스 목록에 표시되지 않는다.
- 조사하지 않은 페이지: SpeechToText/importing-sdk, ADB 명령 레퍼런스(주행 규제 제어), 지역별 주행 규제 정책 페이지, Gleo 하위 가이드(app-capability, examples)
- 로그인이 필요한 페이지(Workspace/My Project, App Market Console)는 건너뛰었다.

## 핵심 요약

- 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/`
  - 좌표: `ai.pleos.playground:{Vehicle:2.3.2, NaviHelper:3.0.5, TextToSpeech:3.3.1, FusedLocation:1.1.3}`
- 개발 환경(샘플앱 기준): JDK 17, AGP 8.8.2, Gradle 8.10.2, Kotlin 2.0.0, compile/targetSdk 35
  - 제품 앱 minSdk는 28 이상이고, 에뮬레이터는 API 34, Pleos Connect v2.1.0이다.
- 에뮬레이터: Mac은 arm64 전용 시스템 이미지 XML(`crp-arm64-sys-img.xml`)이 있다. Apple Silicon 지원은 URL로 추정할 뿐 명시 문구는 없다.
- Virtual Console: 에뮬레이터 내장 웹 UI다(`adb forward tcp:8088 tcp:8088` 후 http://127.0.0.1:8088/).
  - 바꿀 수 있는 것: 기어, 도어, 창문, 라이트, 와이퍼, HVAC, SoC(0~100%), Range, 충전 상태
  - **바꿀 수 없는 것: 실제 차속, 연료량**
- Vehicle SDK
  - 있는 것: Fuel(getFuelPercent), EvBattery(SoC, 충전 스위치/포트), HVAC, Light, Window, Wiper
  - **없는 것: 기어, 차속, Range, 주행 여부 API**
- NaviHelper
  - **경유지는 최대 2개**(WaypointIndex FIRST/SECOND)다.
  - **`onDestinationArrived`와 `onWayPointArrived` 콜백이 있다.**
  - ETA/남은거리: `DrivingInfo.destination.duration`(초)/`distance`(m), `RouteStartInfo.duration`/`distance`. 2026-11-08 에뮬레이터로 확인.
- TTS/STT: ko-KR을 지원하고, 권한은 `TTS_SERVICE`/`STT_SERVICE`다. 서버 모드에는 Client ID/Secret 앱 등록이 필요하다.
- create-my-project 페이지 기준 TTS/STT/FusedLocation/Vehicle Control은 "Coming Soon"이다.
- Gleo: Gleo AI 음성 비서와 앱을 잇는 function-calling 연동 규격이다. `assets/schema.json`과 intent action으로 연동하며, Gleo 팀 검토와 승인이 필요하다.

## 정정 (2026-11-05, 실제 AAR 확인)

- 위의 "기어, Range API 없음"은 문서 기준 판단이었고, 실제 `Vehicle-2.3.2.aar`에는 아래 API가 **있다.**
  - `Vehicle.getGear()`: `getCurrentGearSelectionState`, `registerGearSelection`/`unregister…` (권한 `CAR_DRIVE`)
  - `Odometer`: `getRangeRemainingDistance`, `registerRangeRemainingListener`/`unregister…` (권한 `CAR_DRIVE`, 단위 미터)
  - 둘 다 `@Deprecated`이고, Google `GEAR_SELECTION`/`RANGE_REMAINING` 프로퍼티(AAOS `CarPropertyManager`)를 쓰라고 안내한다.
- 에뮬레이터 v2.1.0에서 둘 다 동작하고, Virtual Console 변경이 리스너로 들어오는 것을 확인했다.
- 리스너의 `onFailed(e: Exception)` 인자는 non-null이다.

## 에뮬레이터 내비 위치 (2026-11-05 확인)

- 내장 내비(`ai.umos.maps.android.navigation.app`, Mapbox 기반)의 현재 위치는 성남 수정구 시흥동(37.41272, 127.09468)에 고정되어 있다.
- `adb emu geo fix`로 넣은 GPS는 Android GPS provider와 내비 앱에 전달되지만, 내비는 이 값을 쓰지 않는다.
- Virtual Console의 ACCEL은 차속만 바꾸고 위치는 바꾸지 않는다. Motion 도메인에 좌표 항목이 없다.
- 그래서 `geo fix`만으로는 `onDestinationArrived`가 발생하지 않는다. 내비 주행 시뮬레이션을 쓰면 발생한다 (아래 11/7 절).
- 원인 (추가 분석): 내장 내비는 `GpsLocationSourceRepositoryImpl`에서 GNSS NMEA 원문을 읽고 체크섬을 검사한다. 에뮬레이터는 GPGGA/GPRMC를 항상 `*47`로 내보내서 체크섬이 맞지 않아 거부된다. 체크섬을 맞춘 유효 NMEA를 연속으로 넣어도 내비 엔진 위치는 바뀌지 않았다.
- 반면 `adb emu geo fix`는 Android GPS·fused provider에 반영되고, Pleos FusedLocation 서비스가 fused provider를 구독하므로 **FusedLocation SDK를 쓰는 앱은 위치 주입으로 테스트할 수 있다** (출처: github.com/Pleos-Playground/pleos-sample-fusedlocation-app README).

## 내비 주행 시뮬레이션 (2026-11-07 확인)

- 내장 내비에 주행 시뮬레이션 기능이 있다. 내비 화면 오른쪽 아래 **설정(톱니) 버튼을 2초 이상 길게 누르면** '주행 시뮬레이션' 창(시작/정지/종료)이 뜬다.
- 경로 안내 중 '시작'을 누르면 차가 경로를 따라 움직인다. 실제보다 빠르다 (1.8km, 예상 9분 경로가 약 2분에 도착).
- 이때 나오는 것:
  - 왼쪽 Driving view의 턴바이턴 카드, NaviHelper `onTBTInfo`, 현재 위치·남은 거리
  - 목적지에서 `onDestinationArrived` (우리 앱 `PleosNavi` 로그에 "목적지 도착" 확인)
- 시뮬레이션 위치는 내비 엔진 안에서만 쓴다. Android 위치·FusedLocation은 바뀌지 않으므로 FusedLocation 근접 알림은 `geo fix`(tools/geo_drive.py)로 따로 움직여야 한다.
- 근거: 내비 앱(`/system_ext/priv-app/Navi42/Navi42.apk`, 3.3.2-rc5-hotfix.3)의 `SimulationUseCase`, `LocationSourceId`(VEHICLE_LOCATION·GPS_LOCATION·LOCATION_SIMULATOR 등), 메인 화면 `combinedClickable`의 onLongClick. APK는 호스트의 AVD `system.img`(super → system_ext ext4)에서 꺼내 확인했다.
- NMEA 재확인: 에뮬레이터가 GGA·RMC를 직접 만들어 `*47`을 붙인다 (`geo nmea`로 넣은 문장도 다시 만들어짐). RMC는 숫자만 바꿔서는 체크섬이 0x47이 될 수 없고 음수 속도('-')가 있어야 한다. GGA·RMC 둘 다 맞춰도 내비 엔진 map-matching 입력(`org(...)`, `sat(0)`)은 시흥동 그대로였다.
- 주의: 앱 프로세스가 얼려진(frozen) 동안 NaviHelper 콜백이 전달되지 못하면, 그 뒤로 해당 프로세스의 리스너가 이벤트를 받지 못했다 (앱 재시작으로 복구). 안내 중에는 포그라운드 서비스로 얼림을 막아야 한다.

## Pleos 계정 로그인 조사 (2026-11-07)

- 공식 문서(개발 가이드, SDK 목록: Vehicle·NaviHelper·TTS·STT·FusedLocation, Gleo Interface)에는 서드파티 앱용 **계정 로그인·사용자 식별 API가 없다**.
- Pleos Maven 저장소에 문서에 없는 `ai.pleos.playground.security:universal-access:0.6.0`이 있다.
  - `UniversalAccess.create(InitSettings(appId, appKey, appName, appCode, appDeveloperIdentity))` → `initialize()` → `getToken()`이 **앱 인증 토큰**(`Token.access` 문자열 하나)을 준다. 환경: LOCAL·INT·STAGE·REAL·CORAL.
  - **사용자 프로필(`getUserProfile`)과 외부 ID 로그인(`openIdpAuthentication`, OIDC)은 소스에서 주석 처리**되어 있어 지금은 쓸 수 없다.
  - appId·appKey는 Pleos가 발급해야 하므로(프로젝트 생성·SDK 신청) 우리는 시험하지 못했다.
- 에뮬레이터의 `ai.pleos.connecttokensampleapp`은 계정이 아니라 **디자인 토큰(테마) 샘플**이다.
- 차 상태바에 프로필 선택기가 있지만, 앱이 현재 운전자 프로필을 읽는 API는 문서에 없다.
- 결론: 지금은 Pleos 계정으로 로그인할 수 없다. 공모전 Q&A로 사용 가능 여부를 문의하고, 회원 연결은 우리 서버 방식(QR 가족 연결 등)으로 설계하되 나중에 Pleos 계정을 붙일 수 있게 둔다.

## 외부(모바일)에서 차 제어 가능 여부 (2026-11-07)

- **차 안 앱(Vehicle SDK 2.3.2)은 제어 API가 있다**: `setHvacTemperature`·`setHvacPower`·`setHvacFanSpeed`·`setHvacSeatTemperature`(시트 열선)·`setHvacSteeringWheelHeat`, `setDoorLock`, `setWindowPosition`, 각종 라이트, `setEvChargeSwitch`·`setEvChargePercentLimit` 등.
  - 권한 `pleos.car.permission.CONTROL_CAR_*` (CLIMATE, DOORS, WINDOWS, LIGHTS, ENERGY 등). 에뮬레이터에서는 보호 수준이 `normal`이라 선언만 하면 받지만, 실차는 "Pleos App Market과 사전 협의·승인된 범위"에서만 쓸 수 있다.
  - **시동(원격 시동) API는 없다.**
  - 앱은 인포테인먼트가 켜져 있을 때만 돈다. 시동이 꺼진 주차 상태에서는 앱이 돌지 않아 **블루링크·기아 커넥트처럼 타기 전에 미리 공조를 켜는 원격 제어는 차 안 앱으로 할 수 없다.**
- **클라우드 API**:
  - Vehicle Data API: 현대·기아·제네시스 커넥티드카 텔레매틱스 데이터 **조회 전용**(배터리·위치·운행·공조 상태 등), OAuth 2.0, **2026년 8월 기준 유럽만 제공**.
  - Fleet API: 플릿 운영사용 차량·단말기 관리와 데이터 조회·Webhook. 원격 제어 기능은 목록에 없다.
- 원격 시동·원격 공조는 현대·기아의 자체 서비스(블루링크·기아 커넥트, 차량 통신 모듈 경유)이고 서드파티에 열려 있지 않다.
- **우리 앱에서 가능한 범위**: (1) 차가 켜져 있을 때 폰(동승 가족)에서 보낸 요청을 서버 경유로 차 안 앱이 받아 공조 등을 제어, (2) 폰에서 미리 정해 둔 프리셋을 다음 시동 때 차 안 앱이 바로 적용, (3) 타기 전 원격 공조는 블루링크·기아 커넥트 앱으로 연결(딥링크).

## ETA·지연·교통 정보 검증 (#17, 2026-11-08)

확인 방법: SDK 소스(`~/.gradle/caches/.../ai.pleos.playground/navi-data/3.0.4/*-sources.jar`)의 필드 정의를 읽고, 에뮬레이터(Pleos_Connect_v2.1.0)에서 내비 주행 시뮬레이션(내비 기어 버튼 2초 이상 길게 누름 → 주행 시뮬레이션 → 시작)을 돌리며 앱의 `NaviHelperEventListener`가 받는 값을 로그로 남겼다. 임시 로그 코드는 커밋하지 않았다.

### 필드 (SDK 소스 기준)

| 콜백 | 필드 | 뜻 |
|---|---|---|
| `onRouteStarted(RouteStartInfo)` | `duration`(초), `distance`(m), `name`, `tollFare` | 경로 시작 때 **전체 경로**의 예상 시간·거리 |
| `onDrivingInfo(DrivingInfo)` | `destination: DrivingState`, `waypoints: List<DrivingState>` | 안내 중 도착지·경유지까지 남은 값 |
| (DrivingState) | `duration`(초, 지금 위치에서 그 지점까지), `distance`(m) | **남은 시간·거리** |
| `onRouteStateInfo(RouteStateInfo)` | `naviMode`(NOT_WORKING / SAFE_GUIDING / ROUTE_GUIDING / **SIMULATION_GUIDING**), `isRouting`, `trafficStatus: TrafficStatus?` | 경로 상태와 교통 |
| (TrafficStatus) | `summary`(정체 총 거리 m·시간 초·평균 속도), `congestionItemList`, `trafficEventList` | 경로 위 정체 구간과 사고·공사 |
| (TrafficCongestionInfo) | `offset`(지금 위치에서 정체 시작까지 m), `distance`, `trafficCongestionTime`(초), `trafficCongestionType`(UNKNOWN/FAST/MODERATE/SLOW/HEAVY), `roadName`, `startCrossName`, `endCrossName` | 정체 구간 하나 |
| (TrafficEventInfo) | `eventType`(ETC/ACCIDENT/ROAD_DAMAGE/REPAIRING/EVENT/DISASTER), `description`, `startTime`, `endTime`(문자열) | 사고·공사 등 |
| `onTripInfo(TripInfo)` | `eta`(초), `distance`, `estimatedSocAtDest`, `summary.trafficConditions`(구간 비율 0~100과 정체 종류) | 경로 요약. **에뮬레이터에서는 한 번도 오지 않았다(0회)** — `requestTrip()`을 부르는 경우에만 오는 것으로 보이나 확인하지 않았다 |

### 에뮬레이터에서 관찰한 것

- **`onDrivingInfo`는 1초마다 온다**(안내 중, 값이 안 바뀌어도). 시뮬레이션 약 8분 동안 530여 회. 안내가 아닐 때는 오지 않는다.
- **`duration`은 60초 단위, `distance`는 100m 단위로만 바뀐다.** 5분 문턱 판단에는 충분하지만 ±1분 오차는 있다.
- **`duration`은 시계가 아니라 경로의 교통 가중치로 줄어든다.** 시뮬레이션 차는 거의 일정한 속도(약 50km/h)로 가는데, 정체 구간에서는 거리가 600m 줄어드는 동안 남은 시간이 900초로 그대로였고, 흐르는 구간에서는 빠르게 줄었다. 그래서 내비의 남은 시간은 구간별 정체 속도를 반영한 값이다.
- 같은 경로(8,400m)를 11분 간격으로 두 번 시작했을 때 `RouteStartInfo.duration`이 1,140초 → 1,080초로 달랐다. 시각에 따라 달라지는 값(실시간 또는 시간대별 통계)이라는 뜻이다. **어느 쪽인지는 가릴 수 없다.**
- **`onRouteStateInfo`는 경로를 시작·취소할 때와, 주행 시뮬레이션 중 몇 초~십수 초 간격(관찰 6회/43초)으로 온다.** `trafficStatus`는 채워져 있었다(정체 구간 여러 개, 대왕판교로·분당내곡로·서현로 등 실제 도로 이름, `totalCongestionDist` 1,700~2,000m, `totalCongestionTime` 180초). 정체 구간의 `offset`은 차가 가면서 줄었다. 시뮬레이션 중 `naviMode`는 `SIMULATION_GUIDING`이고, 안내를 취소하면 `SAFE_GUIDING`으로 오면서 **직전 경로의 `trafficStatus`가 그대로 남아 있다**(그래서 안내 중일 때만 쓴다). `isRouting`은 안내 중 여부가 아니라 항상 false였다(경로 탐색 중 표시로 보임).
- `trafficEventList`는 모두 비어 있었다. 사고·공사 문구는 이 환경에서 확인하지 못했다.

### 결론과 한계

- **내비 값만으로 구현 가능하다.** 출발 때 예상(`RouteStartInfo.duration`)과 안내 중 남은 시간(`DrivingInfo.destination.duration`)으로 도착 지연을 계산하고, `trafficStatus`로 정체 이유 문구를 만든다. 구현은 `navi/DelayTracker.kt`, `navi/TrafficCause.kt`, `navi/DelayAlertController.kt`.
- **한계 1: 시뮬레이션은 교통이 바뀌지 않아** 내비의 남은 시간이 "도중에 정체가 새로 생겨서" 늘어나는 장면을 에뮬레이터에서 만들 수 없다. 그래서 (a) 계산·알림 정책은 시간을 흘려 가며 도는 단위 테스트로, (b) 알림 화면·알림 표시·"경유지 추가/그대로 가기" 동작은 디버그 빌드 전용 장치(`adb shell am broadcast -a io.github.haeyeonyou.familytrip.DEBUG_DELAY --ei seconds 900 -p io.github.haeyeonyou.familytrip`로 내비의 남은 시간에 초를 더함)로 에뮬레이터에서 확인했다. **내비가 정체 발생에 맞춰 남은 시간을 실제로 늘려 주는지는 실차(또는 실시간 교통이 도는 환경)에서만 확인할 수 있다.**
- **한계 2: 남은 시간이 실시간 교통인지 통계인지 가리지 못했다.** 위 "11분 간격 1분 차이"는 시각에 따라 값이 달라진다는 정황일 뿐이다.
- **한계 3: 에뮬레이터 시뮬레이션은 시계보다 빠르게 진행한다**(관찰상 남은 시간이 벽시계보다 빨리 줄었다). 그래서 시뮬레이션만으로는 지연이 0 이하로 나오고, 위 디버그 장치 없이는 알림이 뜨지 않는다. 실차에서는 시계와 같은 속도로 진행하므로 이 값이 그대로 지연이 된다.
- **카카오 길찾기 대체 경로는 만들지 않았다.** 이슈에서 정한 조건("내비 SDK 값이 교통에 반응하지 않는 것으로 확인되면")이 확인되지 않았다 — 반응하지 않는다는 근거도, 반응한다는 확정 근거도 없는 상태다. 실차 확인 결과 내비 값이 교통에 반응하지 않으면 그때 추가한다. 만들 때의 전제: `server/app/sources/kakao.py`의 `route()`(`/v1/waypoints/directions`, `priority=RECOMMEND`)로 "지금 출발" 소요 시간을 받아 비교하고, 호출은 5분에 1회 이하로 제한하며, 응답은 저장하지 않고 즉시 계산해서 쓴다. **카카오의 "지금" 소요 시간이 실시간 교통인지 통계인지는 미확인**이므로 코드 주석과 문서에 그 점을 남긴다.

### 지연 알림 정책 (코드 상수: `DelayConfig`)

- 지연 = (지금 + 남은 시간) − (출발 시각 + 출발 때 예상 시간). 남은 시간이 늘어난 만큼 지연이다.
- 출발 전(길이 200m 줄기 전)에는 기다린 시간을 지연으로 보지 않는다. 경유지를 넣거나 빼면 그 뒤 남은 시간을 새 기준으로 삼는다(60초 동안 따라간다).
- **알림 조건**: 지연 5분 이상이 30초 이어질 때(남은 시간이 잠깐 튀는 것 거름).
- **해제**: 지연이 3분 아래로 30초 이어지면 스스로 거둔다(5분 문턱 근처에서 깜빡이지 않게 문턱보다 낮음). 안내가 끝나거나 취소되거나 도착하면 상태를 초기화한다.
- **재알림**: "그대로 가기" 뒤에는 10분이 지나고 그때보다 5분 더 늦어졌을 때만 다시 알린다. 스스로 풀린 뒤에는 10분 조용하고, 그 뒤 5분 문턱을 다시 넘으면 알린다.
- 표시: 앱 화면 위 패널(피그마 HF 06: 경고 아이콘, "정체로 도착이 N분 늦어져요", 도착 예정·원래 시각, 경유지 후보 카드 2장, "그대로 가기")과 헤드업 알림("경유지 보기/그대로 가기"). 후보는 서버 `/care/nearby`의 수유실 1곳 + 잠깐 세울 곳 1곳(가는 곳이 있으면 경로 위 우선).
- 정체 이유 문구는 `TrafficStatus`로 만든다: 사고·재난 > 공사 > 도로 파손 > 정체(HEAVY가 있으면 그것만, 없으면 SLOW까지, 도로별 정체 시간 합이 가장 큰 도로 이름) > 행사 순. 이미 끝난 사고는 뺀다.
- HF06의 "“첫 번째로 가 줘”라고 말해도 돼요" 음성 안내는 해당 음성 명령이 아직 없어 넣지 않았다.

## 분할 화면 자동 전환 시험 (2026-11-11, 에뮬레이터 API 34, 통제 세션)
- **목적:** 코스 시작 때 내비와 우리 앱을 자동으로 나란히 띄워, 음성 마이크 버튼을 늘 보이게 할 수 있는지.
- **전체 화면에서 `FLAG_ACTIVITY_LAUNCH_ADJACENT`(+NEW_TASK)로 순정 내비(`ai.umos.maps.android.navigation.app/.MainActivity`)를 열기:** 화면이 **나뉘지 않았다.** 내비가 앱 영역 전체를 덮고 우리 앱은 뒤로 갔다. Pleos는 앱 영역의 작업을 모두 자체 `multi-window` 모드로 관리하며 표준 분할 플래그를 따르지 않는 것으로 보인다. (adb `am start`로 시험해 시작한 쪽이 셸이었다. 우리 앱 안에서 호출하는 경우는 시험하지 않았다.)
- **분할은 사용자가 앱 영역 오른쪽 끝 ⋮ 막대를 왼쪽으로 끌 때만 생긴다.** 이미 분할된 상태에서 다른 앱을 열면 그 앱이 한쪽 칸으로 들어간다(내비와 자리를 바꿈).
- `setLaunchDisplayId` 같은 영역 지정은 물리 디스플레이가 하나(2560×1440)라 해당 없음. Pleos 문서에도 70%/30% 영역 ID는 없다.
- **함께 찾은 문제:** 분할 칸(약 840dp 폭)에서 우리 앱이 좁은 화면 배치가 아니라 **넓은 3열 화면을 통째로 줄여** 보여 글씨가 매우 작다. 분할 화면을 안내하려면 좁은 배치를 먼저 확인·보완해야 한다.
- 결론: 앱이 화면 분할을 강제로 만들 수는 없다(확인한 범위). 사용자가 분할을 켜 두도록 안내하고, 분할 칸 배치를 고치는 쪽이 현실적이다.
