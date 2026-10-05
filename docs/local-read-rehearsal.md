# Local authenticated read rehearsal

Runs the production frontend and backend images with a disposable Oracle Free database and a small test OIDC issuer. The backend uses the real Spring Security JWT decoder. Needs Docker, Bash, Python 3 and enough memory for Oracle plus the app.

The test issuer and fixtures are local-only and aren't in any app image or OpenShift template. Keys and passwords are generated per run. Never use real credentials or a shared database with it.

## Start and stop

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

## Browser checks

Click the normal sign-in button, pick a synthetic user, and select **Sign in to local TAPS**.

| Persona | Expected behavior |
| --- | --- |
| Provincial administrator | ECAS and GAS reads across all fixture regions. |
| Cariboo appraiser | Cariboo records, including ECAS `1001`, worksheet `101` and licence `A00001`. |
| Omineca appraiser | Omineca records; opening Cariboo worksheet `101` returns 404. |
| Licensee viewer | ECAS client-scoped access; no GAS worksheets. |
| Signed in without a TAPS role | Access-request view only. |
| Expired access token | Backend rejects it and the frontend clears the session. |

Suggested checks:

1. As the Cariboo appraiser, search ECAS, open reference `1001` and follow it to the GAS worksheet. Check marks, dates and stored amounts.
2. Search GAS for `A00001`, pick a timber mark, and check FTA info and stored rates. Repeat at mobile width, including drawer Tab/Escape and focus return.
3. Sign out and in as the Omineca appraiser and the licensee viewer. Filters must never widen access.
4. Try the no-role and expired-token users. Errors must not show records, SQL or credentials, and the user must be able to sign in again.

The issuer doesn't model real FAM provisioning, IDIR MFA or Business BCeID/SiteMinder.

## Database outage

While signed in:

```sh
bash scripts/local-read-stack.sh pause-db /path/printed/by/start
```

Run a new search. Expect a quick `READ_UNAVAILABLE` / 503 and a retry option. Liveness and readiness stay healthy; `/actuator/health` shows the database down. Then resume and retry:

```sh
bash scripts/local-read-stack.sh resume-db /path/printed/by/start
```

The app should recover without a restart.

## SQL*Plus preflight

```sh
bash scripts/local-read-stack.sh preflight /path/printed/by/start
```

Runs the read-only preflight script against the fixture database. Full output goes to `preflight.log` in the output directory; the command prints the summary and verdict and exits nonzero on any error. For a shared database, follow the [activation checklist](activation-acceptance.md).

## Automated tests

```sh
node --test scripts/local-read-issuer.test.mjs
```

From `backend` with Java 21 and Docker:

```sh
mvn -B -Poracle-it -Dit.test=OracleReadResilienceIT verify
```

[`OracleReadResilienceIT`](../backend/src/test/java/ca/bc/gov/nrs/taps/integration/OracleReadResilienceIT.java) runs real HTTP, the production token decoder and real Oracle. It checks:

- Regional reads work, cross-region reads are denied, and expired, wrong-client, wrong-issuer and wrong-signature tokens are rejected.
- An exhausted connection pool gives a quick 503 and recovers.
- A slow query hits the statement timeout, returns 503 and recovers.
- Pausing Oracle fails reads and aggregate health but not liveness/readiness, and resuming restores reads.

`mvn -B -Poracle-it verify` runs this and `OracleReadIT` together. See [Oracle read runtime](oracle-read-runtime.md) for settings and grants.
