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

import ca.bc.gov.nrs.taps.read.GasAppraisal;
import ca.bc.gov.nrs.taps.security.FamRoleName;
import ca.bc.gov.nrs.taps.security.IdentityProvider;
import ca.bc.gov.nrs.taps.security.RoleGrant;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class OracleFtaLicenceInformationTest {
  private final DataSource dataSource = mock(DataSource.class);
  private final Connection connection = mock(Connection.class);
  private final PreparedStatement statement = mock(PreparedStatement.class);
  private final ResultSet rows = mock(ResultSet.class);
  private final OracleFtaLicenceInformation repository =
      new OracleFtaLicenceInformation(new JdbcTemplate(dataSource));

  @BeforeEach
  void prepareJdbcBoundary() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(rows);
  }

  @Test
  void scopesEveryReadWithTheSameGasGrantAndBindsNormalizedExactFilters() throws SQLException {
    var user = idir(
        "TAPS_HEADQUARTERS", "TAPS_VIEWER_DISTRICT-DYY",
        "TAPS_DISTRICT_APPRAISER_DISTRICT-DZZ", "TAPS_REGION_APPRAISER_REGION-CARIBOO");

    assertThat(repository.find(user, " a'-- ", " x_%' ")).isEmpty();

    String sql = capturedSql();
    assertThat(sql)
        .contains("record_scope.ADMIN_DISTRICT_CODE = ?", "record_scope.ROLLUP_REGION_CODE = ?")
        .doesNotContain("A'--", "X_%'", "DYY", "DZZ", "RCB", " LIKE ");
    assertThat(sql.chars().filter(character -> character == '?').count()).isEqualTo(4);
    verify(statement).setString(1, "A'--");
    verify(statement).setString(2, "X_%'");
    verify(statement).setString(3, "DZZ");
    verify(statement).setString(4, "RCB");
    verify(statement).executeQuery();
    verify(connection, never()).createStatement();
    verifyCleanup(true);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "   ")
  void markOnlyReadBindsAnAbsentLicence(String licence) throws SQLException {
    assertThat(repository.find(idir("TAPS_ADMIN"), licence, " zz1234 ")).isEmpty();

    verify(statement).setString(1, null);
    verify(statement).setString(2, "ZZ1234");
    assertThat(capturedSql().chars().filter(character -> character == '?').count()).isEqualTo(2);
  }

  @Test
  void coversAllMarkFamiliesAndScopesPermitContributorsBeforeAggregation() throws SQLException {
    repository.find(idir("TAPS_DISTRICT_APPRAISER_DISTRICT-DZZ"), "A00001", "ZZ1234");
    String sql = capturedSql();
    assertThat(sql).contains("'PERMIT' AS MARK_FAMILY", "SELECT 'PRIVATE'", "SELECT 'ROAD'",
        "FROM PRIVATE_MARK_CERTIFICATE PMC", "FROM BLANKET_ROAD_MARK BRM",
        "DISTRICT.ORG_UNIT_NO = P.FOREST_DISTRICT",
        "REGION.ORG_UNIT_NO = DISTRICT.ROLLUP_REGION_NO",
        "DISPLAY_REGION.ORG_UNIT_NO = PFU.FOREST_REGION");
    String aggregateAndDisplay = sql.substring(sql.indexOf(", permit_lists AS"));
    assertThat(aggregateAndDisplay).contains("FROM scoped_parents",
        "MARK_FAMILY = 'PERMIT' AND HVA_SKEY IS NOT NULL",
        "LISTAGG(CUTTING_PERMIT_ID || ', ', '')", "WITHIN GROUP (ORDER BY CUTTING_PERMIT_ID)",
        "TFSC.DESCRIPTION AS LICENCE_STATUS_DESC", "P.DISTRICT_ROW_ID IS NOT NULL",
        "FROM HARVEST_AUTH_STATUS_CODE HASC", "FROM PRIVATE_MARK_STATUS_CODE PMSC")
        .doesNotContain("FROM mark_parents", "GAS2_COMMON.", "SYSDATE", "LISTAGG(DISTINCT");
    assertThat(sql.indexOf("SELECT * FROM record_scope WHERE (record_scope.ADMIN_DISTRICT_CODE = ?)"))
        .isLessThan(sql.indexOf("LISTAGG("));
    verify(statement).setString(3, "DZZ");
  }

  @Test
  void preservesSourceFileResolutionAndRejectsArbitraryPreferredClientSelection() throws SQLException {
    repository.find(idir("TAPS_ADMIN"), null, "ZZ1234");
    String sql = capturedSql();
    assertThat(sql).contains("SELECT HA.FOREST_FILE_ID", "SELECT BRM.FOREST_FILE_ID",
        "I.FOREST_FILE_ID IS NULL AND HA.TIMBER_MARK = I.TIMBER_MARK",
        "I.FOREST_FILE_ID IS NULL AND BRM.TIMBER_MARK = I.TIMBER_MARK",
        "SELECT DISTINCT C.CLIENT_NUMBER FROM FOREST_FILE_CLIENT C",
        "C.FOREST_FILE_CLIENT_TYPE_CODE = 'S'", "C.FOREST_FILE_CLIENT_TYPE_CODE = 'A'",
        "C.CLIENT_NUMBER IS NOT NULL", "LEFT JOIN FOREST_CLIENT FC")
        .doesNotContain("ROWNUM", "FETCH FIRST", "MIN(C.CLIENT_NUMBER)", "MAX(C.CLIENT_NUMBER)");
    assertThat(sql.indexOf("C.FOREST_FILE_CLIENT_TYPE_CODE = 'S'"))
        .isLessThan(sql.indexOf("C.FOREST_FILE_CLIENT_TYPE_CODE = 'A'"));
    // Preserve the legacy HA file/HVA PFU file distinction; current GAS readers have no client grant.
    assertThat(sql).contains("HVA.FOREST_FILE_ID AS PFU_FILE_ID",
        "PROV_FOREST_USE PFU ON PFU.FOREST_FILE_ID = P.PFU_FILE_ID")
        .doesNotContain("HVA.FOREST_FILE_ID = HA.FOREST_FILE_ID");
  }

  @ParameterizedTest
  @MethodSource("invalidFilters")
  void invalidFiltersFailBeforeOpeningAConnection(String licence, String mark) {
    assertThatThrownBy(() -> repository.find(idir("TAPS_ADMIN"), licence, mark))
        .isInstanceOf(IllegalArgumentException.class);

    verifyNoInteractions(dataSource, connection, statement, rows);
  }

  @Test
  void matchingFiltersCannotBorrowAnUnrelatedCapability() throws SQLException {
    assertThat(repository.find(idir("TAPS_HEADQUARTERS", "TAPS_VIEWER_DISTRICT-DZZ"),
        "A00001", "ZZ1234")).isEmpty();

    assertThat(capturedSql()).contains("(1 = 0)");
    verify(statement).setString(1, "A00001");
    verify(statement).setString(2, "ZZ1234");
  }

  @Test
  void clientGrantDoesNotGrantGasReadAccessEvenWithMatchingLicenceAndMark() throws SQLException {
    var user = user(IdentityProvider.BCEID_BUSINESS,
        "TAPS_LICENSEE_FOREST_CLIENT-00000001", "TAPS_LICENSEE_VIEWER_FOREST_CLIENT-00000002");

    assertThat(repository.find(user, "A00001", "ZZ1234")).isEmpty();

    assertThat(capturedSql()).contains("(1 = 0)").doesNotContain("00000001", "00000002");
    verify(statement).setString(1, "A00001");
    verify(statement).setString(2, "ZZ1234");
  }

  @Test
  void preservesAllDisplayFieldsAggregatedPermitsAndLicenceStatusWithoutWorksheetRows()
      throws SQLException {
    when(rows.next()).thenReturn(true, false);
    when(rows.getString("CLIENT_NUMBER")).thenReturn("00000001");
    when(rows.getString("CLIENT_NAME")).thenReturn(" Synthetic Licensee ");
    when(rows.getString("FOREST_FILE_ID")).thenReturn(" A00001 ");
    when(rows.getString("CUT_PERMIT_IDS")).thenReturn("001, 002, 002");
    when(rows.getString("FILE_TYPE_CODE")).thenReturn("A01");
    when(rows.getString("TIMBER_MARK")).thenReturn("ZZ1234");
    when(rows.getString("FOREST_REGION_NAME")).thenReturn(" Synthetic Region ");
    when(rows.getString("FOREST_DISTRICT_NAME")).thenReturn(" Synthetic District ");
    when(rows.getTimestamp("EXPIRY_DATE")).thenReturn(Timestamp.valueOf("2030-05-06 23:59:58"));
    when(rows.getTimestamp("EXTEND_DATE")).thenReturn(Timestamp.valueOf("2031-06-07 00:00:01"));
    when(rows.getString("LICENCE_STATUS_DESC")).thenReturn(" Licence Active ");

    assertThat(repository.find(idir("TAPS_ADMIN"), "A00001", "ZZ1234")).contains(
        new GasAppraisal.FtaLicenceInformation(
            "00000001", " Synthetic Licensee ", " A00001 ", "001, 002, 002", "A01", "ZZ1234",
            " Synthetic Region ", " Synthetic District ", LocalDate.of(2030, 5, 6),
            LocalDate.of(2031, 6, 7), " Licence Active "));
    assertThat(capturedSql()).doesNotContain("APPRAISED_WORKSHEET", "APPRAISAL_DATA_SUBMISSION");
    verify(rows, never()).getString("MARK_STATUS_DESC");
    verifyCleanup(true);
  }

  @Test
  void preservesRoadNullDatesAndUnknownDisplayFields() throws SQLException {
    when(rows.next()).thenReturn(true, false);
    when(rows.getString("FOREST_FILE_ID")).thenReturn("A00001");
    when(rows.getString("TIMBER_MARK")).thenReturn("ZZ1234");

    assertThat(repository.find(idir("TAPS_ADMIN"), "A00001", "ZZ1234")).contains(
        new GasAppraisal.FtaLicenceInformation(
            null, null, "A00001", null, null, "ZZ1234", null, null, null, null, null));
    verifyCleanup(true);
  }

  @Test
  void repeatedPermitsRemainIntactAtTheLegacyBufferBoundary() throws SQLException {
    // 99 three-character entries plus one more fit the 500-character working buffer before
    // removing its trailing separator.
    String cuttingPermits = "001, ".repeat(98) + "001, 002";
    assertThat(cuttingPermits).hasSize(498);
    when(rows.next()).thenReturn(true, false);
    when(rows.getString("CUT_PERMIT_IDS")).thenReturn(cuttingPermits);

    assertThat(repository.find(idir("TAPS_ADMIN"), "A00001", "ZZ1234").orElseThrow().cuttingPermit())
        .isEqualTo(cuttingPermits);
  }

  @Test
  void nullPermitComponentsKeepTheirLegacySeparators() throws SQLException {
    when(rows.next()).thenReturn(true, false);
    when(rows.getString("CUT_PERMIT_IDS")).thenReturn("001, , ");

    assertThat(repository.find(idir("TAPS_ADMIN"), "A00001", "ZZ1234").orElseThrow().cuttingPermit())
        .isEqualTo("001, , ");
  }

  @Test
  void permitListOverflowFailsWithoutTruncatingOrReturningPartialContext() throws SQLException {
    String cuttingPermits = "001, ".repeat(98) + "001, , 00";
    assertThat(cuttingPermits).hasSize(499);
    when(rows.next()).thenReturn(true, false);
    when(rows.getString("CUT_PERMIT_IDS")).thenReturn(cuttingPermits);

    assertThatThrownBy(() -> repository.find(idir("TAPS_ADMIN"), "A00001", "ZZ1234"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessage("cutting permit list exceeds legacy buffer");
    verifyCleanup(true);
  }

  @Test
  void absentOrOutOfScopeContextIsEmptyWithoutInventingDisplayValues() throws SQLException {
    assertThat(repository.find(idir("TAPS_ADMIN"), "A00001", "ZZ1234")).isEmpty();

    verify(rows, never()).getString(anyString());
    verify(rows, never()).getTimestamp(anyString());
    verifyCleanup(true);
  }

  @Test
  void multipleDistinctPermittedContextsFailInsteadOfTakingAnUnorderedFirstRow()
      throws SQLException {
    when(rows.next()).thenReturn(true, true, false);

    assertThatThrownBy(() -> repository.find(idir("TAPS_ADMIN"), null, "ZZ1234"))
        .isInstanceOf(IncorrectResultSizeDataAccessException.class);
    verifyCleanup(true);
  }

  @Test
  void queryFailurePropagatesAndReleasesResources() throws SQLException {
    SQLException failure = new SQLSyntaxErrorException("Synthetic query failure", "42000");
    when(statement.executeQuery()).thenThrow(failure);

    assertThatThrownBy(() -> repository.find(idir("TAPS_ADMIN"), "A00001", "ZZ1234"))
        .isInstanceOf(DataAccessException.class).hasCause(failure);
    verifyCleanup(false);
  }

  @Test
  void bindingFailureClosesTheStatementWithoutQuerying() throws SQLException {
    SQLException failure = new SQLDataException("Synthetic bind failure", "22000");
    doThrow(failure).when(statement).setString(2, "ZZ1234");

    assertThatThrownBy(() -> repository.find(idir("TAPS_ADMIN"), "A00001", "ZZ1234"))
        .isInstanceOf(DataAccessException.class).hasCause(failure);
    verify(statement, never()).executeQuery();
    verifyCleanup(false);
  }

  @Test
  void dateMappingFailureDoesNotReturnPartialContext() throws SQLException {
    SQLException failure = new SQLDataException("Synthetic date failure", "22000");
    when(rows.next()).thenReturn(true, false);
    when(rows.getTimestamp("EXPIRY_DATE")).thenThrow(failure);

    assertThatThrownBy(() -> repository.find(idir("TAPS_ADMIN"), "A00001", "ZZ1234"))
        .isInstanceOf(DataAccessException.class).hasCause(failure);
    verifyCleanup(true);
  }

  private String capturedSql() throws SQLException {
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
    return user(IdentityProvider.IDIR, roles);
  }

  private TapsUser user(IdentityProvider provider, String... roles) {
    return new TapsUser("synthetic", "Synthetic user", null, provider, null,
        Arrays.stream(roles)
            .map(role -> RoleGrant.accept(FamRoleName.parse(role), provider).orElseThrow())
            .toList());
  }

  static Stream<Arguments> invalidFilters() {
    return Stream.of(
        Arguments.of("A00001", null),
        Arguments.of("A00001", ""),
        Arguments.of("A00001", "   "),
        Arguments.of("A00001", "ZZ12345"),
        Arguments.of("ABCDEFGHIJK", "ZZ1234"));
  }
}
