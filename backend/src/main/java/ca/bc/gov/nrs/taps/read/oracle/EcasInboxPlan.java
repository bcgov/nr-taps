package ca.bc.gov.nrs.taps.read.oracle;

import ca.bc.gov.nrs.taps.domain.DateRange;
import ca.bc.gov.nrs.taps.read.EcasInbox;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** ECAS05 all-submissions and direct-ID filters. Selected organizations only filter. */
public final class EcasInboxPlan {
  private final String sql;
  private final List<String> parameters;
  private final String orderBy;
  private final long firstRow;
  private final long lastRow;

  private EcasInboxPlan(String sql, List<String> parameters, String orderBy, int page) {
    this.sql = sql;
    this.parameters = List.copyOf(parameters);
    this.orderBy = orderBy;
    this.firstRow = (long) page * EcasInbox.PAGE_SIZE + 1;
    this.lastRow = firstRow + EcasInbox.PAGE_SIZE - 1;
  }

  public static EcasInboxPlan forUser(TapsUser user, EcasInbox.Search search, int page) {
    Objects.requireNonNull(user, "user");
    Objects.requireNonNull(search, "search");
    if (page < 0) {
      throw new IllegalArgumentException("page must be non-negative");
    }
    if (search.mode() != EcasInbox.Mode.ALL_SUBMISSIONS && search.ecasId() == null) {
      throw new UnsupportedOperationException("MY_TO_DO requires a verified legacy user assignment mapping");
    }
    List<String> predicates = new ArrayList<>();
    List<String> parameters = new ArrayList<>();
    var scope = EcasReadPredicate.forUser(user);
    predicates.add(scope.sql());
    parameters.addAll(scope.parameters());
    if (!search.orgUnitNumbers().isEmpty()) {
      predicates.add("""
          EXISTS (SELECT 1 FROM ORG_UNIT selected_org
                   WHERE selected_org.ORG_UNIT_NO IN (%s)
                     AND (UPPER(SUBSTR(selected_org.ORG_UNIT_CODE, 1, 1)) IN ('H', 'T')
                       OR (UPPER(SUBSTR(selected_org.ORG_UNIT_CODE, 1, 1)) = 'R'
                           AND record_scope.ROLLUP_REGION_NO = selected_org.ORG_UNIT_NO
                           AND LENGTH(record_scope.ADMIN_DISTRICT_CODE) = 3)
                       OR (UPPER(SUBSTR(selected_org.ORG_UNIT_CODE, 1, 1)) NOT IN ('H', 'T', 'R')
                           AND record_scope.ADMIN_DISTRICT = selected_org.ORG_UNIT_NO)))
          """.formatted(placeholders(search.orgUnitNumbers().size())).strip());
      parameters.addAll(search.orgUnitNumbers());
    }
    exact(predicates, parameters, "CLIENT_NUMBER", search.clientNumber());
    exact(predicates, parameters, "CLIENT_LOCN_CODE", search.clientLocationCode());
    if (search.certified() != null) {
      predicates.add(search.certified()
          ? "record_scope.CERTIFIED_FLAG = 'Y'"
          : "(record_scope.CERTIFIED_FLAG = 'N' OR record_scope.CERTIFIED_FLAG IS NULL)");
    }
    // A direct ID skips the other filters but keeps the security and certified checks.
    if (search.ecasId() != null) {
      exact(predicates, parameters, "ECAS_ID", search.ecasId());
      predicates.add("record_scope.PRIMARY_MARK_IND = 'Y'");
    } else {
      exact(predicates, parameters, "APPRAISAL_METHOD_CODE",
          search.appraisalMethod() == null ? null : search.appraisalMethod().name());
      exact(predicates, parameters, "LICENCE", search.licence());
      exact(predicates, parameters, "CUTTING_PERMIT", search.cuttingPermit());
      if (search.timberMark() == null) {
        predicates.add("record_scope.PRIMARY_MARK_IND = 'Y'");
      } else {
        exact(predicates, parameters, "TIMBER_MARK", search.timberMark());
      }
      exact(predicates, parameters, "APPRAISAL_CATEGORY_CODE", search.appraisalCategoryCode());
      exact(predicates, parameters, "REAPPRAISAL_REASON_CODE", search.reappraisalReasonCode());
      if (search.bctsFunded() != null) {
        exact(predicates, parameters, "SB_FUNDED_IND", search.bctsFunded() ? "Y" : "N");
      }
      if (!search.statusCodes().isEmpty()) {
        if (search.statusDates() != null && search.statusDates().hasBound()) {
          List<String> actions = search.statusCodes().stream().flatMap(code -> actions(code).stream()).toList();
          List<String> audit = new ArrayList<>();
          audit.add("audit_event.ECAS_ID = record_scope.ECAS_ID");
          audit.add("audit_event.ECAS_ACTION_CODE IN (" + placeholders(actions.size()) + ")");
          parameters.addAll(actions);
          dates(audit, parameters, "audit_event.ENTRY_TIMESTAMP", search.statusDates());
          predicates.add("EXISTS (SELECT 1 FROM ECAS_AUDIT_EVENT audit_event WHERE " + String.join(" AND ", audit) + ")");
        } else {
          predicates.add("record_scope.STATUS_CODE IN (" + placeholders(search.statusCodes().size()) + ")");
          parameters.addAll(search.statusCodes());
        }
      }
      if (search.workedOnByUserId() != null) {
        // The legacy inner join requires an audit row even when an ADS user column matches.
        predicates.add("""
            EXISTS (SELECT 1 FROM ECAS_AUDIT_EVENT worked
                     WHERE worked.ECAS_ID = record_scope.ECAS_ID
                       AND (record_scope.RPF_USER_ID = ? OR record_scope.SENT_BY_USER_ID = ?
                         OR record_scope.TRANSFER_BY_USER_ID = ? OR record_scope.ENTRY_USERID = ?
                         OR worked.ENTRY_USERID = ?))
            """.strip());
        parameters.addAll(Collections.nCopies(5, search.workedOnByUserId()));
      }
      exact(predicates, parameters, "FILE_TYPE_CODE", search.fileTypeCode());
      exact(predicates, parameters, "MGMT_UNIT_TYPE", search.managementUnitType());
      exact(predicates, parameters, "MGMT_UNIT_ID", search.managementUnitId());
      for (EcasInbox.DateType type : search.dateTypes()) {
        // ECAS05 applies FTA CP expiry only when both ends are supplied.
        if (type == EcasInbox.DateType.FCED
            && (search.dates() == null || search.dates().from() == null || search.dates().to() == null)) {
          continue;
        }
        String column = switch (type) {
          case EFFCTV -> "EFFECTIVE_DATE";
          case EXPRY -> "EXPIRY_DATE";
          case NTRY -> "ENTRY_TIMESTAMP";
          case LTMD -> "UPDATE_DATE";
          case STTS -> "STATUS_DATE";
          case FCED -> "FTA_CP_EXPIRY_DATE";
        };
        dates(predicates, parameters, "record_scope." + column, search.dates());
      }
    }
    return new EcasInboxPlan("(" + String.join(" AND ", predicates) + ")", parameters,
        sortColumn(search.sortBy()) + " " + search.sortDirection().name(), page);
  }

