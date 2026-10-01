package ca.bc.gov.nrs.taps.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Matches nr-fam's CssRoleNaming; parse suffixes from the right because scope values can contain
 * underscores.
 */
public record FamRoleName(String baseRole, List<Scope> scopes) {

  public static final String DISTRICT = "DISTRICT";
  public static final String REGION = "REGION";
  public static final String FOREST_CLIENT = "FOREST_CLIENT";

  /** FAM metadata never grants application access. */
  public static final String SIDECAR_PREFIX = "FAM:";

  private static final List<String> SCOPE_TYPES = List.of(FOREST_CLIENT, DISTRICT, REGION);

  public FamRoleName {
    scopes = List.copyOf(scopes);
  }

  public record Scope(String type, String value) {}

  public static boolean isSidecar(String roleName) {
    return roleName != null && roleName.startsWith(SIDECAR_PREFIX);
  }

  public static FamRoleName parse(String roleName) {
    List<Scope> scopes = new ArrayList<>();
    String remaining = roleName;
    while (true) {
      Optional<Split> split = stripOneScope(remaining);
      if (split.isEmpty()) {
        break;
      }
      scopes.add(split.get().scope());
      remaining = split.get().head();
    }
    Collections.reverse(scopes);
    return new FamRoleName(remaining, scopes);
  }

  public Optional<String> scope(String type) {
    return scopes.stream().filter(scope -> scope.type().equals(type)).map(Scope::value).findFirst();
  }

  private static Optional<Split> stripOneScope(String roleName) {
    int hyphen = roleName.lastIndexOf('-');
    if (hyphen < 0) {
      return Optional.empty();
    }
    String head = roleName.substring(0, hyphen);
    String value = roleName.substring(hyphen + 1);
    for (String type : SCOPE_TYPES) {
      String suffix = "_" + type;
      if (head.endsWith(suffix) && head.length() > suffix.length()) {
        return Optional.of(
            new Split(head.substring(0, head.length() - suffix.length()), new Scope(type, value)));
      }
    }
    return Optional.empty();
  }

  private record Split(String head, Scope scope) {}
}
