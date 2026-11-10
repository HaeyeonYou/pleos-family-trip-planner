# 작업 지시·기록: 주행 중 음성 1곳 제안 D01 (11/10 디자인)

- 상태: **진행 중 (코드 완성·병합, 에뮬레이터 모양 확인 남음)** · 세션명 `drive-voice-d01` · 이슈 #151
- 공통 방식·파일 소유·토큰 한도 점검 규칙: [1110-screen-sessions-plan.md](1110-screen-sessions-plan.md) (먼저 읽는다)

## 1. 목표와 완료 조건
주행 중(기어 ≠ P, `LocalDriveMode`) 운전자가 "수유실 찾아줘", "애 운다", "기저귀 갈아야 해" 등을 말하면, 목록이 아니라 **최우선 1곳만** 말하고 큰 버튼 2개(`좋아 · 추가`, `아니요`)로 응답받고, **8초 동안 응답이 없으면 자동으로 닫힌다**. 정차 중에는 기존 패널 방식을 유지한다.
- 완료 조건: D01 화면(`docs/design/pleos-1110/assets/screens/D01-drive-voice.png`)과 같은 모양(앱 프레임 가운데 모달 약 1000×660, 화면 `06-dev-plan.md` P1-10 기준), 1곳 추천 로직과 8초 타이머·응답 처리 단위 테스트, TTS 음성 안내(`voice/`의 기존 음성 출력 사용), "좋아"로 경유지 추가(`addWaypoint`) 후 닫힘, 에뮬레이터 비교 캡처.
- 주행 중 조작 가능한 큰 버튼(`Drive` 규격, 높이 96)만 쓴다. 키보드 입력은 막고 버튼은 허용하는 기존 규칙을 따른다.

## 2. 범위와 파일
- 고침: `ui/VoicePanel.kt`, `voice/` 아래, 신규 컴포저블(`DrivingVoiceCard` 등).
- 건드리지 않음: 케어 화면 레이아웃(`ui/CareScreen.kt`, 케어 세션), 홈·코스, 설정, `guide/`. 케어가 후보를 보여 주는 `care/CareController.kt`의 `findNearby`는 **읽어서 호출만** 한다. 바꿔야 하면 케어 세션과 조율한다(통제 세션에 알린다).
- 공유 파일은 최소 추가만(계획서 3절).

## 3. 참고 자료
- Figma 노드: D01 `356:6661`, 음성 카드 `356:7052`.
- 문서: `docs/design/pleos-1110/02-screen-summaries.md`, `04-dev-spec-and-code-gap.md`(D01·음성 절), `05-gaps-and-open-questions.md`(Q02, Q05, Q23), `06-dev-plan.md` P1-10과 음성 명령 절(V1~V3), `07-m1-foundation.md`.
- 기존 코드: `voice/VoiceController.kt`·`VoiceCommand.kt`·`VoiceIntent.kt`·`VoiceAnswers.kt`, 서버 `POST /voice/interpret`, `care/CareController.kt`(`findNearby`), `navi/`(경유지 추가).

## 4. 결정과 미정
- 정차/주행 기준은 `VehicleState.isParked` 한 곳(Q01 미정). 에뮬레이터는 기어 변경 명령이 막혀 실제 전환을 못 한다 → `DriveMode`를 주입해 단위 테스트로 검증하고, 에뮬레이터는 임시 강제(커밋 금지)로 모양만 확인한 뒤 PR에 적는다.
- 8초 안전 타이머·1곳만 제안·화면 알림 대신 음성 중심은 디자인 확정 규칙이다. 추천 기준(거리·경로 우회·시설 태그)은 `05` Q23의 [제안]을 따르고 PR에 적는다.
- 서버에 필요한 것(있으면 여기에 적는다): 경로 기준 후보 조회.

## 5. 진행 로그
| 날짜 | 한 일 | 다음 단계 |
|---|---|---|
| 2026-11-10 | 지시 파일 작성 | 세션 시작 → 이슈 생성, 브랜치, 1곳 추천 로직과 8초 타이머부터 |
| 2026-11-10 | 이슈 #151. `voice/DriveProposal.kt`(1곳 선택·문장·답 분류·`AutoCloseTimer`), `VoiceController`(주행 중이면 `propose`, 말 끝난 뒤 8초·안전 20초·말로 답 듣기), `ui/DrivingVoiceCard.kt`(중앙 모달, Drive 96 버튼 2개), 단위 테스트 12개 통과 | 에뮬레이터 임시 강제로 모양 비교 캡처 → `docs/screenshots/` |

## 6. 확인하지 못한 것
- 에뮬레이터에서 D01 모양과 Figma 비교 캡처는 **하지 못했다**(사용량 한도 근접으로 정리). 컴파일·단위 테스트만 확인.
- 실제 주행 전환·TTS 끝난 뒤 마이크 재청취(Pleos STT)는 실기기에서 확인하지 못했다. `speak`의 onDone이 Pleos TTS에서도 오는지 확인 필요(안 오면 안전 타이머 20초로 닫힌다).
- 추천 기준은 Q23 ②(경로 우회 시간 최소, 경로 위=0, 같으면 이동시간·거리). 후보 0건일 때 정차 공간 이어 제안(Q23 d)은 아직 없음(문장만).
- 컨트롤러 흐름(`propose`·수락·거절)은 Handler/Care 의존 때문에 단위 테스트하지 못했고 순수 부분만 테스트했다.
