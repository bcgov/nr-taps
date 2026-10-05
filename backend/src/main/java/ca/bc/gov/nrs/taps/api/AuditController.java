package ca.bc.gov.nrs.taps.api;

import ca.bc.gov.nrs.taps.domain.LegacyIdentifiers;
import ca.bc.gov.nrs.taps.read.EcasAudit;
import ca.bc.gov.nrs.taps.read.oracle.OracleEcasAudit;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "taps.oracle.enabled", havingValue = "true")
@PreAuthorize("hasAuthority('ECAS_SUBMISSION_VIEW')")
public class AuditController {
  private static final Logger LOG = LoggerFactory.getLogger(AuditController.class);
  private final OracleEcasAudit audit;

  public AuditController(OracleEcasAudit audit) { this.audit = audit; }

  @GetMapping("/api/ecas/audit/{ecasId}")
  public EcasAudit.HistoryPage history(@AuthenticationPrincipal TapsUser user,
      @PathVariable String ecasId, @RequestParam(defaultValue = "0") int page) {
    String id = LegacyIdentifiers.requiredId(ecasId);
    requirePage(page);
    return read(() -> audit.history(user, id, page));
  }

  @GetMapping("/api/ecas/audit/{ecasId}/events/{eventId}")
  public EcasAudit.DetailPage details(@AuthenticationPrincipal TapsUser user,
      @PathVariable String ecasId, @PathVariable String eventId,
      @RequestParam(defaultValue = "0") int page) {
    String id = LegacyIdentifiers.requiredId(ecasId);
    String eventKey = LegacyIdentifiers.requiredId(eventId);
    requirePage(page);
    return read(() -> audit.details(user, id, eventKey, page));
  }

  private static void requirePage(int page) {
    if (page < 0) throw new IllegalArgumentException("page must be non-negative");
  }

  private static <T> T read(Supplier<Optional<T>> operation) {
    try {
      return operation.get().orElseThrow(ReadController.ReadNotFoundException::new);
    } catch (DataAccessException | IllegalArgumentException | IllegalStateException exception) {
      LOG.warn("event=taps_oracle_audit_failed failureType={}", exception.getClass().getSimpleName());
      throw new ReadController.ReadUnavailableException();
    }
  }
}
