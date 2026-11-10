# 186 서버 젤리 12분류 판정 (그로우 B안)

## 1. 목표와 완료 조건
- 서버가 장소 분류 근거로 B안 12분류(`Jelly`)를 판정해 코스 장소(`Place.jelly`)와 방문 기록(`VisitOut.jelly`)에 싣는다.
- 방문 기록은 장소 분류 원문(`category`)을 함께 받아 저장한다. 기존 기록(분류 원문 없음)은 조회할 때 이름·5분야로 계산한다(마이그레이션은 열 추가만).
- 경험맵(`/me/experience-map`)에 12분류 묶음(`jellies`)을 더하고 웹 그로우(`/m/grow`)가 그것으로 보여 준다.
- 앱 `grow/JellyModel.classify`가 서버 값을 먼저 쓰고, 없을 때만 지금 이름 규칙으로 대체한다. 단위 테스트.
- 서버 테스트는 `tests/api_cache` 실제 응답 재생.

## 2. 범위
- 건드림: `server/app/course/jelly.py`(새), `server/app/schemas.py`(Jelly·Place.jelly), `server/app/sources/tour.py`(lclsSystm3), `server/app/course/planner.py`(_tour_place 분류), `server/app/visits.py`, `server/app/models.py`(VisitRecord.category), 새 alembic, `server/app/auth/web_grow.py`·`templates/grow*.html`, 서버 테스트, 앱 `data/`(Visit·CoursePlace·SyncModels·VisitSync), `grow/JellyModel.kt`, 앱 테스트, 문서.
- 건드리지 않음: `server/app/voice.py`, 앱 `voice/`(그로우 음성 세션), `server/app/family.py`·케어 장소 API(온보딩 세션).

## 3. 관련
- 이슈 #186, 브랜치 `feature/186-server-jelly12`
- 근거: `docs/agent-tasks/164-grow-g01.md` 4절(분류 정의)·6절, PR #170·#182

## 4. 결정과 미정
- 판정 순서: ① 분명한 분류 코드·카테고리(관광공사 cat3/lclsSystm3, 카카오 category_name, 공공데이터 분류) → ② 장소 이름 낱말(앱 규칙과 같은 목록) → ③ 넓은 분류(축제 일반·카카오 '관광,명소' 등) → ④ 5분야 → ⑤ 여행.
- 복합쇼핑몰·백화점·대형마트는 '여행'(둘러보기)으로 본다(예전엔 play → 몸놀이).
- 경험 수는 앱과 같게 10분 이상 머문 확정 방문(머문 시간을 모르면 셈).
- 축제 일반은 여행, 공연은 음악·공연, 전시회는 전시·미술, 지역특산물 축제는 맛 탐험, 자연 축제는 자연·숲(lclsSystm3 EV 코드).

## 5. 진행 로그
| 날짜 | 한 일 | 다음 단계 |
|---|---|---|
| 2026-11-11 | 이슈 #186, 브랜치, 지시서. 캐시 응답의 카카오 category_name·관광공사 cat3/lclsSystm3 값 조사 | 서버 판정 구현 |

## 6. 확인하지 못한 것
