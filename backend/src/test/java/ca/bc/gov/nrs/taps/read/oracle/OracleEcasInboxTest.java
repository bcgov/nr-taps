package ca.bc.gov.nrs.taps.read.oracle;

import static ca.bc.gov.nrs.taps.read.oracle.EcasInboxPlanTest.idir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.taps.domain.AppraisalMethod;
import ca.bc.gov.nrs.taps.read.EcasInbox;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class OracleEcasInboxTest {
  private final DataSource dataSource = mock(DataSource.class);
  private final Connection connection = mock(Connection.class);
  private final PreparedStatement statement = mock(PreparedStatement.class);
  private final ResultSet rows = mock(ResultSet.class);
  private final OracleEcasInbox repository = new OracleEcasInbox(new JdbcTemplate(dataSource));

  @BeforeEach
  void prepareBoundary() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(rows);
    when(rows.next()).thenReturn(true, false);
  }

  @Test
  void oneStatementScopesBothCountAndPageWithoutTempTableMutation() throws SQLException {
    var filters = new EcasInboxPlanTest.Filters();
    filters.mark = "x_%'";
    var user = idir("TAPS_REGION_APPRAISER_REGION-CARIBOO");
    var plan = EcasInboxPlan.forUser(user, filters.search(), 1);

    assertThat(repository.search(user, filters.search(), 1)).isEqualTo(new EcasInbox.Page(List.of(), 0, 1));

    String sql = sql();
    assertThat(sql).containsOnlyOnce("WHERE " + plan.sql())
        .contains("SELECT DISTINCT record_scope.ECAS_ID", "FROM (SELECT COUNT(*) AS TOTAL FROM scoped_rows) totals",
            "LEFT JOIN numbered_rows page_rows ON page_rows.RESULT_ROW BETWEEN ? AND ?",
            "ORDER BY page_rows.RESULT_ROW")
        .doesNotContain("DELETE", "INSERT", "UPDATE ", "ECAS05_INBOX_SEARCH", "X_%'");
    assertThat(sql.indexOf("WHERE " + plan.sql())).isLessThan(sql.indexOf("ROW_NUMBER() OVER"));
    assertThat(sql.chars().filter(c -> c == '?').count()).isEqualTo(plan.parameters().size() + 2);
    int index = 1;
    for (String value : plan.parameters()) {
      verify(statement).setString(index++, value);
    }
    verify(statement).setLong(index++, 101);
    verify(statement).setLong(index, 200);
    verify(connection, never()).createStatement();
    cleanup(true);
  }

  @Test
  void sourceJoinsRetainEveryPermitAndUseCurrentAdsOwnership() throws SQLException {
    repository.search(idir("TAPS_ADMIN"), new EcasInboxPlanTest.Filters().search(), 0);

    assertThat(sql()).contains(
        "JOIN ADS_SUBMITTED_TIMBER_MARK ASTM ON ASTM.ECAS_ID = ADS.ECAS_ID",
        "LEFT JOIN HAULING_AUTHORITY HLA ON HLA.TIMBER_MARK = ASTM.TIMBER_MARK",
        "LEFT JOIN HARVESTING_HAULING_XREF HHX ON HHX.TIMBER_MARK = HLA.TIMBER_MARK",
        "LEFT JOIN HARVESTING_AUTHORITY HVA ON HVA.HVA_SKEY = HHX.HVA_SKEY",
        "LEFT JOIN PROV_FOREST_USE PFU ON PFU.FOREST_FILE_ID = ADS.FOREST_FILE_ID",
        "LEFT JOIN ORG_UNIT DISTRICT ON DISTRICT.ORG_UNIT_NO = ADS.ADMIN_DISTRICT",
        "LEFT JOIN ORG_UNIT REGION ON REGION.ORG_UNIT_NO = DISTRICT.ROLLUP_REGION_NO",
        "ADS.CLIENT_NUMBER", "SUBSTR(SIL_GET_CLIENT_NAME(ADS.CLIENT_NUMBER), 1, 30)",
        "NVL(HVA.EXTEND_DATE, HVA.EXPIRY_DATE)")
        .doesNotContain("ADSC.CLIENT_NUMBER", "MAX(HVA", "MIN(HVA", "SYSDATE", "PRIMARY_MARK_IND = 'Y' AND HVA");
  }

  @Test
  void distinctDatesAndSortPreserveLegacyDayPrecisionWithStablePermitTies() throws SQLException {
    var filters = new EcasInboxPlanTest.Filters();
    filters.sort = EcasInbox.SortField.STATUS;
    filters.direction = EcasInbox.SortDirection.ASC;
    repository.search(idir("TAPS_ADMIN"), filters.search(), 0);

    assertThat(sql()).contains("TRUNC(record_scope.EFFECTIVE_DATE) AS EFFECTIVE_DATE",
        "TRUNC(record_scope.UPDATE_DATE) AS UPDATE_DATE", "ORDER BY STATUS_DESCRIPTION ASC,",
        "ECAS_ID DESC, TIMBER_MARK ASC NULLS LAST, LICENCE ASC NULLS LAST,",
        "CUTTING_PERMIT ASC NULLS LAST", "REVISION_COUNT ASC NULLS LAST");
  }

  @Test
  void preservesMultiPermitRowsNullsExactNamesAndAllItemFields() throws SQLException {
    when(rows.next()).thenReturn(true, true, false);
    when(rows.getLong("TOTAL")).thenReturn(204L);
    when(rows.getString("ECAS_ID")).thenReturn("999999999999");
    when(rows.getString("APPRAISAL_METHOD_CODE")).thenReturn("C");
    when(rows.getString("TIMBER_MARK")).thenReturn("ZZ0001");
    when(rows.getString("LICENCE")).thenReturn(" A00001 ");
    when(rows.getString("CUTTING_PERMIT")).thenReturn("1", "2");
    when(rows.getString("STATUS_CODE")).thenReturn("RGN");
    when(rows.getString("STATUS_DESCRIPTION")).thenReturn(" Region ");
    when(rows.getString("APPRAISAL_TYPE")).thenReturn("New appraisal");
    when(rows.getTimestamp("STATUS_DATE")).thenReturn(Timestamp.valueOf("2026-01-01 00:00:00"));
    when(rows.getTimestamp("EFFECTIVE_DATE")).thenReturn(Timestamp.valueOf("2026-01-02 00:00:00"));
    when(rows.getTimestamp("LICENSEE_SUBMITTED_DATE")).thenReturn(Timestamp.valueOf("2026-01-03 00:00:00"));
    when(rows.getTimestamp("DISTRICT_RECEIVED_DATE")).thenReturn(Timestamp.valueOf("2026-01-04 00:00:00"));
    when(rows.getTimestamp("SENT_TO_REGION_DATE")).thenReturn(Timestamp.valueOf("2026-01-05 00:00:00"));
    when(rows.getString("MULTI_TIMBER_MARKS_IND")).thenReturn("Y");
    when(rows.getString("CLIENT_NUMBER")).thenReturn("00000042");
    when(rows.getString("CLIENT_LOCN_CODE")).thenReturn("01");
    when(rows.getString("CLIENT_NAME")).thenReturn(" Example client ");
    when(rows.getObject("REVISION_COUNT", Integer.class)).thenReturn(12, (Integer) null);

    var page = repository.search(idir("TAPS_ADMIN"), new EcasInboxPlanTest.Filters().search(), 1);

    assertThat(page.total()).isEqualTo(204);
    assertThat(page.page()).isEqualTo(1);
    assertThat(page.items()).extracting(EcasInbox.Item::cuttingPermit).containsExactly("1", "2");
    var first = page.items().getFirst();
    assertThat(first.ecasId()).isEqualTo("999999999999");
    assertThat(first.appraisalMethod()).isEqualTo(AppraisalMethod.C);
    assertThat(first.timberMark()).isEqualTo("ZZ0001");
    assertThat(first.licence()).isEqualTo(" A00001 ");
    assertThat(first.status().description()).isEqualTo(" Region ");
    assertThat(first.statusDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(first.appraisalTypeDescription()).isEqualTo("New appraisal");
    assertThat(first.effectiveDate()).isEqualTo(LocalDate.of(2026, 1, 2));
    assertThat(first.expiryDate()).isNull();
    assertThat(first.licenseeSubmittedDate()).isEqualTo(LocalDate.of(2026, 1, 3));
    assertThat(first.districtReceivedDate()).isEqualTo(LocalDate.of(2026, 1, 4));
    assertThat(first.sentToRegionDate()).isEqualTo(LocalDate.of(2026, 1, 5));
    assertThat(first.multipleTimberMarks()).isTrue();
    assertThat(first.clientNumber()).isEqualTo("00000042");
    assertThat(first.clientLocationCode()).isEqualTo("01");
    assertThat(first.clientName()).isEqualTo(" Example client ");
    assertThat(first.revisionCount()).isEqualTo(12);
    assertThat(page.items().get(1).revisionCount()).isNull();
    assertThatThrownBy(() -> page.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    cleanup(true);
  }

  @Test
  void emptyOutOfRangePageKeepsTotalWithoutCreatingAnArtificialItem() throws SQLException {
    when(rows.getLong("TOTAL")).thenReturn(204L);

    var page = repository.search(idir("TAPS_ADMIN"), new EcasInboxPlanTest.Filters().search(), 3);

    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isEqualTo(204);
    verify(rows, never()).getTimestamp(anyString());
    cleanup(true);
  }

  @Test
  void noGrantIsDeniedBeforeBothCountAndPage() throws SQLException {
    var filters = new EcasInboxPlanTest.Filters();
    filters.id = "123";
    repository.search(idir(), filters.search(), 0);

    assertThat(sql()).contains("WHERE ((1 = 0) AND record_scope.ECAS_ID = ?");
  }

  @Test
  void assignedQueueWithoutReliableAccountDoesNotOpenAConnection() throws SQLException {
    var filters = new EcasInboxPlanTest.Filters();
    filters.mode = EcasInbox.Mode.MY_TO_DO;

    assertThatThrownBy(() -> repository.search(idir("TAPS_HEADQUARTERS"), filters.search(), 0))
        .isInstanceOf(IllegalArgumentException.class);
    verify(dataSource, never()).getConnection();
  }

  @Test
  void myToDoAssignmentIsBoundInsideTheSingleCountAndPageStatement() throws SQLException {
    var filters = new EcasInboxPlanTest.Filters();
    filters.mode = EcasInbox.Mode.MY_TO_DO;
    repository.search(EcasInboxPlanTest.assignedIdir("TAPS_DISTRICT_APPRAISER_DISTRICT-DZZ"), filters.search(), 0);
    assertThat(sql()).containsOnlyOnce("EXISTS (SELECT 1 FROM ADS_ASSIGNED_TO_USER assigned")
        .contains("assigned.ECAS_ID = record_scope.ECAS_ID AND assigned.USER_ID = ?",
            "FROM scoped_rows", "PFU.FOREST_FILE_ID AS LICENCE")
        .doesNotContain("JOIN ADS_ASSIGNED_TO_USER", "IDIR\\SYNTHETIC", "PKG_", "DELETE ", "INSERT ", "UPDATE ");
    verify(statement).setString(1, "DZZ");
    verify(statement).setString(2, "IDIR\\SYNTHETIC");
    verify(statement).setLong(3, 1);
    verify(statement).setLong(4, 100);
    cleanup(true);
  }

  @Test
  void directIdInDefaultModeDoesNotRequireAssignmentsOrBypassFamScope() throws SQLException {
    var filters = new EcasInboxPlanTest.Filters();
    filters.mode = EcasInbox.Mode.MY_TO_DO;
    filters.id = "123";
    repository.search(idir("TAPS_VIEWER_DISTRICT-DZZ"), filters.search(), 0);
    assertThat(sql()).contains("record_scope.ADMIN_DISTRICT_CODE = ?",
        "record_scope.STATUS_CODE <> ?", "record_scope.ECAS_ID = ?",
        "record_scope.PRIMARY_MARK_IND = 'Y'").doesNotContain("ADS_ASSIGNED_TO_USER");
    verify(statement).setString(1, "DZZ");
    verify(statement).setString(2, "DFT");
    verify(statement).setString(3, "123");
    cleanup(true);
  }

  @Test
  void queryFailurePropagatesAndClosesJdbcResources() throws SQLException {
    var failure = new SQLSyntaxErrorException("Synthetic query failure", "42000");
    when(statement.executeQuery()).thenThrow(failure);

    assertThatThrownBy(() -> repository.search(idir("TAPS_ADMIN"), new EcasInboxPlanTest.Filters().search(), 0))
        .isInstanceOf(DataAccessException.class).hasCause(failure);
    cleanup(false);
  }

  @Test
  void bindingFailurePropagatesAndDoesNotExecute() throws SQLException {
    var failure = new SQLException("Synthetic bind failure", "22000");
    doThrow(failure).when(statement).setString(1, "RCB");

    assertThatThrownBy(() -> repository.search(idir("TAPS_REGION_APPRAISER_REGION-CARIBOO"), new EcasInboxPlanTest.Filters().search(), 0))
        .isInstanceOf(DataAccessException.class).hasCause(failure);
    verify(statement, never()).executeQuery();
    cleanup(false);
  }

  @Test
  void mappingFailureDoesNotReturnPartialResultsAndClosesEverything() throws SQLException {
    when(rows.getLong("TOTAL")).thenReturn(1L);
    when(rows.getString("ECAS_ID")).thenReturn("123");
    when(rows.getString("APPRAISAL_METHOD_CODE")).thenReturn("UNSUPPORTED");

    assertThatThrownBy(() -> repository.search(idir("TAPS_ADMIN"), new EcasInboxPlanTest.Filters().search(), 0))
        .isInstanceOf(IllegalArgumentException.class);
    cleanup(true);
  }

  private String sql() throws SQLException {
    var captured = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(captured.capture());
    return captured.getValue();
  }

  private void cleanup(boolean opened) throws SQLException {
    if (opened) {
      verify(rows).close();
    }
    verify(statement).close();
    verify(connection).close();
  }
}
