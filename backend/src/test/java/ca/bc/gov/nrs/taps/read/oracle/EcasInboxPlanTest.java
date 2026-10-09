package ca.bc.gov.nrs.taps.read.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.taps.domain.AppraisalMethod;
import ca.bc.gov.nrs.taps.domain.DateRange;
import ca.bc.gov.nrs.taps.read.EcasInbox;
import ca.bc.gov.nrs.taps.security.FamRoleName;
import ca.bc.gov.nrs.taps.security.IdentityProvider;
import ca.bc.gov.nrs.taps.security.RoleGrant;
import ca.bc.gov.nrs.taps.security.TapsRole;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;

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
    if (mode == EcasInbox.Mode.MY_TO_DO) {
      assertThat(plan.sql()).contains("record_scope.STATUS_CODE IN ('SUB','RCD','RTN')");
    } else {
      assertThat(plan.sql()).doesNotContain("record_scope.STATUS_CODE IN");
    }
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
    assertThatThrownBy(() -> EcasInboxPlan.forUser(idir("TAPS_HEADQUARTERS"), filters.search(), 0))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("verified legacy username");
    assertThatThrownBy(() -> EcasInboxPlan.forUser(idir("TAPS_ADMIN"), new Filters().search(), -1))
        .isInstanceOf(IllegalArgumentException.class);
    var plan = EcasInboxPlan.forUser(idir("TAPS_ADMIN"), new Filters().search(), Integer.MAX_VALUE);
    assertThat(plan.firstRow()).isEqualTo(214_748_364_701L);
    assertThat(plan.lastRow()).isEqualTo(214_748_364_800L);
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "TAPS_ADMIN|ACC,APP,BUP,CLR,DCL,DFT,FWD,GAS,RCD,RTN,SLD,RGN,SUB,SWI,SCN,VER,DTR,UNC|false",
      "TAPS_HEADQUARTERS|NONE|true",
      "TAPS_REGION_APPRAISER|RGN,SWI,SCN,CLR,VER,DTR,SLD,UNC|true",
      "TAPS_DISTRICT_APPRAISER|SUB,RCD,RTN,SCN|true",
      "TAPS_REGION_CLERK|RGN,SWI|false",
      "TAPS_VIEWER|SUB,RCD,RTN|false",
      "TAPS_BCTS|DFT,CLR,BUP,VER,DCL|false",
      "TAPS_BCTS_SUBMITTER|DFT,CLR,BUP,VER,DCL|false",
      "TAPS_LICENSEE|DFT,CLR,DCL|false",
      "TAPS_LICENSEE_SUBMITTER|DFT,CLR,DCL|false",
      "TAPS_LICENSEE_VIEWER|NONE|false"
  })
  void eachRoleUsesItsQueueStatusesAndAssignmentRequirement(TapsRole role, String statuses,
      boolean assigned) {
    IdentityProvider provider = role.identityProviders().contains(IdentityProvider.IDIR)
        ? IdentityProvider.IDIR : IdentityProvider.BCEID_BUSINESS;
    FamRoleName.Scope scope = role.scopeType() == null ? null : new FamRoleName.Scope(role.scopeType(),
        switch (role.scopeType()) {
          case FamRoleName.REGION -> "CARIBOO";
          case FamRoleName.DISTRICT -> "DZZ";
          default -> "00000001";
        });
    var user = new TapsUser("audit", "Synthetic", null, provider, null,
        List.of(new RoleGrant(role, scope)), provider.auditPrefix() + "\\SYNTHETIC");
    var filters = new Filters();
    filters.mode = EcasInbox.Mode.MY_TO_DO;
    var plan = EcasInboxPlan.forUser(user, filters.search(), 0);
    if (statuses.equals("NONE")) {
      assertThat(plan.sql()).doesNotContain("record_scope.STATUS_CODE IN");
    } else {
      assertThat(plan.sql()).contains("record_scope.STATUS_CODE IN ('" + statuses.replace(",", "','") + "')");
    }
    assertThat(plan.sql().contains("ADS_ASSIGNED_TO_USER")).isEqualTo(assigned);
    if (assigned) {
      assertThat(plan.sql()).contains("assigned.ECAS_ID = record_scope.ECAS_ID AND assigned.USER_ID = ?")
          .doesNotContain(user.legacyAccount(), "JOIN ADS_ASSIGNED_TO_USER");
      assertThat(plan.parameters().getLast()).isEqualTo(user.legacyAccount());
    }
    assertThat(plan.sql().chars().filter(c -> c == '?').count()).isEqualTo(plan.parameters().size());
  }

  @Test
  void mixedGrantsKeepQueueAndAssignmentInsideTheSameScopeAlternative() {
    var filters = new Filters();
    filters.mode = EcasInbox.Mode.MY_TO_DO;
    var user = assignedIdir("TAPS_REGION_APPRAISER_REGION-CARIBOO", "TAPS_VIEWER_DISTRICT-DZZ");
    var plan = EcasInboxPlan.forUser(user, filters.search(), 0);
    String[] alternatives = plan.sql().split(" OR ");
    assertThat(alternatives).hasSize(2);
    assertThat(alternatives[0]).contains("record_scope.ROLLUP_REGION_CODE = ?",
        "record_scope.STATUS_CODE IN ('RGN','SWI','SCN','CLR','VER','DTR','SLD','UNC')",
        "ADS_ASSIGNED_TO_USER", "record_scope.LICENCE IS NOT NULL")
        .doesNotContain("record_scope.ADMIN_DISTRICT_CODE", "'SUB','RCD','RTN'");
    assertThat(alternatives[1]).contains("record_scope.ADMIN_DISTRICT_CODE = ?",
        "record_scope.STATUS_CODE IN ('SUB','RCD','RTN')", "record_scope.STATUS_CODE <> ?")
        .doesNotContain("ADS_ASSIGNED_TO_USER", "record_scope.ROLLUP_REGION_CODE");
    assertThat(plan.parameters()).containsExactly("RCB", "IDIR\\SYNTHETIC", "DZZ", "DFT");
  }

  @Test
  void requestActorFiltersNeverReplaceTheCallerAssignmentIdentity() {
    var filters = new Filters();
    filters.mode = EcasInbox.Mode.MY_TO_DO;
    filters.worked = "IDIR\\OTHER";
    var plan = EcasInboxPlan.forUser(assignedIdir("TAPS_HEADQUARTERS"), filters.search(), 0);
    assertThat(plan.parameters()).containsExactly("IDIR\\SYNTHETIC", "IDIR\\OTHER", "IDIR\\OTHER",
        "IDIR\\OTHER", "IDIR\\OTHER", "IDIR\\OTHER");
    assertThat(plan.sql()).contains("assigned.USER_ID = ?", "worked.ENTRY_USERID = ?")
        .doesNotContain("IDIR\\OTHER", "IDIR\\SYNTHETIC");
  }

  @Test
  void oneMissingAssignedIdentityRejectsTheWholeQueueInsteadOfOmittingThatGrant() {
    var filters = new Filters();
    filters.mode = EcasInbox.Mode.MY_TO_DO;
    var user = idir("TAPS_HEADQUARTERS", "TAPS_VIEWER_DISTRICT-DZZ");
    assertThat(user.ecasMyToDoAvailable()).isFalse();
    assertThatThrownBy(() -> EcasInboxPlan.forUser(user, filters.search(), 0))
        .isInstanceOf(IllegalArgumentException.class);
    filters.mode = EcasInbox.Mode.ALL_SUBMISSIONS;
    assertThat(EcasInboxPlan.forUser(user, filters.search(), 0).sql()).doesNotContain("ADS_ASSIGNED_TO_USER");
    filters.mode = EcasInbox.Mode.MY_TO_DO;
    filters.id = "123";
    assertThat(EcasInboxPlan.forUser(user, filters.search(), 0).sql()).doesNotContain("ADS_ASSIGNED_TO_USER");
  }

  @Test
  void regionBctsOmissionUsesMatchedPfuAndPreservesOracleNullSemantics() {
    var filters = new Filters();
    filters.mode = EcasInbox.Mode.MY_TO_DO;
    var user = assignedIdir("TAPS_REGION_APPRAISER_REGION-CARIBOO");
    var omitted = EcasInboxPlan.forUser(user, filters.search(), 0);
    assertThat(omitted.sql()).contains("record_scope.LICENCE IS NOT NULL",
        "NOT (record_scope.STATUS_CODE IN ('VER','DTR') AND record_scope.SB_FUNDED_IND = 'Y')")
        .doesNotContain("NVL", "COALESCE", "SB_FUNDED_IND IS NULL");
    for (boolean funded : List.of(true, false)) {
      filters.bcts = funded;
      var explicit = EcasInboxPlan.forUser(user, filters.search(), 0);
      assertThat(explicit.sql()).contains("record_scope.SB_FUNDED_IND = ?",
          "NOT (record_scope.STATUS_CODE IN ('VER','DTR') AND record_scope.SB_FUNDED_IND = 'Y')")
          .doesNotContain("record_scope.LICENCE IS NOT NULL", "SB_FUNDED_IND IS NULL");
      assertThat(explicit.parameters()).containsExactly("RCB", "IDIR\\SYNTHETIC", funded ? "Y" : "N");
    }
    filters.id = "123";
    var direct = EcasInboxPlan.forUser(user, filters.search(), 0);
    assertThat(direct.sql()).doesNotContain("ADS_ASSIGNED_TO_USER", "record_scope.STATUS_CODE IN",
        "record_scope.LICENCE IS NOT NULL", "SB_FUNDED_IND");
    assertThat(direct.parameters()).containsExactly("RCB", "123");
  }

  @Test
  void directIdRetainsRegionViewOnlyStatusesButNeverChangesReferenceAuthorization() {
    var user = idir("TAPS_REGION_CLERK_REGION-CARIBOO");
    var filters = new Filters();
    filters.mode = EcasInbox.Mode.MY_TO_DO;
    filters.id = "123";
    assertThat(EcasInboxPlan.forUser(user, filters.search(), 0).sql())
        .contains("record_scope.STATUS_CODE IN ('RGN','SWI')").doesNotContain("ADS_ASSIGNED_TO_USER");
    assertThat(EcasReadPredicate.forUser(user).sql()).doesNotContain("record_scope.STATUS_CODE IN", "ADS_ASSIGNED_TO_USER");
  }

  static TapsUser assignedIdir(String... roles) {
    TapsUser user = idir(roles);
    return new TapsUser(user.userId(), user.displayName(), user.email(), user.identityProvider(),
        user.businessName(), user.grants(), "IDIR\\SYNTHETIC");
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
