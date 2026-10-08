#!/usr/bin/env bash
# Local full-stack rehearsal with synthetic data. Don't point it at a shared database or issuer.
set -euo pipefail

repo_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
action=${1:-}
state_dir=${2:-}
usage() { echo "Usage: $0 start [new-output-directory] | status|pause-db|resume-db|stop <output-directory>" >&2; exit 2; }
[[ -n "$action" ]] || usage
if [[ "$action" == start ]]; then
  state_dir=${state_dir:-$(mktemp -d "${TMPDIR:-/tmp}/taps-local-read.XXXXXX")}
  mkdir -p "$state_dir"
else
  [[ -n "$state_dir" && -f "$state_dir/state.json" ]] || usage
fi
state_dir=$(cd "$state_dir" && pwd)
case "$state_dir/" in "$repo_dir/"*) echo 'Keep generated rehearsal files outside Git.' >&2; exit 1;; esac

# Pass the login on stdin to keep it out of process listings.
fixture_sqlplus() {
  { printf 'WHENEVER SQLERROR EXIT FAILURE\nCONNECT taps_fixture/"%s"@//127.0.0.1:1521/FREEPDB1\n' "$(cat "$state_dir/fixture-password")"
    cat "$2"; } | docker exec -i "$1" sqlplus -s -L /nolog
}
state_value() { python3 - "$state_dir/state.json" "$1" <<'PY'
import json, sys
with open(sys.argv[1]) as source:
    print(json.load(source)[sys.argv[2]])
PY
}
if [[ "$action" != start ]]; then
  run_id=$(state_value runId)
  [[ "$run_id" == taps-local-read-* ]] || { echo 'Not a local rehearsal state file.' >&2; exit 1; }
  case "$action" in
    status) docker ps -a --filter "label=taps.local-read=$run_id" --format '{{.Names}}: {{.Status}}'; exit 0;;
    pause-db) docker pause "$run_id-oracle" >/dev/null; echo 'Disposable Oracle paused.'; exit 0;;
    resume-db) docker unpause "$run_id-oracle" >/dev/null; echo 'Disposable Oracle resumed; retry the read.'; exit 0;;
    stop)
      for component in frontend backend issuer oracle; do
        docker logs "$run_id-$component" > "$state_dir/$component.log" 2>&1 || true
        docker rm -f "$run_id-$component" >/dev/null 2>&1 || true
      done
      docker volume rm "$run_id-static" >/dev/null 2>&1 || true
      docker network rm "$run_id" >/dev/null 2>&1 || true
      echo 'Disposable stack stopped. Logs and generated synthetic configuration remain in the private output directory.'
      exit 0;;
    *) usage;;
  esac
fi

[[ ! -e "$state_dir/state.json" ]] || { echo 'Choose a new output directory for start.' >&2; exit 1; }
umask 077
run_id="taps-local-read-$(date +%s)-$$"
backend_image=taps-local-read/backend:local
frontend_image=taps-local-read/frontend:local
issuer=http://taps.localhost:13000/oidc
runtime_uid=1000740000
created=()
network_created=false
volume_created=false
completed=false
cleanup_failure() {
  if "$completed"; then return; fi
  for container in ${created[@]+"${created[@]}"}; do
    docker logs "$container" > "$state_dir/${container##*-}.log" 2>&1 || true
    docker rm -f "$container" >/dev/null 2>&1 || true
  done
  if "$volume_created"; then docker volume rm "$run_id-static" >/dev/null 2>&1 || true; fi
  if "$network_created"; then docker network rm "$run_id" >/dev/null 2>&1 || true; fi
}
trap cleanup_failure EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

