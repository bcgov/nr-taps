# Local production-container rehearsal

Builds the production Dockerfiles and runs the backend and Caddy/Coraza frontend together. Needs Docker, Bash, curl and Python 3. No namespace, Oracle account, SSO client or token is needed.

From the repository root:

```sh
bash scripts/container-smoke.sh
# Or pick an output directory outside the repository:
bash scripts/container-smoke.sh "${TMPDIR:-/tmp}/taps-container-smoke"
```

The script prints its output directory (logs, HTTP responses, result). Each run uses its own image tags, network, containers and volume, and removes only those on exit. If Docker stops mid-run, remove the leftover `taps-smoke-<timestamp>-<pid>` resources by hand.

## What it checks

- Both images build.
- Both servers and the frontend seed step run as UID `1000740000`, GID `0`, with capabilities dropped and no privilege escalation.
- Read-only root filesystems. The backend gets a writable `/tmp`; the frontend gets `/srv`, `/tmp/caddy` and `/tmp/coraza`, as in its OpenShift template.
- Synthetic OIDC settings reach `config.js`. The issuer is on the reserved `.invalid` domain, so no real provider is called.
- Health endpoints respond, SPA deep links load, and anonymous `/api/me` and `/api/gas/worksheets` return `401`.
- The shell, runtime config, API responses and errors aren't cached. Fingerprinted assets are cached as immutable. Security headers are present.
- Responses carry `X-TAPS-Image` (frontend) and `X-TAPS-Backend-Image` (backend). The deploy smoke test compares these with the expected tag: head SHA in DEV, PR number in TEST.
- On SIGTERM the backend shuts down in time, and Caddy returns an uncached `502` and exits cleanly.

A small root helper makes the test volume group-writable, like Kubernetes `fsGroup`. It doesn't run either app.

The setup covers arbitrary-UID Caddy/Coraza, a separate health port, selective caching, no backend keepalive and Spring graceful shutdown.

Oracle stays off here. Real logins, database grants, SCC, network policies, TLS and rolling updates need DEV/TEST.
