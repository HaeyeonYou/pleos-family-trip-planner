# 192: 케어 모드 값(공조 프리셋) 아이별

- 상태: ✅ 완료 (2026-11-11) · 이슈 #192 · 브랜치 `feature/192-child-care-mode` · PR #193(서버), #194(앱)
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
- 앱: `ServerSync.allPresets`(가족 기본값 + 아이별 전부)와 `ServerSync.presets`(**고른 아이 것** 3개). 고른 아이의 서버 id(`presetChildId`, 첫째는 키 null이라 `children[0].serverId`)로 고르고, 서버에 그 아이 값이 없거나 서버와 안 맞춘 아이면 가족 기본값. 그래서 케어·수면·수유 정차·베이비케어·음성(낮잠 음량)이 손대지 않아도 고른 아이 값을 쓴다.
- 앱: 아이를 바꾸면 지금 공조를 쓰는 모드 하나(수유 정차 > 수면 > 베이비케어)를 새 아이 값으로 바로 다시 넣고 토스트 "베이비케어 모드를 이 아이 값으로 바꿨어요 · 23°C · 송풍".
- 앱: 저장 대기열 키는 `"<아이 id>/<슬롯>"`, 예전 키 `"<슬롯>"`은 가족 기본값으로 읽는다. `/presets/children`이 없는 예전 서버(404)면 `/presets`(가족 공통)를 쓴다.
- 앱 화면: C07 행마다 그 아이의 베이비케어 값 한 줄 + 안내 "수유 타이머와 모드 값을 아이마다 따로 써요". C08 제목 "도윤 · 모드 편집"(아이 2명 이상일 때). O05 부제 "케어 모드 값은 아이마다 따로 저장해요", 카드 줄 "24°C · 약풍 · 음량 낮게 · 바꾸기"(값을 받기 전엔 "베이비케어 기본값") → 정차 중 '바꾸기'로 그 아이 모드 편집 시트(C08과 같은 것).
- #168(적용 시점): 같은 표지만 '출발 시각' 기준을 사용자가 정해야 해서 이 PR에 넣지 않았다(사용자 확인 필요).

## 4. 진행 로그
| 날짜 | 한 일 | 다음 |
|---|---|---|
| 2026-11-11 | 서버 PR #193 병합. 앱 구현·단위 테스트 286개 통과(새: 아이별 고르기·주인 id·대기열 키 호환·묶음 JSON·C07 모드 줄·모드 다시 넣기·O05 카드 줄). 에뮬레이터(시험 서버 8030·DB `pleos_childmode`, 앱 `.childmode`, 아이 2명 서아 24°C·도윤 22.5°C): QR 로그인 → O05 카드마다 자기 값 → 도윤 '바꾸기'로 23°C 저장 → 서버 도윤 boarding 23.0·대기 아님 확인 → 케어 서아(24°C) → 베이비케어 켬 → C07에서 도윤 → 공조 23.0°C 다시 적용·카드 3개 도윤 값 → C08 "도윤 · 모드 편집". 캡처 `docs/screenshots/192-child-care-mode/`. 시험 서버·DB는 끄고 지움 | HANDOFF·#24 |
| 2026-11-11 | 이슈 #192, 기록 정정(QO5 확정·D4 폐기). 서버 구현·테스트(전체 254개 통과, 새 3개). 마이그레이션을 빈 DB에서 확인: 아이 2명 계정의 행 2개 → 가족 2 + 아이별 4, downgrade·재 upgrade·`alembic check` 통과 | 서버 PR 병합 후 앱 |

## 5. 확인하지 못한 것
- (서버) 운영 DB(NAS)에는 아직 `alembic upgrade head`를 하지 않았다.
- (앱) 에뮬레이터는 공조 23.0°C 적용 요청을 받았다고 하지만 다시 읽으면 24°C로 나온다(가상 차 공조 값, 이 작업 전부터 같은 동작으로 추정). 실차 확인 필요. 음량은 SDK에 제어 수단이 없어 실패로 남는 것도 예전과 같다.
- (앱) 수면·수유 정차 모드 중 아이 바꾸기는 단위 테스트(모드 고르기 규칙)만 했고 에뮬레이터에서는 베이비케어만 확인했다.
- (웹) 케어 아이 탭·편집 화면은 서버 테스트(HTML 내용)로만 확인했고 브라우저 화면은 보지 않았다.
- (발견, 범위 밖) 홈 음성 입력줄 아바타는 고른 아이가 아니라 첫째("서")로 보인다.
- 에뮬레이터를 같이 쓰는 감사 세션(`.audit` 앱)이 확인 중 화면을 한 번 가져갔다. 그 순간 내 탭 하나가 그 앱 설정 화면에 갔을 수 있다(눈에 띄는 변화는 없었음). 메인 서버(8000) DB에 이 작업 첫 빌드가 만든 차 로그인 요청 1건(대기, 만료됨)이 남았다 — 예전 빌드 찌꺼기 dex가 8000 주소를 들고 있어서였고 clean 빌드로 해결.
