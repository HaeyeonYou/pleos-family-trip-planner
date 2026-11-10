# Figma 11/10 디자인 변경 감시 (이슈 #157)

- 상태: **진행 중** · 브랜치 `feature/157-figma-watch` · 세션: figma-watch (통제 세션이 시작한 서브 세션)
- 키·토큰 값은 이 파일에 적지 않는다.

## 목표
Figma 파일 `xeLwkzhH8tnsazQdOriGU2` → 페이지 `종합 정리` → 섹션 `11/10 · Pleos 공통 UI + 지수ver`(`351:2611`)을 주기적으로 확인해, 달라진 점을 `docs/design/pleos-1110/watch/CHANGELOG.md`에 남기고 진행 중인 화면 세션에 관련 변경만 전달한다. 같은 계정으로 로그인한 다른 PC에 흔적을 남기지 않는다.

## 완료 조건
- [x] 다른 PC에 영향 없는 확인 방법 안내 (`docs/design/pleos-1110/watch/README.md`에도 정리)
- [x] `tools/figma_watch.py` + `tools/tests/test_figma_watch.py`(15개)
- [x] 변경 기록 형식(`watch/CHANGELOG.md`), `.env.example`에 `FIGMA_TOKEN` 키 이름
- [x] PR 병합 (PR #159)
- [ ] 사용자 토큰 발급 → `baseline` 실행 → 기준선 스냅샷 커밋
- [x] 주기 실행: 3시간마다(매 3시간 :17, 세션 예약 작업 `0ab06494`, 이 세션 안에서만 돌고 7일 뒤 자동 종료)
- [x] `docs/HANDOFF.md` 자기 행 갱신

## 범위
- 고침: `tools/figma_watch.py`, `tools/tests/`, `docs/design/pleos-1110/watch/`, `.env.example`(키 이름만), 이 파일, `docs/HANDOFF.md`의 자기 행.
- 안 고침: `android/`, `server/`, 다른 세션의 지시 파일, `docs/design/pleos-1110/`의 기존 문서·PNG(바뀐 내용은 CHANGELOG에만 적고, 문서 갱신은 통제 세션이 정한다).

## 결정
- 주기 확인은 REST API만. Figma MCP는 읽기 전용으로 꼭 필요할 때만(호출 한도 빠듯).
- 호출 순서: `meta`(Tier 3) → 버전이 바뀐 경우만 `nodes`(Tier 1). Tier 1은 스크립트가 월 15회로 막는다.
- 스냅샷 노드 기록 항목: 이름, 종류, 화면 기준 x·y, 크기, 문구, 글꼴(family/weight/size/lineHeight), 채우기·선 색, 모서리, 불투명도, 오토레이아웃, 효과, 숨김, 컴포넌트 id, 부모.
- 화면 → 세션: H01·H02·H02-1 → `home-h01-h02`, C01~C12 → `care-c01`, D01 → `drive-voice-d01`, G01 → 없음(결정 대기). 섹션 공통 변경이나 새 색은 모든 세션.
- 변경이 없으면 기록하지 않는다(`state.json`만 갱신, 커밋 안 함).

## 변경이 있을 때 절차 (주기 작업이 따른다)
1. `python3 tools/figma_watch.py check` → `status: changed`면 `CHANGELOG.md`·`snapshot.json`·`state.json`이 바뀐다.
2. 브랜치 `feature/157-figma-watch`(병합 후엔 `docs/157-figma-watch-<날짜시각>`)에 커밋 → PR → 바로 병합(`PR #N 병합: Figma 변경 기록 <날짜>`, `Refs #157`).
3. `list_sessions {"linked": true}`로 진행 중인 세션을 찾고, 요약 JSON의 `sessions`에 있는 세션에만 그 화면의 변경 줄을 보낸다(지시 파일 경로와 CHANGELOG 링크 포함). 통제 세션에는 전체 요약.

## 진행 로그
- 2026-11-10: 이슈 #157 생성. 공식 문서로 REST 한도 확인(Tier 1: View·Collab 월 20회까지, Dev·Full 분당 10~20회; `meta`는 Tier 3, 버전 기록은 Tier 2). 스크립트·테스트·문서 작성, 테스트 15개 통과. 사용자 `.env`에 `FIGMA_TOKEN` 없음 → 기준선은 토큰 발급 후.

- 2026-11-10: PR #159 병합. 사용자가 간격을 따로 정하지 않아 추천 범위(2~3시간) 중 3시간으로 예약(Tier 1 월 15회 상한과 맞춤). 첫 실행 결과 `no_token` → 토큰이 들어오면 다음 주기에 자동으로 `baseline`을 만들고 PR로 병합한다.
- 주의: 예약은 figma-watch 세션이 꺼지면 사라진다. 세션을 다시 열면 `/loop` 또는 예약 작업으로 다시 건다.

## 확인하지 못한 것
- 실제 Figma 응답으로 돌려 보지 못했다(토큰 없음). 응답 모양은 공식 문서 기준으로 짰고 테스트는 가짜 응답이다. 첫 `baseline` 때 노드 수·화면 코드 매칭을 확인해야 한다.
- 사용자 좌석 종류와 Education 플랜의 실제 REST 한도.
- REST 조회가 "최근 본 파일"에 남는지, 원격 MCP가 다른 PC에 흔적을 남기는지(둘 다 남지 않을 것으로 추정).
