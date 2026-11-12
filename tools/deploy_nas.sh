#!/usr/bin/env bash
# NAS(Synology SA6400)에 서버를 배포한다. 이 Mac(또는 Claude Code)에서 한 줄로:
#   tools/deploy_nas.sh            # 커밋된 HEAD의 server/를 올리고 다시 빌드·재시작, /health 확인
#   tools/deploy_nas.sh --seed-parking   # 위 + 주차장 실제 데이터(seed) 적재 (처음 한 번)
#   tools/deploy_nas.sh --status   # 컨테이너 상태와 최근 로그만 본다
# 접속 정보는 환경 변수로 바꿀 수 있다. 비밀값은 NAS의 $NAS_DIR/.env에만 있고 이 스크립트는 읽지 않는다.
set -euo pipefail

NAS_HOST="${NAS_HOST:-192.168.219.108}"
NAS_PORT="${NAS_PORT:-9022}"
NAS_USER="${NAS_USER:-pleosdeploy}"
NAS_KEY="${NAS_KEY:-$HOME/.ssh/pleos_nas_ed25519}"
NAS_DIR="${NAS_DIR:-pleos}"          # NAS 홈 기준 경로 (~/pleos)
API_PORT="${API_PORT:-8000}"
DOCKER="sudo /usr/local/bin/docker"
COMPOSE="$DOCKER compose --env-file ../.env -p pleos"

ssh_nas() { ssh -p "$NAS_PORT" -i "$NAS_KEY" -o BatchMode=yes -o ConnectTimeout=8 "$NAS_USER@$NAS_HOST" "$@"; }

MODE="${1:-deploy}"
cd "$(git rev-parse --show-toplevel)"

if [[ "$MODE" == "--status" ]]; then
  ssh_nas "cd $NAS_DIR/server && $COMPOSE ps && $COMPOSE logs --tail 30 api"
  exit 0
fi

echo "▶ NAS 접속·준비 확인 ($NAS_USER@$NAS_HOST:$NAS_PORT)"
ssh_nas "test -f $NAS_DIR/.env" || {
  echo "✖ NAS에 ~/$NAS_DIR/.env가 없습니다. docs/deploy-nas.md의 '처음 한 번' 절을 먼저 해 주세요." >&2
  exit 1
}

if [[ -n "$(git status --porcelain -- server)" ]]; then
  echo "⚠ server/에 커밋하지 않은 변경이 있습니다. 배포는 커밋된 HEAD 기준입니다." >&2
fi
REV="$(git rev-parse --short HEAD)"

echo "▶ server/ 올리기 (HEAD $REV)"
git archive --format=tar HEAD server | ssh_nas "set -e; cd $NAS_DIR; rm -rf .incoming; mkdir .incoming; tar -x -C .incoming; rm -rf server.prev; if [ -d server ]; then mv server server.prev; fi; mv .incoming/server server; rmdir .incoming; echo $REV > server/REVISION"

echo "▶ 빌드·재시작 (DB 마이그레이션은 api 컨테이너가 시작할 때 alembic upgrade head로 적용)"
ssh_nas "cd $NAS_DIR/server && $COMPOSE up -d --build --remove-orphans && $DOCKER image prune -f >/dev/null"

if [[ "$MODE" == "--seed-parking" ]]; then
  echo "▶ 주차장 데이터 적재 (같은 행은 덮어써서 여러 번 해도 안전)"
  ssh_nas "cd $NAS_DIR/server && $COMPOSE exec -T api sh -c 'mkdir -p data/parking && gunzip -c seed/parking_lots.csv.gz > data/parking/parking_lots.csv && python scripts/load_parking.py data/parking/parking_lots.csv'"
fi

echo "▶ /health 확인 (최대 90초)"
for _ in $(seq 1 45); do
  if curl -fsS -m 3 "http://$NAS_HOST:$API_PORT/health" >/dev/null 2>&1; then
    echo "✔ 배포 완료: http://$NAS_HOST:$API_PORT (HEAD $REV)"
    exit 0
  fi
  sleep 2
done
echo "✖ /health가 응답하지 않습니다. 최근 로그:" >&2
ssh_nas "cd $NAS_DIR/server && $COMPOSE logs --tail 40 api" >&2 || true
exit 1
