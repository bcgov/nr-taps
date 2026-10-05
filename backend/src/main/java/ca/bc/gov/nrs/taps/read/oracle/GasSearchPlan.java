package ca.bc.gov.nrs.taps.read.oracle;

import ca.bc.gov.nrs.taps.read.GasAppraisal;
import ca.bc.gov.nrs.taps.security.TapsCapability;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Shared filter and paging for the GAS searches. The {@code record_scope} projection must provide the
 * ownership columns plus STATUS_CODE, LICENSE and TIMBER_MARK.
 */
public final class GasSearchPlan {
  // Statuses the GAS2 appraisal search leaves out.
  private static final List<String> EXCLUDED_STATUSES =
      List.of(
          "ACC", "APP", "CLR", "CPC", "DCL", "DFT", "EE", "FWD", "LFS", "GAS", "NAP", "NBS",
          "RCD", "RGN", "RTN", "SCN", "SEC", "SWI", "SUB");

  private final String sql;
  private final List<String> parameters;
  private final long firstRow;
  private final long lastRow;

  private GasSearchPlan(String sql, List<String> parameters, long firstRow, long lastRow) {
    this.sql = sql;
    this.parameters = List.copyOf(parameters);
    this.firstRow = firstRow;
    this.lastRow = lastRow;
  }

  public static GasSearchPlan forUser(TapsUser user, GasAppraisal.Search search) {
    Objects.requireNonNull(search, "search");
    ReadScopePredicate scope =
        ReadScopePredicate.forCapability(user, TapsCapability.GAS_APPRAISAL_VIEW);
    List<String> predicates = new ArrayList<>();
    List<String> parameters = new ArrayList<>(scope.parameters());
    predicates.add(scope.sql());
    predicates.add(
        "record_scope.STATUS_CODE NOT IN ("
            + String.join(", ", Collections.nCopies(EXCLUDED_STATUSES.size(), "?"))
            + ")");
    parameters.addAll(EXCLUDED_STATUSES);
    if (search.licence() != null) {
      predicates.add("record_scope.LICENSE = ?");
      parameters.add(search.licence());
    }
    if (search.timberMark() != null) {
      predicates.add("record_scope.TIMBER_MARK = ?");
      parameters.add(search.timberMark());
    }
    long firstRow = (long) search.page() * GasAppraisal.PAGE_SIZE + 1;
    return new GasSearchPlan(
        "(" + String.join(" AND ", predicates) + ")",
        parameters,
        firstRow,
        firstRow + GasAppraisal.PAGE_SIZE - 1);
  }

  /** Use this same parenthesized predicate for both list and count before paging. */
  public String sql() {
    return sql;
  }

  /** Ordered prepared-statement values for {@link #sql()}, excluding row bounds. */
  public List<String> parameters() {
    return parameters;
  }

  public long firstRow() {
    return firstRow;
  }

  public long lastRow() {
    return lastRow;
  }
}
