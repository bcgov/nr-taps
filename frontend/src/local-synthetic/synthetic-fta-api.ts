import type { FtaLicenceInformation } from '@/contracts/appraisal'
import type { ReadApi } from '@/service/read-service'
import source from '../../../backend/src/test/resources/contracts/synthetic-workflow.json'

// Standalone fictional FTA context prevents the static preview from calling HTTP or importing itself.
export const syntheticFtaApi: Pick<ReadApi, 'licenceInformation'> = {
  licenceInformation: async (_licence, timberMark, signal) => {
    await new Promise<void>((resolve) => setTimeout(resolve, 200))
    signal.throwIfAborted()
    const known = [source.gasSearchResult, source.gasSearchResultWithoutAppraisals]
      .map((result) => result.licenceInformation)
      .find((info) => info.timberMark === timberMark)
    if (known) return known
    if (!/^ZZ999[5-9]$/.test(timberMark)) return null
    const result: FtaLicenceInformation = {
      clientNumber: null,
      licenseeName: 'Synthetic forest client',
      licenceNumber: null,
      cuttingPermit: null,
      fileTypeCode: null,
      timberMark,
      forestRegion: null,
      forestDistrict: null,
      markExpiryDate: null,
      markExtendDate: null,
      ftaStatus: 'Synthetic licence status',
      markStatus: { code: 'SYN', description: 'Synthetic issued mark' },
      cruiseBased: timberMark === 'ZZ9995' ? null : timberMark === 'ZZ9997',
    }
    return result
  },
}
