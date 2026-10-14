import { appendFileSync } from 'node:fs'
import { pathToFileURL } from 'node:url'

function boundedInteger(env, name, fallback, min, max) {
  const value = env[name] || String(fallback)
  if (!/^\d+$/.test(value) || Number(value) < min || Number(value) > max) {
    throw new Error(`${name} must be an integer from ${min} to ${max}`)
  }
  return String(Number(value))
}

export function deploymentSettings(env) {
  const environment = env.DEPLOY_ENV
  const target = env.DEPLOY_TARGET
  const repository = env.GITHUB_REPOSITORY?.toLowerCase()
  if (!/^[a-z0-9_.-]+\/[a-z0-9_.-]+$/.test(repository ?? '')) throw new Error('Invalid repository')
  if (!(environment === 'dev' && /^[1-9]\d*$/.test(target ?? '') && Number.isSafeInteger(Number(target))) &&
      !(environment === 'test' && target === 'test') && !(environment === 'prod' && target === 'prod')) {
    throw new Error('Unsupported deployment environment or target')
  }
  if (environment === 'prod' && (env.GITHUB_EVENT_NAME !== 'workflow_dispatch' ||
      env.GITHUB_REF !== 'refs/heads/main' ||
      env.GITHUB_WORKFLOW_REF !== `${env.GITHUB_REPOSITORY}/.github/workflows/release-prod.yml@refs/heads/main`)) {
    throw new Error('PROD requires the manual Release PROD workflow from main')
  }
  if (environment === 'test' && (env.GITHUB_EVENT_NAME !== 'push' || env.GITHUB_REF !== 'refs/heads/main')) {
    throw new Error('TEST deployment requires a push to main')
  }
  for (const setting of ['OC_SERVER', 'OC_APPS_DOMAIN', 'OC_NAMESPACE', 'OC_TOKEN',
    'TAPS_OIDC_ISSUER_URI', 'TAPS_OIDC_CLIENT_ID']) {
    if (!env[setting]) throw new Error(`${setting} is required for ${environment}`)
  }
  if (!env.OC_NAMESPACE.endsWith(`-${environment}`)) throw new Error('OpenShift namespace does not match the environment')
  const source = env.SOURCE_REF || env.GITHUB_SHA
  if (!/^[a-f0-9]{40}$/.test(source ?? '')) throw new Error('Deployment source must be a commit SHA')
  const images = {}
  for (const component of ['backend', 'frontend']) {
    const prefix = `ghcr.io/${repository}/${component}`
    const supplied = env[`${component.toUpperCase()}_IMAGE`]
    if (environment === 'dev') {
      if (!/^[a-f0-9]{40}$/.test(env.IMAGE_TAG ?? '')) throw new Error('DEV requires a commit SHA image tag')
      images[`${component}_image`] = `${prefix}:${env.IMAGE_TAG}`
    } else {
      if (!supplied?.startsWith(`${prefix}@`) || !/^sha256:[a-f0-9]{64}$/.test(supplied.slice(prefix.length + 1))) {
        throw new Error(`TEST and PROD require the repository's ${component} image pinned by digest`)
      }
      images[`${component}_image`] = supplied
    }
  }
  const oracle = env.TAPS_ORACLE_ENABLED || 'false'
  if (!['true', 'false'].includes(oracle)) throw new Error('TAPS_ORACLE_ENABLED must be true or false')
  if (oracle === 'true') {
    for (const setting of ['DATABASE_HOST', 'DATABASE_SERVICE_NAME', 'DATABASE_USER', 'DATABASE_PASSWORD', 'KEYSTORE_SECRET']) {
      if (!env[setting]) throw new Error(`${setting} is required when Oracle is enabled`)
    }
  }
  const settings = {
    ...images,
    source_ref: source,
    route_slot: environment === 'dev' ? String(Number(target) % 50) : target,
    spring_profiles_active: oracle === 'true' ? 'oracle' : '',
    backend_min_replicas: boundedInteger(env, 'TAPS_BACKEND_MIN_REPLICAS', 1, 1, 3),
    backend_max_replicas: boundedInteger(env, 'TAPS_BACKEND_MAX_REPLICAS', 1, 1, 3),
    database_max_pool_size: boundedInteger(env, 'DATABASE_MAX_POOL_SIZE', 2, 1, 10),
    database_min_idle: boundedInteger(env, 'DATABASE_MIN_IDLE', 0, 0, 10),
    database_connection_timeout_ms: boundedInteger(env, 'DATABASE_CONNECTION_TIMEOUT_MS', 3000, 250, 30000),
    database_query_timeout_seconds: boundedInteger(env, 'DATABASE_QUERY_TIMEOUT_SECONDS', 20, 1, 20),
    database_connect_timeout_ms: boundedInteger(env, 'DATABASE_CONNECT_TIMEOUT_MS', 10000, 1000, 10000),
    database_read_timeout_ms: boundedInteger(env, 'DATABASE_READ_TIMEOUT_MS', 30000, 1000, 30000),
  }
  if (+settings.backend_min_replicas > +settings.backend_max_replicas) throw new Error('Minimum replicas exceed maximum replicas')
  if (+settings.database_min_idle > +settings.database_max_pool_size) throw new Error('Minimum idle connections exceed pool size')
  return settings
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    for (const [key, value] of Object.entries(deploymentSettings(process.env))) {
      appendFileSync(process.env.GITHUB_OUTPUT, `${key}=${value}\n`)
    }
  } catch (error) {
    console.error(error.message)
    process.exitCode = 1
  }
}
