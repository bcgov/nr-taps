package ca.bc.gov.nrs.taps.read.oracle;

import ca.bc.gov.nrs.taps.read.EffectiveCode;
import ca.bc.gov.nrs.taps.read.EcasStatusCode;
import ca.bc.gov.nrs.taps.read.DatedCodeOption;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

/** SELECT versions of three GAS2_COMMON code-list procedures. */
public final class OracleCodeLists {
  private static final String APPRAISAL_METHODS = """
      SELECT C.APPRAISAL_METHOD_CODE AS CODE,
             C.DESCRIPTION,
             C.EFFECTIVE_DATE,
             C.EXPIRY_DATE,
             C.UPDATE_TIMESTAMP
        FROM APPRAISAL_METHOD_CODE C
       WHERE SYSDATE BETWEEN C.EFFECTIVE_DATE AND C.EXPIRY_DATE
       ORDER BY CODE
      """;

  private static final String APPRAISAL_STATUSES = """
      SELECT C.APPRAISAL_STATUS_CODE AS CODE,
             C.DESCRIPTION,
             C.EFFECTIVE_DATE,
             C.EXPIRY_DATE,
             C.UPDATE_TIMESTAMP
        FROM APPRAISAL_STATUS_CODE C
       WHERE SYSDATE BETWEEN C.EFFECTIVE_DATE AND C.EXPIRY_DATE
       ORDER BY CODE
      """;

  private static final String RATE_ADJUSTMENT_TYPES = """
      SELECT C.RATE_ADJUSTMENT_TYPE_CODE AS CODE,
             C.DESCRIPTION,
             C.EFFECTIVE_DATE,
             C.EXPIRY_DATE,
             C.UPDATE_TIMESTAMP
        FROM RATE_ADJUSTMENT_TYPE_CODE C
       WHERE SYSDATE BETWEEN C.EFFECTIVE_DATE AND C.EXPIRY_DATE
       ORDER BY CODE
      """;

  private final JdbcTemplate jdbc;

  public OracleCodeLists(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
  }

  public List<EffectiveCode> appraisalMethods() {
    return read(APPRAISAL_METHODS);
  }

  public List<EffectiveCode> appraisalStatuses() {
    return read(APPRAISAL_STATUSES);
  }

  /** ECAS GET_APPRAISAL_STATUS has no effective-date filter, unlike GAS FIND_ALL_APP_STATUS_CODES. */
  public List<EcasStatusCode> ecasAppraisalStatuses() {
    return jdbc.query("""
        SELECT APPRAISAL_STATUS_CODE AS CODE, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE, UPDATE_TIMESTAMP,
               CASE WHEN SYSDATE > TRUNC(EFFECTIVE_DATE) AND SYSDATE < TRUNC(EXPIRY_DATE)
                    THEN 1 ELSE 0 END AS ACTIVE
          FROM APPRAISAL_STATUS_CODE
         ORDER BY APPRAISAL_STATUS_CODE
        """, (row, rowNumber) -> new EcasStatusCode(row.getString("CODE"), row.getString("DESCRIPTION"),
            localDateTime(row, "EFFECTIVE_DATE"), localDateTime(row, "EXPIRY_DATE"),
            localDateTime(row, "UPDATE_TIMESTAMP"), row.getBoolean("ACTIVE")));
  }

  public List<EffectiveCode> rateAdjustmentTypes() {
    return read(RATE_ADJUSTMENT_TYPES);
  }

  public List<DatedCodeOption> ecasAppraisalCategories() {
    return ecasCodes("APPRAISAL_CATEGORY_CODE");
  }

  public List<DatedCodeOption> ecasReappraisalReasons() {
    return ecasCodes("REAPPRAISAL_REASON_CODE");
  }

  public List<DatedCodeOption> ecasFileTypes() {
    return ecasCodes("FILE_TYPE_CODE");
  }

  // Table and column names are constants, not request input.
  private List<DatedCodeOption> ecasCodes(String table) {
    return jdbc.query("""
        SELECT %s AS CODE, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE,
               CASE WHEN SYSDATE > TRUNC(EFFECTIVE_DATE) AND SYSDATE < TRUNC(EXPIRY_DATE)
                    THEN 1 ELSE 0 END AS ACTIVE
          FROM %s ORDER BY %s
        """.formatted(table, table, table), (row, rowNumber) -> new DatedCodeOption(
            row.getString("CODE"), row.getString("DESCRIPTION"), localDateTime(row, "EFFECTIVE_DATE"),
            localDateTime(row, "EXPIRY_DATE"), row.getBoolean("ACTIVE")));
  }

  private List<EffectiveCode> read(String sql) {
    return jdbc.query(
        connection -> connection.prepareStatement(sql),
        (row, rowNumber) -> new EffectiveCode(
            row.getString("CODE"),
            row.getString("DESCRIPTION"),
            localDateTime(row, "EFFECTIVE_DATE"),
            localDateTime(row, "EXPIRY_DATE"),
            localDateTime(row, "UPDATE_TIMESTAMP")));
  }

  private static LocalDateTime localDateTime(ResultSet row, String column) throws SQLException {
    Timestamp value = row.getTimestamp(column);
    return value == null ? null : value.toLocalDateTime();
  }
}
