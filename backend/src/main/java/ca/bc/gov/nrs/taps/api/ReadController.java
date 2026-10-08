package ca.bc.gov.nrs.taps.api;

import ca.bc.gov.nrs.taps.domain.AppraisalMethod;
import ca.bc.gov.nrs.taps.domain.LegacyIdentifiers;
import ca.bc.gov.nrs.taps.read.EcasInbox;
import ca.bc.gov.nrs.taps.read.GasAppraisal;
import ca.bc.gov.nrs.taps.read.oracle.EcasInboxPlan;
import ca.bc.gov.nrs.taps.read.oracle.OracleAppraisedSummary;
import ca.bc.gov.nrs.taps.read.oracle.OracleEcasInbox;
import ca.bc.gov.nrs.taps.read.oracle.OracleEcasReference;
import ca.bc.gov.nrs.taps.read.oracle.OracleFtaLicenceInformation;
import ca.bc.gov.nrs.taps.read.oracle.OracleGasSearch;
import ca.bc.gov.nrs.taps.read.oracle.OracleLicenceMarks;
import ca.bc.gov.nrs.taps.read.oracle.OracleOtherWorksheetSummary;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Routes check the capability; each reader also limits records to the caller's scope. */
@RestController
@Profile("oracle")
public class ReadController {
  private static final Logger LOG = LoggerFactory.getLogger(ReadController.class);
  private final OracleEcasInbox inbox;
  private final OracleEcasReference references;
  private final OracleGasSearch worksheets;
  private final OracleAppraisedSummary appraised;
  private final OracleOtherWorksheetSummary other;
  private final OracleLicenceMarks marks;
  private final OracleFtaLicenceInformation information;

  public ReadController(OracleEcasInbox inbox, OracleEcasReference references,
      OracleGasSearch worksheets, OracleAppraisedSummary appraised,
      OracleOtherWorksheetSummary other, OracleLicenceMarks marks,
      OracleFtaLicenceInformation information) {
    this.inbox = inbox;
    this.references = references;
    this.worksheets = worksheets;
    this.appraised = appraised;
    this.other = other;
    this.marks = marks;
    this.information = information;
  }

  @PostMapping("/api/ecas/inbox")
  @PreAuthorize("hasAuthority('ECAS_SUBMISSION_VIEW')")
  public EcasInbox.Page inbox(@AuthenticationPrincipal TapsUser user,
      @RequestBody EcasInbox.Search search, @RequestParam(defaultValue = "0") int page) {
    if (page < 0 || search.orgUnitNumbers().size() > 100 || search.statusCodes().size() > 100) {
      throw new IllegalArgumentException("invalid search bounds");
    }
    if (search.mode() == EcasInbox.Mode.MY_TO_DO && search.ecasId() == null) {
      throw new IllegalArgumentException("assignment search is not available");
    }
    EcasInboxPlan.forUser(user, search, page);
    return read(() -> inbox.search(user, search, page));
  }

  @GetMapping("/api/ecas/references/{method}/{ecasId}")
  @PreAuthorize("hasAuthority('ECAS_SUBMISSION_VIEW')")
  public Object reference(@AuthenticationPrincipal TapsUser user,
      @PathVariable AppraisalMethod method, @PathVariable String ecasId) {
    String id = LegacyIdentifiers.requiredId(ecasId);
    return read(() -> switch (method) {
      case C -> found(references.coast(user, id));
      case I -> found(references.interior(user, id));
    });
  }

  @GetMapping("/api/gas/worksheets")
  @PreAuthorize("hasAuthority('GAS_APPRAISAL_VIEW')")
  public GasAppraisal.Page worksheets(@AuthenticationPrincipal TapsUser user,
      @RequestParam(required = false) String licence,
      @RequestParam(required = false) String timberMark,
      @RequestParam(defaultValue = "0") int page) {
    GasAppraisal.Search search = new GasAppraisal.Search(licence, timberMark, page);
    return read(() -> worksheets.search(user, search));
  }

  @GetMapping("/api/gas/worksheets/{type}/{worksheetId}")
  @PreAuthorize("hasAuthority('GAS_APPRAISAL_VIEW')")
  public Object summary(@AuthenticationPrincipal TapsUser user,
      @PathVariable GasAppraisal.WorksheetType type, @PathVariable String worksheetId) {
    GasAppraisal.Key key = new GasAppraisal.Key(type, worksheetId);
    return read(() -> switch (type) {
      case APPRAISED -> found(appraised.byTypedKey(user, key));
      case HISTORIC -> found(other.historic(user, key));
      case NON_APPRAISED -> found(other.nonAppraised(user, key));
    });
  }

  @GetMapping("/api/gas/appraised/by-ecas/{ecasId}")
  @PreAuthorize("hasAuthority('GAS_APPRAISAL_VIEW')")
  public GasAppraisal.AppraisedSummary byEcas(@AuthenticationPrincipal TapsUser user,
      @PathVariable String ecasId) {
    String id = LegacyIdentifiers.requiredId(ecasId);
    return read(() -> found(appraised.byEcasId(user, id)));
  }

  @GetMapping("/api/gas/licences/{licence}/marks")
  @PreAuthorize("hasAuthority('GAS_APPRAISAL_VIEW')")
  public GasAppraisal.LicenceMarks marks(@AuthenticationPrincipal TapsUser user,
      @PathVariable String licence) {
    GasAppraisal.Search search = new GasAppraisal.Search(licence, null, 0);
    if (search.licence() == null) throw new IllegalArgumentException("licence is required");
    return read(() -> marks.forLicence(user, search.licence()));
  }

  @GetMapping("/api/gas/licence-information")
  @PreAuthorize("hasAuthority('GAS_APPRAISAL_VIEW')")
  public GasAppraisal.FtaLicenceInformation information(@AuthenticationPrincipal TapsUser user,
      @RequestParam(required = false) String licence, @RequestParam String timberMark) {
    GasAppraisal.Search search = new GasAppraisal.Search(licence, timberMark, 0);
    if (search.timberMark() == null) throw new IllegalArgumentException("timberMark is required");
    return read(() -> found(information.find(user, search.licence(), search.timberMark())));
  }

  private static <T> T found(Optional<T> result) {
    return result.orElseThrow(ReadNotFoundException::new);
  }

  private static <T> T read(Supplier<T> operation) {
    try {
      return operation.get();
    } catch (DataAccessException | IllegalArgumentException | IllegalStateException exception) {
      // A mapping error means unexpected data in the database, not a bad request.
      LOG.warn("event=taps_oracle_read_failed failureType={}", exception.getClass().getSimpleName());
      throw new ReadUnavailableException(exception);
    }
  }

  static class ReadNotFoundException extends RuntimeException {}

  /** Keeps the failure for diagnostics without copying its message, which can hold SQL. */
  static class ReadUnavailableException extends RuntimeException {
    ReadUnavailableException(Throwable cause) {
      super(null, cause, false, false);
    }
  }
}
