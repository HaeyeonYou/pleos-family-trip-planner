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
| 커밋/push | 사용자가 요청할 때만. 지금까지는 "커밋 후 push"라고 할 때마다 진행 |
| 결정 사항 질문 | 여러 결정을 한 번에 묻지 말고 **하나씩** 질문 |
| 키·비밀값 | 채팅에 붙여 넣게 하지 말고 루트 `.env`에 넣게 안내. 출력할 때도 값은 숨김 |
| 계정 작업 | 가입, 결제, 라이선스 동의는 사용자가 직접 |
| 공개 전환 | 레포는 Private. 나중에 Public으로 바꿀 예정이며, 그 전에 `docs/planning-analysis.md`를 제거해야 함. 이 파일은 첫 커밋(c60b711)부터 기록에 있어서 파일만 지우면 안 되고, 기록에서 삭제(`git filter-repo`)하거나 새 기록으로 시작해야 함 |

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
- 레포 생성, README, `apk/` 폴더, 문서 일체 (커밋 75131df까지 push됨)
- 서버 골격 `server/`
  - `/health`, `/parse`
  - `RuleBasedParser`: 나이, 개월, 반려동물, 테마, 실내, 귀가 시각, 이동 시간, 육아 편의 조건과 부정 표현 처리
  - `ClaudeParser`: `messages.parse` + Pydantic `TripConditions`, 실패하거나 거절되면 규칙 기반으로 대체
  - Dockerfile, docker-compose, pytest 4개 통과
  - 로컬 venv는 Python 3.14 (`server/.venv`)
- Android Studio 2026.2 설치 (Homebrew)

### 진행 중: 막힌 지점
**Pleos 에뮬레이터 이미지가 SDK Manager에 안 보임**
- SDK Update Sites에 위 arm64 XML을 등록했지만 빨간 `!`와 "IO exception while downloading manifest"가 뜸
- 확인한 것
  - curl과 Android Studio 내장 Java(jbr)로는 같은 XML을 정상적으로 받음
  - 인증서 체인은 Amazon으로 정상
  - Android Studio 프록시 설정이 `USE_PROXY_PAC=true`(자동 감지)이고, 시스템에는 프록시가 없음
- 사용자에게 안내한 해결책
  1. Settings → HTTP Proxy → **No proxy**
  2. 중복 등록된 사이트 정리 후 새로고침
  3. 그래도 안 되면 Android Studio 재시작
- **그래도 안 되면 수동 설치**: zip을 내려받아 `~/Library/Android/sdk/system-images/android-34/pleos_car_emulator/arm64-v8a/`에 풀기. 사용자가 42dot 라이선스에 동의해야 진행
- SDK 경로 `~/Library/Android/sdk`에는 build-tools, emulator, platform-tools, platforms가 설치되어 있음

### 아직 안 한 것 (다음 순서)
1. **에뮬레이터 부팅 확인 (S1)**: Apple Silicon에서 Pleos 이미지가 부팅되는지. 계획 전체에서 가장 큰 불확실성
2. 스파이크 S2~S7 (development-plan.md 3-3)
   - Vehicle SoC 읽기와 Virtual Console 반영
   - NaviHelper 경로 요청과 **도착 콜백**
   - 주행 여부 감지
   - Range
   - TTS/STT/FusedLocation
3. Android 앱 골격
   - 모듈: vehicle/, navi/, location/, voice/, data/, demo(Mock)
   - 주행 중/정차 중 UI 분리
4. 서버 `/course`: TourAPI + 카카오 후보 수집 → 규칙 필터 → 길찾기 동선
   - TourAPI 개발계정 한도가 하루 1,000건이라 시연 지역은 미리 DB에 적재하는 방식 권장
5. `TripConditions`에 `interests` 필드 추가 ("공룡 좋아해" 같은 관심사가 지금은 빠짐)
6. 11/13 시연 영상과 release APK, 11/14 제출 (팀원과 협의)
7. 11/16 이후 NAS 배포, P1 기능(EV 에너지 코스, 날씨·지연 플랜 변경, 재추천, 음성, 베이비 케어), 오프라인 데모 모드

---

## 6. 커밋 규칙

커밋 메시지 끝에 아래 줄을 붙여 왔음:
```
Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```
