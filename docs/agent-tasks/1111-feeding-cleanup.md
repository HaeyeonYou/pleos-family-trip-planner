# 수유 기록 삭제에 따른 남은 문구·빈자리 정리 (2026-11-11 지시서)

- 상태: **대기 — child-mode 세션이 끝난 뒤 시작**(실행 세션은 통제 세션이 시작) · 접수 전 필수: **예**(H-12)
- 근거: 감사 `docs/audit/2026-11-11-figma-vs-app.md` 1절 7위(H-12 + H-15, O-13, W-5, 설정 루틴), 2절 "의도된 차이"(수유 기록 기능 삭제 예정), 3-4절 C-6·C-12, 6절 F-7.
- child-mode 세션과 파일 겹침: **겹침** — `ui/CareScreen.kt`, `ui/TripViewModel.kt`, `care/CareController.kt`, `data/SyncModels.kt`·`TripApi.kt`(2026-11-11 그 세션 작업 트리에서 수정 중), 또 `data/FeedingStore.kt`·`FeedingSync.kt`(그 세션이 아이별로 바꾼 파일). child-mode(#168 적용 시점) PR이 병합된 뒤 그 main에서 시작한다.

## 목표
"수유 기록 기능 삭제"(사용자 결정, 감사 2절)를 실제로 끝낸다. 기능만 빼고 남는 문구·빈자리·웹 카드를 한 번에 정리해, 화면에 "수유를 기록하면…" 같은 말이 남지 않게 한다.

## 완료 조건
1. **삭제 범위 확정(시작 전 질문 1개):** 아래 "미정"의 QD1 답을 받는다. 답이 오기 전에는 코드를 지우지 않는다.
2. **[필수] H-12:** H02 도착 카드 "케어에서 수유를 기록하면 도착 때 여유를 알려 드려요" 문구 정리(`ui/CourseOverview.kt`).
3. H-15: H02-1 아래 "바꾸면 뒤 일정과 수유 지점 시간이 다시 계산돼요" → 수유 지점 없이 맞는 말로(`CourseOverview.kt`·`CourseDetailLogic.kt`).
4. O-13: O06 마지막 수유 카드 자리가 비어 화면 아래 1/3이 빈다 → 요약 카드 배치 다시(`ui/onboarding/OnboardingScreen.kt`).
5. C-6·C-12: 케어 가운데 열 수유 타이머(C02 첫 기록 전·C03 수유 중·C05 임박·C06 지남) 처리 — QD1 답에 따라 카드 자체를 없애거나 다른 카드(예: 오늘 케어 기록)로 바꾼다(`ui/CareScreen.kt`, `ui/FeedingTimerCard.kt`, `ui/FeedingCard.kt`, `care/FeedingStop.kt`, `ui/CareFeedingStop.kt`).
6. W-5: 웹 홈·케어의 "수유 시각을 기록해 보세요"·"지금 수유했어요" 카드(`server/app/templates/*`, 웹 라우터).
7. 설정의 수유 루틴 항목(`ui/SettingsScreen.kt`), 음성 "수유할게"·"다 먹었어" 분기(`voice/`), 홈 수유 표시(`ui/HomeScreen.kt`) — QD1 범위에 따라.
8. F-7(나중, QF4 결정 뒤): 모드 카드 제목 "베이비케어 모드" vs "베이비케어 공조 모드".
9. 서버 수유 API·DB 표는 지우지 않는다(데이터 보존, 별도 결정). 앱이 부르지만 않게 한다.
10. 검증: 단위 테스트·서버 테스트, 에뮬레이터 캡처(H02·H02-1·O06·케어), 화면 문자열에 "수유를 기록" 같은 말이 남지 않는지 grep. 이 파일 로그·`docs/HANDOFF.md` 자기 행.

## 건드릴 파일
- 앱: `ui/CourseOverview.kt`, `ui/CourseDetailLogic.kt`, `ui/onboarding/OnboardingScreen.kt`, `onboarding/OnboardingFlow.kt`, `ui/CareScreen.kt`, `ui/FeedingTimerCard.kt`, `ui/FeedingCard.kt`, `ui/CareFeedingStop.kt`, `care/FeedingStop.kt`, `care/CareSlot.kt`, `data/FeedingStore.kt`, `data/FeedingSync.kt`, `ui/SettingsScreen.kt`, `ui/HomeScreen.kt`, `voice/`(수유 분기)
- 서버·웹: `server/app/templates/*`(웹 수유 카드), 웹 라우터
- 건드리지 않음: 그로우 파일, 프리셋·모드 편집(`CareModeEditSheet.kt`, `PresetSync.kt` — child-mode 몫). 수유실 찾기(급한 케어 C09~C12)와 수유 정차 **모드**(공조 프리셋)는 수유 "기록"과 별개라 남긴다(QD1에서 확인).

## Figma 노드
- C02 `355:3567`, C03 `355:3942`, C05 `355:4702`, C06 `355:5094`(제목 "베이비케어 공조 모드" `355:4928`·`355:5320`), H02 `353:2745`, H02-1 `353:3066`, O06 `369:21421`, 웹 S02-1 `369:10451`(수유 간격).

## 결정·미정
- 결정: 수유 기록 기능 삭제 예정(감사 2절, 온보딩 QO3에서 수유 입력 제거 완료).
- **QD1(사용자에게 물을 것):** 삭제 범위 — ① 수유 타이머·기록만 삭제(수유 정차 모드·수유실 찾기·"수유할게" 음성은 유지) ② 수유 정차 모드까지 삭제 ③ 수유 관련 전부(수유실 칩 포함) 삭제. 제안: ①.
- QF4(`05`): "공조" 문구 — 제안: "베이비케어 모드" 유지.

## 진행 로그
- 2026-11-11: 지시서 작성(full-audit 세션).

## 확인하지 못한 것
- 수유 기록 삭제가 언제 어떤 범위로 결정됐는지 원 기록을 찾지 못했다(감사 문서 2절·온보딩 QO3에만 있음).
- 도착 지연 알림(C01)이 수유 시각을 쓰는지(`navi/DelayAlertController.kt`에 "수유"가 나옴) 자세히 보지 않았다.
