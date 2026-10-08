# TAPS local validation scripts

Run these optional checks locally from the repository root. They use synthetic settings and
disposable containers, not the DEV, TEST or PROD databases. Those are OpenShift environments,
as defined in the [environment map](../README.md#environments). The [backend](../backend/README.md#testing)
and [frontend](../frontend/README.md#testing) READMEs own the component test commands.

## Local image checks

Builds the deployment Dockerfiles and runs the backend and Caddy/Coraza frontend locally. Needs Docker, Bash, curl and Python 3. No namespace, Oracle account, SSO client or token is needed.

From the repository root:

```sh
bash scripts/container-smoke.sh
# Or pick an output directory outside the repository:
bash scripts/container-smoke.sh "${TMPDIR:-/tmp}/taps-container-smoke"
```

The script prints its output directory (logs, HTTP responses, result). Each run uses its own image tags, network, containers and volume, and removes only those on exit. If Docker stops mid-run, remove the leftover `taps-smoke-<timestamp>-<pid>` resources by hand.

### What it checks

- Both images build. The backend build fails if the runtime JDK lacks current B.C. time zone rules.
- Both servers and the frontend seed step run as UID `1000740000`, GID `0`, with capabilities dropped and no privilege escalation.
- Read-only root filesystems. The backend gets a writable `/tmp`; the frontend gets `/srv`, `/tmp/caddy` and `/tmp/coraza`, as in its OpenShift template.
- Synthetic OIDC settings reach `config.js`. The issuer is on the reserved `.invalid` domain, so no real provider is called.
- Health endpoints respond, SPA deep links load, and anonymous `/api/me` and `/api/gas/worksheets` return `401`.
- The shell, runtime config, API responses and errors aren't cached. Fingerprinted assets are cached as immutable. Security headers are present.
- Responses carry `X-TAPS-Image` (frontend) and `X-TAPS-Backend-Image` (backend). The deploy smoke test compares these with the expected tag: head SHA in DEV, PR number in TEST.
- On SIGTERM the backend shuts down in time, and Caddy returns an uncached `502` and exits cleanly.

A small root helper makes the test volume group-writable, like Kubernetes `fsGroup`. It doesn't run either app.

The setup covers arbitrary-UID Caddy/Coraza, a separate health port, selective caching, no backend keepalive and Spring graceful shutdown.

Oracle stays off here (no `oracle` profile). Real logins, database grants, SCC, network policies, TLS and rolling updates need DEV/TEST.

## Authenticated read rehearsal

Runs the deployment frontend/backend images locally with a disposable Oracle Free database and a
local test OIDC issuer. The backend uses the real Spring Security JWT decoder and the `oracle`
profile, with its connection set to the disposable container. This does not deploy or connect to
OpenShift DEV, TEST or PROD. Needs Docker, Bash, Python 3 and enough memory for Oracle plus the app.

The [SQL fixture README](../backend/src/test/resources/oracle/README.md#where-they-run) explains why the loader cannot target a live database. The issuer and generated keys/passwords are also for local testing only.

### Start and stop

From the repository root:

```sh
bash scripts/local-read-stack.sh start
```

This builds both images and prints an output directory, the browser URL and the stop command. The other commands take that directory. Logs and generated fixture files go there, outside the repository.

Open `http://taps.localhost:13000`. Only two ports are published, both on `127.0.0.1`: the frontend on 13000 and a random backend port recorded in `state.json`.

The stack uses a copy of the production Caddy config with an extra `/oidc` route to the issuer, so issuer calls stay on the frontend origin and pass the existing CSP. The production Caddyfile, CSP, auth code and role mapping are unchanged.

Set `LOCAL_READ_SKIP_BUILD=true` to reuse images built from the same source. Run one stack at a time.

```sh
bash scripts/local-read-stack.sh status /path/printed/by/start
bash scripts/local-read-stack.sh stop /path/printed/by/start
```

`stop` removes the containers, network and volume. The build cache and output directory stay.

### Browser checks

Click the normal sign-in button, pick a synthetic user, and select **Sign in to local TAPS**.

| Persona | Expected behavior |
| --- | --- |
| Provincial administrator | ECAS and GAS reads across all fixture regions. |
| Cariboo appraiser | Cariboo records, including ECAS `1001`, worksheet `101` and licence `A00001`. |
| Omineca appraiser | Omineca records; opening Cariboo worksheet `101` returns 404. |
| Licensee viewer | ECAS client-scoped access; no GAS worksheets. |
| Signed in without a TAPS role | Access-request view only. |
| Expired access token | Backend rejects it and the frontend signs the user out straight away. |

Suggested checks:

1. As the Cariboo appraiser, search ECAS, open reference `1001` and follow it to the GAS worksheet. Check marks, dates and stored amounts.
2. Search GAS for `A00001`, pick a timber mark, and check FTA info and stored rates. Repeat at mobile width, including drawer Tab/Escape and focus return.
3. Sign out and in as the Omineca appraiser and the licensee viewer. Filters must never widen access.
4. Try the no-role and expired-token users. Errors must not show records, SQL or credentials, and the user must be able to sign in again.

The issuer doesn't model real FAM provisioning, IDIR MFA or Business BCeID/SiteMinder.

### Database outage

While signed in:

```sh
bash scripts/local-read-stack.sh pause-db /path/printed/by/start
```

Run a new search. Expect a quick `READ_UNAVAILABLE` / 503 and a retry option. Liveness and readiness stay healthy; `/actuator/health` shows the database down. Then resume and retry:

```sh
bash scripts/local-read-stack.sh resume-db /path/printed/by/start
```

The app should recover without a restart.

## Rehearsal issuer tests

```sh
node --test scripts/local-read-issuer.test.mjs
```
