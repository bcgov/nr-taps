# Local development and testing

## Run the application

Install Java 21, Maven and Node 24. Set `TAPS_OIDC_ISSUER_URI` and `TAPS_OIDC_CLIENT_ID` for the backend; it won't start without them. Copy [frontend/.env.example](../frontend/.env.example) to `frontend/.env` and set the matching values. Then run each in its own terminal from the repository root:

```sh
cd backend && mvn spring-boot:run
```

```sh
cd frontend && npm ci && npm run dev
```

The frontend runs on `http://localhost:3000` and proxies `/api` to `http://localhost:8080`. Change these with `VITE_DEV_HOST`, `VITE_DEV_PORT` and `VITE_DEV_BACKEND_TARGET`. To sign in, add `http://localhost:3000/authCallback` and its origin to a development FAM client.

Or run both in Docker with `docker compose up`: the backend through Maven on 8080 and the Vite dev server on 3000. Set the two OIDC values in the shell or a root `.env` first. `docker compose --profile caddy up` also serves the production frontend image on `http://localhost:3005`. Oracle stays off unless `SPRING_PROFILES_ACTIVE=oracle` and the database settings are set; [backend/.env.example](../backend/.env.example) lists them.

Read screens remain unavailable until Oracle is enabled; see [Oracle connection settings](deployment-configuration.md#oracle-connection). To run everything locally with a test issuer and disposable Oracle, use the [authenticated read rehearsal](#authenticated-read-rehearsal).

## Tests

From `backend` with Java 21:

```sh
mvn -B -DskipITs verify     # no database needed; JaCoCo report in target/site/jacoco
```

From `frontend`:

```sh
npm run typecheck
npm run lint
npm run format:check
npm run test:cov            # fails below 80% statements, 75% branches, 80% functions and lines
npm run build
```

`npm run test:security-config` checks the Caddy/Coraza config with synthetic requests. It needs a Coraza-enabled Caddy, such as the one `frontend/Dockerfile` builds; set `CADDY_BIN` if it isn't on the path.

The [UI guide](ui-foundation.md#development-preview) covers the synthetic browser preview and component review checklist.

### Oracle integration tests

From `backend` with Docker running:

```sh
mvn -B -Poracle-it verify
# Run only the HTTP/runtime failure cases:
mvn -B -Poracle-it -Dit.test=OracleReadResilienceIT verify
```

The Oracle tests start disposable Testcontainers databases and use synthetic fixtures. Normal `mvn test` does not start Oracle; selecting `oracle-it` fails if Docker or the image is unavailable. See the [fixture inventory and live-database isolation](../backend/src/test/resources/oracle/README.md) before running them.

[OracleReadIT](../backend/src/test/java/ca/bc/gov/nrs/taps/integration/OracleReadIT.java) exercises the read adapters, Oracle SQL, paging, dates, nulls, exact amounts, worksheet families and scope rules. Its HTTP scenario substitutes only token decoding. [OracleReadResilienceIT](../backend/src/test/java/ca/bc/gov/nrs/taps/integration/OracleReadResilienceIT.java) uses real HTTP, the production JWT decoder and synthetic signed tokens to check regional denials, invalid tokens, pool exhaustion, statement timeouts, database outages and recovery. Neither suite validates the shared schema, actual grants, TLS, real FAM tokens, query plans or business-policy acceptance.

From the repository root, check the rehearsal issuer with:

```sh
node --test scripts/local-read-issuer.test.mjs
```

## Production-container checks

Builds the production Dockerfiles and runs the backend and Caddy/Coraza frontend together. Needs Docker, Bash, curl and Python 3. No namespace, Oracle account, SSO client or token is needed.

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

Runs the production frontend/backend images with a disposable Oracle Free database and a small test OIDC issuer. The backend uses the real Spring Security JWT decoder and the `oracle` profile. Needs Docker, Bash, Python 3 and enough memory for Oracle plus the app.

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
