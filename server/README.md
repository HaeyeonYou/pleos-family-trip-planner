# 서버 (FastAPI)

## 로컬 실행
```bash
cd server
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/uvicorn app.main:app --reload
```
- 키는 저장소 루트 `.env`에서 읽습니다 (`.env.example` 참고).
- 에뮬레이터에서는 `http://10.0.2.2:8000`으로 접속합니다.

## 설정
| 환경변수 | 값 | 설명 |
|---|---|---|
| `PARSER` | `rule`(기본) / `claude` | 자연어 → 조건 변환 방식. `claude`는 `ANTHROPIC_API_KEY` 필요, 실패 시 규칙 기반으로 대체 |
| `CLAUDE_MODEL` | `claude-haiku-5-5`(기본) | |
| `DEMO_MODE` | `false`(기본) | 외부 API 대신 고정 데이터 응답 (PT 대비) |

## 테스트
```bash
.venv/bin/python -m pytest
```

## NAS (Synology Container Manager)
`docker compose up -d --build` (저장소 루트 `.env` 필요)
