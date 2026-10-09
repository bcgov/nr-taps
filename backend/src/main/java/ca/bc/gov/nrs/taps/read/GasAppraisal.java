package ca.bc.gov.nrs.taps.read;

import ca.bc.gov.nrs.taps.domain.AppraisalMethod;
import ca.bc.gov.nrs.taps.domain.LegacyIdentifiers;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class GasAppraisal {
  public static final int PAGE_SIZE = 10;

  private GasAppraisal() {}

  public enum WorksheetType {
    APPRAISED,
    NON_APPRAISED,
    HISTORIC;

    public static WorksheetType fromLegacyCode(int code) {
      return switch (code) {
        case 0 -> APPRAISED;
        case 1 -> NON_APPRAISED;
        case 2 -> HISTORIC;
        default -> throw new IllegalArgumentException("unknown worksheet type");
      };
    }
  }

  public record Key(WorksheetType type, String worksheetId) {
    public Key {
      Objects.requireNonNull(type, "type");
      worksheetId = LegacyIdentifiers.requiredId(worksheetId);
    }
  }

  public enum SummaryVariant {
    CVP,
    INTERIOR_MPS,
    COAST_MPS_TOA_Y,
    COAST_MPS_TOA_N;

    public static Optional<SummaryVariant> resolve(
        String rateCalculationMethodCode, AppraisalMethod appraisalMethod, Boolean toaEligible) {
      if ("CVP".equals(rateCalculationMethodCode)) {
        return Optional.of(CVP);
      }
      if ("MPS".equals(rateCalculationMethodCode)) {
        if (appraisalMethod == AppraisalMethod.I) {
          return Optional.of(INTERIOR_MPS);
        }
        if (appraisalMethod == AppraisalMethod.C && toaEligible != null) {
          return Optional.of(toaEligible ? COAST_MPS_TOA_Y : COAST_MPS_TOA_N);
        }
      }
      return Optional.empty();
    }
  }

  public record Search(String licence, String timberMark, Integer page) {
    public Search {
      // GAS uppercases both filters and accepts punctuation.
      licence = upperFilter(licence, 10, "licence");
      timberMark = upperFilter(timberMark, 6, "timberMark");
      page = page == null ? 0 : page;
      if (page < 0) {
        throw new IllegalArgumentException("page must be non-negative");
      }
    }
  }

  public record Item(
      Key key,
      String licence,
      String timberMark,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate effectiveDate,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate expiryDate,
      CodeOption status,
      String referenceTypeCode) {
    public Item {
      Objects.requireNonNull(key, "key");
    }
  }

  public record Page(List<Item> items, long total, int page) {
    public Page {
      items = List.copyOf(items);
      if (total < 0 || page < 0 || items.size() > PAGE_SIZE || total < items.size()) {
        throw new IllegalArgumentException("invalid search page");
      }
    }
  }

  /** Available marks for a licence; an empty list means no marks were found. */
  public record LicenceMarks(String licence, List<String> timberMarks) {
    public LicenceMarks {
      Objects.requireNonNull(licence, "licence");
      timberMarks = List.copyOf(timberMarks);
    }
  }

  /** FTA details; any field may be missing. */
  public record FtaLicenceInformation(
      String clientNumber,
      String licenseeName,
      String licenceNumber,
      String cuttingPermit,
      String fileTypeCode,
      String timberMark,
      String forestRegion,
      String forestDistrict,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate markExpiryDate,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate markExtendDate,
      String ftaStatus,
      CodeOption markStatus,
      Boolean cruiseBased) {}

  /** FTA details and worksheets are independent; either can be missing. */
  public record SearchResult(Page appraisals, FtaLicenceInformation licenceInformation) {
    public SearchResult {
      Objects.requireNonNull(appraisals, "appraisals");
    }
  }

  /** Stored worksheet and rates; calculated breakdowns come later. */
  public record AppraisedSummary(
      Key key,
      String ecasId,
      AppraisalMethod appraisalMethod,
      SummaryVariant variant,
      String rateCalculationMethodCode,
      Boolean toaEligible,
      CodeOption status,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate effectiveDate,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate expiryDate,
      List<String> timberMarks,
      String primaryTimberMark,
      String referenceTypeCode,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate ceaseAdjustmentDate,
      List<StoredRate> rates) {
    public AppraisedSummary {
      Objects.requireNonNull(key, "key");
      if (key.type() != WorksheetType.APPRAISED) {
        throw new IllegalArgumentException("appraised summary requires an APPRAISED key");
      }
      ecasId = LegacyIdentifiers.requiredId(ecasId);
      Objects.requireNonNull(appraisalMethod, "appraisalMethod");
      SummaryVariant resolved =
          SummaryVariant.resolve(rateCalculationMethodCode, appraisalMethod, toaEligible)
              .orElseThrow(() -> new IllegalArgumentException("unsupported summary variant"));
      if (variant != resolved) {
        throw new IllegalArgumentException("summary variant does not match worksheet data");
      }
      timberMarks = List.copyOf(timberMarks);
      rates = List.copyOf(rates);
    }
  }

  /** Stored historic worksheet and rates; calculation inputs come later. */
  public record HistoricSummary(
      Key key,
      String licence,
      String timberMark,
      AppraisalMethod appraisalMethod,
      SummaryVariant variant,
      String rateCalculationMethodCode,
      Boolean tenureObligationAdjustment,
      Boolean adjustQuarterly,
      Boolean active,
      String policyVersion,
      CodeOption status,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate effectiveDate,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate expiryDate,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate ceaseAdjustmentDate,
      List<StoredRate> rates,
      List<StoredNonAppraisedRate> nonAppraisedRates,
      List<HistoricSpecies> historicSpecies,
      List<HistoricCoastSpeciesGrade> coastSpeciesGrades) {
    public HistoricSummary {
      requireFamily(key, WorksheetType.HISTORIC);
      Objects.requireNonNull(appraisalMethod, "appraisalMethod");
      SummaryVariant resolved =
          SummaryVariant.resolve(rateCalculationMethodCode, appraisalMethod, tenureObligationAdjustment)
              .orElseThrow(() -> new IllegalArgumentException("unsupported summary variant"));
      if (variant != resolved) {
        throw new IllegalArgumentException("summary variant does not match worksheet data");
      }
      rates = List.copyOf(rates);
      nonAppraisedRates = List.copyOf(nonAppraisedRates);
      historicSpecies = List.copyOf(historicSpecies);
      coastSpeciesGrades = List.copyOf(coastSpeciesGrades);
    }
  }

  /** Stored non-appraised worksheet with labelled rate components and display totals. */
  public record NonAppraisedSummary(
      Key key,
      String licence,
      String timberMark,
      AppraisalMethod appraisalMethod,
      CodeOption status,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate effectiveDate,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate expiryDate,
      CodeOption referenceType,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate sdmDeclarationAcceptanceDate,
      CodeOption timberSupplyBlock,
      CodeOption appraisalForestZone,
      CodeOption nonAppraisedRateType,
      CodeOption rateAdjustmentType,
      List<StoredNonAppraisedRate> rates,
      List<SelectedRateAddon> selectedRateAddons) {
    public NonAppraisedSummary {
      requireFamily(key, WorksheetType.NON_APPRAISED);
      Objects.requireNonNull(appraisalMethod, "appraisalMethod");
      rates = List.copyOf(rates);
      selectedRateAddons = List.copyOf(selectedRateAddons);
    }
  }

  /** Codes selected on the worksheet; no cost calculation. */
  public record SelectedRateAddon(
      String code,
      String description,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime effectiveDate,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime expiryDate,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime updateTimestamp) {
    public SelectedRateAddon {
      Objects.requireNonNull(code, "code");
    }
  }

  public record HistoricSpecies(
      String speciesId,
      String scaleSpeciesCode,
      String sellingPriceZone,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal speciesVolume,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal lumberRecoveryFactor,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal speciesDecayPercent,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal speciesStudPercent,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal speciesBurnPercent) {
    public HistoricSpecies {
      speciesId = LegacyIdentifiers.requiredId(speciesId);
      Objects.requireNonNull(scaleSpeciesCode, "scaleSpeciesCode");
      Objects.requireNonNull(speciesVolume, "speciesVolume");
    }
  }

  public record HistoricCoastSpeciesGrade(
      String speciesGradeId,
      String scaleSpeciesCode,
      String scaleProductCode,
      String scaleGradeCode,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal speciesGradePercent) {
    public HistoricCoastSpeciesGrade {
      speciesGradeId = LegacyIdentifiers.requiredId(speciesGradeId);
      Objects.requireNonNull(scaleSpeciesCode, "scaleSpeciesCode");
      Objects.requireNonNull(scaleProductCode, "scaleProductCode");
      Objects.requireNonNull(scaleGradeCode, "scaleGradeCode");
      Objects.requireNonNull(speciesGradePercent, "speciesGradePercent");
    }
  }

  public record StoredNonAppraisedRate(
      String rateId,
      CodeOption scaleSpecies,
      CodeOption scaleProduct,
      CodeOption scaleGrade,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal reserveStumpageRate,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal bonusBidAmount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal developmentLevy,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal silvicultureLevy) {
    public StoredNonAppraisedRate {
      rateId = LegacyIdentifiers.requiredId(rateId);
      Objects.requireNonNull(scaleSpecies, "scaleSpecies");
      Objects.requireNonNull(scaleSpecies.code(), "scaleSpecies.code");
      Objects.requireNonNull(scaleProduct, "scaleProduct");
      Objects.requireNonNull(scaleProduct.code(), "scaleProduct.code");
      Objects.requireNonNull(scaleGrade, "scaleGrade");
      Objects.requireNonNull(scaleGrade.code(), "scaleGrade.code");
      Objects.requireNonNull(reserveStumpageRate, "reserveStumpageRate");
      reserveStumpageRate = nonAppraisedAmount(reserveStumpageRate);
      bonusBidAmount = nonAppraisedAmount(bonusBidAmount);
      developmentLevy = nonAppraisedAmount(developmentLevy);
      silvicultureLevy = nonAppraisedAmount(silvicultureLevy);
    }

    @JsonProperty(value = "upsetStumpageRate", access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public BigDecimal upsetStumpageRate() {
      return reserveStumpageRate
          .add(silvicultureLevy == null ? BigDecimal.ZERO : silvicultureLevy)
          .add(developmentLevy == null ? BigDecimal.ZERO : developmentLevy);
    }

    @JsonProperty(value = "totalStumpageRate", access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public BigDecimal totalStumpageRate() {
      return upsetStumpageRate().add(bonusBidAmount == null ? BigDecimal.ZERO : bonusBidAmount);
    }
  }

  private static BigDecimal nonAppraisedAmount(BigDecimal value) {
    if (value == null) {
      return null;
    }
    try {
      value = value.setScale(2, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException("rate component exceeds two decimal places");
    }
    if (value.precision() > 5) {
      throw new IllegalArgumentException("rate component exceeds NUMBER(5,2)");
    }
    return value;
  }

  private static void requireFamily(Key key, WorksheetType family) {
    Objects.requireNonNull(key, "key");
    if (key.type() != family) {
      throw new IllegalArgumentException("summary requires a " + family + " key");
    }
  }

  public record StoredRate(
      String rateId,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate effectiveDate,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalStumpageRate) {
    public StoredRate {
      rateId = LegacyIdentifiers.requiredId(rateId);
      Objects.requireNonNull(effectiveDate, "effectiveDate");
      Objects.requireNonNull(totalStumpageRate, "totalStumpageRate");
      try {
        totalStumpageRate = totalStumpageRate.setScale(2, RoundingMode.UNNECESSARY);
      } catch (ArithmeticException e) {
        throw new IllegalArgumentException("totalStumpageRate exceeds two decimal places");
      }
      if (totalStumpageRate.precision() > 6) {
        throw new IllegalArgumentException("totalStumpageRate exceeds NUMBER(6,2)");
      }
    }
  }

  private static String upperFilter(String value, int length, String field) {
    String text = LegacyIdentifiers.optionalText(value, length, field);
    return text == null
        ? null
        : LegacyIdentifiers.optionalText(text.toUpperCase(Locale.ROOT), length, field);
  }
}
