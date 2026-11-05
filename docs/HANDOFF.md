# 인계 문서: 오늘, 어디 가지? (Pleos 차량용 앱 공모전)

마지막 갱신: 2026-11-05. 이전 Claude 세션에서 한 작업, 파악한 사실, 계획, 남은 일을 다른 세션이 이어받을 수 있게 정리한 문서입니다.

---

## 0. 먼저 지킬 것 (사용자 선호와 규칙)

| 항목 | 내용 |
|---|---|
| 언어 | 사용자에게 보이는 글은 **모두 한국어**. 작업 중간의 진행 안내도 한국어로. 코드, 식별자, 커밋 메시지는 영어 가능 |
| 모델 | 별도 지시가 없으면 **Opus 5.5, effort Medium**. 서브에이전트도 `model: opus`, `effort: medium`. (원래 기획서 분석은 Fable 5.1이었지만 한도가 부족해 Opus로 전환) |
| GitHub 계정 | 개인 프로젝트. **개인 계정 `HaeyeonYou`만 사용.** 회사(APR) 계정이나 조직에는 절대 만들거나 push하지 않음. push 전에 `gh auth status`로 계정 확인 |
| git 사용자 정보 | 전역 설정이 아니라 이 레포에만 설정되어 있음 (`user.name HaeyeonYou`, noreply 이메일) |
| 작업 흐름 | **기능별 GitHub 이슈 → `feature/<이슈번호>-<이름>` 브랜치 → PR(`Closes #번호`)로 main 병합.** PR은 기록용이라 리뷰 없이 바로 병합(작업자 1명). **커밋 메시지, 이슈, PR, 라벨은 한국어로 작성** (11/5에 기존 커밋 메시지도 한국어로 재작성, 해시가 바뀜) 전체 플랜은 고정 이슈 #24에서 추적하고 진행 시 갱신 (11/5부터) |
| 커밋/push | 사용자가 요청할 때만. 지금까지는 "커밋 후 push"라고 할 때마다 진행 |
| 결정 사항 질문 | 여러 결정을 한 번에 묻지 말고 **하나씩** 질문 |
| 키·비밀값 | 채팅에 붙여 넣게 하지 말고 루트 `.env`에 넣게 안내. 출력할 때도 값은 숨김 |
| 계정 작업 | 가입, 결제, 라이선스 동의는 사용자가 직접 |
| 공개 전환 | 레포는 Private. 나중에 Public으로 바꿀 예정이며, 그 전에 `docs/planning-analysis.md`를 제거해야 함. 이 파일은 첫 커밋부터 기록에 있어서 파일만 지우면 안 되고, 기록에서 삭제(`git filter-repo`)하거나 새 기록으로 시작해야 함 |

---

## 1. 프로젝트 개요

- **공모전**: 현대자동차그룹 차량용 앱 개발 공모전. 플랫폼은 Pleos Connect(AAOS 기반)
  - 홈페이지: https://appdevchallenge.auto-hmg.io/
- **앱**: 「오늘, 어디 가지?」. 영유아 동반 가족용 원데이 트립 플래너
  - 식사 → 나들이 → 카페 → 귀가 코스를 차량 위치, 배터리, 날씨, 장소 정보로 설계
  - 주행 중 플랜 변경, 도착 기반 '아이 경험 맵', 재추천
- **기획서 원본**: `/Users/kwonstar/Downloads/오늘_어디가지_패밀리_트립_플래너_기획안.pdf` (12페이지)
- **레포**: https://github.com/HaeyeonYou/pleos-family-trip-planner (Private), 로컬 경로 `/Users/kwonstar/Pleos_Application`
- **팀 구성**: 팀, 일반 부문. **사용자는 개발 담당.** 팀명, 접수양식, 메일 제출은 다른 팀원 담당이고 팀명은 미정

### 일정 (오늘 기준 2026-11-05)
| 날짜 | 내용 |
|---|---|
| **11/15(일)** | 1차 접수 마감. 기획서는 필수, 시연 영상과 GitHub(소스 + APK)은 선택이지만 가산점. 계획상 11/14 제출 |
| 11/23 | 1차 결과 |
| **12/3~4** | PT. 에뮬레이터로 실제 동작 시연. 이때 개발이 끝나 있어야 함 |
| 12/18 | 최종 발표 |
| 2027년 2월 | 전시와 시상 |

---

## 2. 문서 지도 (레포 안)

