#!/usr/bin/env bash
# Rehearse production images without cluster/database credentials or real tokens.
set -euo pipefail

repo_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
output_dir=${1:-$(mktemp -d "${TMPDIR:-/tmp}/taps-container-smoke.XXXXXX")}
mkdir -p "$output_dir"
output_dir=$(cd "$output_dir" && pwd)
case "$output_dir/" in "$repo_dir/"*) echo 'Keep rehearsal output outside the repository.' >&2; exit 1;; esac
run_id="taps-smoke-$(date +%s)-$$"
runtime_uid=1000740000
backend_image="$run_id/backend:local"
frontend_image="$run_id/frontend:local"
backend="$run_id-backend"
frontend="$run_id-frontend"
volume="$run_id-static"
issuer=https://sso.example.invalid/auth/realms/synthetic
client=taps-container-smoke
created_containers=()
created_network=false
created_volume=false

cleanup() {
  local status=$?
  trap - EXIT
  for container in ${created_containers[@]+"${created_containers[@]}"}; do
    docker logs "$container" > "$output_dir/$container.log" 2>&1 || true
    docker rm -f "$container" >/dev/null 2>&1 || true
  done
  if "$created_volume"; then docker volume rm "$volume" >/dev/null 2>&1 || true; fi
  if "$created_network"; then docker network rm "$run_id" >/dev/null 2>&1 || true; fi
  docker image rm "$backend_image" "$frontend_image" >/dev/null 2>&1 || true
  printf 'Rehearsal output: %s\n' "$output_dir"
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

docker info >/dev/null
printf 'Building production images; output: %s\n' "$output_dir"
docker build --tag "$backend_image" "$repo_dir/backend" > "$output_dir/backend-build.log" 2>&1
docker build --tag "$frontend_image" "$repo_dir/frontend" > "$output_dir/frontend-build.log" 2>&1
docker network create "$run_id" >/dev/null
created_network=true
docker volume create "$volume" >/dev/null
created_volume=true

# Docker has no fsGroup, so make the empty test volume writable first; everything
# else runs as an arbitrary UID like OpenShift.
docker run --rm --user 0:0 --read-only --cap-drop ALL --security-opt no-new-privileges \
  --mount "type=volume,src=$volume,dst=/srv-rw,volume-nocopy" \
  --entrypoint sh "$frontend_image" -c 'chmod 0770 /srv-rw'
docker run --rm --user "$runtime_uid:0" --read-only --cap-drop ALL --security-opt no-new-privileges \
  --mount "type=volume,src=$volume,dst=/srv-rw,volume-nocopy" \
  --entrypoint sh "$frontend_image" -c 'cp -r /srv/. /srv-rw/'

created_containers+=("$backend")
docker run -d --name "$backend" --network "$run_id" --network-alias backend \
  --user "$runtime_uid:0" --read-only --cap-drop ALL --security-opt no-new-privileges \
  --memory 800m --cpus 1 --tmpfs /tmp:rw,nosuid,nodev,mode=1777,size=128m \
  -p 127.0.0.1::8080 -e TZ=America/Vancouver -e SPRING_PROFILES_ACTIVE= -e APP_LOG_LEVEL=INFO \
  -e "TAPS_IMAGE_REFERENCE=$backend_image" \
  -e "TAPS_OIDC_ISSUER_URI=$issuer" -e "TAPS_OIDC_CLIENT_ID=$client" \
  "$backend_image" >/dev/null
created_containers+=("$frontend")
docker run -d --name "$frontend" --network "$run_id" \
  --user "$runtime_uid:0" --read-only --cap-drop ALL --security-opt no-new-privileges \
  --memory 128m --cpus 1 \
  --mount "type=volume,src=$volume,dst=/srv,volume-nocopy" \
  --tmpfs "/tmp/caddy:rw,nosuid,nodev,mode=0770,uid=$runtime_uid,gid=0,size=32m" \
  --tmpfs "/tmp/coraza:rw,nosuid,nodev,mode=0770,uid=$runtime_uid,gid=0,size=32m" \
  -p 127.0.0.1::3000 -e BACKEND_URL=http://backend:8080 -e LOG_LEVEL=INFO \
  -e "TAPS_IMAGE_REFERENCE=$frontend_image" \
  -e XDG_CONFIG_HOME=/tmp/caddy -e XDG_DATA_HOME=/tmp/caddy -e TMPDIR=/tmp/caddy \
  -e "VITE_OIDC_ISSUER_URI=$issuer" -e "VITE_OIDC_CLIENT_ID=$client" \
  -e VITE_OIDC_IDIR_HINT=azureidir -e VITE_OIDC_BCEID_HINT=bceidbusiness \
  -e VITE_OIDC_SITEMINDER_LOGOUT_URL=https://sso.example.invalid/logoff \
  "$frontend_image" >/dev/null

backend_url="http://$(docker port "$backend" 8080/tcp)"
frontend_url="http://$(docker port "$frontend" 3000/tcp)"
wait_for() {
  local url=$1 expected=$2 status
  for ((attempt=1; attempt<=90; attempt++)); do
    status=$(curl -sS --max-time 2 -o /dev/null -w '%{http_code}' "$url" 2>/dev/null || true)
    if [[ "$status" == "$expected" ]]; then return; fi
    sleep 2
  done
  printf 'Startup failed for %s (last status %s).\n' "$url" "$status" >&2
  return 1
}
check_http() {
  local name=$1 url=$2 expected=$3 cache=$4 from_backend=${5:-} status
  status=$(curl -sS --max-time 10 -D "$output_dir/$name.headers" -o "$output_dir/$name.body" -w '%{http_code}' "$url")
  [[ "$status" == "$expected" ]] || { printf '%s: expected %s, got %s\n' "$name" "$expected" "$status" >&2; return 1; }
  if [[ -n "$cache" ]]; then grep -qi "^cache-control:.*$cache" "$output_dir/$name.headers"; fi
  if [[ "$url" == "$frontend_url/"* ]]; then
    [[ "$(header_value X-TAPS-Image "$output_dir/$name.headers")" == "$frontend_image" ]] || fail 'frontend image header'
  fi
  if [[ -n "$from_backend" ]]; then
    [[ "$(header_value X-TAPS-Backend-Image "$output_dir/$name.headers")" == "$backend_image" ]] || fail 'backend image header'
  fi
}
# macOS /bin/bash 3.2 ignores set -e when a bare [[ ]] fails, so every check fails explicitly.
fail() { printf 'Check failed: %s\n' "$1" >&2; exit 1; }
header_value() {
  awk -v name="$1:" 'tolower($1) == tolower(name) {sub(/\r$/, ""); print $2}' "$2"
}

wait_for "$backend_url/actuator/health/readiness" 200
wait_for "$frontend_url/" 200
check_http backend-liveness "$backend_url/actuator/health/liveness" 200 '' backend
check_http backend-readiness "$backend_url/actuator/health/readiness" 200 '' backend
check_http shell "$frontend_url/" 200 no-store
check_http spa-route "$frontend_url/ecas/ECAS05" 200 no-store
check_http runtime-config "$frontend_url/config.js" 200 no-store
grep -Fq "$issuer" "$output_dir/runtime-config.body"
grep -Fq "$client" "$output_dir/runtime-config.body"
grep -Fq 'VITE_OIDC_BCEID_HINT: "bceidbusiness"' "$output_dir/runtime-config.body"
check_http anonymous-api "$frontend_url/api/me" 401 no-store backend
check_http anonymous-read-api "$frontend_url/api/gas/worksheets" 401 no-store backend
check_http missing-asset "$frontend_url/assets/does-not-exist.js" 404 no-store
asset=$(python3 - "$output_dir/shell.body" <<'PY'
import re, sys
with open(sys.argv[1]) as source:
    match = re.search(r'(?:src|href)="(/assets/[^\"]+\.js)"', source.read())
if not match:
    raise SystemExit('No fingerprinted JS asset found in production shell.')
print(match.group(1))
PY
)
check_http fingerprinted-asset "$frontend_url$asset" 200 immutable
grep -qi '^x-content-type-options: nosniff' "$output_dir/shell.headers"
grep -qi '^x-content-type-options: nosniff' "$output_dir/anonymous-api.headers"
docker exec "$frontend" wget -q -O /dev/null http://127.0.0.1:3001/health
for container in "$backend" "$frontend"; do
  [[ "$(docker exec "$container" id -u)" == "$runtime_uid" ]] || fail 'container UID'
  [[ "$(docker exec "$container" id -g)" == 0 ]] || fail 'container GID 0'
  [[ "$(docker inspect --format '{{.HostConfig.ReadonlyRootfs}}' "$container")" == true ]] || fail 'read-only root filesystem'
  docker exec "$container" sh -c 'test ! -w /etc'
done

start_seconds=$SECONDS
docker stop --time 75 "$backend" >/dev/null
[[ $((SECONDS - start_seconds)) -lt 75 ]] || fail 'backend shutdown deadline'
[[ "$(docker inspect --format '{{.State.OOMKilled}}' "$backend")" == false ]] || fail 'backend not OOM-killed'
backend_exit=$(docker inspect --format '{{.State.ExitCode}}' "$backend")
[[ "$backend_exit" == 0 || "$backend_exit" == 143 ]] || fail 'backend exit status'
docker logs "$backend" > "$output_dir/backend-shutdown.log" 2>&1
grep -q 'Graceful shutdown complete' "$output_dir/backend-shutdown.log"
check_http unavailable-backend "$frontend_url/api/me" 502 no-store
docker stop --time 15 "$frontend" >/dev/null
[[ "$(docker inspect --format '{{.State.ExitCode}}' "$frontend")" == 0 ]] || fail 'frontend exit status'
printf 'PASS: production builds, arbitrary UID/GID 0, read-only root, probes, runtime config, SPA/API routing, image pair/cache headers, graceful shutdown.\n' | tee "$output_dir/result.txt"
