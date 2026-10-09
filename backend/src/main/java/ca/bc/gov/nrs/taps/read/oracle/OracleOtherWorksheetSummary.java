package ca.bc.gov.nrs.taps.read.oracle;

import ca.bc.gov.nrs.taps.domain.AppraisalMethod;
import ca.bc.gov.nrs.taps.read.CodeOption;
import ca.bc.gov.nrs.taps.read.GasAppraisal;
import ca.bc.gov.nrs.taps.security.TapsCapability;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

/** Stored worksheets and rates, with code labels and non-appraised display totals. */
public final class OracleOtherWorksheetSummary {
  private static final String PARENT = """
      SELECT 0 AS ROW_KIND, P.*,
             CAST(NULL AS NUMBER(12)) AS RATE_ID,
             CAST(NULL AS DATE) AS RATE_EFFECTIVE_DATE,
             CAST(NULL AS NUMBER(6, 2)) AS RATE_AMOUNT,
             CAST(NULL AS VARCHAR2(10)) AS SCALE_SPECIES_CODE,
             CAST(NULL AS VARCHAR2(2)) AS SCALE_PRODUCT_CODE,
             CAST(NULL AS VARCHAR2(1)) AS SCALE_GRADE_CODE,
             CAST(NULL AS NUMBER(5, 2)) AS RESERVE_STUMPAGE_RATE,
             CAST(NULL AS NUMBER(5, 2)) AS BONUS_BID_AMOUNT,
             CAST(NULL AS NUMBER(5, 2)) AS DEVELOPMENT_LEVY,
             CAST(NULL AS NUMBER(5, 2)) AS SILVICULTURE_LEVY,
             CAST(NULL AS NUMBER(12)) AS APPRAISED_PARENT_ID,
             CAST(NULL AS NUMBER(12)) AS HISTORIC_PARENT_ID,
             CAST(NULL AS NUMBER(12)) AS NON_APPRAISED_PARENT_ID,
             CAST(NULL AS VARCHAR2(10)) AS ADDON_CODE,
             CAST(NULL AS VARCHAR2(133)) AS ADDON_DESCRIPTION,
             CAST(NULL AS DATE) AS ADDON_EFFECTIVE_DATE,
             CAST(NULL AS DATE) AS ADDON_EXPIRY_DATE,
             CAST(NULL AS DATE) AS ADDON_UPDATE_TIMESTAMP,
             CAST(NULL AS NUMBER(12)) AS DETAIL_ID,
             CAST(NULL AS VARCHAR2(2)) AS SELLING_PRICE_ZONE,
             CAST(NULL AS NUMBER) AS SPECIES_VOLUME,
             CAST(NULL AS NUMBER) AS LUMBER_RECOVERY_FACTOR,
             CAST(NULL AS NUMBER) AS SPECIES_DECAY_PERCENT,
             CAST(NULL AS NUMBER) AS SPECIES_STUD_PERCENT,
             CAST(NULL AS NUMBER) AS SPECIES_BURN_PERCENT,
             CAST(NULL AS NUMBER) AS SPECIES_GRADE_PERCENT
        FROM scoped_parent P
      """;

  private static final String HISTORIC_RATES = """
      UNION ALL
      SELECT 1, P.*, R.APPRAISED_STUMPAGE_RATE_ID,
             R.STUMPAGE_RATE_EFFECTIVE_DATE, R.TOTAL_STUMPAGE_RATE_AMOUNT,
             NULL, NULL, NULL, NULL, NULL, NULL, NULL,
             R.APPRAISED_WORKSHEET_ID, R.HISTORIC_APPRAISED_WRKSHEET_ID, NULL,
             NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
        FROM scoped_parent P
        JOIN APPRAISED_STUMPAGE_RATE R ON R.HISTORIC_APPRAISED_WRKSHEET_ID = P.WORKSHEET_ID
      """;

