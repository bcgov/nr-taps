package ca.bc.gov.nrs.taps.read.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.taps.read.CodeOption;
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
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Arrays;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class OracleGasSearchTest {
  private final DataSource dataSource = mock(DataSource.class);
  private final Connection connection = mock(Connection.class);
  private final PreparedStatement statement = mock(PreparedStatement.class);
  private final ResultSet rows = mock(ResultSet.class);
  private final OracleGasSearch repository = new OracleGasSearch(new JdbcTemplate(dataSource));

  @BeforeEach
  void prepareJdbcBoundary() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(rows);
    when(rows.next()).thenReturn(true, false);
  }

  @Test
  void scopesAllThreeFamiliesBeforeUnionCountAndPageWithOnlyPreparedValues() throws SQLException {
    var user = idir("TAPS_REGION_APPRAISER_REGION-CARIBOO", "TAPS_REGION_CLERK_REGION-SKEENA");
    var search = new GasAppraisal.Search("a'--", "x_%'", 1);
    var plan = GasSearchPlan.forUser(user, search);

    assertThat(repository.search(user, search).items()).isEmpty();

    String sql = sql();
    assertThat(sql).contains("0 AS APPRAISAL_TYPE_CODE", "1 AS APPRAISAL_TYPE_CODE", "2 AS APPRAISAL_TYPE_CODE",
        "JOIN ADS_SUBMITTED_TIMBER_MARK M ON M.ECAS_ID = P.ECAS_ID",
        "record_scope.ACTIVE_IND = 'Y'",
        "FROM (SELECT COUNT(*) AS TOTAL FROM scoped_rows) totals",
        "LEFT JOIN numbered_rows page_rows ON page_rows.RESULT_ROW BETWEEN ? AND ?")
        .doesNotContain("UNION ALL", "A'--", "X_%'", "TRUNC(", "COUNT(DISTINCT", "PARTITION BY");
    assertThat(sql.split("UNION\\n", -1)).hasSize(3);
    assertThat(sql.split(java.util.regex.Pattern.quote("WHERE " + plan.sql()), -1)).hasSize(4);
    assertThat(sql.lastIndexOf("WHERE " + plan.sql())).isLessThan(sql.indexOf("ROW_NUMBER() OVER"));
    assertThat(sql.chars().filter(c -> c == '?').count()).isEqualTo(plan.parameters().size() * 3 + 2);
    verifyBindings(plan);
    verify(statement).executeQuery();
    verify(connection, never()).createStatement();
    cleanup(true);
  }

  @Test
  void keepsSearchAClientAndProvisionalFamilyOrganizationsSeparateFromFtaPanel() throws SQLException {
    repository.search(idir("TAPS_DISTRICT_APPRAISER_DISTRICT-DZZ"), new GasAppraisal.Search(null, null, 0));

    assertThat(sql()).contains("ADS.CLIENT_NUMBER", "ADSC.APPRAISAL_METHOD_CODE",
        "DISTRICT.ORG_UNIT_NO = ADS.ADMIN_DISTRICT",
        "SELECT DISTINCT HVA.FOREST_FILE_ID", "SELECT DISTINCT FFC.CLIENT_NUMBER",
        "FFC.FOREST_FILE_CLIENT_TYPE_CODE = 'A'",
        "SELECT DISTINCT HVA.FOREST_DISTRICT",
        "COALESCE(HVA.FOREST_DISTRICT, BRM.FOREST_DISTRICT, PMC.FOREST_DISTRICT)",
        "DISTRICT.ORG_UNIT_NO = P.FOREST_DISTRICT",
        "REGION.ORG_UNIT_NO = DISTRICT.ROLLUP_REGION_NO")
        .doesNotContain("ADSC.CLIENT_NUMBER", "FOREST_FILE_CLIENT_TYPE_CODE = 'S'", "MIN(", "MAX(");
  }

  @Test
  void unrelatedGrantsDoNotAcquireScopeFromFiltersForAnyFamily() throws SQLException {
    var user = idir("TAPS_HEADQUARTERS", "TAPS_VIEWER_DISTRICT-DZZ");
    var search = new GasAppraisal.Search("A00001", "ZZ0001", 0);
    repository.search(user, search);
    assertThat(sql().split(java.util.regex.Pattern.quote("WHERE ((1 = 0) AND"), -1)).hasSize(4);
    verifyBindings(GasSearchPlan.forUser(user, search));
  }

  @Test
  void statusLabelsUseCorrectFamilyWithoutChangingUnionProjectionOrCount() throws SQLException {
    repository.search(idir("TAPS_ADMIN"), new GasAppraisal.Search(null, null, 0));
    String sql = sql();
    assertThat(sql).contains("CASE WHEN page_rows.APPRAISAL_TYPE_CODE = 1 THEN",
        "FROM NON_APPRAISED_STATUS_CODE C", "C.NON_APPRAISED_STATUS_CODE = page_rows.STATUS_CODE",
        "FROM APPRAISAL_STATUS_CODE C", "C.APPRAISAL_STATUS_CODE = page_rows.STATUS_CODE",
        "SYSDATE BETWEEN C.EFFECTIVE_DATE AND C.EXPIRY_DATE");
    String firstFamily = sql.substring(sql.indexOf("SELECT record_scope."), sql.indexOf("FROM ("));
    assertThat(firstFamily).contains("record_scope.APPRAISAL_METHOD_CODE", "record_scope.CLIENT_NUMBER")
        .doesNotContain("STATUS_DESCRIPTION", "ADMIN_DISTRICT_CODE", "ROLLUP_REGION_CODE");
  }

  @Test
  void sortRetainsRawStatusDateOrderingAndAllUnionFieldsForStableTies() throws SQLException {
    repository.search(idir("TAPS_ADMIN"), new GasAppraisal.Search(null, null, 0));
    assertThat(sql()).containsPattern("(?s)ORDER BY STATUS_CODE ASC, EFFECTIVE_DATE DESC,\\s+"
        + "APPRAISAL_TYPE_CODE ASC, WORKSHEET_ID DESC,\\s+"
        + "TIMBER_MARK ASC NULLS LAST, LICENSE ASC NULLS LAST,\\s+"
        + "APPRAISAL_METHOD_CODE ASC NULLS LAST, CLIENT_NUMBER ASC NULLS LAST,\\s+"
        + "EXPIRY_DATE ASC NULLS LAST, REFERENCE_TYPE ASC NULLS LAST")
        .contains("ORDER BY page_rows.RESULT_ROW");
  }

  @Test
  void equalIdsAcrossFamiliesAndRepeatedMultiMarksRemainSeparate() throws SQLException {
    when(rows.next()).thenReturn(true, true, true, true, true, false);
    when(rows.getLong("TOTAL")).thenReturn(31L);
    when(rows.getString("WORKSHEET_ID")).thenReturn("999999999999");
    when(rows.getInt("APPRAISAL_TYPE_CODE")).thenReturn(0, 1, 2, 0, 0);
    when(rows.getString("TIMBER_MARK")).thenReturn("ZZ0001", "ZZ0001", "ZZ0001", "ZZ0002", "ZZ0002");
    when(rows.getString("LICENSE")).thenReturn(" A00001 ");
    when(rows.getTimestamp("EFFECTIVE_DATE")).thenReturn(Timestamp.valueOf("2035-01-02 23:59:59"));
    when(rows.getString("STATUS_CODE")).thenReturn("CON");
    when(rows.getString("STATUS_DESCRIPTION")).thenReturn(" Confirmed ");

    var page = repository.search(idir("TAPS_ADMIN"), new GasAppraisal.Search(null, null, 0));

    assertThat(page.items()).extracting(item -> item.key().type()).containsExactly(
        GasAppraisal.WorksheetType.APPRAISED, GasAppraisal.WorksheetType.NON_APPRAISED,
        GasAppraisal.WorksheetType.HISTORIC, GasAppraisal.WorksheetType.APPRAISED, GasAppraisal.WorksheetType.APPRAISED);
    assertThat(page.items()).extracting(GasAppraisal.Item::timberMark)
        .containsExactly("ZZ0001", "ZZ0001", "ZZ0001", "ZZ0002", "ZZ0002");
    assertThat(page.items().getFirst()).isEqualTo(new GasAppraisal.Item(
        new GasAppraisal.Key(GasAppraisal.WorksheetType.APPRAISED, "999999999999"),
        " A00001 ", "ZZ0001", LocalDate.of(2035, 1, 2), null, new CodeOption("CON", " Confirmed "), null));
    assertThat(page.total()).isEqualTo(31);
    cleanup(true);
  }

  @Test
  void emptyOutOfRangePageRetainsCountWithoutArtificialRow() throws SQLException {
    when(rows.getLong("TOTAL")).thenReturn(31L);
    var page = repository.search(idir("TAPS_ADMIN"), new GasAppraisal.Search(null, null, 4));
    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isEqualTo(31);
    assertThat(page.page()).isEqualTo(4);
    verify(rows, never()).getInt("APPRAISAL_TYPE_CODE");
  }

  @Test
  void maximumPageUsesLongBounds() throws SQLException {
    var user = idir("TAPS_ADMIN");
    var search = new GasAppraisal.Search(null, null, Integer.MAX_VALUE);
    repository.search(user, search);
    verify(statement).setLong(58, 21_474_836_471L);
    verify(statement).setLong(59, 21_474_836_480L);
  }

  @Test
  void scalarCardinalityErrorsFailWholeSearchAndReleaseResources() throws SQLException {
    SQLException error = new SQLException("Synthetic scalar multi-row", "21000", 1427);
    when(statement.executeQuery()).thenThrow(error);
    assertThatThrownBy(() -> repository.search(idir("TAPS_ADMIN"), new GasAppraisal.Search(null, null, 0)))
        .isInstanceOf(DataAccessException.class).hasCause(error);
    cleanup(false);
  }

  @Test
  void bindingFailureClosesStatementWithoutQuerying() throws SQLException {
    var error = new SQLDataException("Synthetic bind error", "22000");
    doThrow(error).when(statement).setString(1, "ACC");
    assertThatThrownBy(() -> repository.search(idir("TAPS_ADMIN"), new GasAppraisal.Search(null, null, 0)))
        .isInstanceOf(DataAccessException.class).hasCause(error);
    verify(statement, never()).executeQuery();
    cleanup(false);
  }

  @Test
  void unknownFamilyFailsInsteadOfReturningPartialPage() throws SQLException {
    when(rows.next()).thenReturn(true, true, false);
    when(rows.getLong("TOTAL")).thenReturn(2L);
    when(rows.getString("WORKSHEET_ID")).thenReturn("1");
    when(rows.getInt("APPRAISAL_TYPE_CODE")).thenReturn(0, 7);
    assertThatThrownBy(() -> repository.search(idir("TAPS_ADMIN"), new GasAppraisal.Search(null, null, 0)))
        .isInstanceOf(IllegalArgumentException.class);
    cleanup(true);
  }

  private String sql() throws SQLException {
    var capture = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(capture.capture());
    return capture.getValue();
  }

  private void verifyBindings(GasSearchPlan plan) throws SQLException {
    int index = 1;
    for (int family = 0; family < 3; family++) {
      for (String value : plan.parameters()) {
        verify(statement).setString(index++, value);
      }
    }
    verify(statement).setLong(index++, plan.firstRow());
    verify(statement).setLong(index, plan.lastRow());
  }

  private void cleanup(boolean resultSetOpened) throws SQLException {
    if (resultSetOpened) {
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
}
