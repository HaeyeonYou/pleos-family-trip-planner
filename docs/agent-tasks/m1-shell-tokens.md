# 작업 기록: M1 디자인 토큰 & 공통 셸

- 상태: **완료** (2026-11-10)
- 이슈: #144 · PR: #145 (병합됨) · 브랜치: `feature/144-m1-shell-tokens` · 후속 기록 PR: `docs/144-m1-agent-record`
- 이 파일 하나로 다른 에이전트가 이어받을 수 있게 쓴다. 키·토큰 값은 적지 않는다.

## 1. 목표와 완료 조건
11/10 확정 디자인(Figma `xeLwkzhH8tnsazQdOriGU2` · `종합 정리` · `351:2611`)의 공통 기반을 앱에 넣는다. 이후 화면 세션(홈·코스 상세, 케어 C01~C12, 주행 중 음성 D01, 그로우)이 다시 고치지 않고 쓸 수 있어야 한다.
- 시터노트 팔레트 7색+그라데이션 토큰, Pretendard 10단 위계, 앱 프레임 셸(1256x886 비율, 3열 그리드, 상단 핸들), 정차/주행 상태 전달, 공통 컴포넌트, 아이콘 규격.
- 완료 조건: 기존 화면(홈·코스·케어·설정·그로우·안내)이 깨지지 않고 새 셸 위에서 동작, `./gradlew :app:testDebugUnitTest` 통과, 에뮬레이터 비교 캡처를 PR에 첨부. → 모두 충족.

## 2. 건드린 파일 / 건드리지 않은 파일
- 건드림(`android/app/src/main/java/io/github/haeyeonyou/familytrip/ui/`): `Theme.kt`, `Common.kt`, `AppShell.kt`, 신규 `SitterType.kt` `SitterIcons.kt` `SitterGrid.kt` `DriveMode.kt`. 글꼴 `res/font/pretendard_*.otf`, 라이선스 `docs/licenses/Pretendard-OFL-1.1.txt`, 테스트 `ui/ShellFoundationTest.kt`, `MainActivity.kt`는 `drive = DriveMode.from(vehicle)` 한 줄.
- 건드리지 않음: 모든 화면 파일, `care/CareTone.kt`, `FigmaIcons.kt`, 서버, `feature/139-care-hf03` 브랜치, 설정(HF05).

## 3. 관련 자료
- Figma 노드: 셸 3변형 `351:2613` / `351:2758` / `351:2903`, 앱 프레임 `352:2747`(652,76 1256x886), 독 `352:3003`, 팔레트 `358:5057`, 타입 `357:1733`, 컴포넌트 `361:5000`.
- 문서: `docs/design/pleos-1110/` 의 `01`~`05`, `tokens.json`, `assets/`, 사용법 `07-m1-foundation.md`; 계획서 `docs/development-plan-1110.md`.

## 4. 결정과 미정
결정
- 레일 제거, 독은 화면 3열째(H01·G01·C01~C12·D01), 코스 상세 H02·H02-1은 독 없음. 독 위치가 화면마다 3열째 맨 아래라 셸에 고정하지 않고 화면이 `ShortcutDock()`을 둔다. 미이전 화면은 셸의 임시 독 줄(음성 버튼+독)이 대신한다.
- 프레임: 앱 노출 영역 1280x902를 창에 맞춰 키우고(1682x1210에서 약 1.31배) 프레임은 12/8 안쪽. 좁은 창(<1200dp)은 8dp 여백만 두고 기존 세로 쌓기 유지. 전 화면 일괄 확대는 하지 않고 `SitterCanvas`를 쓰는 새 화면만 확대.
- 정차 정의는 `VehicleState.isParked`(P단) 한 곳. `DriveMode.speedKph`는 SDK에 차속 API가 없어 항상 null.
- 토큰 이름: `SitterPalette`, `SitterSurface.current`(ink/inkSoft/inkMute/primary/primarySoft/card/tile/line/app), `SitterText.*`, `SitterShapes.*`. 새 코드는 `TripColors`를 쓰지 않는다.
- 셸 API: `AppShell(tab,onTab,onMic,drive)`, `LocalShell`, `LocalDriveMode`, `ShellVariant`, `SitterCanvas(ownsDock)`, `SitterVoiceBar/Weather/ChipRow`, `SitterThreeColumns`, `SitterWideAndColumn`, `SitterScrim`.
- 버튼 높이 Large는 차량 터치 규칙에 맞춰 76(Figma 72). 주행용 Drive는 96.
미정
- 정차 기준이 P단인지 주차 브레이크인지(`05` Q01). 바뀌면 `VehicleState.isParked`만 수정.
- `TripColors` 별칭과 임시 독 줄을 지우는 시점(모든 화면이 `SitterCanvas`로 옮겨간 뒤).
- 기저귀·수유·달래기 상황 톤(`CareTone`)의 폐기 여부(케어 세션이 결정).
- Dark 보조 글자·타일 색 확정(현재 추정).

## 5. 진행 로그
| 날짜 | 한 일 | 다음 단계 |
|---|---|---|
| 2026-11-10 | 이슈 #144, 글꼴(OFL) 받아 번들, 토큰·타입·아이콘·DriveMode·셸·컴포넌트 구현, 단위 테스트 9개, 에뮬레이터 5화면·수면 Dark 확인, PR #145 병합, #24 갱신 | 화면 세션이 `07-m1-foundation.md`대로 화면을 `SitterCanvas`로 이전 |
| 2026-11-10 | 이 기록 파일과 `HANDOFF.md` 행 추가 | — |

## 6. 확인하지 못한 것
- 주행(Drive On) 실제 전환: 에뮬레이터 빌드가 `inject-vhal-event`를 막아 기어를 바꾸지 못했다. 셸에 주행을 임시 강제해 깨지지 않는 것만 확인(커밋 안 함), 규칙은 단위 테스트로 검증.
- Pleos 실제 단말의 앱 창 크기·밀도(에뮬레이터는 1682x1210, 밀도 1).
- Dark 보조 글자 #B0B8C1·타일 #2A2F37(Figma 직접 확인 못한 추정).
- 같은 에뮬레이터를 쓰는 다른 세션 앱이 화면을 가로채는 문제가 있어 검증 시 `adb`로 앞 화면 확인이 필요.
