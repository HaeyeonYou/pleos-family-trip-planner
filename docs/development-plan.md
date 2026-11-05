# 개발 계획: 오늘, 어디 가지?

작성일 2026-11-05. 근거 자료:
- [planning-analysis.md](planning-analysis.md): 기획서 분석
- [research/pleos-sdk.md](research/pleos-sdk.md): Pleos SDK와 에뮬레이터 조사
- [research/backend-apis.md](research/backend-apis.md): 외부 API, NAS, LLM 조사
- [contest-briefing-notes.md](contest-briefing-notes.md): 공모전 요건

**[미확인]** 표시는 문서로 확인하지 못해 에뮬레이터에서 직접 검증해야 하는 항목입니다.

---

## 1. 목표와 범위

| 시점 | 목표 | 산출물 |
|---|---|---|
| **11/14 (1차 접수, 마감 11/15)** | 가산점용 **P0 최소 동작 데모** | 접수양식(기획서) + 서명 동의서 / 시연 영상(에뮬레이터 녹화) / GitHub 소스 + `apk/` release APK |
| **12/2 (PT 12/3~4)** | PT 시연용 완성본 | P0 완성 + P1 중 시연 효과가 큰 것 + 오프라인 데모 모드 |

**범위에서 제외 (기획서에는 있음):**
- 모바일 앱: 경험 맵 상세는 차량 앱 안의 정차 중 화면으로 대체합니다.
- Gleo 연동: 스키마를 Gleo 팀이 승인해야 하므로 기획서에 '향후 계획'으로만 남깁니다.
- 실제 출시용 상업 제휴: 카카오모빌리티, 오피넷

---

## 2. 필요한 자료·계정·키 체크리스트

승인 대기가 있거나 오래 걸리는 것부터 적었습니다. **모든 항목은 사용자가 직접 해야 합니다.** 계정 가입과 키 발급은 Claude가 대신할 수 없습니다.

| 순위 | 항목 | 어디서 | 소요 | 용도 |
|---|---|---|---|---|
| 1 | **Pleos Playground 계정** | pleos.ai/playground | 즉시 (추정) | 프로젝트 생성, 서버형 TTS/STT의 Client ID/Secret |
| 2 | **공모전 접수양식·동의서 DOCX** | 공모전 홈페이지 지원·제출 페이지 | 즉시 | 필수 제출물 |
| 3 | 공공데이터포털 가입 후 활용신청 | data.go.kr | 자동승인, 키 동작까지 최대 1시간 (추정) | TourAPI 3종(국문, 무장애, 반려동물), EV 충전소 |
| 3-1 | 기상청 API허브 가입 후 활용신청 (data.go.kr 단기예보가 API허브 연계로 바뀜) | apihub.kma.go.kr → 예특보 → 단기예보 탭 → "4. 동네예보 조회"의 4.1 초단기실황, 4.2 초단기예보, 4.3 단기예보 | 별도 인증키(`authKey`) | `VilageFcstInfoService_2.0`, 위경도를 격자(nx, ny)로 바꿔 조회 |
| 4 | Kakao Developers 앱 생성, 카카오맵 사용 ON | developers.kakao.com | 즉시 | 로컬 검색, 길찾기(자동차, 다중 경유지) |
| 5 | (선택) Anthropic Console API 키 | console.anthropic.com | 즉시 | LLM을 붙일 때만 (D6) |
| 6 | ~~Cloudflare 계정 + 도메인~~ | — | — | 불필요: 기존 DDNS 사용 (D8) |
| 7 | 오피넷 인증키 | opinet.co.kr | 즉시 (추정) | 주유소. P1이고 EV 시연이면 생략 가능 |
| 8 | NAS 정보 확인 | 사용자 NAS | — | 아래 8장 참고 |

키는 `secrets.properties`(앱)와 `.env`(서버)에 보관하고 git에서 제외합니다. 두 파일 모두 `.gitignore`에 등록되어 있습니다.

---

