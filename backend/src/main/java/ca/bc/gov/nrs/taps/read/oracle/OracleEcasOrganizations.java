package ca.bc.gov.nrs.taps.read.oracle;

import ca.bc.gov.nrs.taps.read.CodeOption;
import ca.bc.gov.nrs.taps.security.FamRoleName;
import ca.bc.gov.nrs.taps.security.TapsCapability;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** Ministry organization choices for search; replaces the WebADE list, not its permissions. */
public final class OracleEcasOrganizations {
  private final JdbcTemplate jdbc;

  public OracleEcasOrganizations(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<CodeOption> forUser(TapsUser user) {
    // Client grants do not imply ministry organization membership.
    var alternatives = new ArrayList<String>();
    var parameters = new ArrayList<String>();
    for (var grant : user.grants()) {
      if (FamRoleName.FOREST_CLIENT.equals(grant.role().scopeType())) continue;
      var singleGrant = new TapsUser(user.userId(), user.displayName(), user.email(), user.identityProvider(),
          user.businessName(), List.of(grant));
      var scope = ReadScopePredicate.forCapability(singleGrant, TapsCapability.ECAS_SUBMISSION_VIEW);
      if (scope.sql().equals("(1 = 0)")) continue;
      // The source limits its unfiltered list to primary units, but scoped regions retain subunits.
      alternatives.add(scope.sql().equals("(1 = 1)") ? "record_scope.PRIMARY_UNIT = 1" : scope.sql());
      parameters.addAll(scope.parameters());
    }
    if (alternatives.isEmpty()) return List.of();
    String sql = """
        WITH record_scope AS (
          SELECT O.ORG_UNIT_NO, O.ORG_UNIT_CODE AS ADMIN_DISTRICT_CODE,
                 O.ORG_UNIT_CODE || ' - ' || O.ORG_UNIT_NAME AS DESCRIPTION,
                 R.ORG_UNIT_CODE AS ROLLUP_REGION_CODE, CAST(NULL AS VARCHAR2(8)) AS CLIENT_NUMBER,
                 CASE WHEN O.ORG_UNIT_NO = O.ROLLUP_REGION_NO OR O.ORG_UNIT_NO = O.ROLLUP_DIST_NO THEN 1 ELSE 0 END AS PRIMARY_UNIT
            FROM ORG_UNIT O LEFT JOIN ORG_UNIT R ON R.ORG_UNIT_NO = O.ROLLUP_REGION_NO
           WHERE O.EXPIRY_DATE > SYSDATE
        )
        SELECT ORG_UNIT_NO, DESCRIPTION FROM record_scope WHERE %s ORDER BY ADMIN_DISTRICT_CODE
        """.formatted("(" + String.join(" OR ", alternatives) + ")");
    return jdbc.query(sql, statement -> {
      for (int index = 0; index < parameters.size(); index++) {
        statement.setString(index + 1, parameters.get(index));
      }
    }, (row, index) -> new CodeOption(row.getString("ORG_UNIT_NO"), row.getString("DESCRIPTION")));
  }
}