docker info >/dev/null
python3 - "$state_dir" "$repo_dir" "$issuer" <<'PY'
from pathlib import Path
import secrets, sys
output, repo, issuer = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3]
password = 'TapsTest' + secrets.token_hex(10)
(output / 'oracle.env').write_text('ORACLE_PASSWORD=TapsSystem' + secrets.token_hex(12) + '\nAPP_USER=taps_fixture\nAPP_USER_PASSWORD=' + password + '\n')
(output / 'fixture-password').write_text(password)
# Plain TCP to the disposable database. The TCPS host, service and truststore values are
# unused, but the oracle profile's placeholders must resolve.
(output / 'backend.env').write_text('\n'.join([
 'SPRING_PROFILES_ACTIVE=oracle', 'SPRING_DATASOURCE_URL=jdbc:oracle:thin:@oracle:1521/FREEPDB1',
 'DATABASE_USER=taps_fixture', 'DATABASE_PASSWORD=' + password,
 'DATABASE_HOST=unused.invalid', 'DATABASE_SERVICE_NAME=unused', 'KEYSTORE_SECRET=unused',
 'SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE=2', 'SPRING_DATASOURCE_HIKARI_MINIMUMIDLE=0',
 'SPRING_DATASOURCE_HIKARI_CONNECTIONTIMEOUT=1000',
 'DATABASE_CONNECT_TIMEOUT_MS=1000', 'DATABASE_READ_TIMEOUT_MS=3000',
 'TAPS_OIDC_ISSUER_URI=' + issuer, 'TAPS_OIDC_CLIENT_ID=taps-local-read',
]) + '\n')
caddy = (repo / 'frontend/Caddyfile').read_text()
assert caddy.count(':3000 {') == 1
caddy = caddy.replace(':3000 {', ':13000 {', 1)
caddy = caddy.replace('not path /api* /assets/*', 'not path /api* /oidc* /assets/*', 1)
caddy = caddy.replace('\treverse_proxy /api*', '\treverse_proxy /oidc* http://issuer:18281\n\treverse_proxy /api*', 1)
(output / 'Caddyfile').write_text(caddy)
(output / 'Caddyfile').chmod(0o644)
fixtures = repo / 'backend/src/test/resources/oracle'
(output / 'fixture.sql').write_text('WHENEVER SQLERROR EXIT SQL.SQLCODE\nSET DEFINE OFF\n' +
 '\n'.join((fixtures / name).read_text() for name in ['schema.sql', 'attachments-schema.sql', 'audit-schema.sql']) + '\n' + (fixtures / 'client-name-function.sql').read_text() +
 '\n/\n' + '\n'.join((fixtures / name).read_text() for name in ['seed.sql', 'attachments-seed.sql', 'audit-seed.sql']) + '\nCOMMIT;\nEXIT;\n')
PY

if [[ "${LOCAL_READ_SKIP_BUILD:-false}" != true ]]; then
  echo "Building the production Dockerfiles; logs: $state_dir"
  docker build --tag "$backend_image" "$repo_dir/backend" > "$state_dir/backend-build.log" 2>&1
  docker build --tag "$frontend_image" "$repo_dir/frontend" > "$state_dir/frontend-build.log" 2>&1
fi
docker network create --label "taps.local-read=$run_id" "$run_id" >/dev/null
network_created=true
docker volume create --label "taps.local-read=$run_id" "$run_id-static" >/dev/null
volume_created=true
created+=("$run_id-oracle")
docker run -d --name "$run_id-oracle" --label "taps.local-read=$run_id" \
  --network "$run_id" --network-alias oracle --env-file "$state_dir/oracle.env" \
  --shm-size 1g gvenzl/oracle-free:23.26.3-slim-faststart > "$state_dir/oracle-container-id"
for ((attempt=1; attempt<=120; attempt++)); do
  if docker exec "$run_id-oracle" healthcheck.sh >/dev/null 2>&1; then break; fi
  if [[ "$attempt" == 120 ]]; then echo 'Disposable Oracle startup failed.' >&2; exit 1; fi
  sleep 2
done
fixture_sqlplus "$run_id-oracle" "$state_dir/fixture.sql" > "$state_dir/fixture-load.log" 2>&1

