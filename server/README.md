# 서버 (FastAPI)

## 로컬 실행
```bash
cd server
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000 --no-access-log
```
- 키는 저장소 루트 `.env`에서 읽습니다 (`.env.example` 참고).
- 에뮬레이터에서는 `http://10.0.2.2:8000`으로 접속합니다.

## DB (PostgreSQL)
- 로컬: `brew install postgresql@17 && brew services start postgresql@17`, `createdb pleos && createdb pleos_test`
- 테이블 만들기·바꾸기: `.venv/bin/alembic upgrade head` (모델을 바꾸면 `alembic revision --autogenerate -m "설명"`)
- 접속 주소는 `.env`의 `DATABASE_URL` (기본 `postgresql+asyncpg://localhost/pleos`), 폰이 접속할 주소는 `PUBLIC_BASE_URL` (비우면 맥 사설 IP:8000)
- 테스트는 `pleos_test` DB를 매번 비우고 쓴다

## 모바일 웹 (`/m`)
폰 브라우저로 여는 화면. 차 화면 QR → 회원가입·로그인 → '이 차에서 로그인', 홈에서 '차 공조 미리 설정' → '차로 보내기'.

## 로그
서버를 실행한 터미널에 요청마다 아래처럼 남습니다. 더 자세히 보려면 `LOG_LEVEL=DEBUG`.
```
17:11:45 INFO  app          | [a2a2d6] → POST /course
17:11:45 INFO  app          | 코스 요청: 아이 5살 · 관심 공룡 · 반려견 · 18시 귀가 · 출발 37.413,127.095
17:11:45 INFO  api.tour     | GET /B551011/KorPetTourService2/locationBasedList2 → 200 (196ms)
17:11:45 INFO  course       | 나들이 후보 1곳, 상위: 성남 중앙공원(-0.4, 4.3km)
17:11:45 INFO  api.cache    | 카카오 장소검색 저장본 사용 (1분 전 호출)
17:11:46 INFO  course       | 코스: 17:20 출발 스시이케도(17:37) → 성남 중앙공원(18:43) → 귀가(20:59), 이동 39분, 길찾기=kakao ⚠ ...
17:11:46 INFO  app          | [a2a2d6] ← 200 /course (778ms)
```
| 이름 | 내용 |
|---|---|
| `app` | 요청 시작·끝(같은 요청은 같은 id), 받은 조건 요약 (원문 문장은 남기지 않음) |
| `course` | 나들이 후보 수와 상위 3곳 점수·거리, 근처 식당·카페 수, 고른 코스와 경고 |
| `api.tour` `api.kakao` | 외부 API 경로·상태·걸린 시간 (API 키는 남기지 않음) |
| `api.cache` | 메모리에 저장해 둔 응답을 다시 쓴 경우 (한도 절약 확인용) |
| `app.parsing.claude` | Claude 해석 시간과 토큰 수, 실패 시 규칙 기반 대체 |

## API
| 경로 | 설명 |
|---|---|
| `POST /auth/car-login` | 차 QR 로그인 요청 생성 (코드, QR 이미지 주소, 폴링 비밀값) |
| `POST /auth/car-login/{id}/poll` | 폰 승인 여부. 승인되면 차 세션 토큰을 한 번만 줌 |
| `GET /auth/me`, `POST /auth/logout` | 로그인 사용자 확인, 차 세션 폐기 (`Authorization: Bearer`) |
| `GET /presets/pending` | 폰에서 보내 아직 적용하지 않은 공조 프리셋 (없으면 204) |
| `POST /presets/{id}/applied` | 차에서 적용 완료 표시 |
| `GET /health` | 상태, 설정된 키 여부 |
| `POST /parse` | 자연어 → `TripConditions` |
| `POST /course` | 조건 + 출발지 → 식사·나들이·카페·귀가 코스와 시간표 |
| `POST /course/schedule` | 장소를 바꾸거나 순서를 고친 코스(`stops`, 체류 시간 `stay_minutes` 선택) → 이동 시간·시간표 재계산 (장소는 다시 찾지 않음). `suggest_nearby: true`면 나들이 장소 근처 식사·카페 후보를 `alternatives`로 줌 |

`/course` 요청 예:
```json
{
  "conditions": {"child_ages": [4], "themes": ["experience"], "needs": ["nursing_room"], "return_by_hour": 17},
  "origin": {"latitude": 37.4979, "longitude": 127.0276},
  "departure_time": "10:30"
}
```
- 나들이: TourAPI(관광지·문화시설, 반려동물 동반 시 반려동물 서비스) + 카카오 "키즈카페·실내놀이터"
- 식사·카페: 나들이 장소 근처 카카오 장소 검색
- 이동 시간: 카카오모빌리티 다중 경유지 길찾기, 실패하면 직선거리 추정(`route_source: estimate`)
- 육아 편의(`stroller`, `nursing_room`, `kids_chair`): TourAPI 무장애 정보. 값이 없으면 `unknown`(정보 없음)이며 '없음'으로 단정하지 않음
- 귀가 시각을 넘기면 카페를 빼고, 그래도 넘기면 `warnings`에 알림
- `alternatives`: 역할별 교체 후보
- 관심사(`interests`, 예: 공룡·기차·물고기): 장소 이름·분류에 관련어가 있으면 가점, 앞의 2개는 카카오에서 대표어(물고기→아쿠아리움)로 장소를 더 찾음. 사전은 `app/interests.py`
- TourAPI 개발계정 한도(하루 1,000건) 때문에 목록 6시간, 편의 정보 24시간 메모리 캐시

## 설정
| 환경변수 | 값 | 설명 |
|---|---|---|
| `PARSER` | `rule`(기본) / `claude` | 자연어 → 조건 변환 방식. `claude`는 `ANTHROPIC_API_KEY` 필요, 실패 시 규칙 기반으로 대체. 응답 약 2초(첫 호출은 8초 안팎) |
| `CLAUDE_MODEL` | `claude-haiku-5-5`(기본) | |
| `DEMO_MODE` | `false`(기본) | 외부 API 대신 고정 데이터 응답 (PT 대비) |

## 테스트
```bash
.venv/bin/python -m pytest
```
- `tests/test_course_planner.py`, `test_rule_parser.py`: 규칙 검증용 고정 데이터 (귀가 시각 초과 같은 상황을 일부러 만듦)
- `tests/test_real_api_data.py`: **실제 API 데이터**로 응답 형식과 코스 생성 검증
  - 처음 실행 때 실제로 호출해 `tests/api_cache/`에 저장하고, 이후에는 저장본을 씀 (TourAPI 하루 1,000건 한도, 같은 호출 반복 방지)
  - 저장본은 레포에 포함되어 키 없이도 실행됨. API 키는 저장하지 않음
  - 데이터 새로 받기·API 변경 검증: `API_CACHE=refresh .venv/bin/python -m pytest tests/test_real_api_data.py`
  - 저장본만 쓰기: `API_CACHE=offline`

## NAS (Synology Container Manager)
`docker compose up -d --build` (저장소 루트 `.env` 필요)
