package ca.bc.gov.nrs.taps.security;

import java.util.Optional;
import java.util.regex.Pattern;

/** One accepted FAM grant: a TAPS role and, for a scoped role, the one scope value it was granted for. */
public record RoleGrant(TapsRole role, FamRoleName.Scope scope) {

  private static final Pattern DISTRICT_CODE = Pattern.compile("D[A-Z]{2}");
  private static final Pattern FOREST_CLIENT_NUMBER = Pattern.compile("[0-9]{8}");

  /**
   * Accepts a role only when TAPS knows it, it belongs to the user's sign-in type, and it carries
   * exactly its role's scope with a well-formed value. Anything else grants nothing.
   */
  public static Optional<RoleGrant> accept(FamRoleName name, IdentityProvider identityProvider) {
    Optional<TapsRole> known = TapsRole.fromCode(name.baseRole());
    if (known.isEmpty() || known.get().identityProvider() != identityProvider) {
      return Optional.empty();
    }
    TapsRole role = known.get();
    if (role.scopeType() == null) {
      return name.scopes().isEmpty() ? Optional.of(new RoleGrant(role, null)) : Optional.empty();
    }
    if (name.scopes().size() != 1) {
      return Optional.empty();
    }
    FamRoleName.Scope scope = name.scopes().get(0);
    return scope.type().equals(role.scopeType()) && validValue(scope)
        ? Optional.of(new RoleGrant(role, scope))
        : Optional.empty();
  }

  /** Whether this grant reaches a record; an unscoped grant reaches every record. */
  public boolean covers(RecordScope record) {
    if (record == null) {
      return false;
    }
    if (scope == null) {
      return true;
    }
    return switch (scope.type()) {
      case FamRoleName.DISTRICT -> scope.value().equals(record.adminDistrictCode());
      // A region reaches its districts, as legacy matched an appraisal's rollup region.
      case FamRoleName.REGION ->
          FamRegion.fromScopeValue(scope.value())
              .map(region -> region.orgUnitCode().equals(record.rollupRegionCode()))
              .orElse(false);
      case FamRoleName.FOREST_CLIENT -> scope.value().equals(record.clientNumber());
      default -> false;
    };
  }

  private static boolean validValue(FamRoleName.Scope scope) {
    return switch (scope.type()) {
      case FamRoleName.DISTRICT -> DISTRICT_CODE.matcher(scope.value()).matches();
      case FamRoleName.REGION -> FamRegion.fromScopeValue(scope.value()).isPresent();
      case FamRoleName.FOREST_CLIENT -> FOREST_CLIENT_NUMBER.matcher(scope.value()).matches();
      default -> false;
    };
  }
}
