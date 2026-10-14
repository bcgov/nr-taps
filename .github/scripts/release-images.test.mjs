import assert from 'node:assert/strict'
import { execFileSync, spawnSync } from 'node:child_process'
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'
import { validateManifest } from './release-images.mjs'

const script = fileURLToPath(new URL('./release-images.mjs', import.meta.url))
const expected = { repository: 'bcgov/nr-taps', sha: 'a'.repeat(40), run_id: '123', run_attempt: '2' }
const images = {
  backend_image: `ghcr.io/bcgov/nr-taps/backend@sha256:${'b'.repeat(64)}`,
  frontend_image: `ghcr.io/bcgov/nr-taps/frontend@sha256:${'c'.repeat(64)}`,
}

test('accepts exactly the immutable image pair recorded for the successful TEST attempt', () => {
  assert.deepEqual(validateManifest({ ...expected, ...images }, expected), images)
})

for (const key of Object.keys(expected)) {
  test(`rejects a release manifest from another ${key}`, () => {
    assert.throws(() => validateManifest({ ...expected, ...images, [key]: 'different' }, expected))
  })
}

for (const value of [
  'ghcr.io/bcgov/nr-taps/backend:123',
  `ghcr.io/other/repository/backend@sha256:${'b'.repeat(64)}`,
  images.frontend_image,
  `${images.backend_image}\nextra=output`,
  'ghcr.io/bcgov/nr-taps/backend@sha256:missing',
]) {
  test(`rejects invalid backend reference ${JSON.stringify(value)}`, () => {
    assert.throws(() => validateManifest({ ...expected, ...images, backend_image: value }, expected))
  })
}

function fixture(t) {
  const dir = mkdtempSync(join(tmpdir(), 'taps-release-test-'))
  t.after(() => rmSync(dir, { recursive: true, force: true }))
  mkdirSync(join(dir, 'bin'))
  const env = {
    ...process.env,
    GIT_CONFIG_GLOBAL: '/dev/null',
    GIT_CONFIG_NOSYSTEM: '1',
    GITHUB_REPOSITORY: expected.repository,
    GITHUB_EVENT_NAME: 'workflow_dispatch',
    GITHUB_REF: 'refs/heads/main',
    GITHUB_OUTPUT: join(dir, 'outputs'),
    GITHUB_STEP_SUMMARY: join(dir, 'summary'),
    GITHUB_RUN_ID: expected.run_id,
    GITHUB_RUN_ATTEMPT: expected.run_attempt,
    CAPTURE_ATTEMPT: expected.run_attempt,
    PATH: `${join(dir, 'bin')}:${process.env.PATH}`,
    RELEASE_TAG: 'v1.0.0',
    PR_NUMBER: '123',
  }
  const git = (...args) => execFileSync('git', args, { cwd: dir, env, encoding: 'utf8' }).trim()
  git('init', '-q', '--initial-branch=main')
  git('-c', 'user.name=Test', '-c', 'user.email=test@example.invalid', '-c', 'commit.gpgsign=false',
    'commit', '-q', '--allow-empty', '-m', 'test')
  git('tag', 'v1.0.0')
  env.GITHUB_SHA = git('rev-parse', 'HEAD')
  const workflowRun = {
    id: 123, run_attempt: 2, head_sha: env.GITHUB_SHA,
    head_branch: 'main', event: 'push', conclusion: 'success',
  }
  env.MOCK_GH_RESPONSE = JSON.stringify({ workflow_runs: [workflowRun] })
  const pr = { merged: true, merge_commit_sha: env.GITHUB_SHA, base: { ref: 'main' },
    head: { sha: 'e'.repeat(40), repo: { full_name: expected.repository } } }
  env.MOCK_PR = JSON.stringify(pr)
  env.MOCK_HEAD_TREE = env.MOCK_MERGE_TREE = 'd'.repeat(40)
  writeFileSync(join(dir, 'bin', 'gh'), `#!${process.execPath}
const endpoint = process.argv.at(-1)
const result = endpoint.includes('/pulls/') ? process.env.MOCK_PR :
  endpoint.includes('/git/commits/') ? JSON.stringify({tree: {sha:
    endpoint.endsWith('e'.repeat(40)) ? process.env.MOCK_HEAD_TREE : process.env.MOCK_MERGE_TREE}}) :
  process.env.MOCK_GH_RESPONSE
process.stdout.write(result)
`, { mode: 0o755 })
  writeFileSync(join(dir, 'bin', 'docker'), `#!${process.execPath}\nconst component = process.argv.find(a => a.includes('/backend:')) ? 'b' : 'c'\nprocess.stdout.write(JSON.stringify({digest: 'sha256:' + component.repeat(64)}))\n`, { mode: 0o755 })
  const execute = (command, overrides = {}, file) => spawnSync(process.execPath,
    [script, command, ...(file ? [file] : [])], { cwd: dir, env: { ...env, ...overrides }, encoding: 'utf8' })
  return { dir, env, git, workflowRun, pr, execute }
}

