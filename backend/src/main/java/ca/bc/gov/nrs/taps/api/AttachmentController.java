package ca.bc.gov.nrs.taps.api;

import ca.bc.gov.nrs.taps.domain.LegacyIdentifiers;
import ca.bc.gov.nrs.taps.read.EcasAttachments;
import ca.bc.gov.nrs.taps.read.oracle.OracleEcasAttachments;
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
public class AttachmentController {
  private static final Logger LOG = LoggerFactory.getLogger(AttachmentController.class);
  private final OracleEcasAttachments attachments;

  public AttachmentController(OracleEcasAttachments attachments) {
    this.attachments = attachments;
  }

  @GetMapping("/api/ecas/{ecasId}/attachments")
  @PreAuthorize("hasAuthority('ECAS_SUBMISSION_VIEW')")
  public EcasAttachments.Page attachments(@AuthenticationPrincipal TapsUser user,
      @PathVariable String ecasId, @RequestParam(defaultValue = "0") int page) {
    String id = LegacyIdentifiers.requiredId(ecasId);
    EcasAttachments.validatePage(page);
    try {
      return attachments.forSubmission(user, id, page).orElseThrow(ReadController.ReadNotFoundException::new);
    } catch (DataAccessException | IllegalArgumentException | IllegalStateException exception) {
      LOG.warn("event=taps_attachment_read_failed failureType={}", exception.getClass().getSimpleName());
      throw new ReadController.ReadUnavailableException(exception);
    }
  }
}