## 3. 개발 환경 구성 절차

### 3-1. 현재 Mac 상태 (2026-11-05 확인)
| 항목 | 상태 |
|---|---|
| CPU/RAM/디스크 | Apple Silicon arm64 / 32GB / 여유 804GB |
| Android Studio, JDK, Android SDK | **없음** |
| adb | Homebrew로 설치됨 |
| Docker | 없음 (서버는 NAS에서 돌리지만 로컬 개발에는 필요) |

### 3-2. 설치와 설정 순서
1. **Android Studio** 최신 안정판을 설치합니다. 함께 들어 있는 JBR(JDK 17 이상)을 쓰면 됩니다.
2. SDK Manager에서 아래를 설치합니다.
   - Android SDK Platform 35, Build-Tools, Emulator, Platform-Tools
   - API 34 Automotive 이미지를 받을 수 있게 **SDK Update Sites**에 Pleos arm64 이미지 URL을 추가합니다: `.../connect-avd/2.x/crp-arm64-sys-img.xml`. 전체 URL은 research/pleos-sdk.md에 있습니다.
3. AVD Manager에서 Pleos Connect v2.1.0 이미지(API 34)로 AVD를 만들고 부팅합니다. **[미확인] arm64 Mac에서 실제로 부팅되는지가 가장 먼저 확인할 항목입니다.**
4. Virtual Console을 엽니다: `adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/`
5. 프로젝트 설정
   - 툴체인: Kotlin 2.0, AGP 8.8.2, Gradle 8.10.2, compileSdk/targetSdk 35, **minSdk 28**
   - Maven 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/`
   - 의존성: `ai.pleos.playground:Vehicle:2.3.2`, `NaviHelper:3.0.5`, `FusedLocation:1.1.3`, `TextToSpeech:3.3.1`, `SpeechToText:3.3.1`(STT 버전은 추정)
6. Manifest에 `pleos.car.permission.*` 권한을 선언합니다(4-3 참고). 첫 호출로 `getEvBatteryStateOfCharge`를 부르고, Virtual Console에서 SoC를 바꿔 값이 반영되는지 확인합니다.
7. 서버용으로 Docker Desktop(또는 OrbStack)을 설치합니다. FastAPI 서버를 로컬 compose로 띄우고, 에뮬레이터에서 `10.0.2.2`로 접속합니다.

### 3-3. 1일차 기술 검증 (스파이크)
계획 전체가 아래 결과에 달려 있으므로 **기능을 개발하기 전에** 확인합니다.

| # | 확인할 것 | 실패 시 대안 |
|---|---|---|
| S1 | arm64 Mac에서 Pleos 에뮬레이터가 부팅되는지 | x86 PC나 클라우드 VM. 문의 메일로 지원 여부 확인 |
| S2 | Vehicle: SoC, 연료, HVAC 읽기와 Virtual Console 반영 | 해당 기능을 시뮬레이션 값으로 시연 |
| S3 | NaviHelper `requestRoute`, `requestRouteWithWaypoints`가 동작하는지 | 기획의 핵심이므로 문의 메일로 확인 |
| S4 | **`onDestinationArrived` 콜백이 에뮬레이터에서 실제로 오는지** (문서상 내비 구현에 따라 다름) | FusedLocation `addProximityAlert`로 도착 판정 |
| S5 | 기어/주행 여부 감지: CarPropertyManager나 CarUxRestrictions | 수동 '정차 모드' 토글 |
| S6 | 주행가능거리(Range) 조회 | SoC × 효율 상수로 추정, 화면에 '추정' 표시 |
| S7 | TTS/STT/FusedLocation 동작. 프로젝트 생성 페이지에 'Coming Soon'으로 표시됨 | ON_DEVICE 모드, 안 되면 Android 기본 TTS와 위치 API |

---

## 4. 아키텍처

```
[차량 앱 (Pleos Connect, Kotlin)]
  ├ UI: Jetpack Compose, 주행 중/정차 중 화면 분리
  ├ vehicle/   Vehicle SDK 래퍼 (에너지, HVAC, Light), 시뮬레이션 구현 교체 가능
  ├ navi/      NaviHelper 래퍼 (구간 순차 안내, 도착 이벤트)
  ├ location/  FusedLocation
  ├ voice/     TTS / STT
  ├ data/      Retrofit API 클라이언트 + Room(경험 맵, 프리셋 로컬 저장)
  └ demo/      Mock 모드 (서버 없이 고정 시나리오 재생)
          │ HTTPS (Cloudflare Tunnel)