| 파일 | 내용 |
|---|---|
| `docs/planning-analysis.md` | 기획서 분석: 기능 F1~F8, P0/P1/P2, 화면, 리스크, 질문 |
| `docs/contest-briefing-notes.md` | 설명회 영상과 홈페이지 가이드 요약: 일정, 제출 요건, 심사 기준, SDK |
| `docs/development-plan.md` | **개발 계획 본문**: 체크리스트, 환경 구성, 아키텍처, 기능 계획, 마일스톤, 리스크, **8장 결정 사항** |
| `docs/research/pleos-sdk.md` | Pleos SDK와 에뮬레이터 조사 (출처 URL 포함) |
| `docs/research/backend-apis.md` | 외부 API, NAS, LLM 조사 |
| `server/` | FastAPI 서버 골격 |
| `.env.example` | 필요한 키 목록. 실제 키는 `.env`(git 제외, 이 Mac에만 있음) |

---

## 3. 확정된 결정 (development-plan.md 8장 요약)

| 항목 | 결정 |
|---|---|
| 1차 산출물 | 가능한 만큼. 목표는 P0 데모 + 영상, 11/12에 진행 상황을 보고 판단 |
| 경험 맵 폰 화면 | PT 전에 간단한 웹 페이지로 (차량 앱이 우선) |
| 시연 차종 | **전기차(EV).** Virtual Console로 SoC를 바꿀 수 있음 (연료량은 못 바꿈) |
| 서버 | **Python FastAPI**, 헤놀로지 **SA6400 (i5-8600T, 32GB, x86)**, Container Manager(Docker) |
| 외부 공개 | 기존 **DDNS + DSM 리버스 프록시 + Let's Encrypt** (공인 IP 사용 중, CGNAT 아님) |
| AI | **규칙 기반으로 시작**하고 LLM을 붙일 수 있는 구조. `PARSER=rule` 또는 `claude`로 전환, Claude 실패 시 규칙 기반으로 대체 |
| LLM 모델 | Claude Haiku 5.5 (`claude-haiku-5-5`). 실측 한 건에 약 $0.0003, 2~5초. 크레딧 $5 충전됨 |
| Gleo | 제외 (스키마 승인이 필요해 향후 계획으로만) |
| 시연 위치 | 현재 위치 기반. 에뮬레이터 기본 위치는 PT 장소(강남), 데이터 점검은 수도권 남부 |
| 앱 UI | Kotlin + Jetpack Compose (계획) |

---

## 4. 조사로 확인된 핵심 사실

