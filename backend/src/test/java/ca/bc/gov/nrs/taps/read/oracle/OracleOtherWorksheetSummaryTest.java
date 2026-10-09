package ca.bc.gov.nrs.taps.read.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.taps.domain.AppraisalMethod;
import ca.bc.gov.nrs.taps.read.CodeOption;
import ca.bc.gov.nrs.taps.read.GasAppraisal;
import ca.bc.gov.nrs.taps.security.FamRoleName;
import ca.bc.gov.nrs.taps.security.IdentityProvider;
import ca.bc.gov.nrs.taps.security.RoleGrant;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class OracleOtherWorksheetSummaryTest {
  private final DataSource dataSource = mock(DataSource.class);
  private final Connection connection = mock(Connection.class);
  private final PreparedStatement statement = mock(PreparedStatement.class);
  private final ResultSet rows = mock(ResultSet.class);
  private final OracleOtherWorksheetSummary summaries = new OracleOtherWorksheetSummary(new JdbcTemplate(dataSource));

  @BeforeEach
  void prepareJdbcBoundary() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(rows);
  }

  @ParameterizedTest
  @EnumSource(value = GasAppraisal.WorksheetType.class, names = {"HISTORIC", "NON_APPRAISED"})
  void scopesExactFamilyAndAllChildrenWithinOneStatement(GasAppraisal.WorksheetType type) throws SQLException {
    assertThat(read(type, idir("TAPS_VIEWER_DISTRICT-DXX", "TAPS_DISTRICT_APPRAISER_DISTRICT-DZZ",
        "TAPS_REGION_APPRAISER_REGION-CARIBOO"))).isEmpty();

    String sql = sql();
    assertThat(sql).contains("record_scope.WORKSHEET_ID = ? AND (record_scope.ADMIN_DISTRICT_CODE = ?"
        + " OR record_scope.ROLLUP_REGION_CODE = ?)",
        "FFC.FOREST_FILE_CLIENT_TYPE_CODE = 'A'", "REGION.ORG_UNIT_NO = DISTRICT.ROLLUP_REGION_NO",
        "SYSDATE BETWEEN C.EFFECTIVE_DATE AND C.EXPIRY_DATE",
        "ORDER BY ROW_KIND, RATE_EFFECTIVE_DATE, SCALE_SPECIES_CODE, SCALE_PRODUCT_CODE, SCALE_GRADE_CODE, RATE_ID")
        .doesNotContain("STATUS_CODE NOT IN", "998877", "DZZ", "RCB", "DXX",
            "GAS2.", "FIND_", "UPDATE ", "INSERT ", "DELETE ");
    if (type == GasAppraisal.WorksheetType.HISTORIC) {
      assertThat(sql).contains("FROM HISTORIC_APPRAISED_WORKSHEET W",
          "FROM APPRAISAL_STATUS_CODE C", "R.HISTORIC_APPRAISED_WRKSHEET_ID = P.WORKSHEET_ID",
          "JOIN APPRAISED_STUMPAGE_RATE R", "JOIN NON_APPRAISED_STUMPAGE_RATE R", "record_scope.ACTIVE_IND = 'Y'",
          "JOIN HISTORIC_SPECIES S ON S.HISTORIC_APPRAISED_WRKSHEET_ID = P.WORKSHEET_ID",
          "JOIN HISTORIC_COAST_SPECIES_GRADE G ON G.HISTORIC_APPRAISED_WRKSHEET_ID = P.WORKSHEET_ID")
          .doesNotContain("FROM NON_APPRAISED_WORKSHEET W", "R.NON_APPRAISED_WORKSHEET_ID = P.WORKSHEET_ID");
    } else {
      assertThat(sql).contains("FROM NON_APPRAISED_WORKSHEET W", "FROM NON_APPRAISED_STATUS_CODE C",
          "R.NON_APPRAISED_WORKSHEET_ID = P.WORKSHEET_ID",
          "JOIN NON_APPRAISED_WS_RATE_ADDON A ON A.NON_APPRAISED_WORKSHEET_ID = P.WORKSHEET_ID",
          "JOIN NON_APPRAISED_RATE_ADDON_CODE C")
          .doesNotContain("FROM HISTORIC_APPRAISED_WORKSHEET W", "JOIN APPRAISED_STUMPAGE_RATE R",
              "R.HISTORIC_APPRAISED_WRKSHEET_ID = P.WORKSHEET_ID", "ACTIVE_IND = 'Y'");
    }
    verify(statement).setLong(1, 998877L);
    verify(statement).setString(2, "DZZ");
    verify(statement).setString(3, "RCB");
    verify(statement).executeQuery();
    verify(connection, never()).createStatement();
    cleanup(true);
  }

  @ParameterizedTest
  @EnumSource(value = GasAppraisal.WorksheetType.class, names = {"HISTORIC", "NON_APPRAISED"})
  void directIdDoesNotBorrowUnrelatedCapability(GasAppraisal.WorksheetType type) throws SQLException {
    assertThat(read(type, idir("TAPS_HEADQUARTERS", "TAPS_VIEWER_DISTRICT-DZZ"))).isEmpty();
    assertThat(sql()).contains("record_scope.WORKSHEET_ID = ? AND (1 = 0)");
  }

  @Test
  void wrongFamilyIsRejectedBeforeAnyDatabaseRead() {
    var user = idir("TAPS_ADMIN");
    assertThatThrownBy(() -> summaries.historic(user, key(GasAppraisal.WorksheetType.APPRAISED)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> summaries.historic(user, key(GasAppraisal.WorksheetType.NON_APPRAISED)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> summaries.nonAppraised(user, key(GasAppraisal.WorksheetType.HISTORIC)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> summaries.nonAppraised(user, key(GasAppraisal.WorksheetType.APPRAISED)))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(dataSource);
  }

  @Test
  void historicPreservesHeaderAndBothRateFamiliesWithoutMultiplication() throws SQLException {
    parent("C", "MPS", "Y");
    when(rows.next()).thenReturn(true, true, true, true, false);
    when(rows.getInt("ROW_KIND")).thenReturn(0, 1, 2, 2);
    when(rows.getString("HISTORIC_PARENT_ID")).thenReturn("998877");
    when(rows.getString("RATE_ID")).thenReturn("1", "2", "3");
    when(rows.getDate("RATE_EFFECTIVE_DATE")).thenReturn(Date.valueOf("2030-01-01"));
    when(rows.getBigDecimal("RATE_AMOUNT")).thenReturn(new BigDecimal("12.30"));
    nonAppraisedRate();

    var summary = summaries.historic(idir("TAPS_ADMIN"), key(GasAppraisal.WorksheetType.HISTORIC)).orElseThrow();

    assertThat(summary.key()).isEqualTo(key(GasAppraisal.WorksheetType.HISTORIC));
    assertThat(summary.licence()).isEqualTo(" A00001 ");
    assertThat(summary.timberMark()).isEqualTo("ABC123");
    assertThat(summary.appraisalMethod()).isEqualTo(AppraisalMethod.C);
    assertThat(summary.variant()).isEqualTo(GasAppraisal.SummaryVariant.COAST_MPS_TOA_Y);
    assertThat(summary.tenureObligationAdjustment()).isTrue();
    assertThat(summary.adjustQuarterly()).isFalse();
    assertThat(summary.active()).isTrue();
    assertThat(summary.policyVersion()).isEqualTo(" Policy ");
    assertThat(summary.status()).isEqualTo(new CodeOption("DET", " Determined "));
    assertThat(summary.effectiveDate()).isEqualTo(LocalDate.of(2030, 1, 1));
    assertThat(summary.expiryDate()).isNull();
    assertThat(summary.ceaseAdjustmentDate()).isEqualTo(LocalDate.of(2030, 9, 30));
    assertThat(summary.rates()).containsExactly(new GasAppraisal.StoredRate("1",
        LocalDate.of(2030, 1, 1), new BigDecimal("12.30")));
    assertThat(summary.nonAppraisedRates()).extracting(GasAppraisal.StoredNonAppraisedRate::rateId)
        .containsExactly("2", "3");
    assertThat(summary.nonAppraisedRates().getFirst().bonusBidAmount()).isNull();
    assertThat(summary.nonAppraisedRates().getFirst().developmentLevy()).isEqualTo(new BigDecimal("0.00"));
    assertThat(summary.nonAppraisedRates().getFirst().scaleSpecies())
        .isEqualTo(new CodeOption(" SP ", "Synthetic species"));
    assertThat(summary.nonAppraisedRates().getFirst().upsetStumpageRate()).isEqualTo(new BigDecimal("3.90"));
    assertThat(summary.nonAppraisedRates().getFirst().totalStumpageRate()).isEqualTo(new BigDecimal("3.90"));
    cleanup(true);
  }

  @ParameterizedTest
  @MethodSource("variants")
  void historicUsesStoredFourWaySummaryDispatch(String method, String calculation, String toa,
      GasAppraisal.SummaryVariant expected) throws SQLException {
    parent(method, calculation, toa);
    when(rows.next()).thenReturn(true, false);
    var summary = summaries.historic(idir("TAPS_ADMIN"), key(GasAppraisal.WorksheetType.HISTORIC)).orElseThrow();
    assertThat(summary.variant()).isEqualTo(expected);
    assertThat(summary.rates()).isEmpty();
    assertThat(summary.nonAppraisedRates()).isEmpty();
  }

  @Test
  void nonAppraisedRetainsHeaderValuesAndNullableRawComponents() throws SQLException {
    parent("I", null, null);
    when(rows.next()).thenReturn(true, true, false);
    when(rows.getInt("ROW_KIND")).thenReturn(0, 2);
    when(rows.getString("NON_APPRAISED_PARENT_ID")).thenReturn("998877");
    when(rows.getString("RATE_ID")).thenReturn("3");
    when(rows.getString("REFERENCE_TYPE")).thenReturn(" REF ");
    when(rows.getDate("SDM_DECLARATION_ACCEPTANCE_DT")).thenReturn(Date.valueOf("2030-02-01"));
    when(rows.getString("TSB_NUMBER_CODE")).thenReturn(" T1 ");
    when(rows.getString("APPRAISAL_FOREST_ZONE_CODE")).thenReturn(" Z1 ");
    when(rows.getString("NON_APPRAISED_RATE_TYPE_CODE")).thenReturn(" TYPE ");
    when(rows.getString("RATE_ADJUSTMENT_TYPE_CODE")).thenReturn("R");
    when(rows.getString("REFERENCE_TYPE_DESCRIPTION")).thenReturn("Synthetic reference");
    when(rows.getString("APPRAISAL_FOREST_ZONE_DESCRIPTION")).thenReturn("Synthetic zone");
    when(rows.getString("NON_APPRAISED_RATE_TYPE_DESCRIPTION")).thenReturn("Synthetic rate type");
    when(rows.getString("RATE_ADJUSTMENT_TYPE_DESCRIPTION")).thenReturn("Synthetic adjustment");
    nonAppraisedRate();

    var summary = summaries.nonAppraised(idir("TAPS_ADMIN"), key(GasAppraisal.WorksheetType.NON_APPRAISED)).orElseThrow();

    assertThat(summary.key()).isEqualTo(key(GasAppraisal.WorksheetType.NON_APPRAISED));
    assertThat(summary.appraisalMethod()).isEqualTo(AppraisalMethod.I);
    assertThat(summary.licence()).isEqualTo(" A00001 ");
    assertThat(summary.timberMark()).isEqualTo("ABC123");
    assertThat(summary.expiryDate()).isNull();
    assertThat(summary.referenceType()).isEqualTo(new CodeOption(" REF ", "Synthetic reference"));
    assertThat(summary.sdmDeclarationAcceptanceDate()).isEqualTo(LocalDate.of(2030, 2, 1));
    assertThat(summary.tsbNumberCode()).isEqualTo(" T1 ");
    assertThat(summary.appraisalForestZone()).isEqualTo(new CodeOption(" Z1 ", "Synthetic zone"));
    assertThat(summary.nonAppraisedRateType()).isEqualTo(new CodeOption(" TYPE ", "Synthetic rate type"));
    assertThat(summary.rateAdjustmentType()).isEqualTo(new CodeOption("R", "Synthetic adjustment"));
    assertThat(summary.rates()).containsExactly(new GasAppraisal.StoredNonAppraisedRate("3",
        new CodeOption(" SP ", "Synthetic species"), new CodeOption("PL", "Synthetic product"),
        new CodeOption("A", "Synthetic grade"), new BigDecimal("5.10"), null,
        new BigDecimal("0.00"), new BigDecimal("-1.20")));
    assertThatThrownBy(() -> summary.rates().clear()).isInstanceOf(UnsupportedOperationException.class);
  }

  @ParameterizedTest
  @EnumSource(value = GasAppraisal.WorksheetType.class, names = {"HISTORIC", "NON_APPRAISED"})
  void selectedScaleLabelsUseOnlyCodeAndCannotMultiplyRates(GasAppraisal.WorksheetType type)
      throws SQLException {
    read(type, idir("TAPS_ADMIN"));
    String sql = sql();
    String rateLabels = sql.substring(sql.indexOf("SELECT S.*,"));
    assertThat(rateLabels).contains(
        "CASE WHEN S.ROW_KIND = 2 THEN",
        "SELECT C.DESCRIPTION FROM SCALE_SPECIES_CODE C",
        "C.SCALE_SPECIES_CODE = S.SCALE_SPECIES_CODE",
        "SELECT C.DESCRIPTION FROM SCALE_PRODUCT_CODE C",
        "C.SCALE_PRODUCT_CODE = S.SCALE_PRODUCT_CODE",
        "SELECT C.DESCRIPTION FROM SCALE_GRADE_CODE C",
        "C.SCALE_GRADE_CODE = S.SCALE_GRADE_CODE",
        "AS SCALE_SPECIES_DESCRIPTION", "AS SCALE_PRODUCT_DESCRIPTION", "AS SCALE_GRADE_DESCRIPTION",
        "ORDER BY ROW_KIND, RATE_EFFECTIVE_DATE, SCALE_SPECIES_CODE, SCALE_PRODUCT_CODE, SCALE_GRADE_CODE, RATE_ID")
        .doesNotContain("SYSDATE", "EFFECTIVE_DATE AND", "JOIN SCALE_", "TRIM(", "ORDER BY DESCRIPTION");
  }

  @Test
  void worksheetClassificationLabelsHaveInclusiveCurrentValidityAndScalarCardinality()
      throws SQLException {
    summaries.nonAppraised(idir("TAPS_ADMIN"), key(GasAppraisal.WorksheetType.NON_APPRAISED));
    String sql = sql();
    for (String table : List.of("WORKSHEET_REFERENCE_TYPE_CODE", "APPRAISAL_FOREST_ZONE_CODE",
        "NON_APPRAISED_RATE_TYPE_CODE", "RATE_ADJUSTMENT_TYPE_CODE")) {
      assertThat(sql).contains("SELECT C.DESCRIPTION FROM " + table + " C")
          .doesNotContain("JOIN " + table);
    }
    assertThat(sql).contains("C.WORKSHEET_REFERENCE_TYPE_CODE = record_scope.REFERENCE_TYPE",
        "C.APPRAISAL_FOREST_ZONE_CODE = record_scope.APPRAISAL_FOREST_ZONE_CODE",
        "C.NON_APPRAISED_RATE_TYPE_CODE = record_scope.NON_APPRAISED_RATE_TYPE_CODE",
        "C.RATE_ADJUSTMENT_TYPE_CODE = record_scope.RATE_ADJUSTMENT_TYPE_CODE");
    assertThat(sql.split("SYSDATE BETWEEN C.EFFECTIVE_DATE AND C.EXPIRY_DATE", -1)).hasSize(6);
  }

  @Test
  void missingLabelsKeepClassificationAndLiteralSpaceRateCodesWithoutDroppingRows() throws SQLException {
    parent("I", null, null);
    when(rows.next()).thenReturn(true, true, false);
    when(rows.getInt("ROW_KIND")).thenReturn(0, 2);
    when(rows.getString("NON_APPRAISED_PARENT_ID")).thenReturn("998877");
    when(rows.getString("RATE_ID")).thenReturn("3");
    when(rows.getString("REFERENCE_TYPE")).thenReturn("UNKNOWN");
    when(rows.getString("APPRAISAL_FOREST_ZONE_CODE")).thenReturn("EXPIRED");
    when(rows.getString("NON_APPRAISED_RATE_TYPE_CODE")).thenReturn("UNKNOWN");
    when(rows.getString("RATE_ADJUSTMENT_TYPE_CODE")).thenReturn("EXPIRED");
    nonAppraisedRate();
    when(rows.getString("SCALE_PRODUCT_CODE")).thenReturn(" ");
    when(rows.getString("SCALE_GRADE_CODE")).thenReturn(" ");
    when(rows.getString("SCALE_SPECIES_DESCRIPTION")).thenReturn(null);
    when(rows.getString("SCALE_PRODUCT_DESCRIPTION")).thenReturn(null);
    when(rows.getString("SCALE_GRADE_DESCRIPTION")).thenReturn(null);
    var summary = summaries.nonAppraised(idir("TAPS_ADMIN"), key(GasAppraisal.WorksheetType.NON_APPRAISED)).orElseThrow();
    assertThat(summary.referenceType()).isEqualTo(new CodeOption("UNKNOWN", null));
    assertThat(summary.appraisalForestZone()).isEqualTo(new CodeOption("EXPIRED", null));
    assertThat(summary.nonAppraisedRateType()).isEqualTo(new CodeOption("UNKNOWN", null));
    assertThat(summary.rateAdjustmentType()).isEqualTo(new CodeOption("EXPIRED", null));
    assertThat(summary.rates()).hasSize(1);
    assertThat(summary.rates().getFirst().scaleSpecies()).isEqualTo(new CodeOption(" SP ", null));
    assertThat(summary.rates().getFirst().scaleProduct()).isEqualTo(new CodeOption(" ", null));
    assertThat(summary.rates().getFirst().scaleGrade()).isEqualTo(new CodeOption(" ", null));
  }

  @Test
  void nullClassificationCodesRemainNullEvenIfAResultDescriptionIsPresent() throws SQLException {
    parent("I", null, null);
    when(rows.next()).thenReturn(true, false);
    when(rows.getString("REFERENCE_TYPE_DESCRIPTION")).thenReturn("Synthetic stray label");
    var summary = summaries.nonAppraised(idir("TAPS_ADMIN"), key(GasAppraisal.WorksheetType.NON_APPRAISED)).orElseThrow();
    assertThat(summary.referenceType()).isNull();
    assertThat(summary.appraisalForestZone()).isNull();
    assertThat(summary.nonAppraisedRateType()).isNull();
    assertThat(summary.rateAdjustmentType()).isNull();
  }

  @Test
  void duplicateScalarLabelsFailTheWholeReadAndReleaseResources() throws SQLException {
    var error = new SQLException("Synthetic scalar lookup returned multiple rows", "21000", 1427);
    when(statement.executeQuery()).thenThrow(error);
    assertThatThrownBy(() -> summaries.nonAppraised(idir("TAPS_ADMIN"), key(GasAppraisal.WorksheetType.NON_APPRAISED)))
        .isInstanceOf(DataAccessException.class).hasCause(error);
    cleanup(false);
  }

  @Test
  void selectedAddonsRetainCodeMetadataIncludingTimeOfDayWithoutPolicyCalculations() throws SQLException {
    parent("I", null, null);
    when(rows.next()).thenReturn(true, true, false);
    when(rows.getInt("ROW_KIND")).thenReturn(0, 3);
    when(rows.getString("NON_APPRAISED_PARENT_ID")).thenReturn("998877");
    when(rows.getString("ADDON_CODE")).thenReturn("SILV");
    when(rows.getString("ADDON_DESCRIPTION")).thenReturn("SILV - Silviculture");
    when(rows.getTimestamp("ADDON_UPDATE_TIMESTAMP")).thenReturn(Timestamp.valueOf("2026-01-02 12:34:56"));

    var summary = summaries.nonAppraised(idir("TAPS_ADMIN"), key(GasAppraisal.WorksheetType.NON_APPRAISED)).orElseThrow();

    assertThat(summary.selectedRateAddons()).containsExactly(new GasAppraisal.SelectedRateAddon(
        "SILV", "SILV - Silviculture", null, null, LocalDateTime.of(2026, 1, 2, 12, 34, 56)));
    assertThat(summary.rates()).isEmpty();
    assertThat(sql()).doesNotContain("NON_APPRAISED_RATE_ADDON_COST", "GET_ADDON_COST");
  }

  @Test
  void historicChildrenPreserveExactNumbersNullsAndActualProductColumn() throws SQLException {
    parent("C", "MPS", "Y");
    when(rows.next()).thenReturn(true, true, true, false);
    when(rows.getInt("ROW_KIND")).thenReturn(0, 4, 5);
    when(rows.getString("HISTORIC_PARENT_ID")).thenReturn("998877");
    when(rows.getString("DETAIL_ID")).thenReturn("45", "46");
    when(rows.getString("SCALE_SPECIES_CODE")).thenReturn("FI");
    when(rows.getString("SCALE_PRODUCT_CODE")).thenReturn("02");
    when(rows.getString("SCALE_GRADE_CODE")).thenReturn("B");
    when(rows.getBigDecimal("SPECIES_VOLUME")).thenReturn(new BigDecimal("123.4500"));
    when(rows.getBigDecimal("SPECIES_STUD_PERCENT")).thenReturn(BigDecimal.ZERO);
    when(rows.getBigDecimal("SPECIES_GRADE_PERCENT")).thenReturn(new BigDecimal("40.50"));

    var summary = summaries.historic(idir("TAPS_ADMIN"), key(GasAppraisal.WorksheetType.HISTORIC)).orElseThrow();

    assertThat(summary.historicSpecies()).containsExactly(new GasAppraisal.HistoricSpecies(
        "45", "FI", null, new BigDecimal("123.4500"), null, null, BigDecimal.ZERO, null));
    assertThat(summary.coastSpeciesGrades()).containsExactly(new GasAppraisal.HistoricCoastSpeciesGrade(
        "46", "FI", "02", "B", new BigDecimal("40.50")));
    assertThat(summary.rates()).isEmpty();
    assertThat(summary.nonAppraisedRates()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(ints = {3, 4, 5})
  void newChildrenMustHaveExactlyTheirRequestedFamilyParent(int rowKind) throws SQLException {
    parent("C", "MPS", "Y");
    when(rows.next()).thenReturn(true, true, false);
    when(rows.getInt("ROW_KIND")).thenReturn(0, rowKind);
    when(rows.getString(rowKind == 3 ? "HISTORIC_PARENT_ID" : "NON_APPRAISED_PARENT_ID")).thenReturn("998877");
    assertThatThrownBy(() -> read(rowKind == 3 ? GasAppraisal.WorksheetType.NON_APPRAISED
        : GasAppraisal.WorksheetType.HISTORIC, idir("TAPS_ADMIN")))
        .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("requested family parent");
  }

  @ParameterizedTest
  @EnumSource(value = GasAppraisal.WorksheetType.class, names = {"HISTORIC", "NON_APPRAISED"})
  void wrongWorksheetCannotPublishAnySummary(GasAppraisal.WorksheetType type) throws SQLException {
    parent("I", "CVP", "N");
    when(rows.next()).thenReturn(true, false);
    when(rows.getString("WORKSHEET_ID")).thenReturn("998878");
    assertThatThrownBy(() -> read(type, idir("TAPS_ADMIN")))
        .isInstanceOf(DataIntegrityViolationException.class);
    cleanup(true);
  }

  @ParameterizedTest
  @EnumSource(value = GasAppraisal.WorksheetType.class, names = {"HISTORIC", "NON_APPRAISED"})
  void wrongFamilyChildWithSameNumericIdIsRejected(GasAppraisal.WorksheetType type) throws SQLException {
    parent("I", "CVP", "N");
    when(rows.next()).thenReturn(true, true, false);
    when(rows.getInt("ROW_KIND")).thenReturn(0, 2);
    when(rows.getString(type == GasAppraisal.WorksheetType.HISTORIC
        ? "NON_APPRAISED_PARENT_ID" : "HISTORIC_PARENT_ID")).thenReturn("998877");
    assertThatThrownBy(() -> read(type, idir("TAPS_ADMIN")))
        .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("requested family parent");
    cleanup(true);
  }

  @ParameterizedTest
  @ValueSource(strings = {"APPRAISED_PARENT_ID", "NON_APPRAISED_PARENT_ID"})
  void historicRatesWithMultipleParentsAreRejected(String secondParent) throws SQLException {
    parent("I", "CVP", "N");
    when(rows.next()).thenReturn(true, true, false);
    when(rows.getInt("ROW_KIND")).thenReturn(0, 1);
    when(rows.getString("HISTORIC_PARENT_ID")).thenReturn("998877");
    when(rows.getString(secondParent)).thenReturn("998877");
    assertThatThrownBy(() -> read(GasAppraisal.WorksheetType.HISTORIC, idir("TAPS_ADMIN")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void historicRateWithDifferentParentIdIsRejected() throws SQLException {
    parent("I", "CVP", "N");
    when(rows.next()).thenReturn(true, true, false);
    when(rows.getInt("ROW_KIND")).thenReturn(0, 2);
    when(rows.getString("HISTORIC_PARENT_ID")).thenReturn("998878");
    assertThatThrownBy(() -> read(GasAppraisal.WorksheetType.HISTORIC, idir("TAPS_ADMIN")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @ParameterizedTest
  @EnumSource(value = GasAppraisal.WorksheetType.class, names = {"HISTORIC", "NON_APPRAISED"})
  void multipleParentsFailInsteadOfSelectingOne(GasAppraisal.WorksheetType type) throws SQLException {
    parent("I", "CVP", "N");
    when(rows.next()).thenReturn(true, true, false);
    assertThatThrownBy(() -> read(type, idir("TAPS_ADMIN")))
        .isInstanceOf(IncorrectResultSizeDataAccessException.class);
    cleanup(true);
  }

  @Test
  void childrenWithoutScopedHeaderFail() throws SQLException {
    when(rows.next()).thenReturn(true, false);
    when(rows.getString("WORKSHEET_ID")).thenReturn("998877");
    when(rows.getInt("ROW_KIND")).thenReturn(2);
    when(rows.getString("NON_APPRAISED_PARENT_ID")).thenReturn("998877");
    when(rows.getString("RATE_ID")).thenReturn("3");
    nonAppraisedRate();
    assertThatThrownBy(() -> read(GasAppraisal.WorksheetType.NON_APPRAISED, idir("TAPS_ADMIN")))
        .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("no scoped parent");
  }

  @ParameterizedTest
  @ValueSource(strings = {"TOTAL_OBLIGATION_ADJUSTMNT_IND", "ADJUST_QUARTERLY_IND", "ACTIVE_IND"})
  void unknownHistoricIndicatorsFailRatherThanDefaultFalse(String column) throws SQLException {
    parent("I", "CVP", "N");
    when(rows.next()).thenReturn(true, false);
    when(rows.getString(column)).thenReturn("UNKNOWN");
    assertThatThrownBy(() -> read(GasAppraisal.WorksheetType.HISTORIC, idir("TAPS_ADMIN")))
        .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("unsupported indicator");
  }

  @Test
  void unknownHistoricDispatchFails() throws SQLException {
    parent("C", "MPS", null);
    when(rows.next()).thenReturn(true, false);
    assertThatThrownBy(() -> read(GasAppraisal.WorksheetType.HISTORIC, idir("TAPS_ADMIN")))
        .isInstanceOf(DataIntegrityViolationException.class).hasMessage("unsupported summary variant");
  }

  @Test
  void queryFailurePropagatesWithoutPartialResult() throws SQLException {
    var error = new SQLDataException("Synthetic query error", "21000");
    when(statement.executeQuery()).thenThrow(error);
    assertThatThrownBy(() -> read(GasAppraisal.WorksheetType.NON_APPRAISED, idir("TAPS_ADMIN")))
        .isInstanceOf(DataAccessException.class).hasCause(error);
    cleanup(false);
  }

  @Test
  void bindFailureClosesStatementWithoutQuerying() throws SQLException {
    var error = new SQLDataException("Synthetic bind error", "22000");
    doThrow(error).when(statement).setLong(1, 998877L);
    assertThatThrownBy(() -> read(GasAppraisal.WorksheetType.HISTORIC, idir("TAPS_ADMIN")))
        .isInstanceOf(DataAccessException.class).hasCause(error);
    verify(statement, never()).executeQuery();
    cleanup(false);
  }

  @Test
  void mappingFailureReleasesAllResources() throws SQLException {
    parent("I", "CVP", "N");
    when(rows.next()).thenReturn(true, false);
    var error = new SQLDataException("Synthetic date error", "22000");
    when(rows.getDate("EFFECTIVE_DATE")).thenThrow(error);
    assertThatThrownBy(() -> read(GasAppraisal.WorksheetType.HISTORIC, idir("TAPS_ADMIN")))
        .isInstanceOf(DataAccessException.class).hasCause(error);
    cleanup(true);
  }

  private void parent(String method, String calculation, String toa) throws SQLException {
    when(rows.getString("WORKSHEET_ID")).thenReturn("998877");
    when(rows.getString("LICENSE")).thenReturn(" A00001 ");
    when(rows.getString("TIMBER_MARK")).thenReturn("ABC123");
    when(rows.getString("APPRAISAL_METHOD_CODE")).thenReturn(method);
    when(rows.getString("RATE_CALC_METHOD_CODE")).thenReturn(calculation);
    when(rows.getString("TOTAL_OBLIGATION_ADJUSTMNT_IND")).thenReturn(toa);
    when(rows.getString("ADJUST_QUARTERLY_IND")).thenReturn("N");
    when(rows.getString("ACTIVE_IND")).thenReturn("Y");
    when(rows.getString("POLICY_VERSION")).thenReturn(" Policy ");
    when(rows.getString("STATUS_CODE")).thenReturn("DET");
    when(rows.getString("STATUS_DESCRIPTION")).thenReturn(" Determined ");
    when(rows.getDate("EFFECTIVE_DATE")).thenReturn(Date.valueOf("2030-01-01"));
    when(rows.getDate("CEASE_ADJUSTMENT_DATE")).thenReturn(Date.valueOf("2030-09-30"));
  }

  private void nonAppraisedRate() throws SQLException {
    when(rows.getString("SCALE_SPECIES_CODE")).thenReturn(" SP ");
    when(rows.getString("SCALE_PRODUCT_CODE")).thenReturn("PL");
    when(rows.getString("SCALE_GRADE_CODE")).thenReturn("A");
    when(rows.getString("SCALE_SPECIES_DESCRIPTION")).thenReturn("Synthetic species");
    when(rows.getString("SCALE_PRODUCT_DESCRIPTION")).thenReturn("Synthetic product");
    when(rows.getString("SCALE_GRADE_DESCRIPTION")).thenReturn("Synthetic grade");
    when(rows.getBigDecimal("RESERVE_STUMPAGE_RATE")).thenReturn(new BigDecimal("5.10"));
    when(rows.getBigDecimal("DEVELOPMENT_LEVY")).thenReturn(BigDecimal.ZERO);
    when(rows.getBigDecimal("SILVICULTURE_LEVY")).thenReturn(new BigDecimal("-1.20"));
  }

  private Optional<?> read(GasAppraisal.WorksheetType type, TapsUser user) {
    return type == GasAppraisal.WorksheetType.HISTORIC
        ? summaries.historic(user, key(type)) : summaries.nonAppraised(user, key(type));
  }

  private static GasAppraisal.Key key(GasAppraisal.WorksheetType type) {
    return new GasAppraisal.Key(type, "998877");
  }

  private String sql() throws SQLException {
    var capture = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(capture.capture());
    return capture.getValue();
  }

  private void cleanup(boolean opened) throws SQLException {
    if (opened) {
      verify(rows).close();
    }
    verify(statement).close();
    verify(connection).close();
  }

  private static TapsUser idir(String... roles) {
    return new TapsUser("synthetic", "Synthetic user", null, IdentityProvider.IDIR, null,
        Arrays.stream(roles).map(role ->
            RoleGrant.accept(FamRoleName.parse(role), IdentityProvider.IDIR).orElseThrow()).toList());
  }

  static Stream<Arguments> variants() {
    return Stream.of(
        Arguments.of("I", "CVP", "N", GasAppraisal.SummaryVariant.CVP),
        Arguments.of("C", "CVP", "N", GasAppraisal.SummaryVariant.CVP),
        Arguments.of("I", "MPS", "N", GasAppraisal.SummaryVariant.INTERIOR_MPS),
        Arguments.of("C", "MPS", "Y", GasAppraisal.SummaryVariant.COAST_MPS_TOA_Y),
        Arguments.of("C", "MPS", "N", GasAppraisal.SummaryVariant.COAST_MPS_TOA_N));
  }
}
