package ca.bc.gov.nrs.taps.read;

import ca.bc.gov.nrs.taps.domain.AppraisalMethod;
import ca.bc.gov.nrs.taps.domain.DateRange;
import ca.bc.gov.nrs.taps.domain.LegacyIdentifiers;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public final class EcasInbox {
  public static final int PAGE_SIZE = 100;

  private EcasInbox() {}

  public enum Mode {
    MY_TO_DO,
    ALL_SUBMISSIONS
  }

  public enum DateType {
    EFFCTV,
    EXPRY,
    NTRY,
    LTMD,
    STTS,
    FCED
  }

  public enum SortField {
    ECAS_ID,
    TIMBER_MARK,
    LICENCE,
    CLIENT_NAME,
    STATUS,
    STATUS_CHANGE_DATE,
    APPRAISAL_TYPE,
    EFFECTIVE_DATE,
    EXPIRY_DATE,
    SUBMITTED_DATE,
    DISTRICT_RECEIVED_DATE,
    SENT_TO_REGION_DATE,
    UPDATE_DATE
  }

  public enum SortDirection {
    ASC,
    DESC
  }

  /** Organization and client selections filter results; they don't grant access. */
  public record Search(
      Mode mode,
      AppraisalMethod appraisalMethod,
      String ecasId,
      String licence,
      String cuttingPermit,
      String timberMark,
      String clientNumber,
      String clientLocationCode,
      List<String> orgUnitNumbers,
      String appraisalCategoryCode,
      String reappraisalReasonCode,
      List<String> statusCodes,
      DateRange statusDates,
      Boolean bctsFunded,
      Boolean certified,
      String fileTypeCode,
      String managementUnitType,
      String managementUnitId,
      String workedOnByUserId,
      List<DateType> dateTypes,
      DateRange dates,
      SortField sortBy,
      SortDirection sortDirection) {
    public Search {
      mode = mode == null ? Mode.MY_TO_DO : mode;
      ecasId = LegacyIdentifiers.optionalId(ecasId);
      licence = LegacyIdentifiers.licence(licence);
      cuttingPermit = LegacyIdentifiers.cuttingPermit(cuttingPermit);
      timberMark = LegacyIdentifiers.timberMark(timberMark);
      clientNumber = LegacyIdentifiers.clientNumber(clientNumber);
      clientLocationCode = LegacyIdentifiers.clientLocationCode(clientLocationCode);
      orgUnitNumbers = selections(orgUnitNumbers).stream()
          .map(LegacyIdentifiers::requiredId).distinct().toList();
      appraisalCategoryCode = selection(appraisalCategoryCode);
      reappraisalReasonCode = selection(reappraisalReasonCode);
      fileTypeCode = selection(fileTypeCode);
      statusCodes = selections(statusCodes);
      // Each date type adds at most one predicate, even if selected twice.
      dateTypes = dateTypes == null ? List.of() : List.copyOf(dateTypes).stream().distinct().toList();
      managementUnitType = LegacyIdentifiers.managementUnitType(managementUnitType);
      managementUnitId = LegacyIdentifiers.managementUnitId(managementUnitId);
      workedOnByUserId = LegacyIdentifiers.optionalText(
          workedOnByUserId == null ? null : workedOnByUserId.toUpperCase(Locale.ROOT),
          30, "workedOnByUserId");
      sortBy = sortBy == null ? SortField.ECAS_ID : sortBy;
      sortDirection = sortDirection == null ? SortDirection.DESC : sortDirection;

      if (cuttingPermit != null && licence == null) {
        throw new IllegalArgumentException("cuttingPermit requires licence");
      }
      if (managementUnitId != null && managementUnitType == null) {
        throw new IllegalArgumentException("managementUnitId requires managementUnitType");
      }
      if (clientLocationCode != null && clientNumber == null) {
        throw new IllegalArgumentException("clientLocationCode requires clientNumber");
      }
      if (dates != null && dates.hasBound() && dateTypes.isEmpty()) {
        throw new IllegalArgumentException("dates require at least one dateType");
      }
      if (statusDates != null && statusDates.hasBound()) {
        if (statusCodes.isEmpty()) {
          throw new IllegalArgumentException("statusDates require at least one statusCode");
        }
        if (statusCodes.contains("EE")) {
          throw new IllegalArgumentException("EE status selection cannot have statusDates");
        }
      }
    }
  }

  public record Item(
      String ecasId,
      AppraisalMethod appraisalMethod,
      String timberMark,
      String licence,
      String cuttingPermit,
      CodeOption status,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate statusDate,
      String appraisalTypeDescription,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate effectiveDate,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate expiryDate,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate licenseeSubmittedDate,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate districtReceivedDate,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate sentToRegionDate,
      Boolean multipleTimberMarks,
      String clientNumber,
      String clientLocationCode,
      String clientName,
      Integer revisionCount) {
    public Item {
      ecasId = LegacyIdentifiers.requiredId(ecasId);
    }
  }

  public record Page(List<Item> items, long total, int page) {
    public Page {
      items = List.copyOf(items);
      if (total < 0 || page < 0 || items.size() > PAGE_SIZE || total < items.size()) {
        throw new IllegalArgumentException("invalid inbox page");
      }
    }
  }

  private static List<String> selections(List<String> values) {
    return values == null
        ? List.of()
        : values.stream().map(String::trim).filter(value -> !value.isEmpty()).toList();
  }

  private static String selection(String value) {
    return value == null || value.trim().isEmpty() ? null : value.trim();
  }
}
