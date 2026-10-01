package ca.bc.gov.nrs.taps.security;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** The signed-in person and the TAPS grants FAM gave them. */
public record TapsUser(
    String userId,
    String displayName,
    String email,
    IdentityProvider identityProvider,
    String businessName,
    List<RoleGrant> grants) {

  public TapsUser {
    grants = List.copyOf(grants);
  }

  public Set<TapsCapability> capabilities() {
    Set<TapsCapability> capabilities = EnumSet.noneOf(TapsCapability.class);
    grants.forEach(grant -> capabilities.addAll(grant.role().capabilities()));
    return capabilities;
  }

  /** Whether any grant gives the capability somewhere; use the record form before touching data. */
  public boolean can(TapsCapability capability) {
    return grants.stream().anyMatch(grant -> grant.role().capabilities().contains(capability));
  }

  /**
   * Whether one grant gives the capability for this record. The capability and the scope must come
   * from the same grant: a province-wide viewer who is also a Cariboo appraiser may read a Skeena
   * appraisal but not edit it.
   */
  public boolean can(TapsCapability capability, RecordScope record) {
    return grants.stream()
        .anyMatch(
            grant -> grant.role().capabilities().contains(capability) && grant.covers(record));
  }

  /** Forest clients this user acts for, in grant order. */
  public List<String> forestClients() {
    return grants.stream()
        .map(RoleGrant::scope)
        .filter(scope -> scope != null && scope.type().equals(FamRoleName.FOREST_CLIENT))
        .map(FamRoleName.Scope::value)
        .distinct()
        .toList();
  }
}
