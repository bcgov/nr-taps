package ca.bc.gov.nrs.taps.read.oracle;

import ca.bc.gov.nrs.taps.domain.LegacyIdentifiers;
import ca.bc.gov.nrs.taps.read.CodeOption;
import ca.bc.gov.nrs.taps.read.EcasAudit;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.io.IOException;
import java.io.Reader;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Audit reads use the ECAS view grants and the same submission scope and status rules. */
public final class OracleEcasAudit {
  private static final String PARENT = """
      WITH record_scope AS (
        SELECT ADS.ECAS_ID, ADS.CLIENT_NUMBER, ADS.APPRAISAL_STATUS_CODE AS STATUS_CODE,
               DISTRICT.ORG_UNIT_CODE AS ADMIN_DISTRICT_CODE,
               REGION.ORG_UNIT_CODE AS ROLLUP_REGION_CODE
          FROM APPRAISAL_DATA_SUBMISSION ADS
          LEFT JOIN ORG_UNIT DISTRICT ON DISTRICT.ORG_UNIT_NO = ADS.ADMIN_DISTRICT
          LEFT JOIN ORG_UNIT REGION ON REGION.ORG_UNIT_NO = DISTRICT.ROLLUP_REGION_NO
      ), scoped_parent AS (
        SELECT ECAS_ID FROM record_scope WHERE ECAS_ID = ? AND %s
      ), scoped_events AS (
        SELECT E.AUDIT_EVENT_ID, E.ECAS_ID, E.ENTRY_USERID, E.ENTRY_TIMESTAMP,
               E.ECAS_ACTION_CODE, A.DESCRIPTION AS ACTION_DESCRIPTION, E.SENT_TO_USERID,
               (SELECT F.ECAS_SUBMITTED_FILE_ID FROM ECAS_SUBMITTED_FILE F
                 WHERE F.ECAS_SUBMITTED_FILE_ID = E.ECAS_SUBMITTED_FILE_ID AND F.ECAS_ID = E.ECAS_ID) AS FILE_ID,
               (SELECT F.FILE_NAME FROM ECAS_SUBMITTED_FILE F
                 WHERE F.ECAS_SUBMITTED_FILE_ID = E.ECAS_SUBMITTED_FILE_ID AND F.ECAS_ID = E.ECAS_ID) AS FILE_NAME,
               CASE WHEN E.ECAS_ACTION_CODE = 'IMP' OR A.DESCRIPTION = 'Import' THEN 1 ELSE 0 END AS COMMENTS_SUPPRESSED,
               (SELECT C.AUDIT_COMMENT FROM ECAS_AUDIT_COMMENT C
                 WHERE C.ECAS_ID = E.ECAS_ID AND C.AUDIT_EVENT_ID = E.AUDIT_EVENT_ID
                   AND E.ECAS_ACTION_CODE <> 'IMP'
                   AND (A.DESCRIPTION IS NULL OR A.DESCRIPTION <> 'Import')) AS COMMENT_TEXT
          FROM ECAS_AUDIT_EVENT E
          JOIN ECAS_ACTION_CODE A ON A.ECAS_ACTION_CODE = E.ECAS_ACTION_CODE
         WHERE EXISTS (SELECT 1 FROM scoped_parent P WHERE P.ECAS_ID = E.ECAS_ID)
      )
      """;

  private static final String HISTORY = """
      , numbered_events AS (
        SELECT E.*, ROW_NUMBER() OVER (ORDER BY ENTRY_TIMESTAMP ASC, AUDIT_EVENT_ID ASC) AS RESULT_ROW
          FROM scoped_events E
      )
      SELECT (SELECT COUNT(*) FROM scoped_parent) AS PARENT_COUNT,
             totals.TOTAL, E.AUDIT_EVENT_ID, E.ECAS_ID, E.ENTRY_USERID, E.ENTRY_TIMESTAMP,
             E.ECAS_ACTION_CODE, E.ACTION_DESCRIPTION, E.SENT_TO_USERID, E.FILE_ID, E.FILE_NAME,
             E.COMMENTS_SUPPRESSED, SUBSTR(E.COMMENT_TEXT, 1, 60) AS COMMENT_PREVIEW,
             NVL(DBMS_LOB.GETLENGTH(E.COMMENT_TEXT), 0) AS COMMENT_LENGTH
        FROM (SELECT COUNT(*) AS TOTAL FROM scoped_events) totals
        LEFT JOIN numbered_events E ON E.RESULT_ROW BETWEEN ? AND ?
       ORDER BY E.RESULT_ROW
      """;

