# 서버 (FastAPI)

## 로컬 실행
```bash
cd server
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/uvicorn app.main:app --reload
```
- 키는 저장소 루트 `.env`에서 읽습니다 (`.env.example` 참고).
- 에뮬레이터에서는 `http://10.0.2.2:8000`으로 접속합니다.

## API
| 경로 | 설명 |
|---|---|
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
