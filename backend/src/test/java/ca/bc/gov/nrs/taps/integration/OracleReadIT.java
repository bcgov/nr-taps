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
import ca.bc.gov.nrs.taps.read.CodeOption;
import ca.bc.gov.nrs.taps.read.EcasInbox;
import ca.bc.gov.nrs.taps.read.EcasReference;
import ca.bc.gov.nrs.taps.read.EffectiveCode;
import ca.bc.gov.nrs.taps.read.GasAppraisal;
import ca.bc.gov.nrs.taps.read.GasAudit;
import ca.bc.gov.nrs.taps.read.oracle.OracleAppraisedSummary;
import ca.bc.gov.nrs.taps.read.oracle.OracleCodeLists;
import ca.bc.gov.nrs.taps.read.oracle.OracleEcasInbox;
import ca.bc.gov.nrs.taps.read.oracle.OracleEcasReference;
import ca.bc.gov.nrs.taps.read.oracle.OracleFtaLicenceInformation;
import ca.bc.gov.nrs.taps.read.oracle.OracleGasSearch;
import ca.bc.gov.nrs.taps.read.oracle.OracleGasAudit;
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
    assertThat(summary.primaryTimberMark()).isEqualTo("AA0001");
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
  void appraisedSummaryUsesTheFlaggedPrimaryMarkWithoutFallingBackToListOrder() {
    var reader = new OracleAppraisedSummary(jdbc);
    var key = key(GasAppraisal.WorksheetType.APPRAISED, "101");
    jdbc.update("UPDATE ADS_SUBMITTED_TIMBER_MARK SET PRIMARY_MARK_IND = CASE WHEN TIMBER_MARK = 'AA0002' THEN 'Y' ELSE 'N' END WHERE ECAS_ID = 1001");
    var summary = reader.byTypedKey(CARIBOO, key).orElseThrow();
    assertThat(summary.timberMarks()).containsExactly("AA0001", "AA0002");
    assertThat(summary.primaryTimberMark()).isEqualTo("AA0002");
    assertThat(reader.byEcasId(CARIBOO, "1001")).contains(summary);
    jdbc.update("UPDATE ADS_SUBMITTED_TIMBER_MARK SET PRIMARY_MARK_IND = 'N' WHERE ECAS_ID = 1001");
    assertThat(reader.byTypedKey(CARIBOO, key).orElseThrow().primaryTimberMark()).isNull();
    jdbc.update("UPDATE ADS_SUBMITTED_TIMBER_MARK SET PRIMARY_MARK_IND = 'Y' WHERE ECAS_ID = 1001");
    assertThatThrownBy(() -> reader.byTypedKey(CARIBOO, key))
        .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("ORA-01427");
    assertThat(reader.byTypedKey(OMINECA, key)).isEmpty();
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
    assertThat(historic.nonAppraisedRates().getFirst().scaleSpecies())
        .isEqualTo(new CodeOption("HE", "Synthetic hemlock"));
    assertThat(historic.nonAppraisedRates().getFirst().upsetStumpageRate()).isEqualByComparingTo("5.25");
    assertThat(historic.nonAppraisedRates().getFirst().totalStumpageRate()).isEqualByComparingTo("6.75");
    var other = reader.nonAppraised(CARIBOO, key(GasAppraisal.WorksheetType.NON_APPRAISED, "301")).orElseThrow();
    assertThat(other.status().description()).isEqualTo("Stored non-appraised");
    assertThat(other.rates().getFirst().reserveStumpageRate()).isEqualByComparingTo("1.25");
    assertThat(other.rates().getFirst().bonusBidAmount()).isNull();
    assertThat(other.rates().getFirst().developmentLevy()).isEqualByComparingTo("0.00");
    assertThat(other.referenceType()).isEqualTo(new CodeOption("NEW", "Synthetic reference"));
    assertThat(other.timberSupplyBlock()).isEqualTo(new CodeOption("1201", "1201 - Synthetic TSB"));
    assertThat(other.appraisalForestZone()).isEqualTo(new CodeOption("A", "Synthetic forest zone"));
    assertThat(other.nonAppraisedRateType()).isEqualTo(new CodeOption("S", "Synthetic rate type"));
    assertThat(other.rateAdjustmentType()).isEqualTo(new CodeOption("A", "Synthetic adjustment"));
    assertThat(other.rates().getFirst().scaleSpecies()).isEqualTo(new CodeOption("FI", "Synthetic fir"));
    assertThat(other.rates().getFirst().scaleProduct()).isEqualTo(new CodeOption("01", "Synthetic product"));
    assertThat(other.rates().getFirst().scaleGrade()).isEqualTo(new CodeOption("A", "Synthetic grade"));
    assertThat(other.rates().getFirst().upsetStumpageRate()).isEqualByComparingTo("3.75");
    assertThat(other.rates().getFirst().totalStumpageRate()).isEqualByComparingTo("3.75");
    assertThat(reader.historic(CARIBOO, key(GasAppraisal.WorksheetType.HISTORIC, "203"))).isEmpty();
    assertThat(reader.historic(OMINECA, key(GasAppraisal.WorksheetType.HISTORIC, "201"))).isEmpty();
    assertThat(reader.nonAppraised(OMINECA, key(GasAppraisal.WorksheetType.NON_APPRAISED, "301"))).isEmpty();
  }

  @Test
  void worksheetLabelsRetainLiteralSpaceCodesAndRowsWithMissingOrInactiveDescriptions() {
    var reader = new OracleOtherWorksheetSummary(jdbc);
    var key = key(GasAppraisal.WorksheetType.NON_APPRAISED, "301");
    jdbc.update("UPDATE NON_APPRAISED_STUMPAGE_RATE SET SCALE_PRODUCT_CODE = ' ', SCALE_GRADE_CODE = ' ' WHERE NON_APPRAISED_STUMPAGE_RATE_ID = 30001");
    var summary = reader.nonAppraised(CARIBOO, key).orElseThrow();
    assertThat(summary.rates()).hasSize(1);
    assertThat(summary.rates().getFirst().scaleProduct()).isEqualTo(new CodeOption(" ", "Logs"));
    assertThat(summary.rates().getFirst().scaleGrade()).isEqualTo(new CodeOption(" ", "Ungraded"));

    jdbc.update("UPDATE WORKSHEET_REFERENCE_TYPE_CODE SET EFFECTIVE_DATE = SYSDATE + 1");
    jdbc.update("UPDATE TSB_NUMBER_CODE SET EXPIRY_DATE = SYSDATE - 1");
    jdbc.update("UPDATE APPRAISAL_FOREST_ZONE_CODE SET EXPIRY_DATE = SYSDATE - 1");
    jdbc.update("UPDATE NON_APPRAISED_RATE_TYPE_CODE SET EXPIRY_DATE = NULL");
    jdbc.update("DELETE FROM RATE_ADJUSTMENT_TYPE_CODE WHERE RATE_ADJUSTMENT_TYPE_CODE = 'A'");
    jdbc.update("DELETE FROM SCALE_SPECIES_CODE WHERE SCALE_SPECIES_CODE = 'FI'");
    summary = reader.nonAppraised(CARIBOO, key).orElseThrow();
    assertThat(summary.referenceType()).isEqualTo(new CodeOption("NEW", null));
    assertThat(summary.timberSupplyBlock()).isEqualTo(new CodeOption("1201", null));
    assertThat(summary.appraisalForestZone()).isEqualTo(new CodeOption("A", null));
    assertThat(summary.nonAppraisedRateType()).isEqualTo(new CodeOption("S", null));
    assertThat(summary.rateAdjustmentType()).isEqualTo(new CodeOption("A", null));
    assertThat(summary.rates()).hasSize(1);
    assertThat(summary.rates().getFirst().scaleSpecies()).isEqualTo(new CodeOption("FI", null));
    assertThat(summary.rates().getFirst().totalStumpageRate()).isEqualByComparingTo("3.75");
    assertThat(reader.nonAppraised(OMINECA, key)).isEmpty();

    jdbc.update("UPDATE NON_APPRAISED_WORKSHEET SET WORKSHEET_REFERENCE_TYPE_CODE = NULL, TSB_NUMBER_CODE = NULL, APPRAISAL_FOREST_ZONE_CODE = NULL, NON_APPRAISED_RATE_TYPE_CODE = NULL, RATE_ADJUSTMENT_TYPE_CODE = NULL WHERE NON_APPRAISED_WORKSHEET_ID = 301");
    summary = reader.nonAppraised(CARIBOO, key).orElseThrow();
    assertThat(summary.referenceType()).isNull();
    assertThat(summary.timberSupplyBlock()).isNull();
    assertThat(summary.appraisalForestZone()).isNull();
    assertThat(summary.nonAppraisedRateType()).isNull();
    assertThat(summary.rateAdjustmentType()).isNull();
  }

  @Test
  void nonAppraisedTotalsKeepExactSumsBeyondTheComponentColumnRange() {
    jdbc.update("UPDATE NON_APPRAISED_STUMPAGE_RATE SET RESERVE_STUMPAGE_RATE = 999.99, SILVICULTURE_LEVY = 999.99, DEVELOPMENT_LEVY = 999.99, BONUS_BID_AMOUNT = 999.99 WHERE NON_APPRAISED_STUMPAGE_RATE_ID = 30001");
    var rate = new OracleOtherWorksheetSummary(jdbc)
        .nonAppraised(CARIBOO, key(GasAppraisal.WorksheetType.NON_APPRAISED, "301"))
        .orElseThrow().rates().getFirst();
    assertThat(rate.upsetStumpageRate().toPlainString()).isEqualTo("2999.97");
    assertThat(rate.totalStumpageRate().toPlainString()).isEqualTo("3999.96");
  }

  @Test
  void ambiguousRateLabelsFailRatherThanMultiplyingStoredRows() {
    jdbc.update("INSERT INTO SCALE_GRADE_CODE VALUES ('A', 'Conflicting synthetic grade', DATE '2000-01-01', DATE '9999-12-31')");
    assertThatThrownBy(() -> new OracleOtherWorksheetSummary(jdbc)
        .nonAppraised(CARIBOO, key(GasAppraisal.WorksheetType.NON_APPRAISED, "301")))
        .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("ORA-01427");
  }

  @Test
  void gasHistoryComparesEachRateWithItsOwnPriorStateAndUsesTheChangedFieldValue() {
    var reader = new OracleGasAudit(jdbc);
    gasAuditTransaction(1, "Initial synthetic snapshots");
    gasAuditTransaction(2, "Synthetic <b>comment</b>\nkept as text");
    gasRateSnapshot(1001, 50001, 301, 1, "2026-01-01T10:00:00");
    gasRateSnapshot(1002, 50002, 301, 1, "2026-01-01T10:00:00");
    jdbc.update("UPDATE NON_APPRAISED_STUMPAGE_RTE_AUD SET SCALE_SPECIES_CODE = 'HE', SCALE_GRADE_CODE = 'B', RESERVE_STUMPAGE_RATE = 50 WHERE NON_APPRAISED_STMPG_RTE_AUD_ID = 1002");
    assertThat(reader.history(CARIBOO, "301", 0).orElseThrow().total()).isZero();

    gasRateSnapshot(1003, 50001, 301, 2, "2026-01-02T10:00:00");
    jdbc.update("UPDATE NON_APPRAISED_STUMPAGE_RTE_AUD SET SCALE_GRADE_CODE = 'B', SILVICULTURE_LEVY = 2.50, UPDATE_USERID = 'IDIR\\SYNTHETIC-EDITOR' WHERE NON_APPRAISED_STMPG_RTE_AUD_ID = 1003");
    gasRateSnapshot(1004, 50002, 301, 2, "2026-01-02T10:00:00");
    jdbc.update("UPDATE NON_APPRAISED_STUMPAGE_RTE_AUD SET SCALE_SPECIES_CODE = 'HE', SCALE_GRADE_CODE = 'B', RESERVE_STUMPAGE_RATE = 50 WHERE NON_APPRAISED_STMPG_RTE_AUD_ID = 1004");
    var changes = reader.history(CARIBOO, "301", 0).orElseThrow();
    assertThat(changes.total()).isEqualTo(2);
    assertThat(changes.items()).extracting(GasAudit.Item::attribute).containsExactly("Grade", "Silviculture Levy");
    assertThat(changes.items()).extracting(GasAudit.Item::value).containsExactly("B", "2.50");
    assertThat(changes.items()).allSatisfy(change -> {
      assertThat(change.rateId()).isEqualTo("50001");
      assertThat(change.userId()).isEqualTo("IDIR\\SYNTHETIC-EDITOR");
      assertThat(change.comment()).isEqualTo("Synthetic <b>comment</b>\nkept as text");
      assertThat(change.eventDate()).isEqualTo(LocalDateTime.of(2026, 1, 2, 10, 0));
    });

    gasRateSnapshot(1005, 50001, 301, 2, "2026-01-03T10:00:00");
    jdbc.update("UPDATE NON_APPRAISED_STUMPAGE_RTE_AUD SET SCALE_GRADE_CODE = 'B' WHERE NON_APPRAISED_STMPG_RTE_AUD_ID = 1005");
    var latest = reader.history(CARIBOO, "301", 0).orElseThrow();
    assertThat(latest.total()).isEqualTo(3);
    assertThat(latest.items().getFirst().attribute()).isEqualTo("Silviculture Levy");
    assertThat(latest.items().getFirst().value()).isNull();
  }

  @Test
  void gasHistoryIncludesWorksheetClassificationsAndComparesDatesBeforeFormatting() {
    var reader = new OracleGasAudit(jdbc);
    gasAuditTransaction(1, null);
    gasWorksheetSnapshot(2001, 301, 1, "2026-01-01T10:00:00");
    jdbc.update("""
        UPDATE NON_APPRAISED_WORKSHEET SET TIMBER_MARK = 'AA0002',
          EFFECTIVE_DATE = TO_DATE('2026-02-01 12:00:00', 'YYYY-MM-DD HH24:MI:SS'),
          EXPIRY_DATE = DATE '2027-01-01', APPRAISAL_METHOD_CODE = 'I', NON_APPRAISED_STATUS_CODE = 'UNC',
          WORKSHEET_REFERENCE_TYPE_CODE = 'CHG', SDM_DECLARATION_ACCEPTANCE_DT = DATE '2026-01-02',
          TSB_NUMBER_CODE = 'ZZ9', APPRAISAL_FOREST_ZONE_CODE = 'Z', NON_APPRAISED_RATE_TYPE_CODE = 'X',
          RATE_ADJUSTMENT_TYPE_CODE = 'F' WHERE NON_APPRAISED_WORKSHEET_ID = 301
        """);
    gasWorksheetSnapshot(2002, 301, 1, "2026-01-02T10:00:00");
    var first = reader.history(CARIBOO, "301", 0).orElseThrow();
    var second = reader.history(CARIBOO, "301", 1).orElseThrow();
    assertThat(first.total()).isEqualTo(11);
    assertThat(first.items()).hasSize(10);
    assertThat(first.items()).filteredOn(change -> change.attribute().equals("Effective Date"))
        .extracting(GasAudit.Item::value).containsExactly("2026-02-01");
    assertThat(first.items()).extracting(GasAudit.Item::attribute)
        .contains("Appraisal Forest Zone", "Non Appraised Rate Type");
    assertThat(first.items()).allSatisfy(change -> {
      assertThat(change.rateId()).isNull();
      assertThat(change.comment()).isNull();
    });
    assertThat(second.items()).extracting(GasAudit.Item::attribute).containsExactly("Rate Adjustment Type");
    assertThat(second.items()).extracting(GasAudit.Item::value).containsExactly("F");
  }

  @Test
  void gasHistoryHasStablePagesWhenManyChangesShareOneTimestamp() {
    gasAuditTransaction(1, "Synthetic same-second updates");
    gasRateSnapshot(3000, 50003, 301, 1, "2026-01-01T10:00:00");
    jdbc.update("UPDATE NON_APPRAISED_STUMPAGE_RTE_AUD SET RESERVE_STUMPAGE_RATE = 0 WHERE NON_APPRAISED_STMPG_RTE_AUD_ID = 3000");
    for (int index = 1; index <= 12; index++) {
      gasRateSnapshot(3000 + index, 50003, 301, 1, "2026-01-02T10:00:00");
      jdbc.update("UPDATE NON_APPRAISED_STUMPAGE_RTE_AUD SET RESERVE_STUMPAGE_RATE = ? WHERE NON_APPRAISED_STMPG_RTE_AUD_ID = ?", index, 3000 + index);
    }
    var reader = new OracleGasAudit(jdbc);
    var first = reader.history(CARIBOO, "301", 0).orElseThrow();
    var second = reader.history(CARIBOO, "301", 1).orElseThrow();
    assertThat(first.total()).isEqualTo(12);
    assertThat(first.items()).hasSize(10).doesNotContainAnyElementsOf(second.items());
    assertThat(first.items().getFirst().eventId()).isEqualTo("R:3012:7");
    assertThat(first.items().getFirst().value()).isEqualTo("12.00");
    assertThat(second.items()).extracting(GasAudit.Item::eventId).containsExactly("R:3002:7", "R:3001:7");
    assertThat(reader.history(CARIBOO, "301", 0).orElseThrow()).isEqualTo(first);
    var beyondEnd = reader.history(CARIBOO, "301", Integer.MAX_VALUE).orElseThrow();
    assertThat(beyondEnd.total()).isEqualTo(12);
    assertThat(beyondEnd.items()).isEmpty();
  }

  @Test
  void gasHistoryRequiresTheCurrentAuthorizedParentAndExcludesOtherFamilies() {
    var reader = new OracleGasAudit(jdbc);
    assertThat(reader.history(CARIBOO, "301", 0).orElseThrow().items()).isEmpty();
    assertThat(reader.history(OMINECA, "301", 0)).isEmpty();
    assertThat(reader.history(idir(), "301", 0)).isEmpty();
    gasRateSnapshot(4001, 50004, 301, 1, "2026-01-01T10:00:00");
    gasRateSnapshot(4002, 50004, 301, 1, "2026-01-02T10:00:00");
    jdbc.update("UPDATE NON_APPRAISED_STUMPAGE_RTE_AUD SET APPRAISED_WORKSHEET_ID = 101");
    jdbc.update("UPDATE NON_APPRAISED_STUMPAGE_RTE_AUD SET RESERVE_STUMPAGE_RATE = 99 WHERE NON_APPRAISED_STMPG_RTE_AUD_ID = 4002");
    assertThat(reader.history(CARIBOO, "301", 0).orElseThrow().total()).isZero();
    jdbc.update("UPDATE NON_APPRAISED_STUMPAGE_RTE_AUD SET APPRAISED_WORKSHEET_ID = NULL, HISTORIC_APPRAISED_WRKSHEET_ID = 201");
    assertThat(reader.history(CARIBOO, "301", 0).orElseThrow().total()).isZero();
    jdbc.update("UPDATE NON_APPRAISED_STUMPAGE_RTE_AUD SET NON_APPRAISED_WORKSHEET_ID = 9999, HISTORIC_APPRAISED_WRKSHEET_ID = NULL");
    assertThat(reader.history(ADMIN, "9999", 0)).isEmpty();
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
    assertThat(permit.ftaStatus()).isEqualTo("Active");
    assertThat(permit.markStatus()).isEqualTo(new CodeOption("A", "Synthetic permit issued"));
    assertThat(permit.cruiseBased()).isNull();
    var privateMark = fta.find(CARIBOO, "A00003", "PM0001").orElseThrow();
    assertThat(privateMark.markExpiryDate()).isNull();
    assertThat(privateMark.cuttingPermit()).isNull();
    assertThat(privateMark.markStatus()).isEqualTo(new CodeOption("A", "Synthetic private status"));
    assertThat(privateMark.cruiseBased()).isNull();
    var road = fta.find(CARIBOO, null, "RM0001").orElseThrow();
    assertThat(road.forestDistrict()).isEqualTo("Synthetic district A");
    assertThat(road.markStatus()).isEqualTo(new CodeOption("A", "Active"));
    assertThat(road.cruiseBased()).isNull();
    assertThat(new OracleGasSearch(jdbc).search(CARIBOO, gas("A00003", "PM0001", 0)).total()).isZero();
    assertThat(fta.find(OMINECA, "A00001", "AA0001")).isEmpty();
  }

  @Test
  void ftaCruiseContextDistinguishesYesNoAndUnspecifiedWithoutChangingLicenceStatus() {
    var reader = new OracleFtaLicenceInformation(jdbc);
    for (String yes : List.of("Y", "y")) {
      jdbc.update("UPDATE HARVESTING_AUTHORITY SET CRUISE_BASED_IND = ? WHERE HVA_SKEY = 101", yes);
      assertThat(reader.find(CARIBOO, null, "AA0001").orElseThrow().cruiseBased()).isTrue();
    }
    for (String no : List.of("N", "n")) {
      jdbc.update("UPDATE HARVESTING_AUTHORITY SET CRUISE_BASED_IND = ? WHERE HVA_SKEY = 101", no);
      assertThat(reader.find(CARIBOO, null, "AA0001").orElseThrow().cruiseBased()).isFalse();
    }
    jdbc.update("UPDATE HARVESTING_AUTHORITY SET CRUISE_BASED_IND = NULL WHERE HVA_SKEY = 101");
    assertThat(reader.find(CARIBOO, null, "AA0001").orElseThrow().cruiseBased()).isNull();
    jdbc.update("UPDATE HARVESTING_AUTHORITY SET CRUISE_BASED_IND = 'X' WHERE HVA_SKEY = 101");
    var unknown = reader.find(CARIBOO, null, "AA0001").orElseThrow();
    assertThat(unknown.cruiseBased()).isNull();
    assertThat(unknown.ftaStatus()).isEqualTo("Active");
    assertThat(unknown.markStatus().description()).isEqualTo("Synthetic permit issued");
  }

  @Test
  void ftaContextRejectsConflictingPermitStatusOrCruiseWithinTheGrantedScope() {
    var reader = new OracleFtaLicenceInformation(jdbc);
    jdbc.update("INSERT INTO HARVESTING_AUTHORITY VALUES (103, 'A00001', '004', 10, 10, 'A', DATE '2030-12-31', NULL, '12', 'U', NULL)");
    jdbc.update("INSERT INTO HARVESTING_HAULING_XREF VALUES ('AA0001', 103, 'N')");
    assertThat(reader.find(CARIBOO, null, "AA0001").orElseThrow().cuttingPermit()).isEqualTo("001, 004");
    jdbc.update("UPDATE HARVESTING_AUTHORITY SET CRUISE_BASED_IND = 'Y' WHERE HVA_SKEY = 101");
    assertThatThrownBy(() -> reader.find(CARIBOO, null, "AA0001"))
        .isInstanceOf(IncorrectResultSizeDataAccessException.class);
    jdbc.update("UPDATE HARVESTING_AUTHORITY SET CRUISE_BASED_IND = 'Y' WHERE HVA_SKEY = 103");
    assertThat(reader.find(CARIBOO, null, "AA0001").orElseThrow().cruiseBased()).isTrue();
    jdbc.update("INSERT INTO HARVEST_AUTH_STATUS_CODE VALUES ('B', 'Synthetic alternate mark status')");
    jdbc.update("UPDATE HARVESTING_AUTHORITY SET HARVEST_AUTH_STATUS_CODE = 'B' WHERE HVA_SKEY = 103");
    assertThatThrownBy(() -> reader.find(CARIBOO, null, "AA0001"))
        .isInstanceOf(IncorrectResultSizeDataAccessException.class);
    jdbc.update("UPDATE HARVESTING_AUTHORITY SET FOREST_DISTRICT = 20 WHERE HVA_SKEY = 103");
    var scoped = reader.find(CARIBOO, null, "AA0001").orElseThrow();
    assertThat(scoped.markStatus()).isEqualTo(new CodeOption("A", "Synthetic permit issued"));
    assertThat(scoped.cuttingPermit()).isEqualTo("001");
    assertThatThrownBy(() -> reader.find(ADMIN, null, "AA0001"))
        .isInstanceOf(IncorrectResultSizeDataAccessException.class);
  }

  @Test
  void permitAggregationCannotExposeSiblingPermitsOutsideTheGrantedDistrict() {
    jdbc.update("INSERT INTO HARVESTING_AUTHORITY VALUES (103, 'A00001', '004', 10, 10, 'A', DATE '2030-12-31', NULL, '12', 'U', NULL)");
    jdbc.update("INSERT INTO HARVESTING_HAULING_XREF VALUES ('AA0001', 103, 'N')");
    jdbc.update("INSERT INTO HARVESTING_AUTHORITY VALUES (104, 'A00001', 'SECRET', 20, 20, 'A', DATE '2030-12-31', NULL, '12', 'U', NULL)");
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
    jdbc.update("INSERT INTO HARVESTING_AUTHORITY VALUES (103, 'A00001', '004', 10, 10, 'A', DATE '2030-12-31', NULL, '12', 'U', NULL)");
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
  void assignedQueuesKeepCallerIdentityScopeAndDuplicateAssignmentsSeparate() {
    queueStatuses("RGN", "SUB");
    jdbc.update("UPDATE APPRAISAL_DATA_SUBMISSION SET APPRAISAL_STATUS_CODE = 'RGN'");
    jdbc.update("UPDATE APPRAISAL_DATA_SUBMISSION SET ENTRY_USERID = 'IDIR\\SYNTHETIC' WHERE ECAS_ID = 1001");
    jdbc.update("INSERT INTO ADS_ASSIGNED_TO_USER VALUES (1001, 'IDIR\\SYNTHETIC')");
    jdbc.update("INSERT INTO ADS_ASSIGNED_TO_USER VALUES (1001, 'IDIR\\SYNTHETIC')");
    jdbc.update("INSERT INTO ADS_ASSIGNED_TO_USER VALUES (1002, 'IDIR\\SYNTHETIC')");
    var reader = new OracleEcasInbox(jdbc);
    var filter = new InboxFilters();
    filter.mode = EcasInbox.Mode.MY_TO_DO;
    var assigned = trustedIdir("IDIR\\SYNTHETIC", "TAPS_REGION_APPRAISER_REGION-CARIBOO");

    var page = reader.search(assigned, filter.search(), 0);
    assertThat(page.total()).isEqualTo(1);
    assertThat(page.items()).extracting(EcasInbox.Item::ecasId).containsExactly("1001");
    filter.workedOn = " idir\\synthetic ";
    assertThat(reader.search(assigned, filter.search(), 0).total()).isEqualTo(1);
    assertThat(reader.search(trustedIdir("IDIR\\UNASSIGNED", "TAPS_REGION_APPRAISER_REGION-CARIBOO"),
        filter.search(), 0).total()).isZero();

    filter.workedOn = null;
    var mixed = trustedIdir("IDIR\\SYNTHETIC", "TAPS_REGION_APPRAISER_REGION-CARIBOO", "TAPS_VIEWER_DISTRICT-DOM");
    assertThat(reader.search(mixed, filter.search(), 0).items())
        .extracting(EcasInbox.Item::ecasId).containsExactly("1001");
    jdbc.update("UPDATE APPRAISAL_DATA_SUBMISSION SET APPRAISAL_STATUS_CODE = 'SUB' WHERE ECAS_ID = 1002");
    jdbc.update("DELETE FROM ADS_ASSIGNED_TO_USER WHERE ECAS_ID = 1002");
    assertThat(reader.search(mixed, filter.search(), 0).items())
        .extracting(EcasInbox.Item::ecasId).containsExactly("1002", "1001");
  }

  @Test
  void regionalQueuesRetainOracleNullSemanticsAndRequireTheTenureRow() {
    queueStatuses("RGN", "VER", "DTR");
    jdbc.update("INSERT INTO ADS_ASSIGNED_TO_USER VALUES (1001, 'IDIR\\SYNTHETIC')");
    var reader = new OracleEcasInbox(jdbc);
    var regional = trustedIdir("IDIR\\SYNTHETIC", "TAPS_REGION_APPRAISER_REGION-CARIBOO");
    var filter = new InboxFilters();
    filter.mode = EcasInbox.Mode.MY_TO_DO;
    for (String status : List.of("VER", "DTR")) {
      jdbc.update("UPDATE APPRAISAL_DATA_SUBMISSION SET APPRAISAL_STATUS_CODE = ? WHERE ECAS_ID = 1001", status);
      jdbc.update("UPDATE PROV_FOREST_USE SET SB_FUNDED_IND = 'N' WHERE FOREST_FILE_ID = 'A00001'");
      assertThat(reader.search(regional, filter.search(), 0).total()).isEqualTo(1);
      jdbc.update("UPDATE PROV_FOREST_USE SET SB_FUNDED_IND = 'Y' WHERE FOREST_FILE_ID = 'A00001'");
      assertThat(reader.search(regional, filter.search(), 0).total()).isZero();
      jdbc.update("UPDATE PROV_FOREST_USE SET SB_FUNDED_IND = NULL WHERE FOREST_FILE_ID = 'A00001'");
      assertThat(reader.search(regional, filter.search(), 0).total()).isZero();
    }
    jdbc.update("UPDATE APPRAISAL_DATA_SUBMISSION SET APPRAISAL_STATUS_CODE = 'RGN' WHERE ECAS_ID = 1001");
    assertThat(reader.search(regional, filter.search(), 0).total()).isEqualTo(1);
    jdbc.update("UPDATE APPRAISAL_DATA_SUBMISSION SET FOREST_FILE_ID = 'MISSING' WHERE ECAS_ID = 1001");
    assertThat(reader.search(regional, filter.search(), 0).total()).isZero();
    assertThat(reader.search(CARIBOO, new InboxFilters().search(), 0).total()).isEqualTo(1);
    filter.id = "1001";
    assertThat(reader.search(CARIBOO, filter.search(), 0).total()).isEqualTo(1);
  }

  @Test
  void directIdsRetainMinistryViewerQueueRestrictions() {
    queueStatuses("RGN", "SUB");
    jdbc.update("UPDATE APPRAISAL_DATA_SUBMISSION SET APPRAISAL_STATUS_CODE = 'RGN' WHERE ECAS_ID = 1001");
    var reader = new OracleEcasInbox(jdbc);
    var districtViewer = idir("TAPS_VIEWER_DISTRICT-DCA");
    var regionalViewer = idir("TAPS_REGION_CLERK_REGION-CARIBOO");
    var filter = new InboxFilters();
    filter.id = "1001";
    filter.mode = EcasInbox.Mode.MY_TO_DO;
    assertThat(reader.search(districtViewer, filter.search(), 0).total()).isZero();
    assertThat(reader.search(regionalViewer, filter.search(), 0).total()).isEqualTo(1);
    filter.mode = EcasInbox.Mode.ALL_SUBMISSIONS;
    assertThat(reader.search(districtViewer, filter.search(), 0).total()).isEqualTo(1);
    filter.mode = EcasInbox.Mode.MY_TO_DO;
    jdbc.update("UPDATE APPRAISAL_DATA_SUBMISSION SET APPRAISAL_STATUS_CODE = 'SUB' WHERE ECAS_ID = 1001");
    assertThat(reader.search(districtViewer, filter.search(), 0).total()).isEqualTo(1);
    assertThat(reader.search(regionalViewer, filter.search(), 0).total()).isZero();
  }

  @Test
  void statusOnlyQueuesNeedNoAssignmentIdentityWhileHeadquartersDoes() {
    var reader = new OracleEcasInbox(jdbc);
    var filter = new InboxFilters();
    filter.mode = EcasInbox.Mode.MY_TO_DO;
    var licensee = user(IdentityProvider.BCEID_BUSINESS, "TAPS_LICENSEE_FOREST_CLIENT-00000001");
    var clientViewer = user(IdentityProvider.BCEID_BUSINESS, "TAPS_LICENSEE_VIEWER_FOREST_CLIENT-00000001");
    var bcts = idir("TAPS_BCTS_FOREST_CLIENT-00000001");
    assertThat(reader.search(ADMIN, filter.search(), 0).total()).isZero();
    assertThat(reader.search(licensee, filter.search(), 0).total()).isZero();
    assertThat(reader.search(bcts, filter.search(), 0).total()).isZero();
    assertThat(reader.search(clientViewer, filter.search(), 0).total()).isEqualTo(1);
    jdbc.update("UPDATE APPRAISAL_DATA_SUBMISSION SET APPRAISAL_STATUS_CODE = 'DFT' WHERE ECAS_ID = 1001");
    assertThat(reader.search(ADMIN, filter.search(), 0).total()).isEqualTo(1);
    assertThat(reader.search(licensee, filter.search(), 0).total()).isEqualTo(1);
    assertThat(reader.search(bcts, filter.search(), 0).total()).isEqualTo(1);
    jdbc.update("UPDATE APPRAISAL_DATA_SUBMISSION SET APPRAISAL_STATUS_CODE = 'SCN' WHERE ECAS_ID = 1001");
    assertThat(reader.search(ADMIN, filter.search(), 0).total()).isEqualTo(1);
    assertThat(reader.search(clientViewer, filter.search(), 0).total()).isZero();
    assertThat(reader.search(licensee, filter.search(), 0).total()).isZero();
    assertThat(reader.search(bcts, filter.search(), 0).total()).isZero();
    jdbc.update("UPDATE APPRAISAL_DATA_SUBMISSION SET APPRAISAL_STATUS_CODE = 'CON' WHERE ECAS_ID = 1001");
    jdbc.update("INSERT INTO ADS_ASSIGNED_TO_USER VALUES (1001, 'IDIR\\SYNTHETIC')");
    assertThat(reader.search(trustedIdir("IDIR\\SYNTHETIC", "TAPS_HEADQUARTERS"), filter.search(), 0).total()).isEqualTo(1);
    assertThatThrownBy(() -> reader.search(idir("TAPS_HEADQUARTERS"), filter.search(), 0))
        .isInstanceOf(IllegalArgumentException.class);
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

    queueStatuses("RGN");
    jdbc.update("UPDATE APPRAISAL_DATA_SUBMISSION SET APPRAISAL_STATUS_CODE = 'RGN' WHERE ECAS_ID = 1001 OR ECAS_ID BETWEEN 2000 AND 2100");
    jdbc.update("INSERT INTO ADS_ASSIGNED_TO_USER SELECT ECAS_ID, 'IDIR\\SYNTHETIC' FROM APPRAISAL_DATA_SUBMISSION WHERE ECAS_ID = 1001 OR ECAS_ID BETWEEN 2000 AND 2100");
    var queued = new InboxFilters();
    queued.mode = EcasInbox.Mode.MY_TO_DO;
    var assigned = trustedIdir("IDIR\\SYNTHETIC", "TAPS_REGION_APPRAISER_REGION-CARIBOO");
    assertThat(reader.search(assigned, queued.search(), 0).total()).isEqualTo(102);
    assertThat(reader.search(assigned, queued.search(), 0).items()).hasSize(100);
    assertThat(reader.search(assigned, queued.search(), 1).items()).extracting(EcasInbox.Item::ecasId)
        .containsExactly("2000", "1001");
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
    assertThat(coast.header().coniferousStandRateEligibility())
        .isEqualTo(new CodeOption("S", "Sawlog Grades"));
    assertThat(coast.header().deciduousStandRateEligibility())
        .isEqualTo(new CodeOption("N", "No Grades"));
    var interior = reader.interior(OMINECA, "1002").orElseThrow();
    assertThat(interior.sellingPriceZoneCode()).isEqualTo("2");
    assertThat(interior.comparativeCruise()).isFalse();
    assertThat(interior.salvage()).isNull();
    assertThat(interior.timberMarkRevisionCount()).isNull();
    assertThat(interior.header().coniferousStandRateEligibility())
        .isEqualTo(new CodeOption(null, null));
    assertThat(interior.header().deciduousStandRateEligibility())
        .isEqualTo(new CodeOption(null, null));
    assertThat(reader.coast(OMINECA, "1001")).isEmpty();
    assertThat(reader.interior(CARIBOO, "1002")).isEmpty();
    assertThat(reader.interior(ADMIN, "1001")).isEmpty();
  }

  @Test
  void conflictingFtaReferenceParentsAreRejected() {
    jdbc.update("INSERT INTO HARVESTING_AUTHORITY VALUES (103, 'A00001', '004', 10, 10, 'A', DATE '2030-12-31', NULL, '12', 'U', NULL)");
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
          .andExpect(status().isOk()).andExpect(jsonPath("$.readApiEnabled").value(true))
          .andExpect(jsonPath("$.ecasMyToDoAvailable").value(true));
      mvc.perform(post("/api/ecas/inbox").header("Authorization", "Bearer fixture-cariboo")
          .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"MY_TO_DO\"}"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
      mvc.perform(post("/api/ecas/inbox").header("Authorization", "Bearer fixture-cariboo")
          .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"ALL_SUBMISSIONS\"}"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
          .andExpect(jsonPath("$.items[0].ecasId").value("1001"));
      mvc.perform(get("/api/ecas/references/C/1001").header("Authorization", "Bearer fixture-cariboo"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.timberMarks.length()").value(2))
          .andExpect(jsonPath("$.header.effectiveDate").value("2026-01-02"));
      mvc.perform(get("/api/gas/appraised/by-ecas/1001").header("Authorization", "Bearer fixture-cariboo"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.key.worksheetId").value("101"))
          .andExpect(jsonPath("$.primaryTimberMark").value("AA0001"))
          .andExpect(jsonPath("$.rates[0].totalStumpageRate").value("12.30"));
      mvc.perform(get("/api/gas/worksheets").header("Authorization", "Bearer fixture-cariboo"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(4));
      mvc.perform(get("/api/gas/worksheets/HISTORIC/201").header("Authorization", "Bearer fixture-cariboo"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.rates[0].totalStumpageRate").value("19.25"))
          .andExpect(jsonPath("$.historicSpecies[0].speciesVolume").value("9999999"))
          .andExpect(jsonPath("$.coastSpeciesGrades[1].scaleProductCode").value("02"));
      mvc.perform(get("/api/gas/worksheets/NON_APPRAISED/301").header("Authorization", "Bearer fixture-cariboo"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.rates[0].reserveStumpageRate").value("1.25"))
          .andExpect(jsonPath("$.referenceType.description").value("Synthetic reference"))
          .andExpect(jsonPath("$.rates[0].scaleSpecies.description").value("Synthetic fir"))
          .andExpect(jsonPath("$.rates[0].upsetStumpageRate").value("3.75"))
          .andExpect(jsonPath("$.rates[0].totalStumpageRate").value("3.75"))
          .andExpect(jsonPath("$.selectedRateAddons[0].code").value("EXPIRED"));
      mvc.perform(get("/api/gas/worksheets/NON_APPRAISED/301/history").header("Authorization", "Bearer fixture-cariboo"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.key.type").value("NON_APPRAISED"))
          .andExpect(jsonPath("$.key.worksheetId").value("301"))
          .andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.size").value(10));
      mvc.perform(get("/api/gas/worksheets/NON_APPRAISED/301/history").header("Authorization", "Bearer fixture-omineca"))
          .andExpect(status().isNotFound());
      mvc.perform(get("/api/gas/licences/A00001/marks").header("Authorization", "Bearer fixture-cariboo"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.timberMarks.length()").value(2));
      mvc.perform(get("/api/gas/licence-information?timberMark=PM0001")
          .header("Authorization", "Bearer fixture-cariboo"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.licenceNumber").value("A00003"))
          .andExpect(jsonPath("$.markStatus.description").value("Synthetic private status"))
          .andExpect(jsonPath("$.cruiseBased").isEmpty());
      mvc.perform(get("/api/gas/licence-information?timberMark=AA0001")
          .header("Authorization", "Bearer fixture-cariboo"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.ftaStatus").value("Active"))
          .andExpect(jsonPath("$.markStatus.description").value("Synthetic permit issued"));
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

  private static TapsUser trustedIdir(String account, String... roles) {
    var user = idir(roles);
    return new TapsUser(account, user.displayName(), user.email(), user.identityProvider(),
        user.businessName(), user.grants(), account);
  }

  private void gasAuditTransaction(long id, String comment) {
    jdbc.update("INSERT INTO GAS_TRANSACTION VALUES (?, ?, DATE '2026-01-01', 'SYNTHETIC', DATE '2026-01-01', 'SYNTHETIC')", id, comment);
  }

  private void gasWorksheetSnapshot(long auditId, long worksheetId, long transactionId, String modified) {
    var timestamp = java.sql.Timestamp.valueOf(LocalDateTime.parse(modified));
    jdbc.update("""
        INSERT INTO NON_APPRAISED_WORKSHEET_AUD
        SELECT ?, NON_APPRAISED_WORKSHEET_ID, ?, TIMBER_MARK, EFFECTIVE_DATE, EXPIRY_DATE,
               APPRAISAL_METHOD_CODE, NON_APPRAISED_STATUS_CODE, WORKSHEET_REFERENCE_TYPE_CODE,
               SDM_DECLARATION_ACCEPTANCE_DT, TSB_NUMBER_CODE, APPRAISAL_FOREST_ZONE_CODE,
               RATE_ADJUSTMENT_TYPE_CODE, NON_APPRAISED_RATE_TYPE_CODE,
               'SYNTHETIC', ?, 'SYNTHETIC', ?
          FROM NON_APPRAISED_WORKSHEET WHERE NON_APPRAISED_WORKSHEET_ID = ?
        """, auditId, transactionId, timestamp, timestamp, worksheetId);
  }

  private void gasRateSnapshot(long auditId, long rateId, long worksheetId, long transactionId, String modified) {
    var timestamp = java.sql.Timestamp.valueOf(LocalDateTime.parse(modified));
    jdbc.update("""
        INSERT INTO NON_APPRAISED_STUMPAGE_RTE_AUD VALUES
        (?, ?, ?, 'FI', '01', 'A', NULL, NULL, NULL, 1.00, 'N', 'N', NULL,
         NULL, ?, NULL, ?, 'SYNTHETIC', ?, 'SYNTHETIC')
        """, auditId, rateId, transactionId, worksheetId, timestamp, timestamp);
  }

  private void queueStatuses(String... statuses) {
    for (String status : statuses) {
      jdbc.update("INSERT INTO APPRAISAL_STATUS_CODE VALUES (?, 'Synthetic queue status', DATE '2000-01-01', DATE '9999-12-31', NULL)", status);
    }
  }

  private static TapsUser user(IdentityProvider provider, String... roles) {
    return new TapsUser("synthetic-user", "Synthetic User", null, provider, null,
        Arrays.stream(roles).map(FamRoleName::parse).map(role -> RoleGrant.accept(role, provider).orElseThrow()).toList());
  }

  private static final class InboxFilters {
    EcasInbox.Mode mode = EcasInbox.Mode.ALL_SUBMISSIONS;
    String id;
    String mark;
    List<String> orgs = List.of();
    List<String> statuses = List.of();
    DateRange statusDates;
    String workedOn;
    List<EcasInbox.DateType> dateTypes = List.of();
    DateRange dates;

    EcasInbox.Search search() {
      return new EcasInbox.Search(mode, null, id, null, null, mark,
          null, null, orgs, null, null, statuses, statusDates, null, null, null, null, null,
          workedOn, dateTypes, dates, null, null);
    }
  }
}
