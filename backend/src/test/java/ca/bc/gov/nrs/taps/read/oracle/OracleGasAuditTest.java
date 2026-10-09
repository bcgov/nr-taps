package ca.bc.gov.nrs.taps.read.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import ca.bc.gov.nrs.taps.read.GasAppraisal;
import ca.bc.gov.nrs.taps.read.GasAudit;
import ca.bc.gov.nrs.taps.security.FamRoleName;
import ca.bc.gov.nrs.taps.security.IdentityProvider;
import ca.bc.gov.nrs.taps.security.RoleGrant;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class OracleGasAuditTest {
  private final DataSource dataSource = mock(DataSource.class);
  private final Connection connection = mock(Connection.class);
  private final PreparedStatement statement = mock(PreparedStatement.class);
  private final ResultSet rows = mock(ResultSet.class);
  private final OracleGasAudit audit = new OracleGasAudit(new JdbcTemplate(dataSource));

  @BeforeEach
  void prepareBoundary() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(rows);
    when(rows.next()).thenReturn(true, false);
  }

  @Test
  void scopesBeforeSnapshotsAndBindsOneStatementForCountAndPage() throws SQLException {
    assertThat(audit.history(user("TAPS_DISTRICT_APPRAISER_DISTRICT-DZZ", "TAPS_REGION_APPRAISER_REGION-CARIBOO"),
        nonAppraised("000123"), 2)).isEmpty();
    String sql = sql();
    assertThat(sql).contains("FROM NON_APPRAISED_WORKSHEET W", "WORKSHEET_ID = ? AND (record_scope.ADMIN_DISTRICT_CODE = ? OR record_scope.ROLLUP_REGION_CODE = ?)",
        "FFC.FOREST_FILE_CLIENT_TYPE_CODE = 'A'", "FROM NON_APPRAISED_WORKSHEET_AUD A",
        "FROM NON_APPRAISED_STUMPAGE_RTE_AUD A", "FROM (SELECT COUNT(*) AS TOTAL FROM changed_fields) totals",
        "LEFT JOIN numbered_changes C ON C.RESULT_ROW BETWEEN ? AND ?",
        "CASE WHEN C.SNAPSHOT_ID IS NOT NULL THEN", "ORDER BY C.RESULT_ROW")
        .doesNotContain("123", "DZZ", "RCB", "PKG_", "GAS2_AUDIT", "JOIN GAS_TRANSACTION",
            "FROM SCALE_", "UPDATE ", "INSERT ", "DELETE ");
    assertThat(sql.split(Pattern.quote("WHERE EXISTS (SELECT 1 FROM scoped_parent P WHERE P.WORKSHEET_ID = A.NON_APPRAISED_WORKSHEET_ID)"), -1))
        .hasSize(3);
    assertThat(sql.chars().filter(c -> c == '?').count()).isEqualTo(5);
    verify(statement).setLong(1, 123);
    verify(statement).setString(2, "DZZ");
    verify(statement).setString(3, "RCB");
    verify(statement).setLong(4, 21);
    verify(statement).setLong(5, 30);
    verify(statement).executeQuery();
    verify(connection, never()).createStatement();
    cleanup();
  }

  @Test
  void comparisonWindowsCannotCrossRatesFamiliesOrInventInitialChanges() throws SQLException {
    audit.history(user("TAPS_ADMIN"), nonAppraised("123"), 0);
    String sql = sql();
    assertThat(sql).contains("PARTITION BY A.NON_APPRAISED_WORKSHEET_ID ORDER BY A.UPDATE_TIMESTAMP, A.NON_APPRAISED_WORKSHEET_AUD_ID",
        "PARTITION BY A.NON_APPRAISED_STUMPAGE_RATE_ID ORDER BY A.UPDATE_TIMESTAMP, A.NON_APPRAISED_STMPG_RTE_AUD_ID",
        "A.APPRAISED_WORKSHEET_ID IS NULL AND A.HISTORIC_APPRAISED_WRKSHEET_ID IS NULL",
        "A.NON_APPRAISED_STUMPAGE_RATE_ID AS RATE_ID",
        "ORDER BY EVENT_DATE DESC, SOURCE_ORDER ASC, SNAPSHOT_ID DESC, ATTRIBUTE_ORDINAL ASC")
        .doesNotContain("LEAD(", "TRUNC(A.UPDATE_TIMESTAMP)", "ORDER BY A.UPDATE_TIMESTAMP DESC", "IS DISTINCT FROM");
    assertThat(sql.split("WHERE A.PREVIOUS_ID IS NOT NULL", -1)).hasSize(3);
  }

  @Test
  void appraisedHistoryScopesCurrentAdsBeforeOwnWorksheetAndRateSnapshots() throws SQLException {
    var key = new GasAppraisal.Key(GasAppraisal.WorksheetType.APPRAISED, "000123");
    assertThat(audit.history(user("TAPS_DISTRICT_APPRAISER_DISTRICT-DZZ"), key, 1)).isEmpty();
    String sql = sql();
    assertThat(sql).contains("FROM APPRAISED_WORKSHEET AW",
        "JOIN APPRAISAL_DATA_SUBMISSION ADS ON ADS.ECAS_ID = AW.ECAS_ID",
        "WHERE WORKSHEET_ID = ? AND (record_scope.ADMIN_DISTRICT_CODE = ?)",
        "FROM APPRAISED_WORKSHEET_AUD AWA", "FROM APPRAISED_STUMPAGE_RATE_AUD ASRA",
        "P.WORKSHEET_ID = AWA.APPRAISED_WORKSHEET_ID", "P.WORKSHEET_ID = ASRA.APPRAISED_WORKSHEET_ID",
        "ASRA.HISTORIC_APPRAISED_WRKSHEET_ID IS NULL",
        "PARTITION BY AWA.APPRAISED_WORKSHEET_ID ORDER BY AWA.UPDATE_TIMESTAMP, AWA.APPRAISED_WORKSHEET_AUD_ID",
        "PARTITION BY ASRA.APPRAISED_STUMPAGE_RATE_ID ORDER BY ASRA.UPDATE_TIMESTAMP, ASRA.APPRAISED_STUMPAGE_RATE_AUD_ID",
        "WHERE AWA.PREVIOUS_ID IS NOT NULL", "WHERE ASRA.PREVIOUS_ID IS NOT NULL",
        "FROM (SELECT COUNT(*) AS TOTAL FROM changed_fields) totals",
        "ORDER BY EVENT_DATE DESC, SOURCE_ORDER ASC, SNAPSHOT_ID DESC, ATTRIBUTE_ORDINAL ASC",
        "CASE WHEN C.SNAPSHOT_ID IS NOT NULL THEN")
        .doesNotContain("NON_APPRAISED_STUMPAGE_RTE_AUD", "HISTORIC_APPRAISED_WORKSHEET", "GAS2_AUDIT",
            "DZZ", "UPDATE ", "INSERT ", "DELETE ", "DECODE(AWA.ECAS_ID");
    verify(statement).setLong(1, 123);
    verify(statement).setString(2, "DZZ");
    verify(statement).setLong(3, 11);
    verify(statement).setLong(4, 20);
    assertThat(sql.chars().filter(c -> c == '?').count()).isEqualTo(4);
    cleanup();
  }

  @Test
  void appraisedDatesAndNullableOverridesCompareTypedValuesBeforeFormatting() throws SQLException {
    audit.history(user("TAPS_ADMIN"), new GasAppraisal.Key(GasAppraisal.WorksheetType.APPRAISED, "123"), 0);
    assertThat(sql()).contains("DECODE(AWA.DISCOUNT_PERCENT, AWA.PREV_DISCOUNT_PERCENT, 0, 1)",
        "DECODE(AWA.SDM_DECLARATION_ACCEPTANCE_DT, AWA.PREV_SDM_DATE, 0, 1)",
        "WHEN 3 THEN TO_CHAR(AWA.SDM_DECLARATION_ACCEPTANCE_DT, 'YYYY-MM-DD')",
        "DECODE(AWA.SILVICULTURE_COST_OVERRIDE, AWA.PREV_SILVICULTURE_COST, 0, 1)",
        "DECODE(AWA.LOGGING_COST_OVERRIDE, AWA.PREV_LOGGING_COST, 0, 1)",
        "DECODE(AWA.MANUFACTURING_COST_OVERRIDE, AWA.PREV_MANUFACTURING_COST, 0, 1)",
        "TO_CHAR(AWA.DISCOUNT_PERCENT, 'FM990D0', 'NLS_NUMERIC_CHARACTERS=''.,''')",
        "TO_CHAR(AWA.MANUFACTURING_COST_OVERRIDE, 'FM9999990', 'NLS_NUMERIC_CHARACTERS=''.,''')",
        "DECODE(ASRA.STUMPAGE_RATE_EFFECTIVE_DATE, ASRA.PREV_EFFECTIVE_DATE, 0, 1)",
        "DECODE(ASRA.TOTAL_STUMPAGE_RATE_AMOUNT, ASRA.PREV_TOTAL_RATE, 0, 1)",
        "DECODE(ASRA.UPSET_STUMPAGE_RATE_OVERRIDE, ASRA.PREV_UPSET_OVERRIDE, 0, 1)",
        "DECODE(ASRA.ADJUSTMENT_NOTICE_MESSAGE, ASRA.PREV_ADJUSTMENT_MESSAGE, 0, 1)",
        "TO_CHAR(ASRA.TOTAL_STUMPAGE_RATE_AMOUNT, 'FM9990D00', 'NLS_NUMERIC_CHARACTERS=''.,''')")
        .doesNotContain("DECODE(TO_CHAR", "DECODE(TRUNC", "NVL(AWA.", "NVL(ASRA.", "IS NOT NULL THEN TO_CHAR(AWA.SDM");
  }

  @Test
  void historicFamilyIsRejectedBeforeJdbcEvenWithTheSameNumericId() {
    assertThatThrownBy(() -> audit.history(user("TAPS_ADMIN"),
        new GasAppraisal.Key(GasAppraisal.WorksheetType.HISTORIC, "123"), 0))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("historic");
    verifyNoInteractions(dataSource);
  }

  @Test
  void equalNumericIdsDispatchSeparateFamilySourcesAndReturnTypedKeys() throws SQLException {
    when(rows.next()).thenReturn(true, false, true, false);
    when(rows.getLong("PARENT_COUNT")).thenReturn(1L);
    var appraised = new GasAppraisal.Key(GasAppraisal.WorksheetType.APPRAISED, "123");
    var nonAppraised = nonAppraised("123");
    assertThat(audit.history(user("TAPS_ADMIN"), appraised, 0).orElseThrow().key()).isEqualTo(appraised);
    assertThat(audit.history(user("TAPS_ADMIN"), nonAppraised, 0).orElseThrow().key()).isEqualTo(nonAppraised);
    var capture = ArgumentCaptor.forClass(String.class);
    verify(connection, times(2)).prepareStatement(capture.capture());
    assertThat(capture.getAllValues().get(0)).contains("FROM APPRAISED_WORKSHEET_AUD AWA")
        .doesNotContain("FROM NON_APPRAISED_WORKSHEET_AUD");
    assertThat(capture.getAllValues().get(1)).contains("FROM NON_APPRAISED_WORKSHEET_AUD A")
        .doesNotContain("FROM APPRAISED_WORKSHEET_AUD");
  }

  @Test
  void gradeAndEachNullableLevyCompareTheirOwnTypedPreviousValue() throws SQLException {
    audit.history(user("TAPS_ADMIN"), nonAppraised("123"), 0);
    assertThat(sql()).contains("WHEN 3 THEN A.SCALE_GRADE_CODE",
        "DECODE(A.SCALE_GRADE_CODE, A.PREV_SCALE_GRADE_CODE, 0, 1)",
        "DECODE(A.BONUS_BID_AMOUNT, A.PREV_BONUS_BID_AMOUNT, 0, 1)",
        "DECODE(A.DEVELOPMENT_LEVY, A.PREV_DEVELOPMENT_LEVY, 0, 1)",
        "DECODE(A.SILVICULTURE_LEVY, A.PREV_SILVICULTURE_LEVY, 0, 1)",
        "DECODE(A.EFFECTIVE_DATE, A.PREV_EFFECTIVE_DATE, 0, 1)",
        "DECODE(A.NOTICE_CREATE_DATE, A.PREV_NOTICE_CREATE_DATE, 0, 1)",
        "DECODE(A.APPRAISAL_FOREST_ZONE_CODE, A.PREV_FOREST_ZONE, 0, 1)",
        "DECODE(A.NON_APPRAISED_RATE_TYPE_CODE, A.PREV_RATE_TYPE, 0, 1)",
        "DECODE(A.RATE_ADJUSTMENT_TYPE_CODE, A.PREV_RATE_ADJUSTMENT_TYPE_CODE, 0, 1)",
        "TO_CHAR(A.EFFECTIVE_DATE, 'YYYY-MM-DD')",
        "TO_CHAR(A.RESERVE_STUMPAGE_RATE, 'FM990D00', 'NLS_NUMERIC_CHARACTERS=''.,''')")
        .doesNotContain("DECODE(TO_CHAR", "DECODE(TRUNC", "NVL(A.", "TRIM(", "REPLACE(");
  }

  @Test
  void missingScopeIsDeniedEvenForAnExistingNumericId() throws SQLException {
    assertThat(audit.history(user("TAPS_HEADQUARTERS", "TAPS_VIEWER_DISTRICT-DZZ"), nonAppraised("123"), 0)).isEmpty();
    assertThat(sql()).contains("WHERE WORKSHEET_ID = ? AND (1 = 0)");
  }

  @Test
  void emptyHistoryAndOverflowPageKeepAuthorizedParentAndTotal() throws SQLException {
    when(rows.getLong("PARENT_COUNT")).thenReturn(1L);
    when(rows.getLong("TOTAL")).thenReturn(23L);
    var page = audit.history(user("TAPS_ADMIN"), nonAppraised("123"), Integer.MAX_VALUE).orElseThrow();
    assertThat(page.key()).isEqualTo(new GasAppraisal.Key(GasAppraisal.WorksheetType.NON_APPRAISED, "123"));
    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isEqualTo(23);
    assertThat(page.size()).isEqualTo(10);
    verify(statement).setLong(2, 21_474_836_471L);
    verify(statement).setLong(3, 21_474_836_480L);
    verify(rows, never()).getTimestamp(anyString());
  }

  @Test
  void authorizedParentWithNoChangedFieldsReturnsAnEmptyHistory() throws SQLException {
    when(rows.getLong("PARENT_COUNT")).thenReturn(1L);
    var page = audit.history(user("TAPS_ADMIN"), nonAppraised("123"), 0).orElseThrow();
    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isZero();
    assertThat(page.page()).isZero();
    verify(rows, never()).getTimestamp(anyString());
  }

  @Test
  void outputRetainsNewerActorTimestampCommentsValuesAndSeparateRateIdentity() throws SQLException {
    when(rows.next()).thenReturn(true, true, false);
    when(rows.getLong("PARENT_COUNT")).thenReturn(1L);
    when(rows.getLong("TOTAL")).thenReturn(2L);
    when(rows.getString("WORKSHEET_ID")).thenReturn("123");
    when(rows.getString("EVENT_ID")).thenReturn("R:402:3", "R:403:6");
    when(rows.getString("RATE_ID")).thenReturn("201", "202");
    when(rows.getString("USER_ID")).thenReturn("IDIR\\SYNTHETIC", (String) null);
    when(rows.getTimestamp("EVENT_DATE")).thenReturn(Timestamp.valueOf("2030-01-01 12:34:56"));
    when(rows.getString("ATTRIBUTE")).thenReturn("Grade", "Silviculture Levy");
    when(rows.getString("CHANGED_VALUE")).thenReturn(" ", (String) null);
    when(rows.getString("COMMENT_TEXT")).thenReturn("<b>Synthetic comment</b>", (String) null);
    var page = audit.history(user("TAPS_ADMIN"), nonAppraised("123"), 0).orElseThrow();
    assertThat(page.items()).containsExactly(
        new GasAudit.Item("R:402:3", "201", "IDIR\\SYNTHETIC", LocalDateTime.of(2030, 1, 1, 12, 34, 56),
            "Grade", " ", "<b>Synthetic comment</b>"),
        new GasAudit.Item("R:403:6", "202", null, LocalDateTime.of(2030, 1, 1, 12, 34, 56),
            "Silviculture Levy", null, null));
    assertThatThrownBy(() -> page.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    cleanup();
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "bad", "1234567890123"})
  void invalidIdStopsBeforeJdbc(String id) {
    assertThatThrownBy(() -> audit.history(user("TAPS_ADMIN"), nonAppraised(id), 0)).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(dataSource);
  }

  @Test
  void negativePageStopsBeforeJdbc() {
    assertThatThrownBy(() -> audit.history(user("TAPS_ADMIN"), nonAppraised("123"), -1)).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(dataSource);
  }

  @Test
  void ambiguousParentFailsTheWholeHistory() throws SQLException {
    when(rows.getLong("PARENT_COUNT")).thenReturn(2L);
    assertThatThrownBy(() -> audit.history(user("TAPS_ADMIN"), nonAppraised("123"), 0))
        .isInstanceOf(DataIntegrityViolationException.class).hasMessage("ambiguous history parent");
    cleanup();
  }

  @Test
  void aChildCannotBeReturnedForAnUnscopedParent() throws SQLException {
    when(rows.getString("EVENT_ID")).thenReturn("W:301:2");
    when(rows.getString("WORKSHEET_ID")).thenReturn("124");
    assertThatThrownBy(() -> audit.history(user("TAPS_ADMIN"), nonAppraised("123"), 0))
        .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("requested worksheet");
  }

  @Test
  void missingTimestampIsAStoredDataFailure() throws SQLException {
    when(rows.getLong("PARENT_COUNT")).thenReturn(1L);
    when(rows.getString("EVENT_ID")).thenReturn("W:301:2");
    when(rows.getString("WORKSHEET_ID")).thenReturn("123");
    assertThatThrownBy(() -> audit.history(user("TAPS_ADMIN"), nonAppraised("123"), 0))
        .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("timestamp");
  }

  @Test
  void queryFailurePropagatesWithoutPartialHistoryAndClosesResources() throws SQLException {
    var failure = new SQLException("Synthetic query failure", "42000");
    when(statement.executeQuery()).thenThrow(failure);
    assertThatThrownBy(() -> audit.history(user("TAPS_ADMIN"), nonAppraised("123"), 0))
        .isInstanceOf(DataAccessException.class).hasCause(failure);
    verify(statement).close();
    verify(connection).close();
  }

  private String sql() throws SQLException {
    var capture = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(capture.capture());
    return capture.getValue();
  }

  private void cleanup() throws SQLException {
    verify(rows).close();
    verify(statement).close();
    verify(connection).close();
  }

  private static GasAppraisal.Key nonAppraised(String id) {
    return new GasAppraisal.Key(GasAppraisal.WorksheetType.NON_APPRAISED, id);
  }

  private static TapsUser user(String... roles) {
    return new TapsUser("synthetic", "Synthetic", null, IdentityProvider.IDIR, null,
        Arrays.stream(roles).map(role -> RoleGrant.accept(FamRoleName.parse(role), IdentityProvider.IDIR).orElseThrow()).toList());
  }
}
