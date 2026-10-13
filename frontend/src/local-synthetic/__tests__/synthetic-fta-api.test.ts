import { afterEach, expect, test, vi } from 'vitest'
import source from '../../../../backend/src/test/resources/contracts/synthetic-workflow.json'
import { syntheticFtaApi } from '../synthetic-fta-api'

afterEach(() => vi.unstubAllGlobals())

test.each([
  source.gasSearchResult.licenceInformation,
  source.gasSearchResultWithoutAppraisals.licenceInformation,
])('standalone FTA preview retains the shared metadata for $timberMark', async (info) => {
  const fetch = vi.fn()
  vi.stubGlobal('fetch', fetch)
  await expect(
    syntheticFtaApi.licenceInformation('', info.timberMark, new AbortController().signal),
  ).resolves.toEqual(info)
  expect(fetch).not.toHaveBeenCalled()
})
