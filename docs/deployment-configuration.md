# SSO and deployment configuration

SSO registration and public GitHub configuration can be prepared before Gold provisioning. Deployment credentials and successful rollout acceptance require the actual namespaces. The TAPS project-set identifier supplied for Gold is `af11ba`; provisioning is pending.

## Starting the SSO request

| Request field         | TAPS choice                                                           |
| --------------------- | --------------------------------------------------------------------- |
| Application           | Timber Appraisal and Pricing System (TAPS), modernizing ECAS + GAS2   |
| Protocol              | OpenID Connect                                                        |
| Client                | Public browser/SPA client                                             |
| Flow                  | Authorization code with PKCE                                          |
| Identity providers    | IDIR MFA and Business BCeID                                           |
| Basic BCeID           | Disabled; project direction confirms Business only for external users |
| Environments          | DEV and TEST initially; PROD is a separate later deployment gate      |
| Browser client secret | None; do not create or inject a confidential secret into the SPA      |

Reuse the existing TAPS request/integration if one exists rather than creating a duplicate. Copy the installation JSON's public `resource` client ID for each environment; the request/integration number is not the client ID. The application's browser issuer/client and backend issuer/client must match. A successful integration request does not establish approved TAPS role definitions or user assignments.

The current implementation uses `azureidir` and `bceidbusiness` provider hints. Confirm the actual approved providers and public client metadata before live acceptance.

## Redirects and origins

Register the exact callbacks and corresponding origins/post-logout returns. The prepared DEV host range is bounded to 50 slots; PR number modulo 50 chooses the public hostname.

| Environment                   | Callback URI                                                     | Origin / post-logout return                         |
| ----------------------------- | ---------------------------------------------------------------- | --------------------------------------------------- |
| Local development, DEV client | `http://localhost:3000/authCallback`                             | `http://localhost:3000`                             |
| DEV slots 0-49                | `https://nr-taps-<slot>.apps.gold.devops.gov.bc.ca/authCallback` | `https://nr-taps-<slot>.apps.gold.devops.gov.bc.ca` |
| TEST                          | `https://nr-taps-test.apps.gold.devops.gov.bc.ca/authCallback`   | `https://nr-taps-test.apps.gold.devops.gov.bc.ca`   |

`<slot>` is notation, not a value to submit. Supply each exact host from 0 through 49 according to the CSS request format. The frontend runtime config uses `window.location.origin`, so it returns to the host where sign-in began. DEV clients/redirects also need the local callback if local sign-in is expected.

Generate the DEV values for a request form without credentials:

```sh
for slot in $(seq 0 49); do
  printf 'https://nr-taps-%s.apps.gold.devops.gov.bc.ca\n' "$slot"
  printf 'https://nr-taps-%s.apps.gold.devops.gov.bc.ca/authCallback\n' "$slot"
done
```

No PROD hostname, certificate or redirect is operationally accepted by this document. Add them only with the reviewed PROD deployment path.

## GitHub Actions variables and secrets

Create `dev` and `test` GitHub Environments. Restrict TEST deployment to `main`. Public values go in Actions variables; actual credentials go in secrets. Do not reuse a LEXIS client ID, FAM's own browser client, another application's deployment token or a fake placeholder.

| Scope                                    | Name                              | Required value / availability                                                |
| ---------------------------------------- | --------------------------------- | ---------------------------------------------------------------------------- |
| Repository variable                      | `OC_SERVER`                       | `https://api.gold.devops.gov.bc.ca:6443`                                     |
| Repository variable                      | `OC_APPS_DOMAIN`                  | `apps.gold.devops.gov.bc.ca`                                                 |
| DEV environment variable                 | `TAPS_OIDC_ISSUER_URI`            | `https://dev.loginproxy.gov.bc.ca/auth/realms/standard`                      |
| TEST environment variable                | `TAPS_OIDC_ISSUER_URI`            | `https://test.loginproxy.gov.bc.ca/auth/realms/standard`                     |
| DEV/TEST environment variable            | `TAPS_OIDC_CLIENT_ID`             | Public TAPS `resource` value for that CSS environment; pending registration  |
| DEV/TEST environment variable            | `TAPS_OIDC_SITEMINDER_LOGOUT_URL` | `https://logontest7.gov.bc.ca/clp-cgi/logoff.cgi`; also the workflow default |
| Repository secret for current DEV caller | `oc_namespace`                    | Actual DEV namespace, expected `af11ba-dev`; confirm provisioning            |
| Repository secret for current DEV caller | `oc_token`                        | Least-privilege DEV deployment service token; unavailable until provisioning |
| TEST environment secret                  | `oc_namespace`                    | Actual TEST namespace, expected `af11ba-test`; confirm provisioning          |
| TEST environment secret                  | `oc_token`                        | Separate least-privilege TEST deployment service token                       |

The current DEV workflow/cleanup caller passes repository-scoped DEV secrets. TEST jobs select TEST environment secrets, and validation requires the namespace suffix to match the environment so missing TEST credentials cannot silently fall back to DEV. Keep the environment-specific OIDC values in their environment rather than adding a repository-level fallback.

There is no `TAPS_OIDC_CLIENT_SECRET` input. A CSS API credential used by FAM to administer integrations is a separate FAM operational credential and does not belong in TAPS's public browser configuration.

Example public-variable commands, once access is authorized:

```sh
gh variable set OC_SERVER --repo bcgov/nr-taps --body 'https://api.gold.devops.gov.bc.ca:6443'
gh variable set OC_APPS_DOMAIN --repo bcgov/nr-taps --body 'apps.gold.devops.gov.bc.ca'
gh variable set TAPS_OIDC_ISSUER_URI --repo bcgov/nr-taps --env dev --body 'https://dev.loginproxy.gov.bc.ca/auth/realms/standard'
gh variable set TAPS_OIDC_ISSUER_URI --repo bcgov/nr-taps --env test --body 'https://test.loginproxy.gov.bc.ca/auth/realms/standard'
```

Set the real public client IDs after registration. Use GitHub's secret entry or `gh secret set` with secure interactive/stdin entry for deployment tokens; keep values out of command history, logs and Git. Secret listings expose names, not stored values.

## Acceptance order

1. Confirm SSO request/providers, DEV/TEST public clients and exact callback/origin lists.
2. Confirm GitHub variables, TEST branch restriction and the provisioned namespace/token pairs.
3. Build the images and deploy DEV; the automated smoke checks the public shell/config and anonymous `/api/me` denial.
4. Review the proposed roles before authorized FAM provisioning. Use separate IDIR and Business BCeID personas, including BCTS consultants, to verify accepted grants and negative cases.
5. Check real login, token renewal, logout/re-login and no-role behavior. Then verify record-scope/workflow rules as business endpoints are ported.
6. Perform TEST rollout, security-findings review and actual rolling-availability acceptance. PROD remains disabled.

Local tests, a created GitHub Environment or a submitted SSO request are not evidence of deployed application acceptance.