  private static final String DETAILS = """
      , selected_event AS (
        SELECT * FROM scoped_events WHERE AUDIT_EVENT_ID = ?
      ), scoped_changes AS (
        SELECT D.* FROM ECAS_AUDIT_DETAIL D
         WHERE EXISTS (SELECT 1 FROM selected_event E
                        WHERE E.ECAS_ID = D.ECAS_ID AND E.AUDIT_EVENT_ID = D.AUDIT_EVENT_ID)
      ), numbered_changes AS (
        SELECT D.*, ROW_NUMBER() OVER (ORDER BY ENTRY_USERID ASC, ENTRY_TIMESTAMP ASC, AUDIT_DETAIL_ID ASC) AS RESULT_ROW
          FROM scoped_changes D
      )
      SELECT (SELECT COUNT(*) FROM scoped_parent) AS PARENT_COUNT,
             (SELECT COUNT(*) FROM selected_event) AS EVENT_COUNT, totals.TOTAL,
             E.*, SUBSTR(E.COMMENT_TEXT, 1, 60) AS COMMENT_PREVIEW,
             NVL(DBMS_LOB.GETLENGTH(E.COMMENT_TEXT), 0) AS COMMENT_LENGTH,
             D.AUDIT_DETAIL_ID, D.ENTRY_USERID AS DETAIL_USERID, D.ENTRY_TIMESTAMP AS DETAIL_TIMESTAMP,
             D.ECAS_ID AS DETAIL_ECAS_ID, D.AUDIT_EVENT_ID AS DETAIL_EVENT_ID,
             D.BUSINESS_IDENTIFIER, D.TABLE_NAME, D.COLUMN_NAME, D.OLD_VALUE, D.NEW_VALUE
        FROM selected_event E
        CROSS JOIN (SELECT COUNT(*) AS TOTAL FROM scoped_changes) totals
        LEFT JOIN numbered_changes D ON D.RESULT_ROW BETWEEN ? AND ?
       ORDER BY D.RESULT_ROW
      """;

  private final JdbcTemplate jdbc;

  public OracleEcasAudit(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
  }

  public Optional<EcasAudit.HistoryPage> history(TapsUser user, String ecasId, int page) {
    String id = LegacyIdentifiers.requiredId(ecasId);
    checkPage(page);
    var scope = EcasReadPredicate.forUser(user);
    return jdbc.query(PARENT.formatted(scope.sql()) + HISTORY,
        statement -> bind(statement, scope, id, null, page), rows -> {
          var events = new ArrayList<EcasAudit.Event>();
          long total = 0;
          boolean parent = false;
          while (rows.next()) {
            parent = checkedParent(rows);
            total = rows.getLong("TOTAL");
            if (rows.getString("AUDIT_EVENT_ID") != null) {
              checkId(id, rows.getString("ECAS_ID"));
              events.add(event(rows));
            }
          }
          if (!parent) {
            if (!events.isEmpty() || total != 0) throw new DataIntegrityViolationException("audit rows have no scoped parent");
            return Optional.empty();
          }
          return Optional.of(new EcasAudit.HistoryPage(id, events, total, page));
        });
  }

  public Optional<EcasAudit.DetailPage> details(TapsUser user, String ecasId, String eventId, int page) {
    String id = LegacyIdentifiers.requiredId(ecasId);
    String eventKey = LegacyIdentifiers.requiredId(eventId);
    checkPage(page);
    var scope = EcasReadPredicate.forUser(user);
    return jdbc.query(PARENT.formatted(scope.sql()) + DETAILS,
        statement -> bind(statement, scope, id, eventKey, page), rows -> {
          EcasAudit.Event event = null;
          Text comment = null;
          var changes = new ArrayList<EcasAudit.FieldChange>();
          long total = 0;
          while (rows.next()) {
            if (!checkedParent(rows) || rows.getLong("EVENT_COUNT") != 1) {
              throw new DataIntegrityViolationException("audit event does not have exactly one scoped parent");
            }
            checkId(id, rows.getString("ECAS_ID"));
            checkId(eventKey, rows.getString("AUDIT_EVENT_ID"));
            if (event == null) {
              event = event(rows);
              comment = text(rows, "COMMENT_TEXT", EcasAudit.COMMENT_LIMIT);
            }
            total = rows.getLong("TOTAL");
            if (rows.getString("AUDIT_DETAIL_ID") != null) {
              checkId(id, rows.getString("DETAIL_ECAS_ID"));
              checkId(eventKey, rows.getString("DETAIL_EVENT_ID"));
              changes.add(change(rows));
            }
          }
          return event == null ? Optional.empty() : Optional.of(new EcasAudit.DetailPage(
              id, event, comment.value(), comment.truncated(), changes, total, page));
        });
  }

