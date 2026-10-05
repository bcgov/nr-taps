package ca.bc.gov.nrs.taps.api;

import ca.bc.gov.nrs.taps.read.CodeOption;
import ca.bc.gov.nrs.taps.read.DatedCodeOption;
import ca.bc.gov.nrs.taps.read.EcasStatusCode;
import ca.bc.gov.nrs.taps.read.EffectiveCode;
import ca.bc.gov.nrs.taps.read.oracle.OracleCodeLists;
import ca.bc.gov.nrs.taps.read.oracle.OracleEcasOrganizations;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Search choices only; these lists don't grant access to records. */
@RestController
@ConditionalOnProperty(name = "taps.oracle.enabled", havingValue = "true")
public class LookupController {
  private static final Logger LOG = LoggerFactory.getLogger(LookupController.class);
  private final OracleCodeLists codes;
  private final OracleEcasOrganizations organizations;

  public LookupController(OracleCodeLists codes, OracleEcasOrganizations organizations) {
    this.codes = codes;
    this.organizations = organizations;
  }

  public record EcasLookups(List<CodeOption> appraisalMethods, List<EcasStatusCode> appraisalStatuses,
      List<DatedCodeOption> appraisalCategories, List<DatedCodeOption> reappraisalReasons,
      List<DatedCodeOption> fileTypes, List<CodeOption> organizations) {
    public EcasLookups {
      appraisalMethods = List.copyOf(appraisalMethods);
      appraisalStatuses = List.copyOf(appraisalStatuses);
      appraisalCategories = List.copyOf(appraisalCategories);
      reappraisalReasons = List.copyOf(reappraisalReasons);
      fileTypes = List.copyOf(fileTypes);
      organizations = List.copyOf(organizations);
    }
  }

  public record GasLookups(List<EffectiveCode> appraisalMethods, List<EffectiveCode> appraisalStatuses,
      List<EffectiveCode> rateAdjustmentTypes) {
    public GasLookups {
      appraisalMethods = List.copyOf(appraisalMethods);
      appraisalStatuses = List.copyOf(appraisalStatuses);
      rateAdjustmentTypes = List.copyOf(rateAdjustmentTypes);
    }
  }

  @GetMapping("/api/ecas/lookups")
  @PreAuthorize("hasAuthority('ECAS_SUBMISSION_VIEW')")
  public EcasLookups ecas(@AuthenticationPrincipal TapsUser user) {
    try {
      return new EcasLookups(List.of(new CodeOption("C", "Coast"), new CodeOption("I", "Interior")),
          codes.ecasAppraisalStatuses(), codes.ecasAppraisalCategories(), codes.ecasReappraisalReasons(),
          codes.ecasFileTypes(), organizations.forUser(user));
    } catch (DataAccessException exception) {
      throw unavailable(exception);
    }
  }

  @GetMapping("/api/gas/lookups")
  @PreAuthorize("hasAuthority('GAS_APPRAISAL_VIEW')")
  public GasLookups gas() {
    try {
      return new GasLookups(codes.appraisalMethods(), codes.appraisalStatuses(), codes.rateAdjustmentTypes());
    } catch (DataAccessException exception) {
      throw unavailable(exception);
    }
  }

  private static ReadController.ReadUnavailableException unavailable(DataAccessException exception) {
    LOG.warn("event=taps_oracle_lookup_failed failureType={}", exception.getClass().getSimpleName());
    return new ReadController.ReadUnavailableException();
  }
}
