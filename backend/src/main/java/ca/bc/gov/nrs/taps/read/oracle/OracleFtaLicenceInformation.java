package ca.bc.gov.nrs.taps.read.oracle;

import ca.bc.gov.nrs.taps.read.GasAppraisal;
import ca.bc.gov.nrs.taps.read.CodeOption;
import ca.bc.gov.nrs.taps.security.TapsCapability;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** FTA licence details for the search panel, independent of worksheets. */
public final class OracleFtaLicenceInformation {
  private final JdbcTemplate jdbc;

  public OracleFtaLicenceInformation(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
  }

  public Optional<GasAppraisal.FtaLicenceInformation> find(
      TapsUser user, String licence, String timberMark) {
    var filters = new GasAppraisal.Search(licence, timberMark, 0);
    if (filters.timberMark() == null) {
      throw new IllegalArgumentException("timberMark is required");
    }
    ReadScopePredicate scope =
        ReadScopePredicate.forCapability(user, TapsCapability.GAS_APPRAISAL_VIEW);
    // Parent authorization, permit aggregation and display metadata share one Oracle snapshot.
    return jdbc.query(
        FtaScopeSql.scopedCtes(scope) + FtaScopeSql.INFORMATION_SELECT,
        statement -> {
          statement.setString(1, filters.licence());
          statement.setString(2, filters.timberMark());
          for (int index = 0; index < scope.parameters().size(); index++) {
            statement.setString(index + 3, scope.parameters().get(index));
          }
        },
        OracleFtaLicenceInformation::extract);
  }

  private static Optional<GasAppraisal.FtaLicenceInformation> extract(ResultSet rows)
      throws SQLException {
    if (!rows.next()) {
      return Optional.empty();
    }
    String cuttingPermits = rows.getString("CUT_PERMIT_IDS");
    // GET_ALL_CP_FOR_TM builds a VARCHAR2(500), including its final two-character separator.
    // Keep that character limit.
    if (cuttingPermits != null && cuttingPermits.length() > 498) {
      throw new DataIntegrityViolationException("cutting permit list exceeds legacy buffer");
    }
    var information = new GasAppraisal.FtaLicenceInformation(
        rows.getString("CLIENT_NUMBER"),
        rows.getString("CLIENT_NAME"),
        rows.getString("FOREST_FILE_ID"),
        cuttingPermits,
        rows.getString("FILE_TYPE_CODE"),
        rows.getString("TIMBER_MARK"),
        rows.getString("FOREST_REGION_NAME"),
        rows.getString("FOREST_DISTRICT_NAME"),
        localDate(rows, "EXPIRY_DATE"),
        localDate(rows, "EXTEND_DATE"),
        rows.getString("LICENCE_STATUS_DESC"), markStatus(rows),
        cruiseBased(rows.getString("CRUISE_BASED_IND")));
    // The SQL removes duplicate display rows. If distinct contexts remain we reject them rather than
    // pick one arbitrarily like the legacy code.
    if (rows.next()) {
      throw new IncorrectResultSizeDataAccessException(1, 2);
    }
    return Optional.of(information);
  }

  private static LocalDate localDate(ResultSet row, String column) throws SQLException {
    Timestamp value = row.getTimestamp(column);
    return value == null ? null : value.toLocalDateTime().toLocalDate();
  }

  private static CodeOption markStatus(ResultSet row) throws SQLException {
    String code = row.getString("MARK_STATUS_CODE");
    return code == null ? null : new CodeOption(code, row.getString("MARK_STATUS_DESC"));
  }

  private static Boolean cruiseBased(String value) {
    if ("Y".equalsIgnoreCase(value)) return true;
    if ("N".equalsIgnoreCase(value)) return false;
    return null;
  }
}
