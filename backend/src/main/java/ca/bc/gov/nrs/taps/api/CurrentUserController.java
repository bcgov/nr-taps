package ca.bc.gov.nrs.taps.api;

import ca.bc.gov.nrs.taps.configuration.OracleActivation;
import ca.bc.gov.nrs.taps.security.FamRoleName;
import ca.bc.gov.nrs.taps.security.IdentityProvider;
import ca.bc.gov.nrs.taps.security.TapsCapability;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CurrentUserController {
  private final boolean readApiEnabled;

  public CurrentUserController(Environment environment) {
    this.readApiEnabled = OracleActivation.enabled(environment);
  }

  @GetMapping("/api/me")
  public CurrentUser me(@AuthenticationPrincipal TapsUser user) {
    return new CurrentUser(
        user.userId(),
        user.displayName(),
        user.email(),
        user.identityProvider(),
        user.businessName(),
        user.grants().stream()
            .map(
                grant ->
                    new Grant(
                        grant.role().name(),
                        grant.scope() == null ? List.of() : List.of(grant.scope())))
            .toList(),
        user.capabilities().stream().sorted().toList(),
        user.forestClients(),
        readApiEnabled);
  }

  public record CurrentUser(
      String userId,
      String displayName,
      String email,
      IdentityProvider identityProvider,
      String businessName,
      List<Grant> roles,
      List<TapsCapability> capabilities,
      List<String> forestClients,
      boolean readApiEnabled) {}

  public record Grant(String role, List<FamRoleName.Scope> scopes) {}
}
