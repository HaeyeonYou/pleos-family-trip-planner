# 첫인상 다듬기: 앱 아이콘·날씨 아이콘·알림 띠·음성 패널·"경험 나무" 문구 (2026-11-11 지시서)

- 상태: **대기**(실행 세션은 통제 세션이 시작) · 접수 전 필수: **예**(5건 모두)
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

## 확인하지 못한 것
- Pleos 런처가 적응형 아이콘을 어떻게 자르는지(에뮬레이터에서 확인 필요).
- 날씨 API가 주는 설명 문자열의 전체 목록.
