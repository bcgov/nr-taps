package ca.bc.gov.nrs.taps.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.taps.TapsApplication;
import ca.bc.gov.nrs.taps.domain.DateRange;
import ca.bc.gov.nrs.taps.read.EcasInbox;
import ca.bc.gov.nrs.taps.read.EcasReference;
import ca.bc.gov.nrs.taps.read.EffectiveCode;
import ca.bc.gov.nrs.taps.read.GasAppraisal;
import ca.bc.gov.nrs.taps.read.oracle.OracleAppraisedSummary;
import ca.bc.gov.nrs.taps.read.oracle.OracleCodeLists;
import ca.bc.gov.nrs.taps.read.oracle.OracleEcasInbox;
import ca.bc.gov.nrs.taps.read.oracle.OracleEcasReference;
import ca.bc.gov.nrs.taps.read.oracle.OracleFtaLicenceInformation;
import ca.bc.gov.nrs.taps.read.oracle.OracleGasSearch;
import ca.bc.gov.nrs.taps.read.oracle.OracleLicenceMarks;
import ca.bc.gov.nrs.taps.read.oracle.OracleOtherWorksheetSummary;
import ca.bc.gov.nrs.taps.security.FamRoleName;
import ca.bc.gov.nrs.taps.security.IdentityProvider;
import ca.bc.gov.nrs.taps.security.RoleGrant;
import ca.bc.gov.nrs.taps.security.TapsUser;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports.Binding;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * Runs every read adapter against a disposable Oracle with synthetic data ({@code mvn -Poracle-it
 * verify}). The fixture schema is a minimal stand-in, not a copy of production.
 */
