package ca.bc.gov.nrs.taps.read.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import ca.bc.gov.nrs.taps.security.FamRoleName;
import ca.bc.gov.nrs.taps.security.IdentityProvider;
import ca.bc.gov.nrs.taps.security.RoleGrant;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class OracleEcasAuditTest {
  private final DataSource dataSource = mock(DataSource.class);
  private final Connection connection = mock(Connection.class);
  private final PreparedStatement statement = mock(PreparedStatement.class);
  private final ResultSet rows = mock(ResultSet.class);
  private final OracleEcasAudit reader = new OracleEcasAudit(new JdbcTemplate(dataSource));

  @BeforeEach
  void prepare() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(rows);
  }

  @Test
  void historyScopesBeforeCountAndPageAndBindsOnlyAcceptedGrant() throws Exception {
    when(rows.next()).thenReturn(true, false);
    when(rows.getLong("PARENT_COUNT")).thenReturn(1L);
    when(rows.getLong("TOTAL")).thenReturn(135L);
    var page = reader.history(idir("TAPS_REGION_APPRAISER_REGION-CARIBOO"), "001001", 2).orElseThrow();
    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isEqualTo(135);
    assertThat(page.ecasId()).isEqualTo("1001");
    assertThat(sql()).contains("ECAS_ID = ? AND ((record_scope.ROLLUP_REGION_CODE = ?))",
        "EXISTS (SELECT 1 FROM scoped_parent P WHERE P.ECAS_ID = E.ECAS_ID)",
        "SELECT COUNT(*) AS TOTAL FROM scoped_events", "ORDER BY ENTRY_TIMESTAMP ASC, AUDIT_EVENT_ID ASC",
        "F.ECAS_SUBMITTED_FILE_ID = E.ECAS_SUBMITTED_FILE_ID AND F.ECAS_ID = E.ECAS_ID",
        "C.ECAS_ID = E.ECAS_ID AND C.AUDIT_EVENT_ID = E.AUDIT_EVENT_ID",
        "E.ECAS_ACTION_CODE <> 'IMP'")
        .doesNotContain("RCB", "1001", "FILE_CONTENT", "INSERT ", "UPDATE ", "DELETE ");
    verify(statement).setLong(1, 1001);
    verify(statement).setString(2, "RCB");
    verify(statement).setLong(3, 201);
    verify(statement).setLong(4, 300);
    verify(rows).close();
    verify(statement).close();
    verify(connection).close();
  }

  @Test
  void missingGrantOrParentDoesNotReturnAnArtificialHistory() throws Exception {
    when(rows.next()).thenReturn(true, false);
    assertThat(reader.history(idir(), "1001", 0)).isEmpty();
    assertThat(sql()).contains("ECAS_ID = ? AND (1 = 0)");
  }

  @Test
  void historyPreservesStoredIdentityAndNullableMetadata() throws Exception {
    event();
    var page = reader.history(idir("TAPS_ADMIN"), "1001", 0).orElseThrow();
    assertThat(page.items()).hasSize(1);
    var item = page.items().getFirst();
    assertThat(item.eventId()).isEqualTo("60001");
    assertThat(item.userId()).isEqualTo("IDIR\\SYNTHETIC");
    assertThat(item.eventDate()).isEqualTo(LocalDateTime.of(2026, 1, 2, 13, 14, 15));
    assertThat(item.sentToUserId()).isNull();
    assertThat(item.fileName()).isNull();
    assertThat(item.hasMoreComment()).isTrue();
  }

  @Test
  void detailsBindBothIdsAndBoundUnicodeCommentsWithoutBreakingSurrogates() throws Exception {
    event();
    when(rows.getLong("EVENT_COUNT")).thenReturn(1L);
    when(rows.getLong("TOTAL")).thenReturn(0L);
    when(rows.getCharacterStream("COMMENT_TEXT")).thenReturn(new StringReader("A" + "😀".repeat(3000)));
    var result = reader.details(idir("TAPS_ADMIN"), "1001", "60001", 0).orElseThrow();
    assertThat(result.comment()).isEqualTo("A" + "😀".repeat(1999));
    assertThat(result.commentTruncated()).isTrue();
    assertThat(result.items()).isEmpty();
    assertThat(sql()).contains("WHERE AUDIT_EVENT_ID = ?",
        "E.ECAS_ID = D.ECAS_ID AND E.AUDIT_EVENT_ID = D.AUDIT_EVENT_ID",
        "ORDER BY ENTRY_USERID ASC, ENTRY_TIMESTAMP ASC, AUDIT_DETAIL_ID ASC");
    verify(statement).setLong(1, 1001);
    verify(statement).setLong(2, 60001);
    verify(statement).setLong(3, 1);
    verify(statement).setLong(4, 100);
  }

  @Test
  void fieldChangesUseSourceFileNameDisplayWithoutReturningDownloadLinks() throws Exception {
    event();
    when(rows.getLong("EVENT_COUNT")).thenReturn(1L);
    when(rows.getString("AUDIT_DETAIL_ID")).thenReturn("61001");
    when(rows.getString("DETAIL_ECAS_ID")).thenReturn("1001");
    when(rows.getString("DETAIL_EVENT_ID")).thenReturn("60001");
    when(rows.getString("COLUMN_NAME")).thenReturn("ECAS_SUBMITTED_FILE_ID");
    when(rows.getString("BUSINESS_IDENTIFIER")).thenReturn("ECAS:1001:synthetic.xml");
    when(rows.getCharacterStream("OLD_VALUE")).thenReturn(new StringReader("62001"));
    var result = reader.details(idir("TAPS_ADMIN"), "1001", "60001", 0).orElseThrow();
    var change = result.items().getFirst();
    assertThat(change.columnName()).isEqualTo("FILE_NAME");
    assertThat(change.previousValue()).isEqualTo("synthetic.xml");
    assertThat(change.changedValue()).isNull();
  }

  @Test
  void unrelatedEventOrDetailParentFailsBeforePublishingValues() throws Exception {
    event();
    when(rows.getLong("EVENT_COUNT")).thenReturn(1L);
    when(rows.getString("AUDIT_DETAIL_ID")).thenReturn("61001");
    when(rows.getString("DETAIL_ECAS_ID")).thenReturn("1002");
    assertThatThrownBy(() -> reader.details(idir("TAPS_ADMIN"), "1001", "60001", 0))
        .isInstanceOf(DataIntegrityViolationException.class);
    verify(rows, never()).getCharacterStream("OLD_VALUE");
  }

  @Test
  void duplicateParentOrEventIsRejected() throws Exception {
    event();
    when(rows.getLong("PARENT_COUNT")).thenReturn(2L);
    assertThatThrownBy(() -> reader.history(idir("TAPS_ADMIN"), "1001", 0))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void invalidIdsAndPagesNeverOpenAConnection() {
    assertThatThrownBy(() -> reader.history(idir("TAPS_ADMIN"), "bad", 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> reader.history(idir("TAPS_ADMIN"), "1001", -1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> reader.details(idir("TAPS_ADMIN"), "1001", "bad", 0)).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(dataSource);
  }

  private void event() throws SQLException {
    when(rows.next()).thenReturn(true, false);
    when(rows.getLong("PARENT_COUNT")).thenReturn(1L);
    when(rows.getLong("TOTAL")).thenReturn(1L);
    when(rows.getString("ECAS_ID")).thenReturn("1001");
    when(rows.getString("AUDIT_EVENT_ID")).thenReturn("60001");
    when(rows.getString("ENTRY_USERID")).thenReturn("IDIR\\SYNTHETIC");
    when(rows.getTimestamp("ENTRY_TIMESTAMP")).thenReturn(Timestamp.valueOf("2026-01-02 13:14:15"));
    when(rows.getString("ECAS_ACTION_CODE")).thenReturn("UPD");
    when(rows.getString("ACTION_DESCRIPTION")).thenReturn("Update");
    when(rows.getLong("COMMENT_LENGTH")).thenReturn(70L);
  }

  private String sql() throws SQLException {
    var capture = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(capture.capture());
    return capture.getValue();
  }

  private static TapsUser idir(String... roles) {
    return new TapsUser("synthetic", "Synthetic", null, IdentityProvider.IDIR, null,
        Arrays.stream(roles).map(FamRoleName::parse)
            .map(role -> RoleGrant.accept(role, IdentityProvider.IDIR).orElseThrow()).toList());
  }
}
