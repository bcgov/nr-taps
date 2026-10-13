package ca.bc.gov.nrs.taps.read.oracle;

import ca.bc.gov.nrs.taps.read.EcasInbox;
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
    return forUser(user, null);
  }

  static EcasReadPredicate forInbox(TapsUser user, EcasInbox.Search search) {
    return forUser(user, Objects.requireNonNull(search, "search"));
  }

  private static EcasReadPredicate forUser(TapsUser user, EcasInbox.Search search) {
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
      List<String> conditions = new ArrayList<>();
      conditions.add(scope.sql());
      parameters.addAll(scope.parameters());
      if (excludedStatus != null) {
        conditions.add("record_scope.STATUS_CODE <> ?");
        parameters.add(excludedStatus);
      }
      if (search != null && search.mode() == EcasInbox.Mode.MY_TO_DO) {
        queue(conditions, parameters, grant, user, search);
      }
      if (conditions.equals(List.of("(1 = 1)"))) {
        return new EcasReadPredicate("(1 = 1)", List.of());
      }
      alternatives.add(conditions.size() == 1 ? conditions.getFirst()
          : "(" + String.join(" AND ", conditions) + ")");
    }
    return new EcasReadPredicate(
        alternatives.isEmpty() ? "(1 = 0)" : "(" + String.join(" OR ", alternatives) + ")",
        parameters);
  }

  private static void queue(List<String> conditions, List<String> parameters, RoleGrant grant,
      TapsUser user, EcasInbox.Search search) {
    // Ministry VIEW_ONLY queue restrictions precede the legacy direct-ID branch.
    if (grant.role() == TapsRole.TAPS_VIEWER) {
      conditions.add("record_scope.STATUS_CODE IN ('SUB','RCD','RTN')");
    } else if (grant.role() == TapsRole.TAPS_REGION_CLERK) {
      conditions.add("record_scope.STATUS_CODE IN ('RGN','SWI')");
    }
    if (search.ecasId() != null) {
      return;
    }
    switch (grant.role()) {
      case TAPS_ADMIN -> conditions.add("record_scope.STATUS_CODE IN "
          + "('ACC','APP','BUP','CLR','DCL','DFT','FWD','GAS','RCD','RTN','SLD','RGN','SUB','SWI','SCN','VER','DTR','UNC')");
      case TAPS_DISTRICT_APPRAISER -> conditions.add("record_scope.STATUS_CODE IN ('SUB','RCD','RTN','SCN')");
      case TAPS_REGION_APPRAISER -> {
        conditions.add("record_scope.STATUS_CODE IN ('RGN','SWI','SCN','CLR','VER','DTR','SLD','UNC')");
        // LICENCE is projected from the PFU outer join; legacy region queues require that row.
        if (search.bctsFunded() == null) {
          conditions.add("record_scope.LICENCE IS NOT NULL");
        }
        conditions.add("NOT (record_scope.STATUS_CODE IN ('VER','DTR') AND record_scope.SB_FUNDED_IND = 'Y')");
      }
      case TAPS_BCTS, TAPS_BCTS_SUBMITTER ->
          conditions.add("record_scope.STATUS_CODE IN ('DFT','CLR','BUP','VER','DCL')");
      case TAPS_LICENSEE, TAPS_LICENSEE_SUBMITTER ->
          conditions.add("record_scope.STATUS_CODE IN ('DFT','CLR','DCL')");
      default -> { }
    }
    switch (grant.role()) {
      case TAPS_HEADQUARTERS, TAPS_REGION_APPRAISER, TAPS_DISTRICT_APPRAISER -> {
        conditions.add("""
            EXISTS (SELECT 1 FROM ADS_ASSIGNED_TO_USER assigned
                     WHERE assigned.ECAS_ID = record_scope.ECAS_ID AND assigned.USER_ID = ?)
            """.strip());
        parameters.add(user.legacyAccount());
      }
      default -> { }
    }
  }

  String sql() {
    return sql;
  }

  List<String> parameters() {
    return parameters;
  }
}