  private static List<String> actions(String status) {
    return switch (status) {
      case "RGN" -> List.of("STR", "RTR");
      case "DCL" -> List.of("CLD");
      case "DFT" -> List.of("ADD");
      case "FWD" -> List.of("FWR");
      case "RTN" -> List.of("RTD");
      case "RPL" -> List.of("REP");
      case "SUB", "SLD", "RCD", "CLR", "ACC", "APP", "CON", "BUP", "SWI", "STR", "GAS",
          "VER", "DTR", "UNC", "SCN", "CPC", "EE", "NAP", "NBS", "SEC" -> List.of(status);
      default -> throw new IllegalArgumentException("status has no legacy audit action mapping");
    };
  }

  private static void exact(List<String> predicates, List<String> parameters, String column, String value) {
    if (value != null) {
      predicates.add("record_scope." + column + " = ?");
      parameters.add(value);
    }
  }

  private static void dates(List<String> predicates, List<String> parameters, String column, DateRange range) {
    if (range == null) {
      return;
    }
    // Legacy bounds are inclusive at midnight; don't extend the upper date by a day.
    if (range.from() != null) {
      predicates.add(column + " >= TO_DATE(?, 'YYYY-MM-DD')");
      parameters.add(range.from().toString());
    }
    if (range.to() != null) {
      predicates.add(column + " <= TO_DATE(?, 'YYYY-MM-DD')");
      parameters.add(range.to().toString());
    }
  }

  private static String placeholders(int size) {
    return String.join(", ", Collections.nCopies(size, "?"));
  }

  private static String sortColumn(EcasInbox.SortField field) {
    return switch (field) {
      case ECAS_ID -> "ECAS_ID";
      case TIMBER_MARK -> "TIMBER_MARK";
      case LICENCE -> "LICENCE";
      case CLIENT_NAME -> "CLIENT_NAME";
      case STATUS -> "STATUS_DESCRIPTION";
      case STATUS_CHANGE_DATE -> "STATUS_DATE";
      case APPRAISAL_TYPE -> "APPRAISAL_TYPE";
      case EFFECTIVE_DATE -> "EFFECTIVE_DATE";
      case EXPIRY_DATE -> "EXPIRY_DATE";
      case SUBMITTED_DATE -> "LICENSEE_SUBMITTED_DATE";
      case DISTRICT_RECEIVED_DATE -> "DISTRICT_RECEIVED_DATE";
      case SENT_TO_REGION_DATE -> "SENT_TO_REGION_DATE";
      case UPDATE_DATE -> "UPDATE_DATE";
    };
  }

  public String sql() {
    return sql;
  }

  public List<String> parameters() {
    return parameters;
  }

  public String orderBy() {
    return orderBy;
  }

  public long firstRow() {
    return firstRow;
  }

  public long lastRow() {
    return lastRow;
  }
}