@Testcontainers
class OracleReadIT {
  @Container
  static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart")
      .withUsername("taps_fixture")
      .withPassword("TapsTest" + UUID.randomUUID().toString().replace("-", "").substring(0, 20))
      .withStartupTimeout(Duration.ofMinutes(4))
      .withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
          new PortBinding(Binding.bindIp("127.0.0.1"), new ExposedPort(1521))));

  private static final TapsUser ADMIN = idir("TAPS_ADMIN");
  private static final TapsUser CARIBOO = idir("TAPS_REGION_APPRAISER_REGION-CARIBOO");
  private static final TapsUser OMINECA = idir("TAPS_REGION_APPRAISER_REGION-OMINECA");
  private SingleConnectionDataSource connection;
  private JdbcTemplate jdbc;

  @BeforeAll
  static void createSyntheticSchema() throws Exception {
    var dataSource = new DriverManagerDataSource(ORACLE.getJdbcUrl(), ORACLE.getUsername(), ORACLE.getPassword());
    new ResourceDatabasePopulator(new ClassPathResource("oracle/schema.sql"), new ClassPathResource("oracle/attachments-schema.sql"), new ClassPathResource("oracle/audit-schema.sql")).execute(dataSource);
    String helper = new ClassPathResource("oracle/client-name-function.sql").getContentAsString(StandardCharsets.UTF_8);
    new JdbcTemplate(dataSource).execute(helper);
    new ResourceDatabasePopulator(new ClassPathResource("oracle/seed.sql"), new ClassPathResource("oracle/attachments-seed.sql"), new ClassPathResource("oracle/audit-seed.sql")).execute(dataSource);
  }

  @BeforeEach
  void transactionalFixture() {
    connection = new SingleConnectionDataSource(ORACLE.getJdbcUrl(), ORACLE.getUsername(), ORACLE.getPassword(), true);
    connection.setAutoCommit(false);
    jdbc = new JdbcTemplate(connection);
    jdbc.setQueryTimeout(20);
  }

  @AfterEach
  void rollbackFixtureChanges() throws Exception {
    if (connection != null) {
      try {
        connection.getConnection().rollback();
      } finally {
        connection.destroy();
      }
    }
  }

  @Test
  void effectiveCodeListsExecuteOnOracleAndRetainTimestampAndNullValues() {
    var lists = new OracleCodeLists(jdbc);
    assertThat(lists.appraisalMethods()).extracting(EffectiveCode::code).containsExactly("C", "I");
    assertThat(lists.appraisalMethods().getFirst().updateTimestamp()).isNull();
    assertThat(lists.appraisalMethods().getLast().updateTimestamp()).isEqualTo(LocalDateTime.of(2026, 1, 1, 12, 34, 56));
    assertThat(lists.appraisalStatuses()).extracting(EffectiveCode::code).containsExactly("CON", "DFT", "SCN");
    assertThat(lists.rateAdjustmentTypes()).extracting(EffectiveCode::code).containsExactly("A");
  }

  @Test
  void auditHistoryCommentsAndChangesStayBoundToTheirSubmission() { EcasAuditOracleAssertions.verify(jdbc); }

  @Test
  void attachmentMetadataPreservesDocumentPersonaAndMethodRules() { EcasAttachmentsOracleAssertions.verify(jdbc); }

  @Test
  void organizationChoicesUseFAMGrantsWhileHistoricalSearchCodesRemainSelectable() {
    var organizations = new ca.bc.gov.nrs.taps.read.oracle.OracleEcasOrganizations(jdbc);
    assertThat(organizations.forUser(CARIBOO)).extracting(code -> code.code()).containsExactly("10", "1");
    assertThat(organizations.forUser(OMINECA)).extracting(code -> code.code()).containsExactly("20", "2");
    assertThat(organizations.forUser(user(IdentityProvider.BCEID_BUSINESS, "TAPS_LICENSEE_VIEWER_FOREST_CLIENT-00000001"))).isEmpty();
    assertThat(organizations.forUser(idir())).isEmpty();
    assertThat(organizations.forUser(ADMIN)).extracting(code -> code.code()).containsExactly("10", "20", "1", "2");
    jdbc.update("INSERT INTO ORG_UNIT VALUES (11, 'SCA', 'Synthetic subordinate unit', 1, 10, DATE '2000-01-01', DATE '9999-12-31')");
    assertThat(organizations.forUser(CARIBOO)).extracting(code -> code.code()).containsExactly("10", "1", "11");
    assertThat(organizations.forUser(ADMIN)).extracting(code -> code.code()).doesNotContain("11");
    assertThat(organizations.forUser(idir("TAPS_ADMIN", "TAPS_REGION_APPRAISER_REGION-CARIBOO")))
        .extracting(code -> code.code()).contains("11");
    jdbc.update("UPDATE ORG_UNIT SET EXPIRY_DATE=DATE '2001-01-01' WHERE ORG_UNIT_NO=10");
    assertThat(organizations.forUser(CARIBOO)).extracting(code -> code.code()).containsExactly("1", "11");
    jdbc.update("INSERT INTO REAPPRAISAL_REASON_CODE VALUES ('OLD', 'Synthetic historic reason', DATE '2000-01-01', DATE '2001-01-01')");
    var codes = new OracleCodeLists(jdbc);
    assertThat(codes.ecasAppraisalCategories()).extracting(code -> code.code()).containsExactly("N", "R");
    assertThat(codes.ecasFileTypes()).extracting(code -> code.code()).containsExactly("A01");
    assertThat(codes.ecasReappraisalReasons()).extracting(code -> code.code()).containsExactly("CHG", "OLD");
    assertThat(codes.ecasReappraisalReasons().getLast().active()).isFalse();
  }

  @Test
  void allFamilySearchAppliesEachGrantBeforeCountAndRetainsMultipleMarks() {
    var reader = new OracleGasSearch(jdbc);
    var page = reader.search(CARIBOO, gas(null, null, 0));
    assertThat(page.total()).isEqualTo(4);
    assertThat(page.items()).extracting(item -> item.key().type()).containsExactly(
        GasAppraisal.WorksheetType.NON_APPRAISED, GasAppraisal.WorksheetType.APPRAISED,
        GasAppraisal.WorksheetType.APPRAISED, GasAppraisal.WorksheetType.HISTORIC);
    assertThat(page.items()).filteredOn(item -> item.key().type() == GasAppraisal.WorksheetType.APPRAISED)
        .extracting(GasAppraisal.Item::timberMark).containsExactly("AA0001", "AA0002");
    assertThat(reader.search(OMINECA, gas(null, null, 0)).total()).isEqualTo(3);
    assertThat(reader.search(idir("TAPS_REGION_APPRAISER_REGION-CARIBOO", "TAPS_VIEWER_DISTRICT-DOM"),
        gas(null, null, 0)).total()).isEqualTo(4);
    assertThat(reader.search(idir("TAPS_REGION_APPRAISER_REGION-CARIBOO", "TAPS_REGION_APPRAISER_REGION-OMINECA"),
        gas(null, null, 0)).total()).isEqualTo(7);
    assertThat(reader.search(idir(), gas(null, null, 0)).total()).isZero();
    assertThat(reader.search(CARIBOO, gas("A00002", null, 0)).total()).isZero();
    assertThat(reader.search(CARIBOO, gas("A00001", "AA0002", 0)).total()).isEqualTo(1);
    assertThat(reader.search(CARIBOO, gas("x_%'", null, 0)).total()).isZero();
    assertThat(reader.search(CARIBOO, gas(null, null, 9)).items()).isEmpty();
    assertThat(reader.search(CARIBOO, gas(null, null, 9)).total()).isEqualTo(4);
  }

  @Test
  void ecasStatusChoicesRetainInactiveRowsWhileGasListsRemainEffectiveOnly() {
    jdbc.update("INSERT INTO APPRAISAL_STATUS_CODE VALUES ('OLD', 'Expired', DATE '2000-01-01', DATE '2001-01-01', NULL)");
    jdbc.update("INSERT INTO APPRAISAL_STATUS_CODE VALUES ('FUT', 'Future', DATE '9998-01-01', DATE '9999-01-01', NULL)");
    jdbc.update("INSERT INTO APPRAISAL_STATUS_CODE VALUES ('BND', 'Expiry day', DATE '2000-01-01', TRUNC(SYSDATE), NULL)");
    var lists = new OracleCodeLists(jdbc);
    assertThat(lists.ecasAppraisalStatuses()).extracting(code -> code.code())
        .containsExactly("BND", "CON", "DFT", "FUT", "OLD", "SCN");
    assertThat(lists.ecasAppraisalStatuses()).filteredOn(code -> List.of("BND", "OLD", "FUT").contains(code.code()))
        .allMatch(code -> !code.active());
    assertThat(lists.ecasAppraisalStatuses()).filteredOn(code -> code.code().equals("CON")).allMatch(code -> code.active());
    assertThat(lists.appraisalStatuses()).extracting(EffectiveCode::code).doesNotContain("OLD", "FUT");
  }

  @Test
  void gasPaginationIsStableAcrossNonEmptyPagesAndExcludesInactiveOrDraftRows() {
    for (int index = 0; index < 12; index++) {
      jdbc.update("INSERT INTO APPRAISED_WORKSHEET VALUES (?, 1001, 'NEW', NULL)", 500 + index);
    }
    var reader = new OracleGasSearch(jdbc);
    var first = reader.search(CARIBOO, gas(null, null, 0));
    var second = reader.search(CARIBOO, gas(null, null, 1));
    var third = reader.search(CARIBOO, gas(null, null, 2));
    assertThat(first.total()).isEqualTo(28);
    assertThat(first.items()).hasSize(10).doesNotContainAnyElementsOf(second.items());
    assertThat(second.items()).hasSize(10).doesNotContainAnyElementsOf(third.items());
    assertThat(third.items()).hasSize(8);
    assertThat(reader.search(CARIBOO, gas(null, null, 0))).isEqualTo(first);
    jdbc.update("UPDATE APPRAISAL_DATA_SUBMISSION SET APPRAISAL_STATUS_CODE = 'DFT' WHERE ECAS_ID = 1001");
    assertThat(reader.search(CARIBOO, gas(null, null, 0)).total()).isEqualTo(2);
  }

  @Test
  void appraisedDetailsKeepMarksSeparateFromExactStoredRatesAndScopeBothKeyPaths() {
    var reader = new OracleAppraisedSummary(jdbc);
    var summary = reader.byTypedKey(CARIBOO, key(GasAppraisal.WorksheetType.APPRAISED, "101")).orElseThrow();
    assertThat(summary).isEqualTo(reader.byEcasId(CARIBOO, "1001").orElseThrow());
    assertThat(summary.timberMarks()).containsExactly("AA0001", "AA0002");
    assertThat(summary.rates()).extracting(GasAppraisal.StoredRate::rateId).containsExactly("10001", "10002");
    assertThat(summary.rates().getFirst().totalStumpageRate()).isEqualByComparingTo("12.30");
    assertThat(summary.rates().getLast().totalStumpageRate()).isEqualByComparingTo("0.00");
    assertThat(summary.toaEligible()).isFalse();
    assertThat(summary.variant()).isEqualTo(GasAppraisal.SummaryVariant.COAST_MPS_TOA_N);
    assertThat(summary.expiryDate()).isNull();
    assertThat(reader.byEcasId(OMINECA, "1001")).isEmpty();
    assertThat(reader.byTypedKey(OMINECA, key(GasAppraisal.WorksheetType.APPRAISED, "101"))).isEmpty();
    assertThat(reader.byEcasId(idir(), "1001")).isEmpty();
  }

  @Test
  void ambiguousAppraisedParentIsRejectedInsteadOfReturningOneWorksheet() {
    jdbc.update("INSERT INTO APPRAISED_WORKSHEET VALUES (999, 1001, 'NEW', NULL)");
    assertThatThrownBy(() -> new OracleAppraisedSummary(jdbc).byEcasId(CARIBOO, "1001"))
        .isInstanceOf(IncorrectResultSizeDataAccessException.class);
  }

  @Test
  void otherFamiliesRetainNullableComponentsAndRejectInactiveOrOutOfScopeKeys() {
    var reader = new OracleOtherWorksheetSummary(jdbc);
    var historic = reader.historic(CARIBOO, key(GasAppraisal.WorksheetType.HISTORIC, "201")).orElseThrow();
    assertThat(historic.variant()).isEqualTo(GasAppraisal.SummaryVariant.COAST_MPS_TOA_Y);
    assertThat(historic.adjustQuarterly()).isNull();
    assertThat(historic.rates().getFirst().totalStumpageRate()).isEqualByComparingTo("19.25");
    assertThat(historic.nonAppraisedRates().getFirst().developmentLevy()).isNull();
    var other = reader.nonAppraised(CARIBOO, key(GasAppraisal.WorksheetType.NON_APPRAISED, "301")).orElseThrow();
    assertThat(other.status().description()).isEqualTo("Stored non-appraised");
    assertThat(other.rates().getFirst().reserveStumpageRate()).isEqualByComparingTo("1.25");
    assertThat(other.rates().getFirst().bonusBidAmount()).isNull();
    assertThat(other.rates().getFirst().developmentLevy()).isEqualByComparingTo("0.00");
    assertThat(reader.historic(CARIBOO, key(GasAppraisal.WorksheetType.HISTORIC, "203"))).isEmpty();
    assertThat(reader.historic(OMINECA, key(GasAppraisal.WorksheetType.HISTORIC, "201"))).isEmpty();
    assertThat(reader.nonAppraised(OMINECA, key(GasAppraisal.WorksheetType.NON_APPRAISED, "301"))).isEmpty();
  }

  @Test
  void malformedRateHavingTwoFamilyParentsFailsTheWholeSummary() {
    jdbc.update("UPDATE NON_APPRAISED_STUMPAGE_RATE SET APPRAISED_WORKSHEET_ID = 101 WHERE NON_APPRAISED_STUMPAGE_RATE_ID = 30001");
    assertThatThrownBy(() -> new OracleOtherWorksheetSummary(jdbc)
        .nonAppraised(CARIBOO, key(GasAppraisal.WorksheetType.NON_APPRAISED, "301")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void licenceChooserAndIndependentFtaPanelCoverPermitPrivateAndRoadMarks() {
    var chooser = new OracleLicenceMarks(jdbc);
    assertThat(chooser.forLicence(CARIBOO, "a00001").timberMarks()).containsExactly("AA0001", "AA0002");
    assertThat(chooser.forLicence(OMINECA, "A00001").timberMarks()).isEmpty();
    assertThat(chooser.forLicence(CARIBOO, "A00003").timberMarks()).containsExactly("PM0001");
    assertThat(chooser.forLicence(CARIBOO, "A00004").timberMarks()).containsExactly("RM0001");
    var fta = new OracleFtaLicenceInformation(jdbc);
    var permit = fta.find(CARIBOO, null, "AA0001").orElseThrow();
    assertThat(permit.cuttingPermit()).isEqualTo("001");
    assertThat(permit.licenseeName()).isEqualTo("Synthetic current owner");
    assertThat(permit.markExpiryDate()).isEqualTo(LocalDate.of(2030, 12, 31));
    assertThat(permit.markExtendDate()).isNull();
    var privateMark = fta.find(CARIBOO, "A00003", "PM0001").orElseThrow();
    assertThat(privateMark.markExpiryDate()).isNull();
    assertThat(privateMark.cuttingPermit()).isNull();
    assertThat(fta.find(CARIBOO, null, "RM0001").orElseThrow().forestDistrict()).isEqualTo("Synthetic district A");
    assertThat(new OracleGasSearch(jdbc).search(CARIBOO, gas("A00003", "PM0001", 0)).total()).isZero();
    assertThat(fta.find(OMINECA, "A00001", "AA0001")).isEmpty();
  }

  @Test
  void permitAggregationCannotExposeSiblingPermitsOutsideTheGrantedDistrict() {
    jdbc.update("INSERT INTO HARVESTING_AUTHORITY VALUES (103, 'A00001', '004', 10, 10, 'A', DATE '2030-12-31', NULL, '12', 'U')");
    jdbc.update("INSERT INTO HARVESTING_HAULING_XREF VALUES ('AA0001', 103, 'N')");
    jdbc.update("INSERT INTO HARVESTING_AUTHORITY VALUES (104, 'A00001', 'SECRET', 20, 20, 'A', DATE '2030-12-31', NULL, '12', 'U')");
    jdbc.update("INSERT INTO HARVESTING_HAULING_XREF VALUES ('AA0001', 104, 'N')");
    assertThat(new OracleFtaLicenceInformation(jdbc).find(CARIBOO, "A00001", "AA0001")
        .orElseThrow().cuttingPermit()).isEqualTo("001, 004");
  }

  @Test
  void ambiguousFtaClientsFailInsteadOfPickingAnArbitraryOwner() {
    jdbc.update("INSERT INTO FOREST_FILE_CLIENT VALUES ('A00001', '00000002', 'S')");
    assertThatThrownBy(() -> new OracleFtaLicenceInformation(jdbc).find(CARIBOO, "A00001", "AA0001"))
        .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("ORA-01427");
  }

  @Test
  void ecasInboxKeepsPermitRowsAndSecondaryMarkFiltersWithoutWideningScope() {
    jdbc.update("INSERT INTO HARVESTING_AUTHORITY VALUES (103, 'A00001', '004', 10, 10, 'A', DATE '2030-12-31', NULL, '12', 'U')");
    jdbc.update("INSERT INTO HARVESTING_HAULING_XREF VALUES ('AA0001', 103, 'N')");
    var reader = new OracleEcasInbox(jdbc);
    var page = reader.search(CARIBOO, new InboxFilters().search(), 0);
    assertThat(page.total()).isEqualTo(2);
    assertThat(page.items()).extracting(EcasInbox.Item::cuttingPermit).containsExactly("001", "004");
    assertThat(page.items().getFirst().multipleTimberMarks()).isTrue();
    assertThat(page.items().getFirst().statusDate()).isEqualTo(LocalDate.of(2026, 1, 3));
    var secondary = new InboxFilters();
    secondary.mark = "AA0002";
    assertThat(reader.search(CARIBOO, secondary.search(), 0).items()).extracting(EcasInbox.Item::timberMark).containsExactly("AA0002");
    var direct = new InboxFilters();
    direct.id = "1002";
    assertThat(reader.search(CARIBOO, direct.search(), 0).total()).isZero();
    direct.orgs = List.of("30");
    assertThat(reader.search(CARIBOO, direct.search(), 0).total()).isZero();
    assertThat(reader.search(CARIBOO, new InboxFilters().search(), 1).total()).isEqualTo(2);
    assertThat(reader.search(CARIBOO, new InboxFilters().search(), 1).items()).isEmpty();
  }

  @Test
  void ecasDateBoundsAuditAndWorkedOnFiltersExecuteWithLegacyMidnightSemantics() {
    var reader = new OracleEcasInbox(jdbc);
    var filter = new InboxFilters();
    filter.dateTypes = List.of(EcasInbox.DateType.EFFCTV);
    filter.dates = new DateRange(null, LocalDate.of(2026, 1, 2));
    assertThat(reader.search(CARIBOO, filter.search(), 0).total()).isZero();
    filter.dates = new DateRange(null, LocalDate.of(2026, 1, 3));
    assertThat(reader.search(CARIBOO, filter.search(), 0).total()).isEqualTo(1);
    filter.statuses = List.of("RGN");
    filter.statusDates = new DateRange(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5));
    filter.workedOn = "synthetic-user";
    assertThat(reader.search(CARIBOO, filter.search(), 0).total()).isEqualTo(1);
    jdbc.update("DELETE FROM ECAS_AUDIT_EVENT WHERE ECAS_ID = 1001");
    assertThat(reader.search(CARIBOO, filter.search(), 0).total()).isZero();
  }

  @Test
  void ecasPaginationKeepsScopedCountsAndStableTieOrderAcrossTheHundredRowBoundary() {
    for (int index = 0; index < 101; index++) {
      long id = 2000 + index;
      jdbc.update("""
          INSERT INTO APPRAISAL_DATA_SUBMISSION
          (ECAS_ID, CLIENT_NUMBER, ADMIN_DISTRICT, FOREST_FILE_ID, APPRAISAL_STATUS_CODE,
           APPRAISAL_CATEGORY_CODE, APPRAISAL_EFFECTIVE_DATE)
          SELECT ?, CLIENT_NUMBER, ADMIN_DISTRICT, FOREST_FILE_ID, APPRAISAL_STATUS_CODE,
                 APPRAISAL_CATEGORY_CODE, APPRAISAL_EFFECTIVE_DATE
            FROM APPRAISAL_DATA_SUBMISSION WHERE ECAS_ID = 1001
          """, id);
      jdbc.update("INSERT INTO APPRAISAL_DATA_SUBMISSION_CTRL VALUES (?, 'C', '00000001')", id);
      jdbc.update("INSERT INTO ADS_SUBMITTED_TIMBER_MARK VALUES (?, 'AA0001', 'Y', NULL, NULL)", id);
    }
    var reader = new OracleEcasInbox(jdbc);
    var first = reader.search(CARIBOO, new InboxFilters().search(), 0);
    var second = reader.search(CARIBOO, new InboxFilters().search(), 1);
    assertThat(first.total()).isEqualTo(102);
    assertThat(first.items()).hasSize(100).doesNotContainAnyElementsOf(second.items());
    assertThat(first.items().getFirst().ecasId()).isEqualTo("2100");
    assertThat(second.items()).extracting(EcasInbox.Item::ecasId).containsExactly("2000", "1001");
    assertThat(reader.search(OMINECA, new InboxFilters().search(), 0).total()).isEqualTo(1);
  }

  @Test
  void ecasVisibilityRestrictionsFollowTheSameGrantForListAndReferenceReads() {
    var inbox = new OracleEcasInbox(jdbc);
    var reference = new OracleEcasReference(jdbc);
    var viewer = idir("TAPS_VIEWER_DISTRICT-DCA");
    jdbc.update("UPDATE APPRAISAL_DATA_SUBMISSION SET APPRAISAL_STATUS_CODE = 'DFT' WHERE ECAS_ID = 1001");
    assertThat(inbox.search(viewer, new InboxFilters().search(), 0).total()).isZero();
    assertThat(reference.coast(viewer, "1001")).isEmpty();
    assertThat(reference.coast(CARIBOO, "1001")).isPresent();
    jdbc.update("UPDATE APPRAISAL_DATA_SUBMISSION SET APPRAISAL_STATUS_CODE = 'SCN' WHERE ECAS_ID = 1001");
    var client = user(IdentityProvider.BCEID_BUSINESS, "TAPS_LICENSEE_VIEWER_FOREST_CLIENT-00000001");
    assertThat(inbox.search(client, new InboxFilters().search(), 0).total()).isZero();
    assertThat(reference.coast(client, "1001")).isEmpty();
    jdbc.update("UPDATE APPRAISAL_DATA_SUBMISSION SET APPRAISAL_STATUS_CODE = 'CON' WHERE ECAS_ID = 1001");
    assertThat(inbox.search(client, new InboxFilters().search(), 0).total()).isEqualTo(1);
    assertThat(reference.coast(client, "1001")).isPresent();
    assertThat(reference.interior(client, "1002")).isEmpty();
    jdbc.update("UPDATE APPRAISAL_DATA_SUBMISSION SET APPRAISAL_STATUS_CODE = 'DFT' WHERE ECAS_ID = 1002");
    var mixed = idir("TAPS_REGION_APPRAISER_REGION-CARIBOO", "TAPS_VIEWER_DISTRICT-DOM");
    assertThat(reference.interior(mixed, "1002")).isEmpty();
    assertThat(inbox.search(mixed, new InboxFilters().search(), 0).total()).isEqualTo(1);
  }

  @Test
  void coastAndInteriorReferencesExecuteFtaAndHistoricZoneBranches() {
    var reader = new OracleEcasReference(jdbc);
    var coast = reader.coast(CARIBOO, "1001").orElseThrow();
    assertThat(coast.timberMarks()).extracting(EcasReference.TimberMark::timberMark).containsExactly("AA0001", "AA0002");
    assertThat(coast.primaryTimberMark()).isEqualTo("AA0001");
    assertThat(coast.netCruiseVolume()).isEqualByComparingTo("31.00");
    assertThat(coast.header().licenseeName()).isEqualTo("Synthetic current owner");
    assertThat(coast.header().administrativeDistrict().code()).isEqualTo("DCA");
    assertThat(coast.header().timberSupplyArea().code()).isEqualTo("12");
    assertThat(coast.header().fileType().code()).isEqualTo("A01");
    var interior = reader.interior(OMINECA, "1002").orElseThrow();
    assertThat(interior.sellingPriceZoneCode()).isEqualTo("2");
    assertThat(interior.comparativeCruise()).isFalse();
    assertThat(interior.salvage()).isNull();
    assertThat(interior.timberMarkRevisionCount()).isNull();
    assertThat(reader.coast(OMINECA, "1001")).isEmpty();
    assertThat(reader.interior(CARIBOO, "1002")).isEmpty();
    assertThat(reader.interior(ADMIN, "1001")).isEmpty();
  }

  @Test
  void conflictingFtaReferenceParentsAreRejected() {
    jdbc.update("INSERT INTO HARVESTING_AUTHORITY VALUES (103, 'A00001', '004', 10, 10, 'A', DATE '2030-12-31', NULL, '12', 'U')");
    jdbc.update("INSERT INTO HARVESTING_HAULING_XREF VALUES ('AA0001', 103, 'N')");
    assertThatThrownBy(() -> new OracleEcasReference(jdbc).coast(CARIBOO, "1001"))
        .isInstanceOf(IncorrectResultSizeDataAccessException.class);
  }

  @Test
  void selectedWorksheetAddonsRetainExpiredSelectionsAndHistoricChildrenStayIndependent() {
    var reader = new OracleOtherWorksheetSummary(jdbc);
    var other = reader.nonAppraised(CARIBOO, key(GasAppraisal.WorksheetType.NON_APPRAISED, "301")).orElseThrow();
    assertThat(other.selectedRateAddons()).extracting(GasAppraisal.SelectedRateAddon::code).containsExactly("EXPIRED", "SILV");
    assertThat(other.selectedRateAddons().getFirst().expiryDate()).isEqualTo(LocalDateTime.of(2001, 1, 1, 0, 0));
    assertThat(other.selectedRateAddons().getLast().updateTimestamp()).isEqualTo(LocalDateTime.of(2026, 1, 2, 12, 34, 56));
    assertThat(other.rates()).hasSize(1);
    var historic = reader.historic(CARIBOO, key(GasAppraisal.WorksheetType.HISTORIC, "201")).orElseThrow();
    assertThat(historic.historicSpecies()).extracting(GasAppraisal.HistoricSpecies::speciesId).containsExactly("40001", "40002");
    assertThat(historic.historicSpecies().getFirst().speciesVolume()).isEqualByComparingTo("9999999");
    assertThat(historic.historicSpecies().getFirst().speciesDecayPercent()).isNull();
    assertThat(historic.historicSpecies().getLast().speciesVolume()).isEqualByComparingTo("0");
    assertThat(historic.coastSpeciesGrades()).hasSize(3);
    assertThat(historic.coastSpeciesGrades().get(1).scaleProductCode()).isEqualTo("02");
    assertThat(historic.coastSpeciesGrades().get(1).speciesGradePercent()).isEqualByComparingTo("40");
    assertThat(historic.rates()).hasSize(1);
    assertThat(historic.nonAppraisedRates()).hasSize(1);
    assertThat(reader.historic(OMINECA, key(GasAppraisal.WorksheetType.HISTORIC, "201"))).isEmpty();
    assertThat(reader.nonAppraised(OMINECA, key(GasAppraisal.WorksheetType.NON_APPRAISED, "301"))).isEmpty();
    jdbc.update("DELETE FROM NON_APPRAISED_WS_RATE_ADDON WHERE NON_APPRAISED_WORKSHEET_ID = 301");
    jdbc.update("DELETE FROM HISTORIC_SPECIES WHERE HISTORIC_APPRAISED_WRKSHEET_ID = 201");
    jdbc.update("DELETE FROM HISTORIC_COAST_SPECIES_GRADE WHERE HISTORIC_APPRAISED_WRKSHEET_ID = 201");
    assertThat(reader.nonAppraised(CARIBOO, key(GasAppraisal.WorksheetType.NON_APPRAISED, "301")).orElseThrow().selectedRateAddons()).isEmpty();
    historic = reader.historic(CARIBOO, key(GasAppraisal.WorksheetType.HISTORIC, "201")).orElseThrow();
    assertThat(historic.historicSpecies()).isEmpty();
    assertThat(historic.coastSpeciesGrades()).isEmpty();
    assertThat(historic.rates()).hasSize(1);
  }

  @Test
  void completeHttpWorkflowUsesRealOracleReadersWithCapabilityAndRecordAuthorization() throws Exception {
    try (var context = new SpringApplicationBuilder(TapsApplication.class, SyntheticAuthentication.class)
        .initializers(application -> TestPropertyValues.of(
            "server.address=127.0.0.1", "server.port=0",
            "spring.datasource.url=" + ORACLE.getJdbcUrl(),
            "spring.datasource.username=" + ORACLE.getUsername(),
            "spring.datasource.password=" + ORACLE.getPassword(), "KEYSTORE_SECRET=unused",
            "spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.minimum-idle=0",
            "taps.auth.client-id=taps", "taps.auth.issuer-uri=https://synthetic.invalid/realms/taps")
            .applyTo(application)).run("--spring.profiles.active=oracle")) {
      var mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
          .apply(springSecurity()).build();
      mvc.perform(get("/api/gas/worksheets")).andExpect(status().isUnauthorized());
      mvc.perform(get("/api/me").header("Authorization", "Bearer fixture-cariboo"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.readApiEnabled").value(true));
      mvc.perform(post("/api/ecas/inbox").header("Authorization", "Bearer fixture-cariboo")
          .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"ALL_SUBMISSIONS\"}"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
          .andExpect(jsonPath("$.items[0].ecasId").value("1001"));
      mvc.perform(get("/api/ecas/references/C/1001").header("Authorization", "Bearer fixture-cariboo"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.timberMarks.length()").value(2))
          .andExpect(jsonPath("$.header.effectiveDate").value("2026-01-02"));
      mvc.perform(get("/api/gas/appraised/by-ecas/1001").header("Authorization", "Bearer fixture-cariboo"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.key.worksheetId").value("101"))
          .andExpect(jsonPath("$.rates[0].totalStumpageRate").value("12.30"));
      mvc.perform(get("/api/gas/worksheets").header("Authorization", "Bearer fixture-cariboo"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(4));
      mvc.perform(get("/api/gas/worksheets/HISTORIC/201").header("Authorization", "Bearer fixture-cariboo"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.rates[0].totalStumpageRate").value("19.25"))
          .andExpect(jsonPath("$.historicSpecies[0].speciesVolume").value("9999999"))
          .andExpect(jsonPath("$.coastSpeciesGrades[1].scaleProductCode").value("02"));
      mvc.perform(get("/api/gas/worksheets/NON_APPRAISED/301").header("Authorization", "Bearer fixture-cariboo"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.rates[0].reserveStumpageRate").value("1.25"))
          .andExpect(jsonPath("$.selectedRateAddons[0].code").value("EXPIRED"));
      mvc.perform(get("/api/gas/licences/A00001/marks").header("Authorization", "Bearer fixture-cariboo"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.timberMarks.length()").value(2));
      mvc.perform(get("/api/gas/licence-information?timberMark=PM0001")
          .header("Authorization", "Bearer fixture-cariboo"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.licenceNumber").value("A00003"));
      mvc.perform(get("/api/ecas/references/C/1001").header("Authorization", "Bearer fixture-omineca"))
          .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
      mvc.perform(get("/api/gas/worksheets/APPRAISED/101").header("Authorization", "Bearer fixture-omineca"))
          .andExpect(status().isNotFound());
      mvc.perform(get("/api/gas/worksheets").header("Authorization", "Bearer fixture-licensee"))
          .andExpect(status().isForbidden());
      mvc.perform(post("/api/gas/worksheets").header("Authorization", "Bearer fixture-cariboo"))
          .andExpect(status().isForbidden());
      mvc.perform(get("/api/gas/worksheets?page=-1").header("Authorization", "Bearer fixture-cariboo"))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
      mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }
  }

  /** Test-only token decoder. */
  @TestConfiguration(proxyBeanMethods = false)
  static class SyntheticAuthentication {
    @Bean
    @Primary
    JwtDecoder syntheticJwtDecoder() {
      return token -> {
        String role = switch (token) {
          case "fixture-cariboo" -> "TAPS_REGION_APPRAISER_REGION-CARIBOO";
          case "fixture-omineca" -> "TAPS_REGION_APPRAISER_REGION-OMINECA";
          case "fixture-licensee" -> "TAPS_LICENSEE_VIEWER_FOREST_CLIENT-00000001";
          default -> throw new JwtException("Unknown synthetic token");
        };
        var now = Instant.now();
        return Jwt.withTokenValue(token).header("alg", "RS256").subject("synthetic-user")
            .claim("azp", "taps").claim("typ", "Bearer").issuedAt(now).expiresAt(now.plusSeconds(300))
            .claim("identity_provider", token.equals("fixture-licensee") ? "bceidbusiness" : "azureidir")
            .claim("idir_username", "synthetic").claim("bceid_username", "synthetic")
            .claim("client_roles", List.of(role)).build();
      };
    }
  }

  private static GasAppraisal.Search gas(String licence, String mark, int page) {
    return new GasAppraisal.Search(licence, mark, page);
  }

  private static GasAppraisal.Key key(GasAppraisal.WorksheetType type, String id) {
    return new GasAppraisal.Key(type, id);
  }

  private static TapsUser idir(String... roles) {
    return user(IdentityProvider.IDIR, roles);
  }

  private static TapsUser user(IdentityProvider provider, String... roles) {
    return new TapsUser("synthetic-user", "Synthetic User", null, provider, null,
        Arrays.stream(roles).map(FamRoleName::parse).map(role -> RoleGrant.accept(role, provider).orElseThrow()).toList());
  }

  private static final class InboxFilters {
    String id;
    String mark;
    List<String> orgs = List.of();
    List<String> statuses = List.of();
    DateRange statusDates;
    String workedOn;
    List<EcasInbox.DateType> dateTypes = List.of();
    DateRange dates;

    EcasInbox.Search search() {
      return new EcasInbox.Search(EcasInbox.Mode.ALL_SUBMISSIONS, null, id, null, null, mark,
          null, null, orgs, null, null, statuses, statusDates, null, null, null, null, null,
          workedOn, dateTypes, dates, null, null);
    }
  }
}
