package ca.bc.gov.nrs.taps.read.oracle;

import ca.bc.gov.nrs.taps.security.FamRegion;
import ca.bc.gov.nrs.taps.security.FamRoleName;
import ca.bc.gov.nrs.taps.security.RoleGrant;
import ca.bc.gov.nrs.taps.security.TapsCapability;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds the scope filter for the caller's grants over a {@code record_scope} projection. Its
 * ADMIN_DISTRICT_CODE, ROLLUP_REGION_CODE and CLIENT_NUMBER columns must come from the database, not
 * request filters; leave unknown values NULL.
 */
public final class ReadScopePredicate {
  private final String sql;
  private final List<String> parameters;

  private ReadScopePredicate(String sql, List<String> parameters) {
    this.sql = sql;
    this.parameters = List.copyOf(parameters);
  }

  /** Capability and scope are taken from the same accepted grant, as in TapsUser.can. */
  public static ReadScopePredicate forCapability(TapsUser user, TapsCapability capability) {
    Objects.requireNonNull(user, "user");
    Objects.requireNonNull(capability, "capability");
    List<String> alternatives = new ArrayList<>();
    List<String> parameters = new ArrayList<>();
    for (RoleGrant grant : user.grants()) {
      if (!grant.role().capabilities().contains(capability)) {
        continue;
      }
      // Reuse grant parsing so a malformed scope is never treated as province-wide.
      var accepted =
          RoleGrant.accept(
              new FamRoleName(
                  grant.role().name(),
                  grant.scope() == null ? List.of() : List.of(grant.scope())),
              user.identityProvider());
      if (accepted.isEmpty()) {
        continue;
      }
      if (grant.scope() == null) {
        return new ReadScopePredicate("(1 = 1)", List.of());
      }
      switch (grant.scope().type()) {
        case FamRoleName.DISTRICT -> {
          alternatives.add("record_scope.ADMIN_DISTRICT_CODE = ?");
          parameters.add(grant.scope().value());
        }
        case FamRoleName.REGION -> {
          alternatives.add("record_scope.ROLLUP_REGION_CODE = ?");
          parameters.add(FamRegion.fromScopeValue(grant.scope().value()).orElseThrow().orgUnitCode());
        }
        case FamRoleName.FOREST_CLIENT -> {
          alternatives.add("record_scope.CLIENT_NUMBER = ?");
          parameters.add(grant.scope().value());
        }
        default -> throw new IllegalStateException("accepted grant has an unsupported scope");
      }
    }
    return new ReadScopePredicate(
        alternatives.isEmpty() ? "(1 = 0)" : "(" + String.join(" OR ", alternatives) + ")",
        parameters);
  }

  /** Parenthesized so callers can AND other filters onto it. */
  public String sql() {
    return sql;
  }

  /** Ordered values for prepared-statement placeholders. */
  public List<String> parameters() {
    return parameters;
  }
}