  private static final String NON_APPRAISED_RATES = """
      UNION ALL
      SELECT 2, P.*, R.NON_APPRAISED_STUMPAGE_RATE_ID, NULL, NULL,
             R.SCALE_SPECIES_CODE, R.SCALE_PRODUCT_CODE, R.SCALE_GRADE_CODE,
             R.RESERVE_STUMPAGE_RATE, R.BONUS_BID_AMOUNT, R.DEVELOPMENT_LEVY, R.SILVICULTURE_LEVY,
             R.APPRAISED_WORKSHEET_ID, R.HISTORIC_APPRAISED_WRKSHEET_ID, R.NON_APPRAISED_WORKSHEET_ID,
             NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
        FROM scoped_parent P
        JOIN NON_APPRAISED_STUMPAGE_RATE R ON R.%s = P.WORKSHEET_ID
      """;

  // FIND_SELECTED_NAR_ADDON_CODES retains selected expired codes; it does not use SYSDATE or costs.
  private static final String SELECTED_ADDONS = """
      UNION ALL
      SELECT 3, P.*, NULL, NULL, NULL,
             NULL, NULL, NULL, NULL, NULL, NULL, NULL,
             NULL, NULL, A.NON_APPRAISED_WORKSHEET_ID,
             C.NON_APPRAISED_RATE_ADDON_CODE,
             C.NON_APPRAISED_RATE_ADDON_CODE || ' - ' || C.DESCRIPTION,
             C.EFFECTIVE_DATE, C.EXPIRY_DATE, C.UPDATE_TIMESTAMP,
             NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
        FROM scoped_parent P
        JOIN NON_APPRAISED_WS_RATE_ADDON A ON A.NON_APPRAISED_WORKSHEET_ID = P.WORKSHEET_ID
        JOIN NON_APPRAISED_RATE_ADDON_CODE C
          ON C.NON_APPRAISED_RATE_ADDON_CODE = A.NON_APPRAISED_RATE_ADDON_CODE
      """;

  private static final String HISTORIC_SPECIES = """
      UNION ALL
      SELECT 4, P.*, NULL, NULL, NULL,
             S.SCALE_SPECIES_CODE, NULL, NULL, NULL, NULL, NULL, NULL,
             NULL, S.HISTORIC_APPRAISED_WRKSHEET_ID, NULL,
             NULL, NULL, NULL, NULL, NULL,
             S.HISTORIC_SPECIES_ID, S.SELLING_PRICE_ZONE, S.SPECIES_VOLUME,
             S.LUMBER_RECOVERY_FACTOR, S.SPECIES_DECAY_PERCENT, S.SPECIES_STUD_PERCENT,
             S.SPECIES_BURN_PERCENT, NULL
        FROM scoped_parent P
        JOIN HISTORIC_SPECIES S ON S.HISTORIC_APPRAISED_WRKSHEET_ID = P.WORKSHEET_ID
      UNION ALL
      SELECT 5, P.*, NULL, NULL, NULL,
             G.SCALE_SPECIES_CODE, G.SCALE_PRODUCT_CODE, G.SCALE_GRADE_CODE, NULL, NULL, NULL, NULL,
             NULL, G.HISTORIC_APPRAISED_WRKSHEET_ID, NULL,
             NULL, NULL, NULL, NULL, NULL,
             G.HISTORIC_COAST_SPECIES_GRD_ID, NULL, NULL, NULL, NULL, NULL, NULL, G.SPECIES_GRADE_PERCENT
        FROM scoped_parent P
        JOIN HISTORIC_COAST_SPECIES_GRADE G ON G.HISTORIC_APPRAISED_WRKSHEET_ID = P.WORKSHEET_ID
      """;