created+=("$run_id-issuer")
docker run -d --name "$run_id-issuer" --label "taps.local-read=$run_id" \
  --network "$run_id" --network-alias issuer --user "$runtime_uid:0" --read-only \
  --cap-drop ALL --security-opt no-new-privileges --memory 128m \
  --mount "type=bind,src=$repo_dir/scripts/local-read-issuer.mjs,dst=/issuer.mjs,readonly" \
  -e "LOCAL_READ_ISSUER=$issuer" node:24-slim node /issuer.mjs >/dev/null
created+=("$run_id-backend")
docker run -d --name "$run_id-backend" --label "taps.local-read=$run_id" \
  --network "$run_id" --network-alias backend --user "$runtime_uid:0" --read-only \
  --cap-drop ALL --security-opt no-new-privileges --memory 768m \
  --tmpfs /tmp:rw,nosuid,nodev,mode=1777,size=128m \
  --env-file "$state_dir/backend.env" -p 127.0.0.1::8080 "$backend_image" >/dev/null

# As in the OpenShift seed container, copy immutable image assets into the one writable static volume.
docker run --rm --user 0:0 --read-only --cap-drop ALL --security-opt no-new-privileges \
  --mount "type=volume,src=$run_id-static,dst=/srv-rw,volume-nocopy" \
  --entrypoint sh "$frontend_image" -c 'chmod 0770 /srv-rw'
docker run --rm --user "$runtime_uid:0" --read-only --cap-drop ALL --security-opt no-new-privileges \
  --mount "type=volume,src=$run_id-static,dst=/srv-rw,volume-nocopy" \
  --entrypoint sh "$frontend_image" -c 'cp -r /srv/. /srv-rw/'
created+=("$run_id-frontend")
docker run -d --name "$run_id-frontend" --label "taps.local-read=$run_id" \
  --network "$run_id" --network-alias taps.localhost --user "$runtime_uid:0" --read-only \
  --cap-drop ALL --security-opt no-new-privileges --memory 256m \
  --mount "type=volume,src=$run_id-static,dst=/srv,volume-nocopy" \
  --mount "type=bind,src=$state_dir/Caddyfile,dst=/etc/caddy/Caddyfile,readonly" \
  --tmpfs "/tmp/caddy:rw,nosuid,nodev,mode=0770,uid=$runtime_uid,gid=0,size=32m" \
  --tmpfs "/tmp/coraza:rw,nosuid,nodev,mode=0770,uid=$runtime_uid,gid=0,size=32m" \
  -p 127.0.0.1:13000:13000 -e BACKEND_URL=http://backend:8080 -e LOG_LEVEL=INFO \
  -e XDG_CONFIG_HOME=/tmp/caddy -e XDG_DATA_HOME=/tmp/caddy -e TMPDIR=/tmp/caddy \
  -e "VITE_OIDC_ISSUER_URI=$issuer" -e VITE_OIDC_CLIENT_ID=taps-local-read \
  -e VITE_OIDC_SITEMINDER_LOGOUT_URL= "$frontend_image" >/dev/null
backend_url="http://$(docker port "$run_id-backend" 8080/tcp)"
for ((attempt=1; attempt<=90; attempt++)); do
  if curl --noproxy '*' -fsS --max-time 2 "$backend_url/actuator/health/readiness" >/dev/null 2>&1 && \
      curl --noproxy '*' --resolve taps.localhost:13000:127.0.0.1 -fsS --max-time 2 \
      "$issuer/.well-known/openid-configuration" >/dev/null 2>&1; then break; fi
  if [[ "$attempt" == 90 ]]; then echo 'Application or issuer startup failed.' >&2; exit 1; fi
  sleep 2
done
python3 - "$state_dir/state.json" "$run_id" "$backend_url" <<'PY'
import json, sys
from pathlib import Path
Path(sys.argv[1]).write_text(json.dumps({'runId': sys.argv[2], 'frontendUrl': 'http://taps.localhost:13000', 'backendUrl': sys.argv[3]}, indent=2)+'\n')
PY
completed=true
printf 'Local stack ready: http://taps.localhost:13000\nPrivate output: %s\nStop: bash scripts/local-read-stack.sh stop %q\n' "$state_dir" "$state_dir"
