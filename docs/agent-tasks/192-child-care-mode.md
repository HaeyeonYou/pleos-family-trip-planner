# 192: 케어 모드 값(공조 프리셋) 아이별

- 상태: 🔶 진행 중 (2026-11-11) · 이슈 #192 · 브랜치 `feature/192-child-care-mode`
- 관련: QO5 결정(`1110-onboarding-o01.md` 7절, `docs/design/pleos-1110/05-gaps-and-open-questions.md` H절), D4 폐기(`06-dev-plan.md`), 케어 C07·C08(`care-c01.md`, PR #169·#174), 온보딩 O05(PR #187·#190), 적용 시점 #168
- Figma: `docs/design/pleos-1110/assets/screens/C07-care-child-select.png`, `C08-care-mode-edit.png`, `O05-onboarding-child-confirm.png`

## 1. 목표와 완료 조건
사용자 결정(2026-11-11, QO5): **케어 모드 값(공조 프리셋)은 아이별로 유지하고, 케어 화면에서도 아이별로 케어 모드를 고른다.**
- 서버: 프리셋에 `child_id`, `/presets`·`/me/sync`·웹 MY·케어 화면이 아이별 값을 다룬다. 테스트 통과 → PR 병합.
- 차 앱: O05 아이 카드마다 그 아이 모드 값 표시·수정. C07 아이 선택·C08 모드 편집·공조 적용이 고른 아이 값을 쓴다. 단위 테스트, 에뮬레이터 아이 2명 비교 캡처(`docs/screenshots/`) → PR 병합.
- HANDOFF 자기 행, #24 갱신.

## 2. 범위
- 서버: `server/app/models.py`(ClimatePreset), `server/app/presets.py`, `server/app/sync.py`, `server/app/auth/web_app.py`, 템플릿 `care.html`·`home.html`·`my.html`·`preset.html`, `static/m.css`, 마이그레이션 `c4f1a8e2d6b9`, `tests/test_presets.py`, `docs/research/web-server-contract.md`.
- 앱: `data/PresetSync.kt`·`SyncModels.kt`·`TripApi.kt`·`ServerSync.kt`, `care/CareController.kt`·`ChildSelect.kt`, `ui/CareScreen.kt`·`CareModeEditSheet.kt`, `ui/onboarding/*`, `ui/TripViewModel.kt`(최소 추가).
- 건드리지 않음: 수유 기록 기능(삭제 예정이지만 이 작업에서 지우지 않음), 다른 공유 파일은 최소 추가만.

## 3. 결정 (이 작업에서 정한 세부)
- `child_id`가 NULL인 행 = **가족 기본값**. 예전 차 앱(아이를 모름)이 쓰고, 자기 값이 없는 아이는 이 값(없으면 슬롯 기본값)으로 시작한다. 유일 키 `(user_id, child_id, slot)` NULLS NOT DISTINCT (PostgreSQL 15+).
- 마이그레이션은 기존 계정 행을 가족 기본값으로 남기고 **지금 있는 아이마다 복사**(보낸 시각·적용 상태 포함).
- 새 API `GET /presets/children`(가족 기본값 + 아이별 묶음). `GET /presets?child_id=`, `PUT /presets/{slot}?child_id=`. 다른 가족 아이 id면 404.
- 웹: 케어 화면에 아이 탭(`/m/care?child=`), 편집 화면 제목 "서아 · 낮잠", 아이가 있으면 웹에서 보낸 값은 그 아이 것. 홈 "다음 탑승 때 적용"은 아이 이름을 붙여 모든 아이 것. MY 아이 카드 밑에 탑승 준비 값 한 줄 → 케어 아이 탭으로.
- 권장값 안내(C03)는 첫째가 아니라 고른 아이 기준.
- #168(적용 시점): 같은 표지만 '출발 시각' 기준을 사용자가 정해야 해서 이 PR에 넣지 않았다(사용자 확인 필요).

## 4. 진행 로그
| 날짜 | 한 일 | 다음 |
|---|---|---|
| 2026-11-11 | 이슈 #192, 기록 정정(QO5 확정·D4 폐기). 서버 구현·테스트(전체 254개 통과, 새 3개). 마이그레이션을 빈 DB에서 확인: 아이 2명 계정의 행 2개 → 가족 2 + 아이별 4, downgrade·재 upgrade·`alembic check` 통과 | 서버 PR 병합 후 앱 |

## 5. 확인하지 못한 것
- (서버) 운영 DB(NAS)에는 아직 `alembic upgrade head`를 하지 않았다.
