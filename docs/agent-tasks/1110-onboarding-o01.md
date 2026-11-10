# 온보딩 O01~O06 구현 지시서 (11/10 디자인)

- 상태: **구현 전** (디자인 정리만 끝남, 2026-11-11) · 정리한 세션: figma-watch (이슈 #157)
- 구현 세션은 이슈를 새로 만들고 `feature/<번호>-onboarding` 브랜치에서 한다. 이 파일의 진행 로그와 상태를 갱신한다. 키·토큰 값은 적지 않는다.

## 1. 목표
차 앱 첫 실행에서 앱 소개(O01) → (웹앱 계정 QR 연결 O02) → 차량·앱 권한(O03) → 아이 정보(O04·O04-1) → 아이 확인·추가(O05) → 준비 완료(O06) → 홈(H01)으로 이어지는 온보딩을 11/10 디자인대로 만든다. 시연용이 아니라 실제 데이터(서버 계정·가족 API, 차 로컬 저장)로 동작해야 한다.

## 2. 완료 조건
- [ ] 처음 실행(온보딩 미완료)이면 O01부터, 끝났거나 `나중에 하기`를 누르면 다음 실행부터 H01. 안내 중이면 안내 화면이 우선(지금 규칙 유지).
- [ ] O02: 기존 QR 로그인(`ui/LoginScreen.kt` 로직)을 새 화면으로 옮김, 만료 카운트다운·`새 QR 받기`, 로그인 후 불러온 항목 체크 표시. 아이가 있으면 O05로(O04 생략).
- [ ] O03: 위치 등 권한 요청, 공조 지원 조회 결과로 미지원 항목은 "이 차는 안내만 해요". 선택 토글 저장.
- [ ] O04·O04-1: 이름(최대 10자, 키보드 + 음성), 생년(월) 휠 시트, 수유 간격·낮잠·함께 챙길 것 칩, 오른쪽 미리보기가 입력에 따라 바뀜. 저장은 `FamilyProfile`/서버 `ChildIn`과 같은 값.
- [ ] O05: 아이 카드 목록·선택·편집·추가(→ O04).
- [ ] O06: 요약 3칸, 마지막 수유 칩 → 수유 기록 1건(`나중에`면 기록 없음 = C02 상태), `홈으로` → H01.
- [ ] 주행 중이면 온보딩 전체를 "정차하면 이어서 할게요" 안내로 덮고 입력값 유지(QO8 결정 따름).
- [ ] 순수 로직 단위 테스트(단계 경로, 월령 계산, 칩 → 저장값 변환, 시작 화면 분기), 에뮬레이터 캡처와 Figma PNG 비교 이미지 `docs/screenshots/`.

## 3. 디자인 자료
- 화면 글·구성: `docs/design/pleos-1110/02-screen-summaries.md` "온보딩 O01~O06"
- 데이터·코드 차이, 서버·앱에 필요한 것: `04-dev-spec-and-code-gap.md` "4-O"
- 미정 사항: `05-gaps-and-open-questions.md` "H. 온보딩"(QO1~QO9)
- PNG: `docs/design/pleos-1110/assets/screens/O01-onboarding-start.png`, `O02-onboarding-qr.png`, `O03-onboarding-permission.png`, `O04-onboarding-child-info.png`, `O04-1-onboarding-birthdate.png`, `O05-onboarding-child-confirm.png`, `O06-onboarding-ready.png`
- 토큰·셸: `07-m1-foundation.md`(`SitterPalette` 등, `TripColors` 쓰지 않음). 온보딩은 앱 셸의 음성 입력줄·칩 줄·바로가기 독이 **없는** 전용 레이아웃.

| 화면 | Figma 노드 | Note 노드 |
|---|---|---|
| 묶음 제목 `⑤ 온보딩 · 아이 정보 입력 (정차 중)` | `369:20598` | — |
| O01 시작 | `369:20599` | `369:20656` |
| O02 웹앱 계정 연결 (QR) | `369:20657` | `369:20958` |
| O03 차량 · 앱 권한 | `369:20959` | `369:21046` |
| O04 아이 정보 입력 | `369:21047` | `369:21171` |
| O04-1 생년월일 선택 | `369:21172` (시트 `369:21297`) | `369:21334` |
| O05 아이 확인 · 추가 | `369:21335` | `369:21420` |
| O06 준비 완료 | `369:21421` | `369:21490` |
| (참고) 웹 S02 · S02-1 아이 정보 | `369:10409` · `369:10451` | — |

파일 `xeLwkzhH8tnsazQdOriGU2`, 페이지 `종합 정리`(130:5) 최상위. 섹션 `351:2611` 밖이라 `tools/figma_watch.py` 감시 대상이 아니다(바뀌면 감시 범위를 넓혀야 함).

## 4. 건드릴 파일
- 새로: `android/.../ui/onboarding/`(화면 O01~O06, 생년월 휠 시트), `android/.../onboarding/`(단계 상태·경로·저장 로직, 순수 함수 + 테스트), 온보딩 완료 플래그 저장소(차 로컬).
- 최소 추가만(공유 파일, 기존 줄 변경 금지 원칙 — `1110-screen-sessions-plan.md` 3절): `MainActivity.kt`(시작 화면 분기·라우트), `ui/AppShell.kt`(온보딩 중 독·음성줄 숨김이 필요하면).
- 재사용: `ui/LoginScreen.kt`의 QR 요청·폴링 로직(옮기거나 함수로 분리), `data/AccountStore.kt`, `data/FamilyProfile.kt`(`Child`, `ProfileStore`), `data/FamilySync.kt`, `data/FeedingStore.kt`, `vehicle/PleosVehicleRepository.kt`(공조 지원 조회), `care/ChildSelect.kt`.
- 서버(별도 세션, QO 결정 후): `server/app/family.py`(`ChildIn` 필드), 필요 시 `presets.py`.
- 안 건드림: 케어·홈·그로우 화면 파일(각 세션 소유), 설정 화면, 안내(`guide/`).

## 5. 기존 코드와의 관계
- 지금 첫 실행: 계정이 없고 이번 실행에서 건너뛰지 않았으면 `Routes.LOGIN`(QR)부터(`MainActivity.kt:172`). 건너뛰기는 메모리 값(`AccountStore.skipped`)이라 다음 실행에 또 뜬다 → 온보딩 완료 플래그로 바꾼다.
- 폰 웹 가입·로그인: 서버 `/signup`·`/login`·`/car`(`server/app/auth/web.py`), 가입 후 단계 `/start`·`/start/child`·`/start/care`·`/start/car`(`web_start.py`) — 웹 S02·S02-1과 같은 데이터. 차 O04에서 입력한 값과 같은 `Child`로 저장해야 "웹앱에서 입력해도 같은 데이터"(O04 Note)가 된다.
- 차 QR 로그인: `POST /auth/car-login` → `GET .../qr.png` → `POST .../poll`(`server/app/auth/api.py:55~87`, `expires_at` 있음). 로그인 뒤 `ServerSync`가 가족·수유·방문·프리셋을 맞춘다(차 기록도 계정으로 올라감 = O02 "합쳐져요").
- 아이 정보: 차 `Child(birthYear, birthMonth, name, serverId)`, 서버 `ChildIn`(`nickname`, `birth_month` YYYY-MM, `feed_interval_minutes`, `nap_count`, `nap_times`, `stroller_use` …). 코스 요청에는 개월 수만 보냄(#47).

## 6. 서버에 필요한 것
- (QO2) 생년월일 일·출산 예정일을 저장한다면 `birth_date`/`is_due_date` 추가. [제안]은 월까지 유지 → 서버 변경 없음.
- (QO4) 카시트 뒤보기 필드 `rear_facing_seat`(쓰임새 정해지면).
- (QO5) 아이별 모드 값으로 정하면 프리셋에 `child_id`.
- (QO9) 자주 가는 케어 장소 목록 API(지금은 차 로컬만).
- 온보딩 완료 여부는 서버 불필요(추정).

## 7. 미정 사항 (사용자 결정 필요, 상세는 `05` H절)
QO1 단계 번호·경로 / QO2 생년월일 일·예정일 / QO3 수유 간격 선택지·"수유 안 해요" / QO4 "함께 챙길 것" 저장 단위 / QO5 아이별 모드 값(D4와 충돌) / QO6 캘린더·음성 권한 동작 / QO7 "나중에 하기" 후 동작·다시 열기 / QO8 주행 중 온보딩 / QO9 케어 장소 불러오기. 결정 전에는 각 항목의 [제안]대로 만들고 PR에 적는다.

## 8. 진행 로그
- 2026-11-11: figma-watch 세션이 Figma REST API(읽기 전용)로 노드 17개·PNG 7장을 받아 02·04·05와 이 지시서를 씀. 구현은 하지 않음.

## 9. 확인하지 못한 것
- 프로토타입 연결이 없어 화면 이동은 Note 글 기준이다. 주행 중 안내 화면, "이 차는 안내만 해요" 상태, 빈 상태(아이 0명) O05, 출산 예정일 모드 화면은 그려져 있지 않다.
- Pleos SDK에서 "문·정차 상태", "차량 정보" 권한이 앱 권한으로 따로 있는지 확인하지 못했다.
- 이름 음성 입력 버튼 위치.
