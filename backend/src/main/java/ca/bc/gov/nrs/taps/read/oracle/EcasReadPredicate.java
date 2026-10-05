package ca.bc.gov.nrs.taps.read.oracle;

import ca.bc.gov.nrs.taps.security.FamRoleName;
import ca.bc.gov.nrs.taps.security.RoleGrant;
import ca.bc.gov.nrs.taps.security.TapsCapability;
import ca.bc.gov.nrs.taps.security.TapsRole;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Ownership and status visibility shared by ECAS lists and reference reads. */
final class EcasReadPredicate {
  private final String sql;
  private final List<String> parameters;

  private EcasReadPredicate(String sql, List<String> parameters) {
    this.sql = sql;
    this.parameters = List.copyOf(parameters);
  }

  static EcasReadPredicate forUser(TapsUser user) {
    Objects.requireNonNull(user, "user");
    List<String> alternatives = new ArrayList<>();
    List<String> parameters = new ArrayList<>();
    for (RoleGrant grant : user.grants()) {
      TapsUser singleGrant = new TapsUser(user.userId(), user.displayName(), user.email(),
          user.identityProvider(), user.businessName(), List.of(grant));
      ReadScopePredicate scope =
          ReadScopePredicate.forCapability(singleGrant, TapsCapability.ECAS_SUBMISSION_VIEW);
      if (scope.sql().equals("(1 = 0)")) {
        continue;
      }
      // Provisional mapping of the legacy ECAS_BCTS/LICENSEE/RPF and ministry VIEW_ONLY rules.
      // Read-only client roles receive the same scenario restriction as other client roles.
      String excludedStatus = FamRoleName.FOREST_CLIENT.equals(grant.role().scopeType())
          ? "SCN" : grant.role() == TapsRole.TAPS_VIEWER ? "DFT" : null;
      if (excludedStatus == null && scope.sql().equals("(1 = 1)")) {
        return new EcasReadPredicate("(1 = 1)", List.of());
      }
      parameters.addAll(scope.parameters());
      if (excludedStatus == null) {
        alternatives.add(scope.sql());
      } else {
        alternatives.add("(" + scope.sql() + " AND record_scope.STATUS_CODE <> ?)");
        parameters.add(excludedStatus);
      }
    }
    return new EcasReadPredicate(
        alternatives.isEmpty() ? "(1 = 0)" : "(" + String.join(" OR ", alternatives) + ")",
        parameters);
  }

  String sql() {
    return sql;
  }

  List<String> parameters() {
    return parameters;
  }
}
