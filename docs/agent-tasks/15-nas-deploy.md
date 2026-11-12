# 작업 지시·기록: NAS 배포와 자동 배포 (#15)

- 상태: **완료** — 로컬 네트워크 배포·자동 배포·외부 HTTPS 공개(`https://pleos.kwonstar.synology.me`) · 세션명 `nas-deploy` · 이슈: #15 · 1차 접수(11/15) 전
- 공통 규칙: `docs/HANDOFF.md`

## 1. 목표와 완료 조건
사용자 요청(2026-11-12): "NAS에 배포 진행 후 ClaudeCode에서 내 NAS로 자동배포까지 진행가능한지 확인.(로컬 네트워크임)"
- 서버(FastAPI + PostgreSQL 17)를 SA6400 NAS의 Docker(Container Manager)에 올리고, 같은 로컬 네트워크에서 `/health`가 200을 돌려준다. 이미 있는 `server/Dockerfile`과 `server/docker-compose.yml`을 쓴다.
- DB 마이그레이션(`alembic upgrade head`)을 적용하고, 로컬 서버 데이터를 옮길지는 사용자에게 묻는다.
- **자동 배포 확인**: 이 Mac의 Claude Code에서 NAS로 배포를 반복할 수 있는지 확인한다. 예를 들어 SSH와 `docker compose`로 하는 스크립트 `tools/deploy_nas.sh` 등. 되면 한 줄 명령으로 만들어 문서화하고, 안 되면 막힌 이유를 적는다.
- 운영 설정: `/docs`(API 문서)는 운영에서 끄거나 막는다. `/`가 404이므로 `/m/`로 넘긴다(#15 댓글).
- 앱 연결: 에뮬레이터 앱이 NAS 서버를 쓰게 하는 방법(빌드 설정 값)을 정하고, 로컬 서버로 되돌리는 방법도 문서화한다(#15 체크리스트 "PT 현장 대비").
- 외부 공개(DDNS, 리버스 프록시, Let's Encrypt)는 #15 체크리스트에 있다. 진행 여부를 사용자에게 먼저 묻는다.

## 2. 주의
- NAS 주소, SSH 계정·포트, Docker 사용 가능 여부는 레포에 없다. 사용자에게 **한 번에 하나씩** 묻는다. 지금 Mac의 `~/.ssh/config`에는 NAS 항목이 없다.
- 비밀번호·키·토큰은 채팅으로 받지 않는다. SSH 키 등록은 사용자가 직접 하도록 안내하고, 서버 비밀값은 NAS 쪽 `.env`에 사용자가 넣게 한다. 값을 출력하지 않는다.
- NAS의 다른 서비스·설정은 건드리지 않는다. 지우거나 덮어쓰기 전에는 사용자에게 확인한다.
- 로컬 개발 서버(main 체크아웃, :8000)와 에뮬레이터 연결은 그대로 둔다(다른 세션이 쓰는 중).

## 3. 진행 로그
| 날짜 | 한 일 | 다음 단계 |
|---|---|---|
| 2026-11-12 | 지시 파일 작성(통제 세션) | 세션 시작 → NAS 접속 정보 확인 |
| 2026-11-12 | NAS `192.168.219.108`, SSH 9022, 배포 전용 계정 `pleosdeploy`(administrators, docker만 sudo 비밀번호 없이) + Mac 키 `~/.ssh/pleos_nas_ed25519` 등록(사용자). NAS `~/pleos/.env`는 사용자가 Mac `.env` 복사 + `POSTGRES_PASSWORD`·`PUBLIC_BASE_URL` 추가 | — |
| 2026-11-12 | compose에 postgres:17(`pleos_pgdata` 볼륨) 추가, 컨테이너 시작 시 `alembic upgrade head`. `EXPOSE_DOCS`(기본 false)로 `/docs`·`/redoc`·`/openapi.json` 끔, `/` → `/m/` 307. `tools/deploy_nas.sh`(git archive HEAD → NAS → compose up --build → /health, `--seed-parking`, `--status`). 문서 `docs/deploy-nas.md`. 앱 NAS IP 평문 허용 | — |
| 2026-11-12 | Claude Code에서 `tools/deploy_nas.sh --seed-parking` 실행 → 배포 성공, 주차장 17,147행 적재, `/health` 200, `/` 307→`/m/`, `/docs`·`/openapi.json` 404, alembic head `a6c2e9f41d58`. 두 번째 배포(재실행)도 8초에 성공. 에뮬레이터에서 NAS `/health` 응답 확인 | PR #255 병합 → 외부 공개 진행(사용자 결정) |

| 2026-11-12 | 외부 공개: 기존 DDNS `kwonstar.synology.me`·443 포워딩·와일드카드 인증서 재사용, 사용자가 DSM 역방향 프록시 `pleos.kwonstar.synology.me:443 → 127.0.0.1:8000` 추가. NAS `.env`의 `PUBLIC_BASE_URL`을 HTTPS로, `SECURE_COOKIES=true`. compose `FORWARDED_ALLOW_IPS`. 배포 스크립트가 외부 `/health`도 확인. 외부 주소 `/health` 200·`/` 307·`/docs` 404, 차 QR `login_url`이 HTTPS로 나옴. server/ 변경 재배포로 api 컨테이너 교체도 확인 | — |

## 4. 확인하지 못한 것
- NAS 서버로 빌드한 앱(`-PserverUrl=http://192.168.219.108:8000/`)을 에뮬레이터에 설치해 화면까지 써 보지는 않았다(다른 세션이 에뮬레이터 사용 중). 에뮬레이터→NAS HTTP 응답만 확인.
- 로컬 DB의 회원·가족 데이터는 NAS로 옮기지 않았다(사용자에게 아직 묻지 않음).
- NAS 재부팅 뒤 자동 기동(`restart: unless-stopped`)과 sudoers 유지는 확인하지 못했다.
- 외부 HTTPS 확인은 집 Mac에서 공인 주소로 돌아 들어오는 경로(공유기 헤어핀)로만 했다. 진짜 외부망(LTE 폰, 다른 Wi-Fi)에서는 확인하지 못했다.
- HTTPS 빌드 앱(`-PserverUrl=https://pleos.kwonstar.synology.me/`)을 에뮬레이터에서 써 보지 않았다.
- `SECURE_COOKIES=true`라 집 안 `http://192.168.219.108:8000`으로는 폰 웹 로그인이 안 된다(의도). 앱 API(토큰)는 영향 없음.
