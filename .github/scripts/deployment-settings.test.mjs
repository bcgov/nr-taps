import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { deploymentSettings } from './deployment-settings.mjs'

const sha = 'a'.repeat(40)
const repository = 'bcgov/nr-taps'
const shared = {
  GITHUB_REPOSITORY: repository, GITHUB_SHA: sha, GITHUB_REF: 'refs/heads/main',
  GITHUB_EVENT_NAME: 'push', OC_SERVER: 'https://api.example.invalid:6443',
  OC_APPS_DOMAIN: 'apps.example.invalid', OC_TOKEN: 'synthetic',
  TAPS_OIDC_ISSUER_URI: 'https://sso.example.invalid/realms/synthetic', TAPS_OIDC_CLIENT_ID: 'synthetic',
  DATABASE_HOST: 'oracle.example.invalid', DATABASE_SERVICE_NAME: 'synthetic', DATABASE_USER: 'synthetic',
  DATABASE_PASSWORD: 'sensitive-test-value', KEYSTORE_SECRET: 'sensitive-test-value',
  BACKEND_IMAGE: `ghcr.io/${repository}/backend@sha256:${'b'.repeat(64)}`,
  FRONTEND_IMAGE: `ghcr.io/${repository}/frontend@sha256:${'c'.repeat(64)}`,
}
const production = {
  ...shared, DEPLOY_ENV: 'prod', DEPLOY_TARGET: 'prod', OC_NAMESPACE: 'synthetic-prod',
  GITHUB_EVENT_NAME: 'workflow_dispatch',
  GITHUB_WORKFLOW_REF: `${repository}/.github/workflows/release-prod.yml@refs/heads/main`,
  SOURCE_REF: 'd'.repeat(40),
}

test('PROD requires a manual dispatch of the release workflow on main', () => {
  assert.equal(deploymentSettings(production).source_ref, production.SOURCE_REF)
  for (const overrides of [
    { GITHUB_EVENT_NAME: 'push' }, { GITHUB_EVENT_NAME: 'schedule' },
    { GITHUB_EVENT_NAME: 'pull_request' }, { GITHUB_EVENT_NAME: 'workflow_run' },
    { GITHUB_REF: 'refs/tags/v1.0.0' }, { GITHUB_REF: 'refs/heads/feature' },
    { GITHUB_WORKFLOW_REF: `${repository}/.github/workflows/merge.yml@refs/heads/main` },
    { GITHUB_WORKFLOW_REF: undefined }, { DEPLOY_TARGET: 'test' },
    { OC_NAMESPACE: 'synthetic-dev' }, { BACKEND_IMAGE: `ghcr.io/${repository}/backend:latest` },
    { FRONTEND_IMAGE: shared.BACKEND_IMAGE }, { SOURCE_REF: 'main' },
  ]) assert.throws(() => deploymentSettings({ ...production, ...overrides }))
})

test('TEST requires main and the pinned image pair; DEV retains SHA tags and numbered slots', () => {
  const testEnv = { ...shared, DEPLOY_ENV: 'test', DEPLOY_TARGET: 'test', OC_NAMESPACE: 'synthetic-test' }
  assert.equal(deploymentSettings(testEnv).backend_image, shared.BACKEND_IMAGE)
  assert.throws(() => deploymentSettings({ ...testEnv, GITHUB_REF: 'refs/pull/7/merge' }))
  assert.throws(() => deploymentSettings({ ...testEnv, FRONTEND_IMAGE: '' }))
  const dev = { ...shared, DEPLOY_ENV: 'dev', DEPLOY_TARGET: '57', OC_NAMESPACE: 'synthetic-dev', IMAGE_TAG: sha,
    GITHUB_EVENT_NAME: 'pull_request', GITHUB_REF: 'refs/pull/57/merge' }
  const result = deploymentSettings(dev)
  assert.equal(result.route_slot, '7')
  assert.equal(result.backend_image, `ghcr.io/${repository}/backend:${sha}`)
  for (const overrides of [{ DEPLOY_TARGET: 'test' }, { IMAGE_TAG: 'prod' }, { DEPLOY_TARGET: '9007199254740992' }]) {
    assert.throws(() => deploymentSettings({ ...dev, ...overrides }))
  }
})

test('invalid settings fail with a workflow error annotation', () => {
  const script = fileURLToPath(new URL('./deployment-settings.mjs', import.meta.url))
  const run = spawnSync(process.execPath, [script], { env: { ...production, OC_NAMESPACE: 'synthetic-dev' }, encoding: 'utf8' })
  assert.equal(run.status, 1)
  assert.equal(run.stdout, '::error title=Invalid deployment settings::OpenShift namespace does not match the environment\n')
  assert.ok(!run.stdout.includes('sensitive-test-value'))
})

