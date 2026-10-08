package ca.bc.gov.nrs.taps.read.oracle;

import ca.bc.gov.nrs.taps.read.CodeOption;
import ca.bc.gov.nrs.taps.read.GasAppraisal;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

/** GAS search across all worksheet families, using provisional ownership. */
public final class OracleGasSearch {
  private final JdbcTemplate jdbc;

  public OracleGasSearch(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
  }

  public GasAppraisal.Page search(TapsUser user, GasAppraisal.Search search) {
    GasSearchPlan plan = GasSearchPlan.forUser(user, search);
    String appraisedSource = "SELECT P.*, M.TIMBER_MARK FROM (\n" + AppraisedScopeSql.SOURCE
        + ") P JOIN ADS_SUBMITTED_TIMBER_MARK M ON M.ECAS_ID = P.ECAS_ID";
    // UNION compares the same ten fields as the legacy search, including hidden method/client. Scope is
    // applied in each family first; status descriptions are joined after the union so counts match.
    String sql = """
        WITH scoped_rows AS (
        %s
        UNION
        %s
        UNION
        %s
        ), numbered_rows AS (
          SELECT scoped_rows.*,
                 ROW_NUMBER() OVER (
                   ORDER BY STATUS_CODE ASC, EFFECTIVE_DATE DESC,
                            APPRAISAL_TYPE_CODE ASC, WORKSHEET_ID DESC,
                            TIMBER_MARK ASC NULLS LAST, LICENSE ASC NULLS LAST,
                            APPRAISAL_METHOD_CODE ASC NULLS LAST, CLIENT_NUMBER ASC NULLS LAST,
                            EXPIRY_DATE ASC NULLS LAST, REFERENCE_TYPE ASC NULLS LAST
                 ) AS RESULT_ROW
            FROM scoped_rows
        )
        SELECT totals.TOTAL, page_rows.*,
               CASE WHEN page_rows.APPRAISAL_TYPE_CODE = 1 THEN
                 (SELECT C.DESCRIPTION FROM NON_APPRAISED_STATUS_CODE C
                   WHERE C.NON_APPRAISED_STATUS_CODE = page_rows.STATUS_CODE
                     AND SYSDATE BETWEEN C.EFFECTIVE_DATE AND C.EXPIRY_DATE)
               ELSE
                 (SELECT C.DESCRIPTION FROM APPRAISAL_STATUS_CODE C
                   WHERE C.APPRAISAL_STATUS_CODE = page_rows.STATUS_CODE
                     AND SYSDATE BETWEEN C.EFFECTIVE_DATE AND C.EXPIRY_DATE)
               END AS STATUS_DESCRIPTION
          FROM (SELECT COUNT(*) AS TOTAL FROM scoped_rows) totals
          LEFT JOIN numbered_rows page_rows ON page_rows.RESULT_ROW BETWEEN ? AND ?
         ORDER BY page_rows.RESULT_ROW
        """.formatted(
            family(appraisedSource, 0, plan.sql()),
            family(OtherWorksheetScopeSql.NON_APPRAISED, 1, plan.sql()),
            family(OtherWorksheetScopeSql.HISTORIC, 2, plan.sql() + " AND record_scope.ACTIVE_IND = 'Y'"));
    return jdbc.query(
        sql,
        statement -> {
          int parameter = 1;
          for (int family = 0; family < 3; family++) {
            for (String value : plan.parameters()) {
              statement.setString(parameter++, value);
            }
          }
          statement.setLong(parameter++, plan.firstRow());
          statement.setLong(parameter, plan.lastRow());
        },
        rows -> {
          var items = new ArrayList<GasAppraisal.Item>();
          long total = 0;
          while (rows.next()) {
            total = rows.getLong("TOTAL");
            String id = rows.getString("WORKSHEET_ID");
            if (id != null) {
              items.add(new GasAppraisal.Item(
                  new GasAppraisal.Key(
                      GasAppraisal.WorksheetType.fromLegacyCode(rows.getInt("APPRAISAL_TYPE_CODE")), id),
                  rows.getString("LICENSE"), rows.getString("TIMBER_MARK"),
                  localDate(rows, "EFFECTIVE_DATE"), localDate(rows, "EXPIRY_DATE"),
                  new CodeOption(rows.getString("STATUS_CODE"), rows.getString("STATUS_DESCRIPTION")),
                  rows.getString("REFERENCE_TYPE")));
            }
          }
          return new GasAppraisal.Page(items, total, search.page());
        });
  }

  private static String family(String source, int type, String predicate) {
    return """
        SELECT record_scope.WORKSHEET_ID, %d AS APPRAISAL_TYPE_CODE,
               record_scope.LICENSE, record_scope.TIMBER_MARK,
               record_scope.APPRAISAL_METHOD_CODE, record_scope.CLIENT_NUMBER,
               record_scope.EFFECTIVE_DATE, record_scope.EXPIRY_DATE,
               record_scope.STATUS_CODE, record_scope.REFERENCE_TYPE
          FROM (
        %s
               ) record_scope
         WHERE %s
        """.formatted(type, source, predicate);
  }

  private static LocalDate localDate(ResultSet row, String column) throws SQLException {
    Timestamp value = row.getTimestamp(column);
    return value == null ? null : value.toLocalDateTime().toLocalDate();
  }
}