  private static final String NON_APPRAISED_LABELS = """
      , (SELECT record_scope.TSB_NUMBER_CODE || ' - ' || C.DESCRIPTION FROM TSB_NUMBER_CODE C
           WHERE C.TSB_NUMBER_CODE = record_scope.TSB_NUMBER_CODE
             AND SYSDATE BETWEEN C.EFFECTIVE_DATE AND C.EXPIRY_DATE) AS TSB_DESCRIPTION
      , (SELECT C.DESCRIPTION FROM WORKSHEET_REFERENCE_TYPE_CODE C
           WHERE C.WORKSHEET_REFERENCE_TYPE_CODE = record_scope.REFERENCE_TYPE
             AND SYSDATE BETWEEN C.EFFECTIVE_DATE AND C.EXPIRY_DATE) AS REFERENCE_TYPE_DESCRIPTION
      , (SELECT C.DESCRIPTION FROM APPRAISAL_FOREST_ZONE_CODE C
           WHERE C.APPRAISAL_FOREST_ZONE_CODE = record_scope.APPRAISAL_FOREST_ZONE_CODE
             AND SYSDATE BETWEEN C.EFFECTIVE_DATE AND C.EXPIRY_DATE) AS APPRAISAL_FOREST_ZONE_DESCRIPTION
      , (SELECT C.DESCRIPTION FROM NON_APPRAISED_RATE_TYPE_CODE C
           WHERE C.NON_APPRAISED_RATE_TYPE_CODE = record_scope.NON_APPRAISED_RATE_TYPE_CODE
             AND SYSDATE BETWEEN C.EFFECTIVE_DATE AND C.EXPIRY_DATE) AS NON_APPRAISED_RATE_TYPE_DESCRIPTION
      , (SELECT C.DESCRIPTION FROM RATE_ADJUSTMENT_TYPE_CODE C
           WHERE C.RATE_ADJUSTMENT_TYPE_CODE = record_scope.RATE_ADJUSTMENT_TYPE_CODE
             AND SYSDATE BETWEEN C.EFFECTIVE_DATE AND C.EXPIRY_DATE) AS RATE_ADJUSTMENT_TYPE_DESCRIPTION
      """;

  // Selected scale codes use code-only lookups, including literal spaces and expired codes.
  // Scalar subqueries preserve missing-label rows and reject duplicate labels without multiplying rates.
  private static final String RATE_LABELS = """
      SELECT S.*,
             CASE WHEN S.ROW_KIND = 2 THEN
               (SELECT C.DESCRIPTION FROM SCALE_SPECIES_CODE C
                 WHERE C.SCALE_SPECIES_CODE = S.SCALE_SPECIES_CODE) END AS SCALE_SPECIES_DESCRIPTION,
             CASE WHEN S.ROW_KIND = 2 THEN
               (SELECT C.DESCRIPTION FROM SCALE_PRODUCT_CODE C
                 WHERE C.SCALE_PRODUCT_CODE = S.SCALE_PRODUCT_CODE) END AS SCALE_PRODUCT_DESCRIPTION,
             CASE WHEN S.ROW_KIND = 2 THEN
               (SELECT C.DESCRIPTION FROM SCALE_GRADE_CODE C
                 WHERE C.SCALE_GRADE_CODE = S.SCALE_GRADE_CODE) END AS SCALE_GRADE_DESCRIPTION
        FROM summary_rows S
      """;

  private final JdbcTemplate jdbc;

  public OracleOtherWorksheetSummary(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
  }

