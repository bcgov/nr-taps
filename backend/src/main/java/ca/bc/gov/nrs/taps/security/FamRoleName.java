package ca.bc.gov.nrs.taps.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A FAM role name read from the token, split into its base code and scopes.
 *
 * <p>A CSS role is a bare name, so FAM writes a grant's scope into the name: {@code
 * <CODE>_DISTRICT-<value>}, {@code <CODE>_REGION-<value>} and {@code <CODE>_FOREST_CLIENT-<value>},
 * chained in that order when a role has several. A scoped holder never carries the bare code. Codes
 * match {@code ^[A-Z][A-Z0-9_]{1,58}$}, so they contain no {@code -}, while region values and the
 * {@code FOREST_CLIENT} type contain underscores. Suffixes are therefore peeled from the right at
 * the last hyphen, never split on underscores. This is a port of {@code CssRoleNaming.parse} in
 * nr-fam; keep the two in step.
 */
public record FamRoleName(String baseRole, List<Scope> scopes) {

  public static final String DISTRICT = "DISTRICT";
  public static final String REGION = "REGION";
  public static final String FOREST_CLIENT = "FOREST_CLIENT";

  /** FAM bookkeeping roles such as {@code FAM:EXPIRES:2026-09-30:<ROLE>}; never authorities. */
  public static final String SIDECAR_PREFIX = "FAM:";

  // Longest first, so FOREST_CLIENT is tried before a type that it ends with.
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
    // Peeled right to left; restore the written order.
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
    // A hyphen that is not a scope separator: the name is its own base role.
    return Optional.empty();
  }

  private record Split(String head, Scope scope) {}
}
