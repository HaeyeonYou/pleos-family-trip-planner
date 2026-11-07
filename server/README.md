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

## 주차장 데이터 (`parking_lots`)
전국주차장정보표준데이터는 지자체 250곳이 각자 올린 파일이라 API로 부르지 않고, 정제된 파일을 **미리 받아 DB에 넣어** 씁니다. 데이터 정제는 다른 에이전트가 하고, 서버는 아래 규격의 파일을 받습니다.

| 파일 | 설명 |
|---|---|
| `parking_lots.csv` | UTF-8, 헤더 포함, 열 이름·순서는 `app/models.py`의 `ParkingLot`과 같음 (`source, source_id, name, kind, lot_type, address_road, address_lot, latitude, longitude, capacity, fee_type, base_minutes, base_fee, add_minutes, add_fee, day_fee, weekday_open, weekday_close, sat_open, sat_close, holiday_open, holiday_close, phone, provider, data_date`) |
| `parking_lots.sql` | CSV와 같은 내용. `BEGIN; INSERT ... ON CONFLICT (source, source_id) DO UPDATE SET ...; COMMIT;` |
| `rejected.csv`, `README.md` | 좌표 누락 등으로 뺀 행과 이유, 출처·기준일·건수 |

- 작업용 파일은 `server/data/parking/`에 둡니다 (git 제외). 실제 데이터 원본(csv.gz)은 `server/seed/`에 커밋해 두었습니다. 테스트용 **샘플**(실제 데이터 아님, `source=sample-std-parking`, 이름 앞 `[샘플]`)은 `server/tests/data/parking_sample.csv`.
- 표는 `alembic upgrade head`로 만들어져 있어야 합니다. 열 값 규칙: `kind` 공영/민영, `lot_type` 노외/노상/부설, `fee_type` 무료/유료/혼합, 시간 `HH:MM`(`24:00` 가능), 날짜 `YYYY-MM-DD`, 좌표는 국내 범위. `(source, source_id)`가 같으면 덮어씁니다.

### 어느 쪽으로 넣나
| 상황 | 방법 |
|---|---|
| CSV를 검증하며 넣고 싶을 때 (로컬, 에이전트가 만든 파일 확인) | `load_parking.py` — 잘못된 행을 건너뛰고 이유와 개수를 출력합니다 |
| NAS 등 새 PostgreSQL에 그대로 옮길 때 (파이썬 환경 없이) | `parking_lots.sql`을 `psql -f`로 |

**스크립트** (같은 파일을 여러 번 넣어도 안전)
```bash
cd server
.venv/bin/python scripts/load_parking.py data/parking/parking_lots.csv --dry-run   # 검증만
.venv/bin/python scripts/load_parking.py data/parking/parking_lots.csv             # 적재
```
접속은 `.env`의 `DATABASE_URL`(`--database-url`로 바꿈). 헤더가 규격과 다르면 고치지 않고 오류로 끝나며, UTF-8(BOM 포함)과 CP949를 읽습니다.

**SQL 파일** (PostgreSQL 17)
```bash
cd server && .venv/bin/alembic upgrade head          # 표가 없으면 먼저
psql -d pleos -v ON_ERROR_STOP=1 -f data/parking/parking_lots.sql
psql -d pleos -c "SELECT count(*) FROM parking_lots"
```
**NAS로 옮긴 뒤 (저장소에 실제 데이터 포함)**: 전국 17,147행(2026-11-08 수집, 출처·제외 사유는 `seed/parking_lots_README.md`)을 `server/seed/parking_lots.csv.gz`(약 1MB)로 저장소에 넣어 두었습니다. `git clone` 후 이 파일만 풀어 스크립트로 넣으면 되고, 데이터를 따로 복사할 필요가 없습니다.
```bash
cd server
gunzip -k -c seed/parking_lots.csv.gz > data/parking/parking_lots.csv
.venv/bin/alembic upgrade head          # NAS DB에 표 만들기 (DATABASE_URL을 NAS 주소로)
.venv/bin/python scripts/load_parking.py data/parking/parking_lots.csv
```
- `pg_dump --data-only`로 옮기는 방법은 권하지 않습니다. `id` 시퀀스가 따라오지 않아 이후 적재 때 `id`가 겹칠 수 있습니다.
- 데이터를 새로 받으면(다른 에이전트가 갱신) 같은 이름으로 `seed/parking_lots.csv.gz`를 바꿔 커밋하고, 위 적재 명령을 다시 실행하면 덮어쓰기됩니다.