  public Optional<GasAppraisal.HistoricSummary> historic(TapsUser user, GasAppraisal.Key key) {
    requireFamily(key, GasAppraisal.WorksheetType.HISTORIC);
    return read(user, key, OtherWorksheetScopeSql.HISTORIC,
        "APPRAISAL_STATUS_CODE", "APPRAISAL_STATUS_CODE",
        HISTORIC_RATES + NON_APPRAISED_RATES.formatted("HISTORIC_APPRAISED_WRKSHEET_ID") + HISTORIC_SPECIES,
        rows -> {
          HistoricParent parent = null;
          var rates = new ArrayList<GasAppraisal.StoredRate>();
          var otherRates = new ArrayList<GasAppraisal.StoredNonAppraisedRate>();
          var species = new ArrayList<GasAppraisal.HistoricSpecies>();
          var grades = new ArrayList<GasAppraisal.HistoricCoastSpeciesGrade>();
          while (rows.next()) {
            checkWorksheet(rows, key);
            switch (rows.getInt("ROW_KIND")) {
              case 0 -> {
                if (parent != null) {
                  throw new IncorrectResultSizeDataAccessException(1, 2);
                }
                parent = new HistoricParent(Header.read(rows), rows.getString("RATE_CALC_METHOD_CODE"),
                    indicator(rows, "TOTAL_OBLIGATION_ADJUSTMNT_IND"),
                    indicator(rows, "ADJUST_QUARTERLY_IND"), indicator(rows, "ACTIVE_IND"),
                    rows.getString("POLICY_VERSION"), localDate(rows, "CEASE_ADJUSTMENT_DATE"));
              }
              case 1 -> {
                checkRateParent(rows, key);
                rates.add(new GasAppraisal.StoredRate(rows.getString("RATE_ID"),
                    localDate(rows, "RATE_EFFECTIVE_DATE"), rows.getBigDecimal("RATE_AMOUNT")));
              }
              case 2 -> {
                checkRateParent(rows, key);
                otherRates.add(nonAppraisedRate(rows));
              }
              case 4 -> {
                checkRateParent(rows, key);
                species.add(new GasAppraisal.HistoricSpecies(rows.getString("DETAIL_ID"),
                    rows.getString("SCALE_SPECIES_CODE"), rows.getString("SELLING_PRICE_ZONE"),
                    rows.getBigDecimal("SPECIES_VOLUME"), rows.getBigDecimal("LUMBER_RECOVERY_FACTOR"),
                    rows.getBigDecimal("SPECIES_DECAY_PERCENT"), rows.getBigDecimal("SPECIES_STUD_PERCENT"),
                    rows.getBigDecimal("SPECIES_BURN_PERCENT")));
              }
              case 5 -> {
                checkRateParent(rows, key);
                grades.add(new GasAppraisal.HistoricCoastSpeciesGrade(rows.getString("DETAIL_ID"),
                    rows.getString("SCALE_SPECIES_CODE"), rows.getString("SCALE_PRODUCT_CODE"),
                    rows.getString("SCALE_GRADE_CODE"), rows.getBigDecimal("SPECIES_GRADE_PERCENT")));
              }
              default -> throw new DataIntegrityViolationException("unknown summary row kind");
            }
          }
          if (parent == null) {
            requireNoOrphans(rates, otherRates, species, grades);
            return Optional.empty();
          }
          var h = parent.header();
          var variant = GasAppraisal.SummaryVariant.resolve(parent.calculation(), h.method(), parent.toa())
              .orElseThrow(() -> new DataIntegrityViolationException("unsupported summary variant"));
          return Optional.of(new GasAppraisal.HistoricSummary(key, h.licence(), h.mark(), h.method(),
              variant, parent.calculation(), parent.toa(), parent.quarterly(), parent.active(),
              parent.policy(), h.status(), h.effective(), h.expiry(), parent.cease(), rates, otherRates,
              species, grades));
        });
  }

