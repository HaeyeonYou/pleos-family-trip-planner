# 첫인상 다듬기: 앱 아이콘·날씨 아이콘·알림 띠·음성 패널·"경험 나무" 문구 (2026-11-11 지시서)

- 상태: **완료**(shell-polish 세션, 이슈 #214, PR #220, 브랜치 `feature/214-shell-polish`) · 접수 전 필수: **예**(5건 모두)
- 근거: 감사 `docs/audit/2026-11-11-figma-vs-app.md` 1절 상위 목록 2~6위(O-2, H-1, O-1, D-3, H-2)와 6절 F-6.
- child-mode 세션과 파일 겹침: **가능성 있음** — `MainActivity.kt`(O-1)와 `ui/onboarding/OnboardingScreen.kt`(H-2 O01 문구). 2026-11-11 기준 child-mode 작업 트리의 미커밋 변경은 `CareController`·`PresetSync`·`SyncModels`·`TripApi`·`CareModeEditSheet`·`CareScreen`·`TripViewModel`·새 `ModeApplyAt`로, 이 지시서 파일과 직접 겹치지 않는다. 시작 전에 다시 확인할 것.

## 목표
모든 화면에서 보이는 틀린 정보·옛 디자인을 1차 접수(11/15) 전에 없앤다. 작은 작업 5개를 한 PR(또는 작은 PR 여럿)로.

## 완료 조건
1. **O-2 앱 아이콘:** 차 하단 독에 안드로이드 기본 로봇 대신 시터노트 로고(Sw). `AndroidManifest.xml` `<application android:icon / android:roundIcon>`, `res/mipmap-*`(적응형 아이콘). 로고 원본은 Figma Lib `Logo · Mark`(`351:2612` 안, 피드백 사본은 인스턴스) — 레포에 없으면 Figma REST로 SVG 1장을 읽기 전용으로 받는다(Tier 1 한도 확인, `watch/state.json`).
2. **H-1 날씨 아이콘:** `ui/SitterGrid.kt` `SitterWeather`(171행~)가 `SitterIcons.Sun`을 고정으로 그린다. 날씨 설명(맑음·구름·흐림·비·눈·소나기 등)에 맞는 아이콘을 고른다. `ui/SitterIcons.kt`에 구름·비·눈 아이콘이 없으니 같은 선 굵기로 추가. 판정은 순수 함수 + 단위 테스트.
3. **O-1 첫 실행 알림 띠:** 첫 실행(온보딩 O01~O03) 동안은 "알림이 꺼져 있어…" 띠(`ui/Common.kt:126~`, `MainActivity.kt` `notificationsAllowed`)를 띄우지 않는다. 권한을 묻는 O03 뒤에도 거절했을 때만, 앱 프레임을 줄이지 않는 방식으로 보인다.
4. **D-3 음성 듣기 패널:** `ui/VoicePanel.kt`가 옛 `TripColors`(초록 마이크 원·문장 칩·"직접 입력" 상자)를 쓴다. 11/10 셸 톤(인디고, `ui/Sitter*` 토큰)으로 바꾼다. Figma에 "듣는 중" 화면은 없으니 D01 위쪽 "방금 말씀하신 것" 띠와 Voice Button 컴포넌트를 기준으로 한다. 주행 중엔 키보드 입력("직접 입력")을 막는 규칙은 그대로.
5. **H-2 / F-6 "경험 나무" → "경험 젤리":** `ui/HomeScreen.kt:363`("경험 나무 보기"), `ui/CourseOverview.kt:147`(H02 귀가 줄), `ui/onboarding/OnboardingScreen.kt:356`(O01 GROW 카드). 주석 속 "경험 나무"도 정리.
6. 검증: 단위 테스트 통과, 에뮬레이터 캡처(독 아이콘, 흐림 날씨, 첫 실행 O01, 음성 패널, 홈 타일). 이 파일 진행 로그·`docs/HANDOFF.md` 자기 행.

## 건드릴 파일
- `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/res/mipmap-*`(새로)
- `.../familytrip/ui/SitterGrid.kt`, `ui/SitterIcons.kt`, `ui/Common.kt`, `MainActivity.kt`(알림 띠 조건만), `ui/VoicePanel.kt`, `ui/HomeScreen.kt`, `ui/CourseOverview.kt`, `ui/onboarding/OnboardingScreen.kt`
- 건드리지 않음: 케어·프리셋 파일, 그로우 파일(`1111-grow-jelly`), 수유 관련 문구(`1111-feeding-cleanup` — `CourseOverview.kt`는 두 지시서가 함께 고치므로 먼저 끝나는 쪽 뒤에 다른 쪽이 rebase).

## Figma 노드
- Lib 셸 `351:2612`(로고 `Logo · Mark`, `Logo · 시터노트 (Full)`, `Voice Button`), D01 `356:6661`, H01 `352:2601`(날씨 자리), O01 `369:20599`.

## 결정·미정
- 결정: 주행 중 버튼 허용·키보드만 막음(사용자 결정).
- 미정 없음. 아이콘 그림은 Figma 로고를 그대로 쓴다.

## 진행 로그
- 2026-11-11: 지시서 작성(full-audit 세션).

- 2026-11-11 (shell-polish 세션, 이슈 #214):
  - O-2: Figma REST로 `Logo · Mark`(358:5049) SVG 1장 조회(글자 윤곽선, Tier 1 11월 17→18, `watch/state.json`), 원본은 `docs/design/pleos-1110/assets/concept/logo-mark.svg`. 적응형 아이콘 `res/mipmap-anydpi-v26/ic_launcher(_round).xml` = 배경 `drawable/ic_launcher_background.xml`(Cue Gradient) + 전경 `drawable/ic_launcher_foreground.xml`("Sn" 윤곽선, 120 타일을 0.6배·18 이동해 보이는 영역 72에 맞춤), monochrome도 전경. minSdk 28이라 PNG mipmap은 만들지 않음. Manifest `android:icon`·`roundIcon`.
  - H-1: `ui/WeatherKind.kt` `weatherKindOf()`(순수 함수) → `SitterWeather`가 맑음=해, 구름 많음=해+구름, 흐림=구름, 비·빗방울·비/눈=비구름, 눈·눈날림=눈구름. `SitterIcons`에 CloudSun·Cloud·CloudRain·CloudSnow(Lucide 24, 선 1.4 → 34로 키우면 icon/sun과 같은 2).
  - O-1: `notificationBannerVisible(allowed, onboarding)`. 띠를 `MainActivity` 맨 위 Column에서 빼고 앱 영역 Box 위쪽(음성 바 안내 문구 자리)에 떠 있게 옮김 → 앱 프레임을 줄이지 않음. 온보딩(ONBOARDING·ONBOARDING_CHILDREN) 동안 숨김. Sitter 토큰(WarnFill·Alert 아이콘·SitterButton)으로 바꾸고 X로 닫기(앱 다시 켤 때까지). 처음엔 아래쪽에 뒀으나 3열 바로가기 독의 "홈"을 가려 위로 옮김.
  - D-3: `ui/VoicePanel.kt`를 Sitter 토큰으로 다시 그림(D01 머리: 그라데이션 마이크 원 80 + 파형 + "방금 말씀하신 것", 답변 = primarySoft 상자, 문장 = SitterChip, 버튼 = SitterButton, 입력창 = 알약). `SitterCanvas(ownsDock=false)`로 Figma px 기준. 기본 톤(CareTone.NONE)의 옛 초록 accent 대신 primary. 주행 중 입력창 숨김 규칙 그대로. `DrivingVoiceCard.VoiceWave`를 internal로.
  - H-2/F-6: "경험 나무" → "경험 젤리"(홈 타일 버튼, H02 귀가 줄, O01 GROW 카드, 주석). O01 부제 "아이의 나무로 키워요"도 "아이의 경험 젤리로 키워요"로. **Figma O01(369:20610·369:20655)은 아직 "나무"** — 디자이너 쪽 수정이 필요할 수 있음(통제 세션 판단).
  - Figma 변경 기록(2026-11-11 15:20·18:20·22:25) 중 이 세션 몫 검토: H01 `추천 반경`→`반경 설정`(369:20071)은 반경 드롭다운(H01 두 번째 안) 제목으로 home-fuel 지시서 4번(반경 설정 시트)과 같은 곳이라 반영하지 않음. 18:20 H01 변경은 연료 타일·Course Item 폭(342)으로 home-fuel 몫. Lib·Type 화면 삭제는 앱에 바꿀 것 없음. H02-1 대체 장소 이름은 Figma 예시 데이터라 앱(실데이터) 변경 없음. H03·H03-1·H04 추가는 course-progress 몫. 22:25는 C05-T1(케어)뿐.
  - 검증: 단위 테스트 295개 통과(새 `ShellPolishTest` 3개). 에뮬레이터(앱 `.shell`, 시험 서버 8040): 독 아이콘 Sn 확인(런처가 둥근 사각으로 자름, 잘림 없음), O01 띠 없음·경험 젤리 문구, 알림 "허용 안함" 뒤 O02에도 띠 없음, 홈에서만 위쪽 띠, 실제 기상청 "구름 많음 · 제주시"(위치를 제주로 잠시 옮김)에 해+구름 아이콘, 음성 패널 듣는 중. 캡처 `docs/agent-tasks/assets/1111-shell-polish/`.

## 확인하지 못한 것
- 흐림·비·눈 아이콘은 촬영 시각에 그런 날씨인 곳이 없어 단위 테스트로만 확인(에뮬레이터 캡처 없음).
- 음성 패널 후보 카드·되묻기 상태는 캡처하지 못함(home-fuel 세션이 같은 에뮬레이터를 쓰기 시작해 중단). 변경 전 음성 패널 캡처도 없음.
- 위쪽 알림 띠가 다른 탭(케어·그로우·설정)에서 가리는 요소는 홈만 확인.