**DataGrip으로 넣을 때**: `parking_lots.csv`는 표 우클릭 → Import Data from File(`id`, `created_at`, `updated_at`은 매핑 제외)로, `parking_lots.sql`은 파일 우클릭 → Run으로 실행합니다. SQL 파일을 Import에 쓰면 `BEGIN;`이 데이터로 읽혀 오류가 납니다. Import는 `ON CONFLICT`가 없어 두 번 넣으면 중복 오류가 나므로, 다시 넣을 때는 먼저 `TRUNCATE parking_lots RESTART IDENTITY;`를 하거나 스크립트를 쓰세요.

### 근처 조회
`GET /places/parking?lat=&lng=&radius_m=1000&limit=5` (`radius_m` 100~20000, `limit` 1~30). 가까운 순 목록:
```json
[{"name": "구리시청 공영주차장", "kind": "공영", "lot_type": "노외", "address": "경기도 구리시 아차산로 481", "distance_m": 120, "capacity": 180,
  "fee_summary": "30분 1,000원·추가 10분 500원·일 최대 10,000원", "hours": "평일 07:00~22:00", "latitude": 37.5943, "longitude": 127.1296, "phone": "031-000-0008"}]
```
- 요금 문구: 무료는 `무료`, 기본 시간이 무료면 `60분 무료·추가 10분 300원`, 정보가 없으면 `요금 정보 없음`.
- 운영시간은 한국 시간 오늘 요일 기준(평일/토요일/일요일·공휴일, 공휴일 달력은 없어 평일은 평일로 봄). `00:00~24:00`은 `24시간`.
- 위경도 사각형으로 먼저 거르고(`ix_parking_lots_latitude_longitude`) 정확한 거리는 haversine으로 계산합니다.

### planner 연결 방법 (아직 연결 안 함)
코스에 주차장을 붙이는 일은 `planner.py`를 고치는 세션과 겹치지 않도록 이번에는 하지 않았습니다. 연결은 `parking.nearby`를 부르면 됩니다.
```python
from app.sources import parking
lots = await parking.nearby(db, stop.latitude, stop.longitude, radius_m=800, limit=3)   # list[ParkingPlace]
```
`nearby`는 `AsyncSession`을 받는 `async` 함수입니다. 현재 `/course`는 동기 함수(`def`)라서, 연결할 때 `get_db`를 주입하고 엔드포인트를 `async def`로 바꾸거나 `CourseResponse`에 `parking: list[ParkingPlace]`를 더하는 변경이 함께 필요합니다.

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
| `GET /places/parking` | 좌표 근처 주차장(거리순, 요금·운영시간 문구 포함) |
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
- 관심사(`interests`, 예: 공룡·기차·물고기): 장소 이름·분류에 관련어가 있으면 가점, 앞의 3개는 카카오에서 대표어(물고기→아쿠아리움)로 장소를 더 찾음. 사전은 `app/interests.py`
- TourAPI 개발계정 한도(하루 1,000건) 때문에 목록 6시간, 편의 정보 24시간 메모리 캐시

## 설정
| 환경변수 | 값 | 설명 |
|---|---|---|
| `PARSER` | `auto`(기본) / `rule` / `claude` | 자연어 → 조건 변환 방식. `auto`는 `ANTHROPIC_API_KEY`가 있으면 Claude, 없으면 규칙. 3초를 넘기거나 실패하면 규칙 기반으로 대체(`/parse` 응답의 `parser`가 실제로 쓴 방식). `claude`는 키 필수 |
| `CLAUDE_MODEL` | `claude-haiku-5-5`(기본) | |
| `DEMO_MODE` | `false`(기본) | 외부 API 대신 고정 데이터 응답 (PT 대비) |

## 공공데이터 사전 적재
어린이놀이시설·문화시설(박물관·미술관·도서관)은 `server/data/*.json`에 미리 받아 둔 것을 씁니다. 다시 받으려면:
```bash
cd server && .venv/bin/python -m scripts.load_public_data   # 문화시설은 호출을 천천히 해서 약 4분
```
인증키는 `.env`의 `DATA_GO_KR_KEY`이고, 데이터셋마다 공공데이터포털 활용신청 승인이 필요합니다. 자세한 내용은 `docs/research/course-data-sources.md`.

## 사용자 문장의 외부 전송 안내
조건 입력 칸의 자유 문장(관심사·요청)은 `/parse`에서 해석하려고 **Anthropic(Claude API)으로 전송**됩니다(키가 설정된 경우). 키는 서버 `.env`의 `ANTHROPIC_API_KEY`에만 두고 앱에는 넣지 않습니다. 서버 로그에는 문장 원문 없이 길이·걸린 시간·토큰 수만 남깁니다. 전송을 원하지 않으면 `PARSER=rule`로 두면 서버 안의 키워드 규칙만 씁니다. Claude 호출은 모두 `app/llm.py` 한 곳을 지납니다(음성 명령 분류 등도 여기를 쓰세요).

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