test('resolves a tested main commit from lightweight and annotated tags', t => {
  const f = fixture(t)
  assert.equal(f.execute('resolve').status, 0)
  assert.match(readFileSync(f.env.GITHUB_OUTPUT, 'utf8'), new RegExp(`sha=${f.env.GITHUB_SHA}\\nrun_id=123\\nrun_attempt=2`))
  f.git('-c', 'user.name=Test', '-c', 'user.email=test@example.invalid', '-c', 'tag.gpgsign=false',
    'tag', '-a', 'v1.0.1', '-m', 'annotated')
  assert.equal(f.execute('resolve', { RELEASE_TAG: 'v1.0.1' }).status, 0)
})

test('rejects dispatches outside main, missing or malformed tags, and unmerged commits', t => {
  const f = fixture(t)
  for (const overrides of [
    { GITHUB_REF: 'refs/heads/feature' },
    { GITHUB_EVENT_NAME: 'push' },
    { GITHUB_EVENT_NAME: 'schedule' },
    { RELEASE_TAG: 'missing' },
    { RELEASE_TAG: 'main' },
    { RELEASE_TAG: 'v1;echo injected' },
    { RELEASE_TAG: '../main' },
  ]) assert.notEqual(f.execute('resolve', overrides).status, 0)
  f.git('-c', 'user.name=Test', '-c', 'user.email=test@example.invalid', '-c', 'commit.gpgsign=false',
    'commit', '-q', '--allow-empty', '-m', 'not in selected main')
  f.git('tag', 'unmerged')
  assert.notEqual(f.execute('resolve', { RELEASE_TAG: 'unmerged' }).status, 0)
})

test('rejects absent, failed, wrong-commit and wrong-event TEST runs', t => {
  const f = fixture(t)
  for (const runs of [[],
    [{ ...f.workflowRun, conclusion: 'failure' }],
    [{ ...f.workflowRun, head_sha: 'f'.repeat(40) }],
    [{ ...f.workflowRun, event: 'pull_request' }],
    [{ ...f.workflowRun, head_branch: 'feature' }],
  ]) {
    assert.notEqual(f.execute('resolve', { MOCK_GH_RESPONSE: JSON.stringify({ workflow_runs: runs }) }).status, 0)
  }
})

test('captures, records and validates the same digests without resolving mutable tags again', t => {
  const f = fixture(t)
  assert.equal(f.execute('capture').status, 0)
  assert.match(readFileSync(f.env.GITHUB_OUTPUT, 'utf8'), new RegExp(images.backend_image))
  const file = join(f.dir, 'candidate.json')
  assert.equal(f.execute('record', { BACKEND_IMAGE: images.backend_image, FRONTEND_IMAGE: images.frontend_image }, file).status, 0)
  assert.equal(f.execute('validate', { RELEASE_SHA: f.env.GITHUB_SHA, TEST_RUN_ID: '123', TEST_RUN_ATTEMPT: '2' }, file).status, 0)
  assert.notEqual(f.execute('validate', { RELEASE_SHA: f.env.GITHUB_SHA, TEST_RUN_ID: '123', TEST_RUN_ATTEMPT: '3' }, file).status, 0)
  assert.notEqual(f.execute('capture', { PR_NUMBER: '' }).status, 0)
  assert.notEqual(f.execute('record', { CAPTURE_ATTEMPT: '1', BACKEND_IMAGE: images.backend_image, FRONTEND_IMAGE: images.frontend_image }, file).status, 0)
})


test('rejects PR images from an unsynchronized tree, wrong merge or foreign repository', t => {
  const f = fixture(t)
  for (const overrides of [
    { MOCK_HEAD_TREE: 'f'.repeat(40) }, { MOCK_HEAD_TREE: '' },
    { MOCK_PR: JSON.stringify({ ...f.pr, merged: false }) },
    { MOCK_PR: JSON.stringify({ ...f.pr, merge_commit_sha: 'f'.repeat(40) }) },
    { MOCK_PR: JSON.stringify({ ...f.pr, head: { ...f.pr.head, repo: { full_name: 'other/repository' } } }) },
    { MOCK_PR: JSON.stringify({ ...f.pr, base: { ref: 'feature' } }) },
  ]) assert.notEqual(f.execute('capture', overrides).status, 0)
})