  public Optional<GasAppraisal.NonAppraisedSummary> nonAppraised(TapsUser user, GasAppraisal.Key key) {
    requireFamily(key, GasAppraisal.WorksheetType.NON_APPRAISED);
    return read(user, key, OtherWorksheetScopeSql.NON_APPRAISED,
        "NON_APPRAISED_STATUS_CODE", "NON_APPRAISED_STATUS_CODE",
        NON_APPRAISED_RATES.formatted("NON_APPRAISED_WORKSHEET_ID") + SELECTED_ADDONS,
        rows -> {
          NonAppraisedParent parent = null;
          var rates = new ArrayList<GasAppraisal.StoredNonAppraisedRate>();
          var addons = new ArrayList<GasAppraisal.SelectedRateAddon>();
          while (rows.next()) {
            checkWorksheet(rows, key);
            switch (rows.getInt("ROW_KIND")) {
              case 0 -> {
                if (parent != null) {
                  throw new IncorrectResultSizeDataAccessException(1, 2);
                }
                parent = new NonAppraisedParent(Header.read(rows),
                    option(rows, "REFERENCE_TYPE", "REFERENCE_TYPE_DESCRIPTION"),
                    localDate(rows, "SDM_DECLARATION_ACCEPTANCE_DT"),
                    option(rows, "TSB_NUMBER_CODE", "TSB_DESCRIPTION"),
                    option(rows, "APPRAISAL_FOREST_ZONE_CODE", "APPRAISAL_FOREST_ZONE_DESCRIPTION"),
                    option(rows, "NON_APPRAISED_RATE_TYPE_CODE", "NON_APPRAISED_RATE_TYPE_DESCRIPTION"),
                    option(rows, "RATE_ADJUSTMENT_TYPE_CODE", "RATE_ADJUSTMENT_TYPE_DESCRIPTION"));
              }
              case 2 -> {
                checkRateParent(rows, key);
                rates.add(nonAppraisedRate(rows));
              }
              case 3 -> {
                checkRateParent(rows, key);
                addons.add(new GasAppraisal.SelectedRateAddon(rows.getString("ADDON_CODE"),
                    rows.getString("ADDON_DESCRIPTION"), localDateTime(rows, "ADDON_EFFECTIVE_DATE"),
                    localDateTime(rows, "ADDON_EXPIRY_DATE"), localDateTime(rows, "ADDON_UPDATE_TIMESTAMP")));
              }
              default -> throw new DataIntegrityViolationException("unknown summary row kind");
            }
          }
          if (parent == null) {
            requireNoOrphans(rates, addons);
            return Optional.empty();
          }
          var h = parent.header();
          return Optional.of(new GasAppraisal.NonAppraisedSummary(key, h.licence(), h.mark(), h.method(),
              h.status(), h.effective(), h.expiry(), parent.reference(), parent.sdm(), parent.tsb(),
              parent.zone(), parent.rateType(), parent.adjustmentType(), rates, addons));
        });
  }

  private <T> T read(TapsUser user, GasAppraisal.Key key, String source, String statusTable,
      String statusColumn, String children, ResultSetExtractor<T> extractor) {
    ReadScopePredicate scope = ReadScopePredicate.forCapability(user, TapsCapability.GAS_APPRAISAL_VIEW);
    // Legacy FIND_HIST_APP_WORKSHEET_BY_ID, like search, requires ACTIVE='Y'.
    // Table and column names are constants. One statement keeps parent and children consistent.
    String active = key.type() == GasAppraisal.WorksheetType.HISTORIC
        ? " AND record_scope.ACTIVE_IND = 'Y'" : "";
    String worksheetLabels = key.type() == GasAppraisal.WorksheetType.NON_APPRAISED
        ? NON_APPRAISED_LABELS : "";
    String sql = "WITH record_scope AS (\n" + source + "), scoped_parent AS (\n"
        + "SELECT record_scope.*, (SELECT C.DESCRIPTION FROM " + statusTable + " C WHERE C."
        + statusColumn + " = record_scope.STATUS_CODE AND SYSDATE BETWEEN C.EFFECTIVE_DATE"
        + " AND C.EXPIRY_DATE) AS STATUS_DESCRIPTION" + worksheetLabels + " FROM record_scope\n"
        + "WHERE record_scope.WORKSHEET_ID = ? AND " + scope.sql() + active + "\n), summary_rows AS (\n"
        + PARENT + children + ")\n" + RATE_LABELS
        + "ORDER BY ROW_KIND, RATE_EFFECTIVE_DATE, SCALE_SPECIES_CODE, SCALE_PRODUCT_CODE, SCALE_GRADE_CODE, RATE_ID, ADDON_CODE, DETAIL_ID";
    return jdbc.query(sql,
        statement -> {
          statement.setLong(1, Long.parseLong(key.worksheetId()));
          for (int index = 0; index < scope.parameters().size(); index++) {
            statement.setString(index + 2, scope.parameters().get(index));
          }
        }, extractor);
  }

