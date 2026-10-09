package ca.bc.gov.nrs.taps.api;

import ca.bc.gov.nrs.taps.domain.LegacyIdentifiers;
import ca.bc.gov.nrs.taps.read.GasAudit;
import ca.bc.gov.nrs.taps.read.oracle.OracleGasAudit;
import ca.bc.gov.nrs.taps.security.TapsUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("oracle")
@PreAuthorize("hasAuthority('GAS_APPRAISAL_VIEW')")
public class GasAuditController {
  private static final Logger LOG = LoggerFactory.getLogger(GasAuditController.class);
  private final OracleGasAudit audit;

  public GasAuditController(OracleGasAudit audit) { this.audit = audit; }

  @GetMapping("/api/gas/worksheets/NON_APPRAISED/{worksheetId}/history")
  public GasAudit.HistoryPage history(@AuthenticationPrincipal TapsUser user,
      @PathVariable String worksheetId, @RequestParam(defaultValue = "0") int page) {
    String id = LegacyIdentifiers.requiredId(worksheetId);
    if (page < 0) throw new IllegalArgumentException("page must be non-negative");
    try {
      return audit.history(user, id, page).orElseThrow(ReadController.ReadNotFoundException::new);
    } catch (DataAccessException | IllegalArgumentException | IllegalStateException exception) {
      LOG.warn("event=taps_oracle_gas_history_failed failureType={}", exception.getClass().getSimpleName());
      throw new ReadController.ReadUnavailableException(exception);
    }
  }
}