**Pleos SDK** (상세는 `docs/research/pleos-sdk.md`)
- 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/`
- 좌표: `ai.pleos.playground:Vehicle:2.3.2`, `NaviHelper:3.0.5`, `FusedLocation:1.1.3`, `TextToSpeech:3.3.1`, STT 3.3.1(추정)
- 툴체인: JDK 17, AGP 8.8.2, Gradle 8.10.2, Kotlin 2.0, compile/targetSdk 35, **minSdk 28**
- 에뮬레이터 이미지: Pleos Connect v2.1.0, API 34
  - arm64 목록: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
  - 패키지 경로 `system-images;android-34;pleos_car_emulator;arm64-v8a`, 압축 파일 `crp_car_arm64_emulator.zip`(4,669,762,816 bytes, sha1 `24d28eb2f18eba5e87c861a7eb2bdccebb049419`)
  - 42dot SDK 라이선스가 붙어 있음
- Virtual Console: `adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/`
  - 바꿀 수 있는 값: 기어, 도어, 창문, 라이트, 와이퍼, HVAC, SoC, 주행가능거리, 충전
  - **차속과 연료량은 바꿀 수 없음**
- Vehicle SDK
  - 있음: 연료, EV SoC, HVAC, Light, Window 등. 권한은 `pleos.car.permission.CAR_ENERGY` 등
  - **없음: 주행가능거리, 기어, 차속 API.** AAOS 표준 API로 대체 검토
- NaviHelper
  - **경유지 최대 2개** → 구간마다 순차 안내하는 방식으로 설계
  - `onDestinationArrived`, `onWayPointArrived` 콜백은 있음. 다만 실제로 오는지는 내비 구현에 따라 다름 → **에뮬레이터에서 검증 필요**
- 주행 중 UX 제한: AAOS `CarUxRestrictionsManager`

**외부 API** (11/5 실제 호출로 모두 200 응답 확인, 기준 강남역)
| API | 확인 결과 |
|---|---|
| TourAPI 국문 `KorService2/locationBasedList2` | 반경 5km 285곳 |
| TourAPI 무장애 `KorWithService2/locationBasedList2` | 36곳. 유아 편의 필드(`stroller`, `lactationroom`, `babysparechair`, `infantsfamilyetc`)의 출처, 대부분 자유 텍스트 |
| TourAPI 반려동물 `KorPetTourService2/locationBasedList2` | 6곳 |
| EV 충전소 `B552584/EvCharger/getChargerInfo` | 정상. `numOfRows`가 무시되는 듯 |
| 카카오 로컬 `dapi.kakao.com/v2/local/search/keyword.json` | 정상. 헤더 `Authorization: KakaoAK {키}` |
| 카카오모빌리티 `apis-navi.kakaomobility.com/v1/directions` | 정상. 상업 출시 시 제휴 필요 |
| 기상청 API허브 `typ02/openApi/VilageFcstInfoService_2.0/getUltraSrtNcst` | 정상. `authKey` 파라미터, 격자 nx/ny |

- 기상청은 공공데이터포털이 아니라 **API허브(apihub.kma.go.kr)** 키를 씀 (데이터포털 단기예보가 API허브 연계로 바뀜)
- 카카오 앱 "오늘 어디가지 패밀리 트립 플래너"(ID 1099834)가 카카오맵 무료 쿼터 대상, 카카오맵 사용 ON
- `.env` 키 4개(`DATA_GO_KR_KEY`, `KAKAO_REST_API_KEY`, `KMA_API_HUB_KEY`, `ANTHROPIC_API_KEY`) 모두 입력 완료

**제출 요건** (홈페이지 가이드 기준)
- APK는 `apk/` 폴더에, 파일명 `팀명_앱이름_release.apk`, release 빌드
- 용량을 넘으면 Releases에 올림
- README에 앱 이름, 한 줄 소개, 실행 방법, 테스트 환경, 설정, APK 위치
- Private 레포면 심사용 계정을 Collaborator로 초대해야 함. **심사용 계정은 아직 공지 안 됨**

---

## 5. 현재 상태

### 완료
- 레포 생성, README, `apk/` 폴더, 문서 일체 (push 완료)
- 서버 골격 `server/`
  - `/health`, `/parse`
  - `RuleBasedParser`: 나이, 개월, 반려동물, 테마, 실내, 귀가 시각, 이동 시간, 육아 편의 조건과 부정 표현 처리
  - `ClaudeParser`: `messages.parse` + Pydantic `TripConditions`, 실패하거나 거절되면 규칙 기반으로 대체
  - Dockerfile, docker-compose, pytest 4개 통과
  - 로컬 venv는 Python 3.14 (`server/.venv`)
- Android Studio 2026.2 설치 (Homebrew)

### 에뮬레이터 (S1 완료, 11/5)
- SDK Manager의 "IO exception while downloading manifest"는 HTTP Proxy를 **No proxy**로 바꾸고 중복 사이트를 정리해서 해결
- 이미지 설치: `~/Library/Android/sdk/system-images/android-34/pleos_car_emulator/arm64-v8a` (19GB)
- AVD `Pleos_Connect_v2.1.0`: 하드웨어 프로필 Pleos Connect v2.1.0 (2560×1440). 생성 시 RAM이 2GB로 잡혀서 `config.ini`를 RAM 8192, GPU host로 수정함
- **Apple Silicon에서 부팅 성공.** 콜드 부팅 약 25초, Android 14 / API 34, `RELEASE.release_connect_v2.1.0`
- 실행: `emulator -avd Pleos_Connect_v2.1.0 -no-snapshot-load`. Studio와 CLI로 동시에 띄우면 FATAL(같은 AVD 중복)
- Virtual Console: `adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/`에서 HTTP 200
- 설치된 주요 패키지: `ai.pleos.playground.service.{vehicle,navi,location.fused,adas}`, `ai.pleos.hardware.vehicle.emulator`, `ai.pleos.connectwebconsole`, 내비 앱 `ai.umos.maps.android.navigation.app`
- cmdline-tools(avdmanager, sdkmanager)는 미설치
- Virtual Console 사용법: 왼쪽 "Emulation Controls" 카드를 열고 도메인 탭(Energy, Vehicle 등)에서 값을 바꿈. ACCEL/BRAKE 페달도 있어 차속 변경도 가능해 보임 [미확인]

### Android 앱 골격 + S2 (11/5)
- 위치: `android/` (단일 `:app` 모듈, 패키지 `io.github.haeyeonyou.familytrip`, 패키지명은 임시)
- 툴체인: Pleos 샘플앱 기준 AGP 8.8.2, Gradle 8.10.2(wrapper), Kotlin 2.0.0, Compose BOM 2024.12.01, compile/targetSdk 35, minSdk 28
- **JDK 17 필요**: Android Studio 내장 JBR은 JDK 25라서 Gradle 8.10.2가 안 돎. `brew install openjdk@17`로 설치함
  - CLI 빌드: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :app:assembleDebug`
  - Android Studio에서 열 때는 Settings → Build Tools → Gradle → Gradle JDK를 17로 지정
