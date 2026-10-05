package ca.bc.gov.nrs.taps.read;

import ca.bc.gov.nrs.taps.domain.AppraisalMethod;
import ca.bc.gov.nrs.taps.domain.LegacyIdentifiers;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class EcasReference {
  private EcasReference() {}

  public record Header(
      String ecasId,
      AppraisalMethod appraisalMethod,
      Integer revisionCount,
      String licence,
      String cuttingPermit,
      String clientNumber,
      String clientLocationCode,
      String licenseeName,
      CodeOption status,
      String appraisalCategoryCode,
      String reappraisalReasonCode,
      String rateCalculationMethodCode,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate effectiveDate,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate expiryDate,
      CodeOption administrativeDistrict,
      CodeOption geographicDistrict,
      CodeOption fileType,
      CodeOption timberSupplyArea,
      CodeOption timberSupplyBlock) {
    public Header {
      ecasId = LegacyIdentifiers.requiredId(ecasId);
      Objects.requireNonNull(appraisalMethod, "appraisalMethod");
    }
  }

  public record TimberMark(
      String timberMark, BigDecimal cruiseVolume, Boolean primary, Integer revisionCount) {}

  public record Coast(
      Header header,
      String primaryTimberMark,
      List<TimberMark> timberMarks,
      String referenceMark,
      BigDecimal netCruiseVolume,
      BigDecimal netMerchantableArea,
      BigDecimal initialMerchantableArea,
      BigDecimal pointOfAppraisalDistance,
      String majorCentreCode,
      BigDecimal majorCentreDistance) {
    public Coast {
      requireMethod(header, AppraisalMethod.C);
      timberMarks = List.copyOf(timberMarks);
    }
  }

  public record Interior(
      Header header,
      String timberMark,
      Integer timberMarkRevisionCount,
      String referenceMark,
      CodeOption pointOfAppraisal,
      String sellingPriceZoneCode,
      Boolean comparativeCruise,
      Boolean salvage) {
    public Interior {
      requireMethod(header, AppraisalMethod.I);
    }
  }

  private static void requireMethod(Header header, AppraisalMethod expected) {
    Objects.requireNonNull(header, "header");
    if (header.appraisalMethod() != expected) {
      throw new IllegalArgumentException("reference details do not match appraisalMethod");
    }
  }
}