test('every deployment requires Oracle credentials and accepts only valid TCPS ports', () => {
  for (const setting of ['DATABASE_HOST', 'DATABASE_SERVICE_NAME', 'DATABASE_USER', 'DATABASE_PASSWORD', 'KEYSTORE_SECRET']) {
    assert.throws(() => deploymentSettings({ ...production, [setting]: '' }))
    // The removed opt-out must not bypass the required deployed-read configuration.
    assert.throws(() => deploymentSettings({ ...production, [setting]: '', TAPS_ORACLE_ENABLED: 'false' }))
  }
  for (const port of ['', '1', '1543', '2484', '65535']) {
    assert.ok(deploymentSettings({ ...production, DATABASE_PORT: port }))
  }
  for (const port of ['0', '65536', '-1', '1.5', '01543', ' 1543', '1543\n', '1543\nmalformed']) {
    assert.throws(() => deploymentSettings({ ...production, DATABASE_PORT: port }))
  }
  const result = deploymentSettings(production)
  assert.ok(!JSON.stringify(result).includes('sensitive-test-value'))
  assert.ok(!Object.hasOwn(result, 'spring_profiles_active'))
})

test('connection, timeout and replica budgets are conservative and reject unbounded values', () => {
  const result = deploymentSettings(production)
  assert.equal(result.database_max_pool_size, '2')
  assert.equal(result.database_min_idle, '0')
  assert.equal(result.backend_max_replicas, '1')
  assert.equal(result.database_connection_timeout_ms, '3000')
  for (const overrides of [
    { DATABASE_MAX_POOL_SIZE: '0' }, { DATABASE_MAX_POOL_SIZE: '11' },
    { DATABASE_MIN_IDLE: '3' }, { DATABASE_MAX_POOL_SIZE: '2\n3' },
    { DATABASE_CONNECTION_TIMEOUT_MS: '0' }, { DATABASE_QUERY_TIMEOUT_SECONDS: '0' },
    { DATABASE_CONNECT_TIMEOUT_MS: '0' }, { DATABASE_READ_TIMEOUT_MS: '0' },
    { TAPS_BACKEND_MAX_REPLICAS: '4' }, { TAPS_BACKEND_MIN_REPLICAS: '2' },
  ]) assert.throws(() => deploymentSettings({ ...production, ...overrides }))
})

test('no automatic workflow calls PROD, and releases stage the selected templates', () => {
  const workflow = name => readFileSync(new URL(`../workflows/${name}.yml`, import.meta.url), 'utf8')
  const release = workflow('release-prod')
  const triggers = release.slice(release.indexOf('\non:'), release.indexOf('\nconcurrency:'))
  assert.match(triggers, /workflow_dispatch:/)
  assert.doesNotMatch(triggers, /\b(push|pull_request|schedule|workflow_run|workflow_call):/)
  for (const name of ['merge', 'pr-open', 'pr-close', 'scheduled', 'analysis']) {
    assert.doesNotMatch(workflow(name), /(?:environment|target): prod\b/)
  }
  assert.match(workflow('merge'), /needs: \[init, smoke\]/)
  const deploy = workflow('reusable-deploy')
  for (const file of ['backend', 'frontend', 'route']) {
    assert.match(deploy, new RegExp(`file: \\$\\{\\{ runner.temp \\}\\}/taps-deploy/${file}\\.yml`))
  }
  assert.equal((deploy.match(/ref: \$\{\{ needs.validate.outputs.source_ref \}\}/g) ?? []).length, 2)
})

test('older releases tolerate newer parameters and PROD records only the released commit', () => {
  const deploy = readFileSync(new URL('../workflows/reusable-deploy.yml', import.meta.url), 'utf8')
  assert.match(deploy, /OC_PROCESS_FLAGS: \$\{\{ inputs.source_ref != '' && inputs.source_ref != github.sha && '--ignore-unknown-parameters' \|\| '' \}\}/)
  // Backend, frontend and route templates all come from the selected commit.
  assert.equal((deploy.match(/^\s+\$OC_PROCESS_FLAGS$/gm) ?? []).length, 3)
  const result = deploy.slice(deploy.indexOf('\n  deployment-result:'))
  assert.match(result, /environment:\n\s+name: \$\{\{ inputs.environment \}\}\n(?:\s+#.*\n)?\s+deployment: \$\{\{ inputs.environment != 'prod' \}\}/)
})


test('deployed Oracle remains enabled and the same port reaches certificate init and JDBC', () => {
  const deploy = readFileSync(new URL('../workflows/reusable-deploy.yml', import.meta.url), 'utf8')
  const backend = readFileSync(new URL('../../backend/openshift.deploy.yml', import.meta.url), 'utf8')
  assert.doesNotMatch(deploy, /TAPS_ORACLE_ENABLED|spring_profiles_active/)
  assert.match(deploy, /-p SPRING_PROFILES_ACTIVE=oracle/)
  assert.match(deploy, /-p DATABASE_PORT="\$DATABASE_PORT"/)
  assert.equal((deploy.match(/DATABASE_PORT: \$\{\{ secrets.port \|\| '1543' \}\}/g) ?? []).length, 2)
  assert.match(backend, /name: SPRING_PROFILES_ACTIVE\n\s+description:.*\n\s+value: oracle/)
  assert.match(backend, /name: ORACLEDB_PORT\n\s+value: \$\{DATABASE_PORT\}/)
  assert.match(backend, /name: DATABASE_PORT\n\s+value: \$\{DATABASE_PORT\}/)
})
