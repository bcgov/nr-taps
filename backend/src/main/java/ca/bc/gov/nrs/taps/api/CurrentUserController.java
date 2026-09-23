package ca.bc.gov.nrs.taps.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CurrentUserController {
  @GetMapping("/api/me")
  public CurrentUser me(@AuthenticationPrincipal Jwt jwt) {
    String name = jwt.getClaimAsString("name");
    return new CurrentUser(jwt.getSubject(), name == null ? jwt.getSubject() : name);
  }

  public record CurrentUser(String subject, String name) {}
}
