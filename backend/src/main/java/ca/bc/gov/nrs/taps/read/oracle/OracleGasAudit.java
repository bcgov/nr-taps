package ca.bc.gov.nrs.taps.read.oracle;

import ca.bc.gov.nrs.taps.domain.LegacyIdentifiers;
import ca.bc.gov.nrs.taps.read.GasAppraisal;
import ca.bc.gov.nrs.taps.read.GasAudit;
import ca.bc.gov.nrs.taps.security.TapsCapability;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** One scoped statement compares snapshots and returns a bounded page of changed fields. */
public final class OracleGasAudit {
  private final JdbcTemplate jdbc;

  public OracleGasAudit(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
  }

  public Optional<GasAudit.HistoryPage> history(TapsUser user, String worksheetId, int page) {
    String id = LegacyIdentifiers.requiredId(worksheetId);
    if (page < 0) throw new IllegalArgumentException("page must be non-negative");
    var key = new GasAppraisal.Key(GasAppraisal.WorksheetType.NON_APPRAISED, id);
    var scope = ReadScopePredicate.forCapability(user, TapsCapability.GAS_APPRAISAL_VIEW);
    return jdbc.query(GasAuditSql.select(scope), statement -> {
      int parameter = 1;
      statement.setLong(parameter++, Long.parseLong(id));
      for (String value : scope.parameters()) statement.setString(parameter++, value);
      long first = (long) page * GasAudit.PAGE_SIZE + 1;
      statement.setLong(parameter++, first);
      statement.setLong(parameter, first + GasAudit.PAGE_SIZE - 1);
    }, rows -> {
      var items = new ArrayList<GasAudit.Item>();
      long total = 0;
      boolean parent = false;
      while (rows.next()) {
        long parentCount = rows.getLong("PARENT_COUNT");
        if (parentCount > 1) throw new DataIntegrityViolationException("ambiguous history parent");
        parent = parentCount == 1;
        total = rows.getLong("TOTAL");
        String eventId = rows.getString("EVENT_ID");
        if (eventId != null) {
          if (!id.equals(rows.getString("WORKSHEET_ID"))) {
            throw new DataIntegrityViolationException("history row does not match requested worksheet");
          }
          items.add(item(rows, eventId));
        }
      }
      if (!parent) {
        if (!items.isEmpty() || total != 0) {
          throw new DataIntegrityViolationException("history rows have no scoped parent");
        }
        return Optional.empty();
      }
      return Optional.of(new GasAudit.HistoryPage(key, items, total, page, GasAudit.PAGE_SIZE));
    });
  }

  private static GasAudit.Item item(ResultSet row, String eventId) throws SQLException {
    Timestamp timestamp = row.getTimestamp("EVENT_DATE");
    if (timestamp == null) throw new DataIntegrityViolationException("history timestamp is missing");
    return new GasAudit.Item(eventId, row.getString("RATE_ID"),
        row.getString("USER_ID"), timestamp.toLocalDateTime(), row.getString("ATTRIBUTE"),
        row.getString("CHANGED_VALUE"), row.getString("COMMENT_TEXT"));
  }
}