[서버 (Synology NAS, Docker Compose)]
  ├ FastAPI (Python)
  │  ├ /parse    자연어 → 조건 JSON (Claude Haiku 5.5, structured output)
  │  ├ /course   후보 수집 → 규칙 기반 필터·조합 → 동선 계산
  │  ├ /replan   날씨/지연/운영시간/에너지 재검토
  │  └ /recommend 경험 맵 기반 추천
  ├ PostgreSQL (+PostGIS 선택), Redis 캐시
  └ 외부 API: TourAPI, 카카오 로컬/모빌리티, 기상청, EV 충전소, (오피넷)
```

**설계 원칙 (기획서 기준)**
- LLM은 자연어를 조건으로 구조화하는 데만 씁니다. 운영시간, 경로, 에너지, 일정 검증은 서버 규칙으로 처리합니다.
- 주행 중에는 큰 버튼, 짧은 문구, 음성을 씁니다. 장소 비교와 변경 확정은 정차 후에만 할 수 있습니다.
- 목적지는 자동으로 바꾸지 않고, 대체안을 제안한 뒤 사용자가 확인하게 합니다.

### 4-1. 화면 목록
| 화면 | 주행 중 | 정차 중 |
|---|---|---|
| 홈: 오늘의 추천, 차량 에너지 요약 | 요약 카드만 | O |
| 조건 입력: 가족, 테마, 시간, 음성 입력 | 음성만 | O |
| 코스 결과: 장소 3곳, 시간표, 동선, 에너지 판정 | 다음 목적지 카드 | 전체 |
| 장소 교체, 재계산 | X | O |
| 플랜 변경 제안 (날씨/지연/마감/에너지) | 음성 + 1탭 수락/거절 | O |
| 방문 확인 → 경험 맵 | X | O |
| 베이비 케어 프리셋 | 불러오기 1탭 | 편집 |

### 4-2. 코스 안내 방식 (NaviHelper 경유지 최대 2개 제약)
코스는 식사 → 나들이 → 카페 → 귀가로 경유 3곳에 귀가까지 있어서, **경유지 2개 제한을 넘습니다.** 그래서 **구간 순차 안내**를 기본으로 합니다. 다음 장소 하나를 목적지로 설정하고, 도착 이벤트가 오면 방문 기록 후보를 만들고 다음 장소를 제안합니다. 주유·충전 경유가 있는 구간에만 `requestRouteWithWaypoints`로 경유지 1개를 추가합니다.

### 4-3. SDK와 기능 매핑
| 기능 | SDK / API | 권한 |
|---|---|---|
| 현재 위치, 근접 판정 | FusedLocation | `FUSED_LOCATION` |
| 목적지/경유지, 도착 이벤트 | NaviHelper | `NAVI_ROUTE`, `NAVI_ROUTE_SEARCH` |
| 에너지 판정 | Vehicle `getEvBatteryStateOfCharge`, `getFuelPercent` | `CAR_ENERGY` |
| 베이비 케어 프리셋 | Vehicle HVAC / Light / Window | `CAR_CLIMATE`, `CAR_LIGHTS`, `CAR_WINDOWS` (+ 제어용 `CONTROL_CAR_*`) |
| 주행 중 UI 제한 | CarUxRestrictionsManager (AAOS 표준) | — |
| 음성 요청과 안내 | SpeechToText / TextToSpeech | `STT_SERVICE`, `TTS_SERVICE` |

권한 이름은 `pleos.car.permission.` 접두어 기준입니다. 정확한 문자열은 research/pleos-sdk.md에 있습니다.

---

## 5. 기능별 구현 계획

| 우선 | 기능 | 사용 SDK/API | SDK가 안 될 때 대안 |
|---|---|---|---|
| **P0** | 가족 조건 → 원데이 코스 | 서버: Claude 파싱, TourAPI(무장애 육아 필드), 카카오 로컬 | LLM 없이 폼 입력만 받기 |
| **P0** | 전체 동선 계산 (이동시간, 귀가 예정) | 카카오모빌리티 길찾기 | 직선거리 × 평균속도 추정 |
| **P0** | 위치와 내비 연결 | FusedLocation, NaviHelper | 위치 고정값(시연용 출발지) |
| **P0** | 경험 맵 기본 기록 | 도착 콜백 → Room 저장 | 근접 알림, 수동 '도착' 버튼 |
| P1 | 에너지 기반 코스 (EV 우선) | Vehicle SoC + EV 충전소 API | **연료량은 Virtual Console로 바꿀 수 없으므로 EV 시나리오로 시연** |
| P1 | 이동 중 플랜 변경 | 기상청, 길찾기 ETA, 운영시간 | 서버 DEMO_MODE로 날씨 급변 이벤트 주입 |
| P1 | 경험 기반 재추천 | 서버 규칙 (분야 분포 + 거리 + 날씨) | — |
| P1 | 음성 요청과 안내 | STT/TTS | Android 기본 TTS |
| P1~P2 | 베이비 케어 모드 | HVAC/Light/Window | 상태 조회만, 제어는 시뮬레이션 |
| P2 | 경유지 최적화 고도화 | 다중 경유지 길찾기 | — |

**육아 편의 데이터의 한계:** TourAPI에는 유모차 대여, 수유실, 유아 의자, 놀이방 필드가 있지만 대부분 자유 텍스트이고, 키즈존과 기저귀 교환대 필드는 없습니다. 서버에서 텍스트를 키워드로 정규화해 '있음/정보 없음' 배지로 보여 줍니다. '없음'이라고 단정하지 않습니다.

---

## 6. 마일스톤 (11/5 ~ 12/3)

| 기간 | 작업 | 완료 기준 |
|---|---|---|
| **11/5 (목)** | 키 발급(사용자), Android Studio와 에뮬레이터 설치, 스파이크 S1~S4 | SoC 값 읽기, 목적지 설정, 도착 이벤트 확인 |
| **11/6 (금)** | 스파이크 S5~S7, 앱 골격(Compose, 모듈, Mock 모드), 서버 골격(FastAPI + compose) | 에뮬레이터 앱이 로컬 서버 `/health` 호출 |
| 11/7~11/9 | 서버 `/course`: TourAPI + 카카오 로컬 후보 수집, 규칙 필터, 길찾기 동선 | 조건 JSON → 코스 3곳과 시간표 응답 |
| 11/10~11/11 | 앱: 조건 입력 → 코스 결과 → 구간 안내 → 도착 → 방문 후보 → 경험 맵 | P0 흐름이 에뮬레이터에서 끝까지 동작 |
| 11/12 | `/parse` (Claude), 주행 중/정차 중 화면 분기, 버그 수정 | 자연어 한 문장으로 코스 생성 |
| 11/13 | **시연 영상 녹화**, release APK(`apk/팀명_앱이름_release.apk`), README | 영상과 APK가 저장소에 올라감 |
| **11/14 (토)** | 접수양식(기획서) 최종본 반영, 이메일 제출 (마감 하루 전) | 제출 완료 |
| 11/16~11/22 | NAS 배포 + Cloudflare Tunnel, P1 에너지(EV) 코스, 플랜 변경(날씨/지연) | 외부망 에뮬레이터에서 NAS 서버 호출 |
| 11/23 | 1차 결과 발표 | — |
| 11/24~11/29 | 재추천, 음성, 베이비 케어 프리셋, 오프라인 데모 모드 | PT 시나리오 전체 동작 |
| 11/30~12/2 | 기능 동결, PT 리허설, 장애 대비 점검 (NAS 다운, 네트워크 없음) | Mock 모드로도 끝까지 시연 가능 |
| **12/3~4** | PT | — |

---

## 7. 리스크와 대응

| 리스크 | 근거 | 대응 |
|---|---|---|
| arm64 Mac에서 에뮬레이터가 안 뜸 | arm64 이미지 URL은 있음. 동작 여부는 [미확인] | 1일차에 S1 확인. 안 되면 x86 장비 확보 |
| 도착 이벤트가 에뮬레이터에서 오지 않음 | **확인됨(11/5)**: 에뮬레이터 내비 위치가 고정되어 도착이 일어나지 않음 | 앱 쪽 근접 판정 + 수동 '도착' 버튼 + 데모 모드 이벤트 (#4, #11) |
| Range API 없음 | Vehicle 레퍼런스에 없음 | SoC 기반 추정 + '추정' 표기 |
| 연료량을 Virtual Console로 바꿀 수 없음 | 조사 결과 | EV 시나리오로 시연 |
| 경유지 최대 2개 | NaviHelper 문서 | 구간 순차 안내 |
| 육아 편의 데이터 부족 | TourAPI 필드가 자유 텍스트 | 키워드 정규화, '정보 없음' 표기 |
| PT 현장에서 NAS 접속 실패 | 집 회선, CGNAT, 현장 네트워크 | 앱 내장 Mock 모드 + 노트북 로컬 서버 |
| TTS/STT가 'Coming Soon' | 프로젝트 생성 페이지 | ON_DEVICE 모드, Android 기본 API |
| 일정 (1인, 실제 개발 약 8일) | 11/15 마감 | 11/14까지는 P0만 하고 나머지는 PT까지 진행 |
| PT에서 생성형 AI로 만든 코드 설명 | 공모전 Q&A | 모듈마다 설계 메모 유지, 리허설 때 구조 설명 연습 |

---

## 8. 결정 사항 (2026-11-05 확정)

| # | 항목 | 결정 |
|---|---|---|
| D1 | 참가 형태 | **팀, 일반 부문**. 개발은 본인 담당, 팀명·접수양식·메일 제출은 다른 팀원 담당. 팀명이 정해질 때까지 APK 파일명은 임시 이름 |
| D2 | 1차(11/15) 개발 산출물 | **가능한 만큼만.** 목표는 P0 데모 + 영상, 11/12에 진행 상황을 보고 영상만 낼지 결정 |
| D3 | 경험 맵 스마트폰 화면 | **PT 전에 간단히.** P0·P1이 끝난 뒤 간단한 웹 페이지로 경험 맵 조회 화면 추가 |
| D4 | 시연 차종 | **전기차(EV).** Virtual Console로 SoC를 바꿔 충전소 경유 코스 시연 |
| D5 | 서버 언어 | **Python FastAPI** |
| D6 | AI 방식 | **규칙 기반으로 시작.** `/parse`를 인터페이스로 두고 Ollama(NAS)나 Claude를 설정으로 붙일 수 있게 설계 |
| D7 | NAS | **헤놀로지 SA6400, i5-8600T(x86, 6코어), RAM 32GB.** Container Manager 사용, DSM 버전 [미확인](SA6400은 7.2 이상) |
| D8 | 외부 공개 | 기존 **DDNS + DSM 리버스 프록시 + Let's Encrypt 인증서**(공인 IP 사용 중이라 CGNAT 아님) |
| D9 | Gleo 연동 | 제외. 기획서에 향후 계획으로 기재 |
| D10 | 시연 위치 | 앱은 현재 위치 기준으로 동작. 에뮬레이터 기본 위치는 **PT 장소(강남)**, 데이터 품질은 수도권 남부 기준으로 점검 |
