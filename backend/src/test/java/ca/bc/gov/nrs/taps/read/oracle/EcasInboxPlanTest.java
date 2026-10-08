package ca.bc.gov.nrs.taps.read.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.taps.domain.AppraisalMethod;
import ca.bc.gov.nrs.taps.domain.DateRange;
import ca.bc.gov.nrs.taps.read.EcasInbox;
import ca.bc.gov.nrs.taps.security.FamRoleName;
import ca.bc.gov.nrs.taps.security.IdentityProvider;
import ca.bc.gov.nrs.taps.security.RoleGrant;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EcasInboxPlanTest {
  @Test
  void defaultSearchRequiresPrimaryMarkAndUsesBoundScope() {
    var plan = EcasInboxPlan.forUser(idir("TAPS_REGION_APPRAISER_REGION-CARIBOO"), new Filters().search(), 0);

    assertThat(plan.sql()).contains("record_scope.ROLLUP_REGION_CODE = ?", "record_scope.PRIMARY_MARK_IND = 'Y'");
    assertThat(plan.parameters()).containsExactly("RCB");
    assertThat(plan.orderBy()).isEqualTo("ECAS_ID DESC");
    assertThat(plan.firstRow()).isEqualTo(1);
    assertThat(plan.lastRow()).isEqualTo(100);
  }

  @Test
  void selectedHeadquartersAndClientRemainAnIntersectionWithFAMAuthority() {
    var filters = new Filters();
    filters.orgs = List.of("10", "20");
    filters.client = "42";
    filters.location = "1";
    var plan = EcasInboxPlan.forUser(idir("TAPS_VIEWER_DISTRICT-DZZ"), filters.search(), 1);

    assertThat(plan.sql()).contains("record_scope.ADMIN_DISTRICT_CODE = ?", "record_scope.STATUS_CODE <> ?",
        "AND EXISTS (SELECT 1 FROM ORG_UNIT selected_org", "selected_org.ORG_UNIT_NO IN (?, ?)",
        "IN ('H', 'T')", "record_scope.ROLLUP_REGION_NO = selected_org.ORG_UNIT_NO",
        "LENGTH(record_scope.ADMIN_DISTRICT_CODE) = 3", "record_scope.CLIENT_NUMBER = ?");
    assertThat(plan.parameters()).containsExactly("DZZ", "DFT", "10", "20", "00000042", "01");
    assertThat(plan.firstRow()).isEqualTo(101);
    assertThat(plan.lastRow()).isEqualTo(200);
  }

  @Test
  void everyOrdinaryExactFilterIsBoundAndSpecificMarkReplacesPrimaryOnly() {
    var filters = new Filters();
    filters.method = AppraisalMethod.C;
    filters.licence = "a00001";
    filters.permit = "1";
    filters.mark = "x_%'";
    filters.category = "x' OR 1=1--";
    filters.reason = "RED";
    filters.bcts = false;
    filters.file = "A01";
    filters.managementType = "T";
    filters.managementId = "01";
    var plan = EcasInboxPlan.forUser(idir("TAPS_ADMIN"), filters.search(), 0);

    assertThat(plan.parameters()).containsExactly("C", "A00001", "1", "X_%'", "x' OR 1=1--", "RED", "N", "A01", "T", "01");
    assertThat(plan.sql()).contains("record_scope.APPRAISAL_METHOD_CODE = ?", "record_scope.LICENCE = ?",
        "record_scope.CUTTING_PERMIT = ?", "record_scope.TIMBER_MARK = ?", "record_scope.APPRAISAL_CATEGORY_CODE = ?",
        "record_scope.REAPPRAISAL_REASON_CODE = ?", "record_scope.SB_FUNDED_IND = ?", "record_scope.FILE_TYPE_CODE = ?",
        "record_scope.MGMT_UNIT_TYPE = ?", "record_scope.MGMT_UNIT_ID = ?")
        .doesNotContain("PRIMARY_MARK_IND", "X_%'", "x' OR 1=1--");
    assertThat(plan.sql().chars().filter(c -> c == '?').count()).isEqualTo(plan.parameters().size());
  }

  @ParameterizedTest
  @EnumSource(EcasInbox.Mode.class)
  void directIdPreservesSecuritySelectionsAndCertifiedButBypassesOrdinaryFilters(EcasInbox.Mode mode) {
    var filters = new Filters();
    filters.mode = mode;
    filters.id = "000123";
    filters.client = "42";
    filters.orgs = List.of("10");
    filters.certified = false;
    filters.licence = "a00001";
    filters.mark = "zz0001";
    filters.statuses = List.of("UNMAPPED");
    filters.statusDates = new DateRange(LocalDate.of(2026, 1, 1), null);
    filters.worked = "some-user";
    var plan = EcasInboxPlan.forUser(idir("TAPS_VIEWER_DISTRICT-DZZ"), filters.search(), 0);

    assertThat(plan.parameters()).containsExactly("DZZ", "DFT", "10", "00000042", "123");
    assertThat(plan.sql()).contains("record_scope.STATUS_CODE <> ?", "record_scope.CLIENT_NUMBER = ?",
        "(record_scope.CERTIFIED_FLAG = 'N' OR record_scope.CERTIFIED_FLAG IS NULL)",
        "record_scope.ECAS_ID = ?", "record_scope.PRIMARY_MARK_IND = 'Y'")
        .doesNotContain("record_scope.LICENCE =", "record_scope.TIMBER_MARK =", "ECAS_AUDIT_EVENT");
  }

  @Test
  void certifiedTrueAndFalseHaveDifferentNullSemanticsThanBcts() {
    var filters = new Filters();
    filters.certified = true;
    filters.bcts = false;
    var plan = EcasInboxPlan.forUser(idir("TAPS_ADMIN"), filters.search(), 0);

    assertThat(plan.sql()).contains("record_scope.CERTIFIED_FLAG = 'Y'", "record_scope.SB_FUNDED_IND = ?")
        .doesNotContain("SB_FUNDED_IND IS NULL", "CERTIFIED_FLAG IS NULL");
    assertThat(plan.parameters()).containsExactly("N");
  }

  @Test
  void currentStatusesUseCurrentAdsFieldWithoutAuditJoin() {
    var filters = new Filters();
    filters.statuses = List.of("RGN", "DCL");
    var plan = EcasInboxPlan.forUser(idir("TAPS_ADMIN"), filters.search(), 0);

    assertThat(plan.sql()).contains("record_scope.STATUS_CODE IN (?, ?)").doesNotContain("ECAS_AUDIT_EVENT");
    assertThat(plan.parameters()).containsExactly("RGN", "DCL");
  }

  @Test
  void statusDatesTranslateLegacyActionsAndSearchHistoryInsteadOfCurrentStatus() {
    var filters = new Filters();
    filters.statuses = List.of("RGN", "DCL", "DFT", "FWD", "RTN", "RPL", "SUB");
    filters.statusDates = new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
    var plan = EcasInboxPlan.forUser(idir("TAPS_ADMIN"), filters.search(), 0);

    assertThat(plan.sql()).contains("EXISTS (SELECT 1 FROM ECAS_AUDIT_EVENT audit_event",
        "audit_event.ECAS_ID = record_scope.ECAS_ID", "audit_event.ENTRY_TIMESTAMP >= TO_DATE(?, 'YYYY-MM-DD')",
        "audit_event.ENTRY_TIMESTAMP <= TO_DATE(?, 'YYYY-MM-DD')")
        .doesNotContain("record_scope.STATUS_CODE IN", "+ 1");
    assertThat(plan.parameters()).containsExactly("STR", "RTR", "CLD", "ADD", "FWR", "RTD", "REP", "SUB", "2026-01-01", "2026-01-31");
  }

  @Test
  void unmappedHistoricStatusFailsInsteadOfGuessingAction() {
    var filters = new Filters();
    filters.statuses = List.of("NEW");
    filters.statusDates = new DateRange(LocalDate.of(2026, 1, 1), null);

    assertThatThrownBy(() -> EcasInboxPlan.forUser(idir("TAPS_ADMIN"), filters.search(), 0))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("audit action mapping");
  }

  @Test
  void workedOnRequiresAnAuditRowAndPreservesAllFiveUserPaths() {
    var filters = new Filters();
    filters.worked = "A' OR 1=1--";
    var plan = EcasInboxPlan.forUser(idir("TAPS_ADMIN"), filters.search(), 0);

    assertThat(plan.sql()).contains("EXISTS (SELECT 1 FROM ECAS_AUDIT_EVENT worked",
        "worked.ECAS_ID = record_scope.ECAS_ID", "record_scope.RPF_USER_ID = ?", "record_scope.SENT_BY_USER_ID = ?",
        "record_scope.TRANSFER_BY_USER_ID = ?", "record_scope.ENTRY_USERID = ?", "worked.ENTRY_USERID = ?")
        .doesNotContain(filters.worked);
    assertThat(plan.parameters()).containsExactlyElementsOf(java.util.Collections.nCopies(5, filters.worked));
  }

  @Test
  void workedOnUserIdIsNormalizedBeforeEveryBoundMatch() {
    var filters = new Filters();
    filters.worked = " idir\\SyntheticUser ";
    var mixedCase = EcasInboxPlan.forUser(idir("TAPS_ADMIN"), filters.search(), 0);
    filters.worked = "IDIR\\SYNTHETICUSER";
    var upperCase = EcasInboxPlan.forUser(idir("TAPS_ADMIN"), filters.search(), 0);

    assertThat(mixedCase.sql()).isEqualTo(upperCase.sql());
    assertThat(mixedCase.parameters()).isEqualTo(upperCase.parameters())
        .containsExactlyElementsOf(java.util.Collections.nCopies(5, "IDIR\\SYNTHETICUSER"));
  }

  @Test
  void blankWorkedOnUserIdAddsNoActorFilterAndOverlongValueIsRejected() {
    var filters = new Filters();
    filters.worked = "   ";
    var plan = EcasInboxPlan.forUser(idir("TAPS_ADMIN"), filters.search(), 0);
    assertThat(plan.sql()).doesNotContain("ECAS_AUDIT_EVENT worked");
    assertThat(plan.parameters()).isEmpty();

    filters.worked = "IDIR\\" + "x".repeat(26);
    assertThatThrownBy(filters::search).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("workedOnByUserId exceeds 30 characters");
  }

  @Test
  void selectedDateTypesAreAnIntersectionWithInclusiveMidnightBounds() {
    var filters = new Filters();
    filters.dateTypes = List.of(EcasInbox.DateType.values());
    filters.dates = new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
    var plan = EcasInboxPlan.forUser(idir("TAPS_ADMIN"), filters.search(), 0);

    for (String column : List.of("EFFECTIVE_DATE", "EXPIRY_DATE", "ENTRY_TIMESTAMP", "UPDATE_DATE", "STATUS_DATE", "FTA_CP_EXPIRY_DATE")) {
      assertThat(plan.sql()).contains("record_scope." + column + " >= TO_DATE(?, 'YYYY-MM-DD')",
          "record_scope." + column + " <= TO_DATE(?, 'YYYY-MM-DD')");
    }
    assertThat(plan.parameters()).hasSize(12);
    assertThat(plan.sql()).doesNotContain("TRUNC(", "+ 1");
  }

  @Test
  void openEndedFtaExpiryIsIgnoredLikeLegacyWhileOtherDatesStillApply() {
    var filters = new Filters();
    filters.dateTypes = List.of(EcasInbox.DateType.FCED, EcasInbox.DateType.STTS);
    filters.dates = new DateRange(null, LocalDate.of(2026, 1, 31));
    var plan = EcasInboxPlan.forUser(idir("TAPS_ADMIN"), filters.search(), 0);

    assertThat(plan.sql()).contains("record_scope.STATUS_DATE <= TO_DATE(?, 'YYYY-MM-DD')")
        .doesNotContain("FTA_CP_EXPIRY_DATE", ">=");
    assertThat(plan.parameters()).containsExactly("2026-01-31");
  }

  @ParameterizedTest
  @EnumSource(EcasInbox.SortField.class)
  void everySortUsesOnlyEnumControlledSql(EcasInbox.SortField field) {
    var filters = new Filters();
    filters.sort = field;
    filters.direction = EcasInbox.SortDirection.ASC;
    var plan = EcasInboxPlan.forUser(idir("TAPS_ADMIN"), filters.search(), 0);

    assertThat(plan.orderBy()).matches("[A-Z_]+ ASC");
    assertThat(plan.parameters()).isEmpty();
  }

  @Test
  void modeAndPageAreValidatedBeforeAnyQueryCouldRun() {
    var filters = new Filters();
    filters.mode = EcasInbox.Mode.MY_TO_DO;
    assertThatThrownBy(() -> EcasInboxPlan.forUser(idir("TAPS_ADMIN"), filters.search(), 0))
        .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("assignment mapping");
    assertThatThrownBy(() -> EcasInboxPlan.forUser(idir("TAPS_ADMIN"), new Filters().search(), -1))
        .isInstanceOf(IllegalArgumentException.class);
    var plan = EcasInboxPlan.forUser(idir("TAPS_ADMIN"), new Filters().search(), Integer.MAX_VALUE);
    assertThat(plan.firstRow()).isEqualTo(214_748_364_701L);
    assertThat(plan.lastRow()).isEqualTo(214_748_364_800L);
  }

  static TapsUser idir(String... roles) {
    return new TapsUser("synthetic", "Synthetic user", null, IdentityProvider.IDIR, null,
        Arrays.stream(roles).map(role -> RoleGrant.accept(FamRoleName.parse(role), IdentityProvider.IDIR).orElseThrow()).toList());
  }

  static final class Filters {
    EcasInbox.Mode mode = EcasInbox.Mode.ALL_SUBMISSIONS;
    AppraisalMethod method;
    String id, licence, permit, mark, client, location, category, reason, file, managementType, managementId, worked;
    List<String> orgs = List.of(), statuses = List.of();
    DateRange statusDates, dates;
    Boolean bcts, certified;
    List<EcasInbox.DateType> dateTypes = List.of();
    EcasInbox.SortField sort;
    EcasInbox.SortDirection direction;

    EcasInbox.Search search() {
      return new EcasInbox.Search(mode, method, id, licence, permit, mark, client, location, orgs, category,
          reason, statuses, statusDates, bcts, certified, file, managementType, managementId, worked,
          dateTypes, dates, sort, direction);
    }
  }
}
