# 11/10 확정 디자인 에셋 (화면 캡처 · 컨셉 이미지 · 아이콘)

Figma 파일 `xeLwkzhH8tnsazQdOriGU2`, 페이지 `종합 정리`(130:5), 섹션 `11/10 · Pleos 공통 UI + 지수ver`(351:2611)에서 2026-11-10에 저장했다. 다른 세션이 Figma를 열지 않고도 구현할 수 있게 PNG와 SVG를 레포에 둔다. 총 약 4.8MB.

## 화면 캡처 (`screens/`, 모두 1920x1080)

| 파일 | 노드 id | 화면 |
|---|---|---|
| screens/H01-home.png | 352:2601 | H01 홈 — 오늘의 추천 코스 |
| screens/H02-course-detail.png | 353:2745 | H02 홈 > 코스 상세 |
| screens/H02-1-replace-place.png | 353:3066 | H02-1 코스 상세 > 대체 장소 고르기 |
| screens/G01-grow.png | 354:3033 | G01 그로우 — 경험 나무 |
| screens/C01-care-delay.png | 355:3177 | C01 케어 — 정차 중 · 도착 지연 알림 |
| screens/C02-care-feeding-first.png | 355:3567 | C02 케어 — 수유 타이머 첫 기록 전 |
| screens/C03-care-feeding-parked.png | 355:3942 | C03 케어 — 수유 정차 모드 적용 중 |
| screens/C04-care-sleep-dark.png | 355:4322 | C04 케어 — 낮잠·수면 모드 (다크) |
| screens/C05-care-feeding-soon.png | 355:4702 | C05 케어 — 수유 시각 임박 (15분 전) |
| screens/C06-care-feeding-overdue.png | 355:5094 | C06 케어 — 수유 시각 지남 |
| screens/C07-care-child-select.png | 356:4041 | C07 케어 — 아이 선택 펼침 (정차 중) |
| screens/C08-care-mode-edit.png | 356:4452 | C08 케어 — 모드 편집 (정차 중) |
| screens/C09-urgent-nursing-room.png | 356:4897 | C09 급한 케어 — 수유실 후보 (정차 중) |
| screens/C10-urgent-no-result.png | 356:5359 | C10 급한 케어 — 수유실 결과 없음 |
| screens/C11-urgent-parking.png | 356:5796 | C11 급한 케어 — 정차 공간 후보 |
| screens/C12-waypoint-added.png | 356:6254 | C12 경유지 추가 완료 배너 |
| screens/D01-drive-voice.png | 356:6661 | D01 주행 중 — 음성으로 1곳만 제안 |
| screens/type-pretendard.png | 357:1733 | Type · Pretendard 위계 (실제 글꼴 Asta Sans) |

확인: H01·C04(저장 담당 에이전트), C09·D01(메인 세션)을 직접 열어 이름과 맞는 화면임을 확인했다. 나머지는 파일 크기와 PNG 헤더(1920x1080)만 확인했다.

## 컨셉 이미지 (`concept/`)

| 파일 | 노드 id | 내용 |
|---|---|---|
| concept/shell-light-parked.png | 351:2613 | 공통 UI 셸 · Light · 정차 (1920x1080) |
| concept/shell-dark-parked.png | 351:2758 | 공통 UI 셸 · Dark · 정차 |
| concept/shell-light-driving.png | 351:2903 | 공통 UI 셸 · Light · 주행 (속도 62, 기어 D) |
| concept/palette.png | 358:5057 | Palette · 시터노트 (Naver cue 계열) 1200x140 |
| concept/logo-mark.png | 358:5049 | Logo · Mark (120x120) |
| concept/logo-full.png | 358:5051 | Logo · 시터노트 (Full) 443x145 |
| concept/components.png | 361:5000 | Components · 오토레이아웃 (Tag, Button, Course Item, Timeline Row, Place Row, Voice Button) 3193x222 |

로고는 비트맵 120px·443px라 앱 아이콘 제작용 원본으로는 작다. 필요하면 Figma에서 SVG로 다시 받는다.

## 아이콘 (`icons/`)

11/10 확정 디자인의 `icon/...` 프레임 29종을 Figma에서 SVG로 직접 내려받았다. 표(노드 id, 크기, 색, 용도, 등장 화면)와 앱에 옮기는 법은 `icons/README.md`.

- 일부 아이콘은 그라디언트 버튼 위에서 쓰여 선 색이 `white`다(`mic`, `nav`, `turn`, `check` 등). 흰 배경에서 열면 보이지 않으니 어두운 배경에서 확인한다.
- 앱의 `FigmaIcons.kt`에 이미 있는 아이콘은 **옛 디자인에서 옮긴 것**이라 11/10 원본과 모양이 다를 수 있다. 교체 여부는 P0-6(`../06-dev-plan.md`)에서 비교해 정한다.
- 차량 셸(계기판·하단 독) 아이콘은 Pleos OS가 그리는 영역이라 저장하지 않았다.
- 등장 화면은 Figma 노드 순회로 얻은 목록이다(상세 화면 목록은 icons/README.md의 값과 대조해 쓸 것).

## 저장하지 못했거나 확인하지 못한 것

- 아이콘의 정확한 등장 화면 일부와 "용도" 칸의 추정 항목(`icons/README.md`에서 "추정"으로 표시).
- 컴포넌트 라이브러리 변형 각각의 개별 이미지(Tag Yes/No, Button 3종 등)는 `components.png` 한 장으로만 저장했다.
- Figma MCP 호출 한도(Education 플랜)에 병렬 스크린샷이 걸린 적이 있다. 이미지를 더 받을 때는 한 장씩 순서대로 요청한다.
