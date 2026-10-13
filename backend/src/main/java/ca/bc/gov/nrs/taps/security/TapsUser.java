package ca.bc.gov.nrs.taps.security;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record TapsUser(
    String userId,
    String displayName,
    String email,
    IdentityProvider identityProvider,
    String businessName,
    List<RoleGrant> grants,
    String legacyAccount) {

  public TapsUser(String userId, String displayName, String email, IdentityProvider identityProvider,
      String businessName, List<RoleGrant> grants) {
    this(userId, displayName, email, identityProvider, businessName, grants, null);
  }

  public TapsUser {
    grants = List.copyOf(grants);
  }

  public Set<TapsCapability> capabilities() {
    Set<TapsCapability> capabilities = EnumSet.noneOf(TapsCapability.class);
    grants.forEach(grant -> capabilities.addAll(grant.role().capabilities()));
    return capabilities;
  }

  /** Use the record overload before accessing data. */
  public boolean can(TapsCapability capability) {
    return grants.stream().anyMatch(grant -> grant.role().capabilities().contains(capability));
  }

  /** Capability and scope must come from the same grant. */
  public boolean can(TapsCapability capability, RecordScope record) {
    return grants.stream()
        .anyMatch(
            grant -> grant.role().capabilities().contains(capability) && grant.covers(record));
  }

  public List<String> forestClients() {
    return grants.stream()
        .map(RoleGrant::scope)
        .filter(scope -> scope != null && scope.type().equals(FamRoleName.FOREST_CLIENT))
        .map(FamRoleName.Scope::value)
        .distinct()
        .toList();
  }

  /** An assigned queue must use a signed provider username, never the GUID audit fallback. */
  public boolean ecasMyToDoAvailable() {
    List<RoleGrant> ecasGrants = grants.stream()
        .filter(grant -> grant.role().capabilities().contains(TapsCapability.ECAS_SUBMISSION_VIEW))
        .filter(grant -> RoleGrant.accept(new FamRoleName(grant.role().name(),
            grant.scope() == null ? List.of() : List.of(grant.scope())), identityProvider).isPresent())
        .toList();
    return !ecasGrants.isEmpty() && ((legacyAccount != null && !legacyAccount.isBlank())
        || ecasGrants.stream().noneMatch(grant ->
        switch (grant.role()) {
          case TAPS_HEADQUARTERS, TAPS_REGION_APPRAISER, TAPS_DISTRICT_APPRAISER -> true;
          default -> false;
        }));
  }
}
