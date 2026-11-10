# 작업 지시·기록: 케어 화면 C01~C12 (11/10 디자인)

- 상태: **한도 근접으로 중단 (WIP 푸시, 미병합)** (2026-11-10 시작) · 세션명 `care-c01` · 이슈 #150 · 브랜치 `feature/150-care-c01-grid`
- 공통 방식·파일 소유·토큰 한도 점검 규칙: [1110-screen-sessions-plan.md](1110-screen-sessions-plan.md) (먼저 읽는다)

## 1. 목표와 완료 조건
케어 화면을 11/10 디자인의 C01~C12로 새 셸 위에서 만든다. **1차 제출용(11/14까지)은 C01·C02·C05·C06**: 3열 그리드, 오른쪽 가변 슬롯(392×370)이 상황에 따라 바뀐다.
- C01 정차 중 기본 + 지연 알림 슬롯, C02 첫 기록 전, C05 수유 15분 전, C06 수유 시각 지남.
- 완료 조건: 위 4개 화면이 Figma(`docs/design/pleos-1110/assets/screens/C01~C06`)와 구조·값이 같고, 상황에 따라 슬롯이 자동 전환되며(실제 수유 기록·다음 수유 시각·지연 알림 상태에서), 단위 테스트(슬롯 우선순위, 수유 타이머 상태, 지연 문구)와 에뮬레이터 비교 캡처가 PR에 있다.
- 2차(1차 제출 후): C09~C11 급한 케어 시트와 C12 경유지 완료, C08 모드 편집, C03 수유 정차 모드, C04 수면 다크, C07 아이 선택. `06-dev-plan.md` P1-4~P1-9.

## 2. 범위와 파일
- 고침: `ui/CareScreen.kt`, `ui/FeedingCard.kt`(타이머 카드로 대체 가능), `ui/DelayAlertPanel.kt`(정차 중은 슬롯 카드로), `care/` 아래 신규 로직, 필요한 신규 컴포저블.
- 건드리지 않음: 홈·코스 화면, `ui/VoicePanel.kt`/`voice/`(D01 세션 몫, 케어와 음성이 같은 상태를 읽어야 하면 읽기만), 설정, 경험 우주, `guide/`.
- 공유 파일은 최소 추가만(계획서 3절).

## 3. 이전 작업 흡수
브랜치 `feature/139-care-hf03`(옛 HF03 v2 케어)의 **로직만** 가져온다: `care/FrequentCarePlaces.kt`(방문 기록 기반 자주 가는 케어 장소, 테스트 포함), 수유 타이머 상태(`ui/FeedingTimerCard.kt`의 상태 부분), 지연 문구 함수(`ui/DelayAlertBanner.kt`, `CareScreenTextTest.kt`). 화면 레이아웃은 새로 만든다. 그 브랜치는 병합하지 않는다(필요한 파일은 `git checkout feature/139-care-hf03 -- <경로>`로 가져와 정리).

## 4. 참고 자료
- 화면 번호·Figma 노드: C01 `355:3177`, C02 `355:3567`, C03 `355:3942`, C04 `355:4322`, C05 `355:4702`, C06 `355:5094`, C07 `356:4041`, C08 `356:4452`, C09 `356:4897`, C10 `356:5359`, C11 `356:5796`, C12 `356:6254`.
- 문서: `docs/design/pleos-1110/02-screen-summaries.md`, `03-flow-components-states.md`, `04-dev-spec-and-code-gap.md`(케어 절), `05-gaps-and-open-questions.md`(1절 빠진 상태, Q03·Q06·Q09~Q14·Q24·Q25), `06-dev-plan.md` P1-1~P1-3, 토큰 `tokens.json`, 새 셸 사용법 `07-m1-foundation.md`.
- 기존 코드: `care/CareController.kt`, `navi/DelayAlertController.kt`·`DelayTracker.kt`, `data/FeedingStore.kt`, `data/VisitStore.kt`, 서버 `/me/feedings`·`/me/feedings/status`·`/presets`.

## 5. 결정과 미정
- 슬롯 우선순위 등 임시 결정값은 계획서 4절(D2·D3·D4)을 따른다. 정차 기준은 `VehicleState.isParked`(P단) 한 곳.
- 미정: "실내 온도"의 실체(Q11, 센서 없음 → 기존 '차 공조 설정' 표기 유지), 수면 모드 사양 통일(Q09), 알림 정책 충돌(Q04).
- 서버에 필요한 것(있으면 여기에 적는다): 모드 편집의 "적용 시점" 필드, `feedings/status?child_id=`, 급한 케어 경로 우회 시간.

## 6. 진행 로그
| 날짜 | 한 일 | 다음 단계 |
|---|---|---|
| 2026-11-10 | 이슈 #150, 브랜치 `feature/150-care-c01-grid`. #139 로직 흡수(FrequentCarePlaces·VisitStore 좌표·delayBannerView·feedingTimerView, 옛 컴포저블은 제거). `care/CareSlot.kt`(수유 단계 NoRecord/Normal/Soon/Overdue, 슬롯 우선순위 `pickCareSlot`, 빠른 칩 시각, 우회 문구)+테스트. `ui/CareScreen.kt`를 새 셸 3열로 재작성(베이비케어 카드·수유 타이머 4단계·자주 가는 케어 장소·급한 케어 타일 2·슬롯 392x370·독). MainActivity 케어 경로 연결. 컴파일·단위 테스트 통과. 5시간 한도 92%라 중단 | 에뮬레이터에서 C01·C02·C05·C06 캡처를 Figma와 비교하고 어긋남 보정 → docs/screenshots 비교 이미지 → PR 병합. 이후 2차(C09~C12, C08, C03, C04, C07) |
| 2026-11-10 | 지시 파일 작성 | 세션 시작 → 이슈 생성, 브랜치, P1-1(3열 그리드·슬롯 컨트롤러)부터 |

## 7. 확인하지 못한 것
- 에뮬레이터·화면 캡처 비교를 하지 않았다(레이아웃 수치는 PNG에서 읽은 값). 상단 날씨는 데이터 원천이 없어 그리지 않았다.
- 기저귀 교환대·화장실 칩은 결과를 보일 곳(C09~C11)이 없어 비활성, 소아과·약국은 D4대로 비활성.
- 수면 모드 토글 카드는 C04(2차) 전까지 화면에서 빠졌다(음성 "애 잔다"는 그대로). 아이 칩 드롭다운(C07)·모드 편집 시트(C08)는 기존 PresetEditDialog 유지.
- 실내 온도는 센서가 없어 '차 공조 설정' 표기(Q11). 주행 중 전환·실차 확인 못 함. 정차 중 케어 화면에서는 전체 화면 지연 알림을 덮지 않게 했다(MainActivity).
- 로컬 `android/local.properties`(sdk.dir)는 gitignore라 커밋하지 않았다.
