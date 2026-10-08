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
import java.sql.SQLSyntaxErrorException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class OracleAppraisedSummaryTest {
  private final DataSource dataSource = mock(DataSource.class);
  private final Connection connection = mock(Connection.class);
  private final PreparedStatement statement = mock(PreparedStatement.class);
  private final ResultSet rows = mock(ResultSet.class);
  private final OracleAppraisedSummary summaries =
      new OracleAppraisedSummary(new JdbcTemplate(dataSource));
  private final GasAppraisal.Key key =
      new GasAppraisal.Key(GasAppraisal.WorksheetType.APPRAISED, "998877");

  @BeforeEach
  void prepareJdbcBoundary() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(rows);
  }

  @Test
  void scopesParentAndBothChildFamiliesInOnePreparedStatement() throws SQLException {
    assertThat(summaries.byTypedKey(
        idir("TAPS_DISTRICT_APPRAISER_DISTRICT-DZZ", "TAPS_REGION_APPRAISER_REGION-CARIBOO"), key))
        .isEmpty();

    String sql = preparedSql();
    assertThat(sql).contains("ADS.CLIENT_NUMBER", "ADSC.APPRAISAL_METHOD_CODE",
        "DISTRICT.ORG_UNIT_NO = ADS.ADMIN_DISTRICT",
        "REGION.ORG_UNIT_NO = DISTRICT.ROLLUP_REGION_NO",
        "LEFT JOIN APPRAISAL_STATUS_CODE STATUS_LOOKUP",
        "SYSDATE BETWEEN STATUS_LOOKUP.EFFECTIVE_DATE AND STATUS_LOOKUP.EXPIRY_DATE",
        "NVL(ADS.TOA_ELIGIBLE_IND, 'N')",
        "record_scope.WORKSHEET_ID = ? AND (record_scope.ADMIN_DISTRICT_CODE = ?"
            + " OR record_scope.ROLLUP_REGION_CODE = ?)",
        "JOIN ADS_SUBMITTED_TIMBER_MARK M ON M.ECAS_ID = P.ECAS_ID",
        "JOIN APPRAISED_STUMPAGE_RATE R ON R.APPRAISED_WORKSHEET_ID = P.WORKSHEET_ID",
        "ORDER BY ROW_KIND, TIMBER_MARK, RATE_EFFECTIVE_DATE, RATE_ID");
    assertThat(sql).doesNotContain("ADSC.CLIENT_NUMBER", "STATUS_CODE NOT IN", "COALESCE", "DISTINCT", "998877", "DZZ",
        "RCB", "GAS2.", "FIND_", "UPDATE ", "INSERT ", "DELETE ");
    assertThat(sql.split("FROM scoped_parent P", -1)).hasSize(4);
    assertThat(sql.split("UNION ALL", -1)).hasSize(3);
    verify(statement).setLong(1, 998877L);
    verify(statement).setString(2, "DZZ");
    verify(statement).setString(3, "RCB");
    verify(connection, never()).createStatement();
    verify(statement).executeQuery();
    verifyCleanup(true);
  }

  @Test
  void ecasLookupUsesValidatedBoundEcasIdAndIdenticalCapability() throws SQLException {
    assertThat(summaries.byEcasId(idir("TAPS_ADMIN"), "00012345")).isEmpty();
    assertThat(preparedSql()).contains("record_scope.ECAS_ID = ? AND (1 = 1)");
    verify(statement).setLong(1, 12345L);
  }

  @Test
  void missingGasCapabilityCannotBorrowAViewerGrant() throws SQLException {
    assertThat(summaries.byTypedKey(idir("TAPS_VIEWER_DISTRICT-DZZ"), key)).isEmpty();
    assertThat(preparedSql()).contains("record_scope.WORKSHEET_ID = ? AND (1 = 0)");
  }

  @ParameterizedTest
  @EnumSource(value = GasAppraisal.WorksheetType.class, names = {"HISTORIC", "NON_APPRAISED"})
  void refusesOtherWorksheetFamiliesBeforeReading(GasAppraisal.WorksheetType type) {
    assertThatThrownBy(() -> summaries.byTypedKey(idir("TAPS_ADMIN"),
        new GasAppraisal.Key(type, "998877")))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(dataSource);
  }

  @Test
  void rejectsInvalidEcasIdBeforeReading() {
    assertThatThrownBy(() -> summaries.byEcasId(idir("TAPS_ADMIN"), "1 OR 1=1"))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(dataSource);
  }

  @Test
  void preservesMultipleMarksAndRatesWithoutDeduplicatingOrCalculating() throws SQLException {
    parent("I", "MPS", "N");
    when(rows.next()).thenReturn(true, true, true, true, true, true, false);
    when(rows.getInt("ROW_KIND")).thenReturn(0, 1, 1, 1, 2, 2);
    when(rows.getString("TIMBER_MARK")).thenReturn("ABC123", "ABC123", "XYZ987");
    when(rows.getString("RATE_ID")).thenReturn("51", "52");
    when(rows.getDate("RATE_EFFECTIVE_DATE"))
        .thenReturn(Date.valueOf("2030-01-01"), Date.valueOf("2030-01-01"));
    when(rows.getBigDecimal("RATE_AMOUNT"))
        .thenReturn(new BigDecimal("12.30"), new BigDecimal("0.00"));

    var summary = summaries.byTypedKey(idir("TAPS_ADMIN"), key).orElseThrow();

    assertThat(summary.key()).isEqualTo(key);
    assertThat(summary.ecasId()).isEqualTo("12345");
    assertThat(summary.appraisalMethod()).isEqualTo(AppraisalMethod.I);
    assertThat(summary.variant()).isEqualTo(GasAppraisal.SummaryVariant.INTERIOR_MPS);
    assertThat(summary.rateCalculationMethodCode()).isEqualTo("MPS");
    assertThat(summary.toaEligible()).isFalse();
    assertThat(summary.status()).isEqualTo(new CodeOption("CON", "Confirmed"));
    assertThat(summary.effectiveDate()).isEqualTo(LocalDate.of(2030, 1, 1));
    assertThat(summary.expiryDate()).isEqualTo(LocalDate.of(2030, 12, 31));
    assertThat(summary.referenceTypeCode()).isEqualTo("REF");
    assertThat(summary.ceaseAdjustmentDate()).isEqualTo(LocalDate.of(2030, 11, 30));
    assertThat(summary.timberMarks()).containsExactly("ABC123", "ABC123", "XYZ987");
    assertThat(summary.rates()).containsExactly(
        new GasAppraisal.StoredRate("51", LocalDate.of(2030, 1, 1), new BigDecimal("12.30")),
        new GasAppraisal.StoredRate("52", LocalDate.of(2030, 1, 1), new BigDecimal("0.00")));
    assertThat(summary.rates().getFirst().totalStumpageRate()).isEqualTo(new BigDecimal("12.30"));
    verifyCleanup(true);
  }

  @ParameterizedTest
  @MethodSource("variants")
  void selectsStoredVariantWithoutConstructingLegacySummary(
      String method, String calculation, String toa, GasAppraisal.SummaryVariant variant)
      throws SQLException {
    parent(method, calculation, toa);
    when(rows.next()).thenReturn(true, false);
    assertThat(summaries.byEcasId(idir("TAPS_ADMIN"), "12345").orElseThrow().variant())
        .isEqualTo(variant);
  }

  @Test
  void nullableFieldsAndMissingChildrenRemainEmpty() throws SQLException {
    parent("I", "CVP", null);
    when(rows.next()).thenReturn(true, false);
    when(rows.getString("STATUS_CODE")).thenReturn(null);
    when(rows.getString("STATUS_DESCRIPTION")).thenReturn(null);
    when(rows.getDate("EFFECTIVE_DATE")).thenReturn(null);
    when(rows.getDate("EXPIRY_DATE")).thenReturn(null);
    when(rows.getString("REFERENCE_TYPE")).thenReturn(null);
    when(rows.getDate("CEASE_ADJUSTMENT_DATE")).thenReturn(null);

    var summary = summaries.byTypedKey(idir("TAPS_ADMIN"), key).orElseThrow();
    assertThat(summary.status()).isEqualTo(new CodeOption(null, null));
    assertThat(summary.toaEligible()).isNull();
    assertThat(summary.effectiveDate()).isNull();
    assertThat(summary.expiryDate()).isNull();
    assertThat(summary.referenceTypeCode()).isNull();
    assertThat(summary.ceaseAdjustmentDate()).isNull();
    assertThat(summary.timberMarks()).isEmpty();
    assertThat(summary.rates()).isEmpty();
  }

  @Test
  void multipleParentsFailInsteadOfChoosingOne() throws SQLException {
    parent("I", "MPS", "N");
    when(rows.next()).thenReturn(true, true, false);
    when(rows.getInt("ROW_KIND")).thenReturn(0, 0);
    assertThatThrownBy(() -> summaries.byEcasId(idir("TAPS_ADMIN"), "12345"))
        .isInstanceOf(IncorrectResultSizeDataAccessException.class);
    verifyCleanup(true);
  }

  @Test
  void unsupportedStoredVariantFailsRatherThanInventingOne() throws SQLException {
    parent("I", "UNKNOWN", "N");
    when(rows.next()).thenReturn(true, false);
    assertThatThrownBy(() -> summaries.byTypedKey(idir("TAPS_ADMIN"), key))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessage("unsupported summary variant");
  }

  @Test
  void unexpectedToaIndicatorFailsRatherThanBecomingFalse() throws SQLException {
    parent("C", "MPS", "UNKNOWN");
    when(rows.next()).thenReturn(true, false);
    assertThatThrownBy(() -> summaries.byTypedKey(idir("TAPS_ADMIN"), key))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessage("unsupported TOA eligibility value");
  }

  @Test
  void missingStatusLabelPreservesStoredStatusAndDirectSummary() throws SQLException {
    parent("I", "MPS", "N");
    when(rows.next()).thenReturn(true, false);
    when(rows.getString("STATUS_CODE")).thenReturn("DFT");
    when(rows.getString("STATUS_DESCRIPTION")).thenReturn(null);
    assertThat(summaries.byTypedKey(idir("TAPS_ADMIN"), key).orElseThrow().status())
        .isEqualTo(new CodeOption("DFT", null));
  }

  @Test
  void invalidStoredRatePrecisionFailsRatherThanRounding() throws SQLException {
    parent("I", "MPS", "N");
    when(rows.next()).thenReturn(true, true, false);
    when(rows.getInt("ROW_KIND")).thenReturn(0, 2);
    when(rows.getString("RATE_ID")).thenReturn("51");
    when(rows.getDate("RATE_EFFECTIVE_DATE")).thenReturn(Date.valueOf("2030-01-01"));
    when(rows.getBigDecimal("RATE_AMOUNT")).thenReturn(new BigDecimal("12.345"));
    assertThatThrownBy(() -> summaries.byTypedKey(idir("TAPS_ADMIN"), key))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("totalStumpageRate exceeds two decimal places");
    verifyCleanup(true);
  }

  @Test
  void queryFailuresPropagateAndReleaseResources() throws SQLException {
    SQLException failure = new SQLSyntaxErrorException("Synthetic query failure", "42000");
    when(statement.executeQuery()).thenThrow(failure);
    assertThatThrownBy(() -> summaries.byTypedKey(idir("TAPS_ADMIN"), key))
        .isInstanceOf(DataAccessException.class).hasCause(failure);
    verifyCleanup(false);
  }

  @Test
  void bindingFailuresCloseThePreparedStatementWithoutQuerying() throws SQLException {
    SQLException failure = new SQLDataException("Synthetic bind failure", "22000");
    doThrow(failure).when(statement).setLong(1, 998877L);
    assertThatThrownBy(() -> summaries.byTypedKey(idir("TAPS_ADMIN"), key))
        .isInstanceOf(DataAccessException.class).hasCause(failure);
    verify(statement, never()).executeQuery();
    verifyCleanup(false);
  }

  @Test
  void mappingFailuresDoNotReturnPartialSummary() throws SQLException {
    SQLException failure = new SQLDataException("Synthetic date failure", "22000");
    parent("I", "MPS", "N");
    when(rows.next()).thenReturn(true, false);
    when(rows.getDate("EFFECTIVE_DATE")).thenThrow(failure);
    assertThatThrownBy(() -> summaries.byTypedKey(idir("TAPS_ADMIN"), key))
        .isInstanceOf(DataAccessException.class).hasCause(failure);
    verifyCleanup(true);
  }

  private void parent(String method, String calculation, String toa) throws SQLException {
    when(rows.getString("WORKSHEET_ID")).thenReturn("998877");
    when(rows.getString("ECAS_ID")).thenReturn("12345");
    when(rows.getString("APPRAISAL_METHOD_CODE")).thenReturn(method);
    when(rows.getString("RATE_CALC_METHOD_CODE")).thenReturn(calculation);
    when(rows.getString("TOA_ELIGIBLE_IND")).thenReturn(toa);
    when(rows.getString("STATUS_CODE")).thenReturn("CON");
    when(rows.getString("STATUS_DESCRIPTION")).thenReturn("Confirmed");
    when(rows.getDate("EFFECTIVE_DATE")).thenReturn(Date.valueOf("2030-01-01"));
    when(rows.getDate("EXPIRY_DATE")).thenReturn(Date.valueOf("2030-12-31"));
    when(rows.getString("REFERENCE_TYPE")).thenReturn("REF");
    when(rows.getDate("CEASE_ADJUSTMENT_DATE")).thenReturn(Date.valueOf("2030-11-30"));
  }

  private String preparedSql() throws SQLException {
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(sql.capture());
    return sql.getValue();
  }

  private void verifyCleanup(boolean resultSetWasOpened) throws SQLException {
    if (resultSetWasOpened) {
      verify(rows).close();
    }
    verify(statement).close();
    verify(connection).close();
  }

  private TapsUser idir(String... roles) {
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
