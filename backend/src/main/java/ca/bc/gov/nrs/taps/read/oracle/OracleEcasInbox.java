package ca.bc.gov.nrs.taps.read.oracle;

import ca.bc.gov.nrs.taps.domain.AppraisalMethod;
import ca.bc.gov.nrs.taps.read.CodeOption;
import ca.bc.gov.nrs.taps.read.EcasInbox;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

/** ECAS05 all-submissions and direct-ID search, without the legacy temp-table procedure. */
public final class OracleEcasInbox {
  private final JdbcTemplate jdbc;

  public OracleEcasInbox(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
  }

  public EcasInbox.Page search(TapsUser user, EcasInbox.Search search, int page) {
    var plan = EcasInboxPlan.forUser(user, search, page);
    // The legacy DISTINCT projection has day precision and retains each mark/permit combination.
    // Keep raw dates until after filtering, then truncate before DISTINCT and sorting.
    String sql = """
        WITH scoped_rows AS (
          SELECT DISTINCT record_scope.ECAS_ID,
                 record_scope.APPRAISAL_METHOD_CODE,
                 record_scope.TIMBER_MARK,
                 record_scope.LICENCE,
                 record_scope.CUTTING_PERMIT,
                 record_scope.STATUS_CODE,
                 TRUNC(record_scope.STATUS_DATE) AS STATUS_DATE,
                 record_scope.STATUS_DESCRIPTION,
                 record_scope.APPRAISAL_TYPE,
                 TRUNC(record_scope.EFFECTIVE_DATE) AS EFFECTIVE_DATE,
                 TRUNC(record_scope.EXPIRY_DATE) AS EXPIRY_DATE,
                 TRUNC(record_scope.LICENSEE_SUBMITTED_DATE) AS LICENSEE_SUBMITTED_DATE,
                 TRUNC(record_scope.DISTRICT_RECEIVED_DATE) AS DISTRICT_RECEIVED_DATE,
                 TRUNC(record_scope.SENT_TO_REGION_DATE) AS SENT_TO_REGION_DATE,
                 TRUNC(record_scope.UPDATE_DATE) AS UPDATE_DATE,
                 record_scope.MULTI_TIMBER_MARKS_IND,
                 record_scope.CLIENT_NUMBER,
                 record_scope.CLIENT_LOCN_CODE,
                 record_scope.CLIENT_NAME,
                 record_scope.REVISION_COUNT
            FROM (
              SELECT ADS.ECAS_ID,
                     ADSC.APPRAISAL_METHOD_CODE,
                     ASTM.TIMBER_MARK,
                     ASTM.PRIMARY_MARK_IND,
                     PFU.FOREST_FILE_ID AS LICENCE,
                     HVA.CUTTING_PERMIT_ID AS CUTTING_PERMIT,
                     ADS.APPRAISAL_STATUS_CODE AS STATUS_CODE,
                     ADS.STATUS_CHANGE_DATE AS STATUS_DATE,
                     ASTC.DESCRIPTION AS STATUS_DESCRIPTION,
                     ACC.DESCRIPTION AS APPRAISAL_TYPE,
                     ADS.APPRAISAL_EFFECTIVE_DATE AS EFFECTIVE_DATE,
                     ADS.APPRAISAL_EXPIRY_DATE AS EXPIRY_DATE,
                     ADS.RPF_SUBMITTED_DATE AS LICENSEE_SUBMITTED_DATE,
                     ADS.DISTRICT_RECEIVED_DATE,
                     ADS.SENT_DATE AS SENT_TO_REGION_DATE,
                     ADS.UPDATE_TIMESTAMP AS UPDATE_DATE,
                     ADS.ENTRY_TIMESTAMP,
                     CASE WHEN (SELECT COUNT(*) FROM ADS_SUBMITTED_TIMBER_MARK marks
                                 WHERE marks.ECAS_ID = ADS.ECAS_ID) > 1 THEN 'Y' ELSE 'N' END
                       AS MULTI_TIMBER_MARKS_IND,
                     ADS.CLIENT_NUMBER,
                     ADS.CLIENT_LOCN_CODE,
                     SUBSTR(SIL_GET_CLIENT_NAME(ADS.CLIENT_NUMBER), 1, 30) AS CLIENT_NAME,
                     ADS.REVISION_COUNT,
                     ADS.ADMIN_DISTRICT,
                     DISTRICT.ORG_UNIT_CODE AS ADMIN_DISTRICT_CODE,
                     DISTRICT.ROLLUP_REGION_NO,
                     REGION.ORG_UNIT_CODE AS ROLLUP_REGION_CODE,
                     ADS.CERTIFIED_FLAG,
                     ADS.APPRAISAL_CATEGORY_CODE,
                     ADS.REAPPRAISAL_REASON_CODE,
                     PFU.SB_FUNDED_IND,
                     PFU.FILE_TYPE_CODE,
                     PFU.MGMT_UNIT_TYPE,
                     PFU.MGMT_UNIT_ID,
                     NVL(HVA.EXTEND_DATE, HVA.EXPIRY_DATE) AS FTA_CP_EXPIRY_DATE,
                     ADS.RPF_USER_ID,
                     ADS.SENT_BY_USER_ID,
                     ADS.TRANSFER_BY_USER_ID,
                     ADS.ENTRY_USERID
                FROM APPRAISAL_DATA_SUBMISSION ADS
                JOIN APPRAISAL_DATA_SUBMISSION_CTRL ADSC ON ADSC.ECAS_ID = ADS.ECAS_ID
                JOIN ADS_SUBMITTED_TIMBER_MARK ASTM ON ASTM.ECAS_ID = ADS.ECAS_ID
                LEFT JOIN HAULING_AUTHORITY HLA ON HLA.TIMBER_MARK = ASTM.TIMBER_MARK
                LEFT JOIN HARVESTING_HAULING_XREF HHX ON HHX.TIMBER_MARK = HLA.TIMBER_MARK
                LEFT JOIN HARVESTING_AUTHORITY HVA ON HVA.HVA_SKEY = HHX.HVA_SKEY
                LEFT JOIN PROV_FOREST_USE PFU ON PFU.FOREST_FILE_ID = ADS.FOREST_FILE_ID
                JOIN APPRAISAL_CATEGORY_CODE ACC
                  ON ACC.APPRAISAL_CATEGORY_CODE = ADS.APPRAISAL_CATEGORY_CODE
                JOIN APPRAISAL_STATUS_CODE ASTC
                  ON ASTC.APPRAISAL_STATUS_CODE = ADS.APPRAISAL_STATUS_CODE
                LEFT JOIN ORG_UNIT DISTRICT ON DISTRICT.ORG_UNIT_NO = ADS.ADMIN_DISTRICT
                LEFT JOIN ORG_UNIT REGION ON REGION.ORG_UNIT_NO = DISTRICT.ROLLUP_REGION_NO
                 ) record_scope
           WHERE %s
        ), numbered_rows AS (
          SELECT scoped_rows.*,
                 ROW_NUMBER() OVER (ORDER BY %s,
                   ECAS_ID DESC, TIMBER_MARK ASC NULLS LAST, LICENCE ASC NULLS LAST,
                   CUTTING_PERMIT ASC NULLS LAST, APPRAISAL_METHOD_CODE ASC NULLS LAST,
                   STATUS_CODE ASC NULLS LAST, STATUS_DATE ASC NULLS LAST,
                   STATUS_DESCRIPTION ASC NULLS LAST, APPRAISAL_TYPE ASC NULLS LAST,
                   EFFECTIVE_DATE ASC NULLS LAST, EXPIRY_DATE ASC NULLS LAST,
                   LICENSEE_SUBMITTED_DATE ASC NULLS LAST, DISTRICT_RECEIVED_DATE ASC NULLS LAST,
                   SENT_TO_REGION_DATE ASC NULLS LAST, UPDATE_DATE ASC NULLS LAST,
                   MULTI_TIMBER_MARKS_IND ASC NULLS LAST, CLIENT_NUMBER ASC NULLS LAST,
                   CLIENT_LOCN_CODE ASC NULLS LAST, CLIENT_NAME ASC NULLS LAST,
                   REVISION_COUNT ASC NULLS LAST) AS RESULT_ROW
            FROM scoped_rows
        )
        SELECT totals.TOTAL, page_rows.*
          FROM (SELECT COUNT(*) AS TOTAL FROM scoped_rows) totals
          LEFT JOIN numbered_rows page_rows ON page_rows.RESULT_ROW BETWEEN ? AND ?
         ORDER BY page_rows.RESULT_ROW
        """.formatted(plan.sql(), plan.orderBy());
    return jdbc.query(
        connection -> connection.prepareStatement(sql),
        statement -> {
          int parameter = 1;
          for (String value : plan.parameters()) {
            statement.setString(parameter++, value);
          }
          statement.setLong(parameter++, plan.firstRow());
          statement.setLong(parameter, plan.lastRow());
        },
        rows -> {
          var items = new ArrayList<EcasInbox.Item>();
          long total = 0;
          while (rows.next()) {
            total = rows.getLong("TOTAL");
            String ecasId = rows.getString("ECAS_ID");
            if (ecasId != null) {
              items.add(new EcasInbox.Item(
                  ecasId, method(rows.getString("APPRAISAL_METHOD_CODE")),
                  rows.getString("TIMBER_MARK"), rows.getString("LICENCE"),
                  rows.getString("CUTTING_PERMIT"),
                  new CodeOption(rows.getString("STATUS_CODE"), rows.getString("STATUS_DESCRIPTION")),
                  localDate(rows, "STATUS_DATE"), rows.getString("APPRAISAL_TYPE"),
                  localDate(rows, "EFFECTIVE_DATE"), localDate(rows, "EXPIRY_DATE"),
                  localDate(rows, "LICENSEE_SUBMITTED_DATE"), localDate(rows, "DISTRICT_RECEIVED_DATE"),
                  localDate(rows, "SENT_TO_REGION_DATE"), indicator(rows.getString("MULTI_TIMBER_MARKS_IND")),
                  rows.getString("CLIENT_NUMBER"), rows.getString("CLIENT_LOCN_CODE"),
                  rows.getString("CLIENT_NAME"), rows.getObject("REVISION_COUNT", Integer.class)));
            }
          }
          return new EcasInbox.Page(items, total, page);
        });
  }

  private static AppraisalMethod method(String value) {
    return value == null ? null : AppraisalMethod.valueOf(value);
  }

  private static Boolean indicator(String value) {
    if (value == null) {
      return null;
    }
    return switch (value) {
      case "Y" -> true;
      case "N" -> false;
      default -> throw new IllegalArgumentException("unknown multiple timber marks indicator");
    };
  }

  private static LocalDate localDate(ResultSet rows, String column) throws SQLException {
    Timestamp value = rows.getTimestamp(column);
    return value == null ? null : value.toLocalDateTime().toLocalDate();
  }
}
