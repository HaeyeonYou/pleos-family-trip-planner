# NAS 배포 (Synology SA6400, 로컬 네트워크)

서버(FastAPI)와 PostgreSQL 17을 NAS의 Container Manager(Docker)에 올린다. 이 Mac에서 한 줄로 다시 배포한다.

| 항목 | 값 |
|---|---|
| NAS 주소 | `192.168.219.108` (집 공유기 안) |
| SSH | 포트 `9022`, 배포 전용 계정 `pleosdeploy`(administrators 그룹), 키 `~/.ssh/pleos_nas_ed25519` |
| NAS 안 위치 | `~/pleos/.env`(비밀값, 직접 넣음) · `~/pleos/server`(배포 스크립트가 덮어씀) · `~/pleos/server.prev`(직전 버전) |
| 서버 주소 | `http://192.168.219.108:8000` (`/`는 `/m/`로 넘어감, `/docs`는 꺼 둠) |
| DB | compose의 `db` 컨테이너(postgres:17), 데이터는 Docker 볼륨 `pleos_pgdata` |

## 배포 (반복)
```bash
tools/deploy_nas.sh
```
- **커밋된 HEAD**의 `server/`를 `git archive`로 올린 뒤 `docker compose up -d --build`로 다시 빌드·재시작하고 `/health`를 기다린다.
- DB 마이그레이션은 api 컨테이너가 시작할 때 `alembic upgrade head`로 적용한다(이미 최신이면 그대로).
- `tools/deploy_nas.sh --status`: 컨테이너 상태와 최근 로그를 본다.
- `tools/deploy_nas.sh --seed-parking`: 배포한 뒤 주차장 실제 데이터(`server/seed/parking_lots.csv.gz`, 17,147행)를 넣는다. 같은 행은 덮어쓰므로 여러 번 해도 안전하다.
- 접속 정보는 환경 변수 `NAS_HOST`, `NAS_PORT`, `NAS_USER`, `NAS_KEY`, `NAS_DIR`, `API_PORT`로 바꿀 수 있다.
- Claude Code에서도 같은 명령을 그대로 실행하면 된다. SSH 키 로그인이고 docker만 sudo 비밀번호 없이 허용했으므로 중간에 입력을 묻지 않는다.

## 처음 한 번 (사용자가 직접)
1. DSM 제어판 → 터미널 및 SNMP → SSH 서비스 활성화(포트 9022).
2. 제어판 → 사용자 및 그룹 → 사용자 `pleosdeploy` 생성(그룹 `administrators`). 고급 탭 → '사용자 홈 서비스 활성화'.
3. Mac 터미널(비밀번호는 터미널에만 입력):
   ```bash
   ssh-copy-id -i ~/.ssh/pleos_nas_ed25519.pub -p 9022 pleosdeploy@192.168.219.108
   ssh -p 9022 pleosdeploy@192.168.219.108 'chmod 755 ~ && chmod 700 ~/.ssh && chmod 600 ~/.ssh/authorized_keys'
   ssh -t -p 9022 pleosdeploy@192.168.219.108 'echo "pleosdeploy ALL=(ALL) NOPASSWD: /usr/local/bin/docker" | sudo tee /etc/sudoers.d/pleosdeploy >/dev/null && sudo chmod 440 /etc/sudoers.d/pleosdeploy'
   ```
4. NAS에 `.env`를 둔다. Mac의 `.env`를 복사한 뒤 NAS 전용 값을 덧붙인다.
   ```bash
   ssh -p 9022 -i ~/.ssh/pleos_nas_ed25519 pleosdeploy@192.168.219.108 'mkdir -p ~/pleos && chmod 700 ~/pleos'
   scp -P 9022 -i ~/.ssh/pleos_nas_ed25519 .env pleosdeploy@192.168.219.108:pleos/.env
   ssh -p 9022 -i ~/.ssh/pleos_nas_ed25519 pleosdeploy@192.168.219.108 'chmod 600 ~/pleos/.env && nano ~/pleos/.env'
   ```
   덧붙일 값(`nano`에서 맨 아래에 추가):
   - `POSTGRES_PASSWORD=` 영문·숫자로만 된 새 비밀번호(DB 주소에 들어가므로 특수문자는 쓰지 않는다)
   - `PUBLIC_BASE_URL=http://192.168.219.108:8000` (차 화면 QR이 가리킬 주소)
   - Mac `.env`에 `DATABASE_URL`이 있어도 그대로 둔다. compose가 NAS DB 주소로 덮어쓴다.
5. `tools/deploy_nas.sh --seed-parking`으로 첫 배포.

## 운영 설정
- `EXPOSE_DOCS`(기본 `false`): `/docs`, `/redoc`, `/openapi.json`을 끈다. 잠깐 볼 때만 NAS `.env`에 `EXPOSE_DOCS=true`를 넣고 다시 배포한다.
- `/`는 어디서나 `/m/`(폰 웹)로 넘긴다.
- `PARSER`(기본 `auto`), `DEMO_MODE`(기본 `false`)는 NAS `.env`에서 바꾼다.

## 앱이 쓰는 서버 바꾸기 (NAS ↔ 로컬)
앱의 서버 주소는 빌드할 때 정한다(`android/app/build.gradle.kts`의 `serverUrl`, 기본 `http://10.0.2.2:8000/` = Mac 로컬 서버).

| 쓸 서버 | 빌드 명령 (`android/`에서) |
|---|---|
| Mac 로컬 서버 (기본) | `./gradlew installDebug` |
| NAS 서버 | `./gradlew installDebug -PserverUrl=http://192.168.219.108:8000/` |

- 에뮬레이터는 Mac의 네트워크를 그대로 쓰므로 Mac이 집 공유기에 붙어 있으면 NAS에 바로 닿는다. `adb reverse`는 필요 없다.
- NAS IP는 `android/app/src/main/res/xml/network_security_config.xml`에서 평문 HTTP를 허용해 두었다. IP가 바뀌면 여기와 위 명령의 주소를 함께 바꾼다.
- 로그인 토큰은 서버마다 따로다. 서버를 바꾸면 앱에서 다시 로그인한다. 로컬 DB의 회원·가족 데이터는 NAS로 옮기지 않았다.
- **PT 현장(12/3~4)**: 현장 네트워크에서는 집 NAS에 닿지 않는다. 외부 공개(DDNS·HTTPS)를 하지 않는 한 PT에서는 **Mac 로컬 서버(기본 빌드)**로 시연한다.

## 되돌리기
- 직전 버전으로: NAS에서 `cd ~/pleos && rm -rf server && mv server.prev server && cd server && sudo /usr/local/bin/docker compose --env-file ../.env -p pleos up -d --build`
- 서버만 멈추기: `ssh ... 'cd ~/pleos/server && sudo /usr/local/bin/docker compose --env-file ../.env -p pleos down'` (DB 볼륨은 남는다. `down -v`는 DB를 지우니 쓰지 않는다)
