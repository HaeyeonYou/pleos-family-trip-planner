# 폰 웹 작업 기록 (컴팩트·인계용)

## 진행 상황 (2026-11-08)
- 1단계 병합: PR #108 (이슈 #107) — 디자인 시스템(`server/app/static/m.css`), 탭바, 홈·케어·그로우 안내·MY·계정 삭제
- 2단계 서버 병합: PR #111 (이슈 #109) — `/me/*`, `/presets` 3개, 계약 문서 `docs/research/web-server-contract.md`. 메인 세션에 알림 완료
- 2단계 웹 (이슈 #110), PR 두 개로 나눈다
  - A `feature/110-web-care-my-home`: 케어(수유 타이머·기록, 프리셋 3개+음량), MY(아이·외출 멤버·기록 삭제), 홈 갱신
  - B 그로우(경험맵 G01~G03)와 추천 코스(H03·H04)

## 결정 (사용자 → 메인 세션 경유)
1. 아이·가족 정보 서버 저장 예(최소 항목) 2. 수유 기록 서버 저장 예 3. 일정 저장·구글 캘린더는 안 함, 추천 코스 보기만
4. 방문 기록 서버 저장 예(정책 변경) 5. 프리셋 3개+음량 지금, 예전 차 앱 하위 호환

## 개발 환경 메모
- 테스트: `cd server && /Users/kwonstar/Pleos_Application/server/.venv/bin/python -m pytest -q` (이 워크트리엔 .venv 없음, 메인 체크아웃 것을 씀)
- 로컬 서버: 새 DB를 만들고 `DATABASE_URL=postgresql+asyncpg://localhost/<db> alembic upgrade head` 후 `uvicorn app.main:app --port 8001 --reload`
- 브라우저 검증: 내장 브라우저 375×812, 테스트 비밀번호는 JS로 만들어 localStorage에만 두고 문서·채팅에 쓰지 않음
- 스크린샷: `docs/screenshots/web-mobile/{figma,web,compare}`; 도구가 돌려주는 jpg 경로를 cp로 저장
- 끝나면 #24 진행 기록 갱신, 병합 제목 `PR #번호 병합: <제목>`