- 구조: `vehicle/VehicleRepository`(인터페이스, 데모 모드용 교체 가능) + `PleosVehicleRepository`(SDK 래퍼), `MainActivity`는 확인용 상태 카드 화면
- **S2 통과**: SoC, 주행가능거리, 기어를 get으로 읽고 리스너로 변경을 받음. Virtual Console에서 SoC 100→35%, 기어 P→D로 바꾸면 앱에 바로 반영됨
- 권한 `CAR_ENERGY`, `CAR_DRIVE`, `VEHICLE_BINDING`은 설치할 때 자동으로 부여됨(런타임 요청 불필요)
- **조사 문서 정정**: 실제 Vehicle 2.3.2 AAR에는 `Gear`(`getCurrentGearSelectionState`, `registerGearSelection`)와 `Odometer.getRangeRemainingDistance`가 **있음**. 다만 둘 다 `@Deprecated`이고 "Google GEAR_SELECTION / RANGE_REMAINING 프로퍼티를 쓰라"고 안내함. 지금은 동작하므로 그대로 쓰고, 나중에 AAOS `CarPropertyManager`로 옮길지 검토
- 주행가능거리 단위는 **미터**(Console 219000 m → 클러스터 219 km)
- 주의: Range 리스너는 값이 안 바뀌어도 약 3~4초마다 호출됨. 첫 get 값(500km)과 Console 초기값(0 m)이 달랐음

### 아직 안 한 것 (다음 순서)
1. ~~에뮬레이터 부팅 확인 (S1)~~ 완료
2. 스파이크 S3~S7 (development-plan.md 3-3)
   - ~~S2 Vehicle SoC 읽기와 Virtual Console 반영~~ 완료
   - ~~S3 NaviHelper 경로 요청~~ 완료 (#3): `requestRoute` → 내장 내비 안내 시작. `onRouteStarted` 직후 `onRouteEnded`가 같이 오니 도착으로 쓰면 안 됨
   - ~~S4 도착 콜백~~ 결론 (#4): **에뮬레이터에서는 검증 불가.** 대안으로 도착 판정을 앱에서 함 (아래 참고)
     - Virtual Console D + ACCEL: 차속만 오르고 내비 위치는 그대로
     - `adb emu geo fix`: Android GPS에는 들어가고 내비 앱에도 전달되지만(dumpsys 기준) 내비는 무시하고 성남 수정구 시흥동(37.41272, 127.09468)에 고정
     - 내비 설정에 모의 주행 없음. 공식 문서(에뮬레이터 사용·설정, Virtual Console 가이드)에도 위치·경로 시뮬레이션 없음
     - 부수 효과: geo fix로 에뮬레이터 시간대가 한국 시간으로 바뀜
     - **도착 판정 방식 (확정)**: `onDestinationArrived`(실차용)와 함께, 앱 쪽 근접 판정(목적지 반경 안 진입)과 정차 중 수동 "도착" 버튼을 둠. 시연에서는 데모 모드로 도착 이벤트를 발생시킴. 구현은 #11
   - ~~S5 주행 여부 감지~~ 기어 API로 가능 (deprecated)
   - ~~S6 Range~~ Odometer API로 가능 (deprecated)
   - S7 TTS/STT/FusedLocation
3. Android 앱 골격 확장
   - 패키지: navi/, location/, voice/, data/, demo(Mock) 추가 (vehicle/는 완료)
   - 주행 중/정차 중 UI 분리
4. 서버 `/course`: TourAPI + 카카오 후보 수집 → 규칙 필터 → 길찾기 동선
   - TourAPI 개발계정 한도가 하루 1,000건이라 시연 지역은 미리 DB에 적재하는 방식 권장
5. `TripConditions`에 `interests` 필드 추가 ("공룡 좋아해" 같은 관심사가 지금은 빠짐)
6. 11/13 시연 영상과 release APK, 11/14 제출 (팀원과 협의)
7. 11/16 이후 NAS 배포, P1 기능(EV 에너지 코스, 날씨·지연 플랜 변경, 재추천, 음성, 베이비 케어), 오프라인 데모 모드

---

## 6. 커밋 규칙

- 커밋 메시지는 **한국어**로 쓴다 (제목 한 줄 + 빈 줄 + 본문 목록). 이슈 참조는 `Refs #번호`
- PR 병합 커밋 제목은 `PR #번호 병합: <PR 제목>`
- 메시지 끝에 아래 줄을 붙인다 (Git 트레일러라 영어 그대로):
```
Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```