  private static EcasAudit.Event event(ResultSet row) throws SQLException {
    return new EcasAudit.Event(row.getString("AUDIT_EVENT_ID"), row.getString("ENTRY_USERID"),
        timestamp(row, "ENTRY_TIMESTAMP"), new CodeOption(row.getString("ECAS_ACTION_CODE"), row.getString("ACTION_DESCRIPTION")),
        row.getString("SENT_TO_USERID"), row.getString("FILE_ID"), fileName(row.getString("FILE_NAME")),
        row.getString("COMMENT_PREVIEW"), row.getLong("COMMENT_LENGTH") > 60, row.getBoolean("COMMENTS_SUPPRESSED"));
  }

  private static EcasAudit.FieldChange change(ResultSet row) throws SQLException {
    String businessId = row.getString("BUSINESS_IDENTIFIER");
    String column = row.getString("COLUMN_NAME");
    Text previous = text(row, "OLD_VALUE", EcasAudit.VALUE_LIMIT);
    Text changed = text(row, "NEW_VALUE", EcasAudit.VALUE_LIMIT);
    // ECAS04 displays stored file-name metadata, not numeric IDs or a link to deleted bytes.
    if ("ECAS_SUBMITTED_FILE_ID".equalsIgnoreCase(column)) {
      String name = fileName(businessId == null ? null : businessId.substring(businessId.lastIndexOf(':') + 1));
      previous = new Text(changed.value() == null ? name : null, false);
      changed = new Text(changed.value() == null ? null : name, false);
      businessId = name;
      column = "FILE_NAME";
    } else if ("FILE_NAME".equalsIgnoreCase(column) || "XML_FILE_NAME".equalsIgnoreCase(column)) {
      previous = new Text(fileName(previous.value()), previous.truncated());
      changed = new Text(fileName(changed.value()), changed.truncated());
      businessId = fileName(businessId == null ? null : businessId.substring(businessId.lastIndexOf(':') + 1));
    }
    return new EcasAudit.FieldChange(row.getString("AUDIT_DETAIL_ID"), row.getString("DETAIL_USERID"),
        timestamp(row, "DETAIL_TIMESTAMP"), businessId, row.getString("TABLE_NAME"), column,
        previous.value(), changed.value(), previous.truncated(), changed.truncated());
  }

  private static String fileName(String value) {
    if (value == null) return null;
    return value.substring(Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\')) + 1);
  }

  private static Text text(ResultSet row, String column, int limit) throws SQLException {
    // Reading the CLOB in Java avoids Oracle's byte-limited VARCHAR2 conversion for Unicode text.
    try (Reader reader = row.getCharacterStream(column)) {
      if (reader == null) return new Text(null, false);
      char[] buffer = new char[limit + 1];
      int size = 0;
      while (size < buffer.length) {
        int read = reader.read(buffer, size, buffer.length - size);
        if (read < 0) break;
        size += read;
      }
      int length = Math.min(size, limit);
      if (length > 0 && size > limit && Character.isHighSurrogate(buffer[length - 1])) length--;
      return new Text(new String(buffer, 0, length), size > limit);
    } catch (IOException exception) {
      throw new SQLException("Unable to read bounded audit text", exception);
    }
  }

  private static boolean checkedParent(ResultSet row) throws SQLException {
    long count = row.getLong("PARENT_COUNT");
    if (count > 1) throw new DataIntegrityViolationException("ambiguous audit parent");
    return count == 1;
  }

  private static void checkId(String expected, String actual) {
    if (!expected.equals(actual)) throw new DataIntegrityViolationException("audit child does not match its parent");
  }

  private static LocalDateTime timestamp(ResultSet row, String column) throws SQLException {
    Timestamp value = row.getTimestamp(column);
    return value == null ? null : value.toLocalDateTime();
  }

  private static void checkPage(int page) {
    if (page < 0) throw new IllegalArgumentException("page must be non-negative");
  }

  private static void bind(PreparedStatement statement, EcasReadPredicate scope,
      String id, String eventId, int page) throws SQLException {
    int parameter = 1;
    statement.setLong(parameter++, Long.parseLong(id));
    for (String value : scope.parameters()) statement.setString(parameter++, value);
    if (eventId != null) statement.setLong(parameter++, Long.parseLong(eventId));
    long first = (long) page * EcasAudit.PAGE_SIZE + 1;
    statement.setLong(parameter++, first);
    statement.setLong(parameter, first + EcasAudit.PAGE_SIZE - 1);
  }

  private record Text(String value, boolean truncated) {}
}
