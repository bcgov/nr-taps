package ca.bc.gov.nrs.taps.read.oracle;

import ca.bc.gov.nrs.taps.domain.AppraisalMethod;
import ca.bc.gov.nrs.taps.domain.LegacyIdentifiers;
import ca.bc.gov.nrs.taps.read.CodeOption;
import ca.bc.gov.nrs.taps.read.GasAppraisal;
import ca.bc.gov.nrs.taps.security.TapsCapability;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Stored appraised worksheet reads using the provisional ADS ownership; no rate calculation. */
public final class OracleAppraisedSummary {
  // A single statement keeps authorization, parent and children in one Oracle read snapshot.
  // Separate tagged rows preserve every mark/rate without multiplying marks by rate count.
  private static final String DETAILS = """
      SELECT 0 AS ROW_KIND, P.*,
             CAST(NULL AS VARCHAR2(6)) AS TIMBER_MARK,
             CAST(NULL AS NUMBER(12)) AS RATE_ID,
             CAST(NULL AS DATE) AS RATE_EFFECTIVE_DATE,
             CAST(NULL AS NUMBER(6, 2)) AS RATE_AMOUNT
        FROM scoped_parent P
      UNION ALL
      SELECT 1, P.*, M.TIMBER_MARK, NULL, NULL, NULL
        FROM scoped_parent P
        JOIN ADS_SUBMITTED_TIMBER_MARK M ON M.ECAS_ID = P.ECAS_ID
      UNION ALL
      SELECT 2, P.*, NULL, R.APPRAISED_STUMPAGE_RATE_ID,
             R.STUMPAGE_RATE_EFFECTIVE_DATE, R.TOTAL_STUMPAGE_RATE_AMOUNT
        FROM scoped_parent P
        JOIN APPRAISED_STUMPAGE_RATE R ON R.APPRAISED_WORKSHEET_ID = P.WORKSHEET_ID
       ORDER BY ROW_KIND, TIMBER_MARK, RATE_EFFECTIVE_DATE, RATE_ID
      """;

  private final JdbcTemplate jdbc;

  public OracleAppraisedSummary(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
  }

  public Optional<GasAppraisal.AppraisedSummary> byTypedKey(TapsUser user, GasAppraisal.Key key) {
    Objects.requireNonNull(key, "key");
    if (key.type() != GasAppraisal.WorksheetType.APPRAISED) {
      throw new IllegalArgumentException("appraised summary requires an APPRAISED key");
    }
    return read(user, "WORKSHEET_ID", key.worksheetId());
  }

  public Optional<GasAppraisal.AppraisedSummary> byEcasId(TapsUser user, String ecasId) {
    return read(user, "ECAS_ID", LegacyIdentifiers.requiredId(ecasId));
  }

  private Optional<GasAppraisal.AppraisedSummary> read(TapsUser user, String idColumn, String id) {
    ReadScopePredicate scope =
        ReadScopePredicate.forCapability(user, TapsCapability.GAS_APPRAISAL_VIEW);
    String sql = "WITH record_scope AS (\n" + AppraisedScopeSql.SOURCE
        + "), authorized_parent AS (\nSELECT * FROM record_scope WHERE record_scope."
        + idColumn + " = ? AND " + scope.sql() + "\n), scoped_parent AS (\n"
        + "SELECT P.*, (SELECT M.TIMBER_MARK FROM ADS_SUBMITTED_TIMBER_MARK M "
        + "WHERE M.ECAS_ID = P.ECAS_ID AND M.PRIMARY_MARK_IND = 'Y') AS PRIMARY_TIMBER_MARK "
        + "FROM authorized_parent P\n)\n" + DETAILS;
    return jdbc.query(
        sql,
        statement -> {
          statement.setLong(1, Long.parseLong(id));
          for (int index = 0; index < scope.parameters().size(); index++) {
            statement.setString(index + 2, scope.parameters().get(index));
          }
        },
        OracleAppraisedSummary::extract);
  }

  private static Optional<GasAppraisal.AppraisedSummary> extract(ResultSet rows) throws SQLException {
    Parent parent = null;
    List<String> timberMarks = new ArrayList<>();
    List<GasAppraisal.StoredRate> rates = new ArrayList<>();
    while (rows.next()) {
      switch (rows.getInt("ROW_KIND")) {
        case 0 -> {
          if (parent != null) {
            throw new IncorrectResultSizeDataAccessException(1, 2);
          }
          parent = Parent.read(rows);
        }
        case 1 -> timberMarks.add(rows.getString("TIMBER_MARK"));
        case 2 -> rates.add(new GasAppraisal.StoredRate(
            rows.getString("RATE_ID"),
            localDate(rows, "RATE_EFFECTIVE_DATE"),
            rows.getBigDecimal("RATE_AMOUNT")));
        default -> throw new DataIntegrityViolationException("unknown summary row kind");
      }
    }
    if (parent == null) {
      if (!timberMarks.isEmpty() || !rates.isEmpty()) {
        throw new DataIntegrityViolationException("summary children have no scoped parent");
      }
      return Optional.empty();
    }
    return Optional.of(parent.summary(timberMarks, rates));
  }

  private record Parent(
      GasAppraisal.Key key,
      String ecasId,
      AppraisalMethod appraisalMethod,
      String rateCalculationMethod,
      Boolean toaEligible,
      CodeOption status,
      LocalDate effectiveDate,
      LocalDate expiryDate,
      String primaryTimberMark,
      String referenceType,
      LocalDate ceaseAdjustmentDate) {
    static Parent read(ResultSet row) throws SQLException {
      String toa = row.getString("TOA_ELIGIBLE_IND");
      Boolean toaEligible = switch (toa == null ? "" : toa) {
        case "Y" -> true;
        case "N" -> false;
        case "" -> null;
        default -> throw new DataIntegrityViolationException("unsupported TOA eligibility value");
      };
      return new Parent(
          new GasAppraisal.Key(GasAppraisal.WorksheetType.APPRAISED, row.getString("WORKSHEET_ID")),
          row.getString("ECAS_ID"),
          AppraisalMethod.valueOf(row.getString("APPRAISAL_METHOD_CODE")),
          row.getString("RATE_CALC_METHOD_CODE"),
          toaEligible,
          new CodeOption(row.getString("STATUS_CODE"), row.getString("STATUS_DESCRIPTION")),
          localDate(row, "EFFECTIVE_DATE"),
          localDate(row, "EXPIRY_DATE"),
          row.getString("PRIMARY_TIMBER_MARK"),
          row.getString("REFERENCE_TYPE"),
          localDate(row, "CEASE_ADJUSTMENT_DATE"));
    }

    GasAppraisal.AppraisedSummary summary(List<String> marks, List<GasAppraisal.StoredRate> rates) {
      GasAppraisal.SummaryVariant variant =
          GasAppraisal.SummaryVariant.resolve(rateCalculationMethod, appraisalMethod, toaEligible)
              .orElseThrow(() -> new DataIntegrityViolationException("unsupported summary variant"));
      return new GasAppraisal.AppraisedSummary(
          key, ecasId, appraisalMethod, variant, rateCalculationMethod, toaEligible, status,
          effectiveDate, expiryDate, marks, primaryTimberMark, referenceType, ceaseAdjustmentDate, rates);
    }
  }

  private static LocalDate localDate(ResultSet row, String column) throws SQLException {
    Date date = row.getDate(column);
    return date == null ? null : date.toLocalDate();
  }
}
