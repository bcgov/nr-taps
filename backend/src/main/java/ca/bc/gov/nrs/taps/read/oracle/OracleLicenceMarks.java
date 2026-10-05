package ca.bc.gov.nrs.taps.read.oracle;

import ca.bc.gov.nrs.taps.read.GasAppraisal;
import ca.bc.gov.nrs.taps.security.TapsCapability;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.sql.Types;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

/** Scoped version of GAS2_COMMON.FIND_HAUL_AUTHORITIES_BY_FFID for the timber mark chooser. */
public final class OracleLicenceMarks {
  private static final String MARKS = """
      SELECT HA.TIMBER_MARK
        FROM HAULING_AUTHORITY HA
        CROSS JOIN requested_input I
       WHERE HA.FOREST_FILE_ID = I.FOREST_FILE_ID
         AND EXISTS (
           SELECT 1 FROM scoped_parents P
            WHERE P.FOREST_FILE_ID = HA.FOREST_FILE_ID
              AND P.TIMBER_MARK = HA.TIMBER_MARK
         )
       ORDER BY HA.TIMBER_MARK
      """;

  private final JdbcTemplate jdbc;

  public OracleLicenceMarks(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
  }

  public GasAppraisal.LicenceMarks forLicence(TapsUser user, String licence) {
    String normalizedLicence = new GasAppraisal.Search(licence, null, null).licence();
    if (normalizedLicence == null) {
      throw new IllegalArgumentException("licence is required");
    }
    ReadScopePredicate scope =
        ReadScopePredicate.forCapability(user, TapsCapability.GAS_APPRAISAL_VIEW);
    var marks = jdbc.query(
        FtaScopeSql.scopedCtes(scope) + "\n" + MARKS,
        statement -> {
          statement.setString(1, normalizedLicence);
          statement.setNull(2, Types.VARCHAR);
          for (int index = 0; index < scope.parameters().size(); index++) {
            statement.setString(index + 3, scope.parameters().get(index));
          }
        },
        (row, rowNumber) -> row.getString("TIMBER_MARK"));
    return new GasAppraisal.LicenceMarks(normalizedLicence, marks);
  }
}