  private static void requireFamily(GasAppraisal.Key key, GasAppraisal.WorksheetType family) {
    Objects.requireNonNull(key, "key");
    if (key.type() != family) {
      throw new IllegalArgumentException("summary requires a " + family + " key");
    }
  }

  private static void checkWorksheet(ResultSet row, GasAppraisal.Key key) throws SQLException {
    if (!key.worksheetId().equals(row.getString("WORKSHEET_ID"))) {
      throw new DataIntegrityViolationException("summary row does not match requested worksheet");
    }
  }

  private static void checkRateParent(ResultSet row, GasAppraisal.Key key) throws SQLException {
    String historic = row.getString("HISTORIC_PARENT_ID");
    String nonAppraised = row.getString("NON_APPRAISED_PARENT_ID");
    boolean matches = key.type() == GasAppraisal.WorksheetType.HISTORIC
        ? key.worksheetId().equals(historic) && nonAppraised == null
        : key.worksheetId().equals(nonAppraised) && historic == null;
    if (!matches || row.getString("APPRAISED_PARENT_ID") != null) {
      throw new DataIntegrityViolationException("stored rate does not have exactly the requested family parent");
    }
  }

  private static void requireNoOrphans(List<?>... children) {
    for (List<?> rows : children) {
      if (!rows.isEmpty()) {
        throw new DataIntegrityViolationException("summary children have no scoped parent");
      }
    }
  }

  private static GasAppraisal.StoredNonAppraisedRate nonAppraisedRate(ResultSet row) throws SQLException {
    return new GasAppraisal.StoredNonAppraisedRate(row.getString("RATE_ID"),
        option(row, "SCALE_SPECIES_CODE", "SCALE_SPECIES_DESCRIPTION"),
        option(row, "SCALE_PRODUCT_CODE", "SCALE_PRODUCT_DESCRIPTION"),
        option(row, "SCALE_GRADE_CODE", "SCALE_GRADE_DESCRIPTION"), row.getBigDecimal("RESERVE_STUMPAGE_RATE"),
        row.getBigDecimal("BONUS_BID_AMOUNT"), row.getBigDecimal("DEVELOPMENT_LEVY"),
        row.getBigDecimal("SILVICULTURE_LEVY"));
  }

  private static CodeOption option(ResultSet row, String codeColumn, String descriptionColumn)
      throws SQLException {
    String code = row.getString(codeColumn);
    return code == null ? null : new CodeOption(code, row.getString(descriptionColumn));
  }

  private static Boolean indicator(ResultSet row, String column) throws SQLException {
    String value = row.getString(column);
    if (value == null) {
      return null;
    }
    return switch (value) {
      case "Y" -> true;
      case "N" -> false;
      default -> throw new DataIntegrityViolationException("unsupported indicator: " + column);
    };
  }

  private static LocalDate localDate(ResultSet row, String column) throws SQLException {
    Date value = row.getDate(column);
    return value == null ? null : value.toLocalDate();
  }

  private static LocalDateTime localDateTime(ResultSet row, String column) throws SQLException {
    Timestamp value = row.getTimestamp(column);
    return value == null ? null : value.toLocalDateTime();
  }

  private record Header(String licence, String mark, AppraisalMethod method, CodeOption status,
      LocalDate effective, LocalDate expiry) {
    static Header read(ResultSet row) throws SQLException {
      return new Header(row.getString("LICENSE"), row.getString("TIMBER_MARK"),
          AppraisalMethod.valueOf(row.getString("APPRAISAL_METHOD_CODE")),
          new CodeOption(row.getString("STATUS_CODE"), row.getString("STATUS_DESCRIPTION")),
          localDate(row, "EFFECTIVE_DATE"), localDate(row, "EXPIRY_DATE"));
    }
  }

  private record HistoricParent(Header header, String calculation, Boolean toa, Boolean quarterly,
      Boolean active, String policy, LocalDate cease) {}

  private record NonAppraisedParent(Header header, CodeOption reference, LocalDate sdm, CodeOption tsb,
      CodeOption zone, CodeOption rateType, CodeOption adjustmentType) {}
}
