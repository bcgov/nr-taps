package ca.bc.gov.nrs.taps.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

@Component
public class TapsAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  private final String clientId;

  public TapsAuthenticationConverter(@Value("${taps.auth.client-id:}") String clientId) {
    this.clientId = clientId;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    TapsUser user = toUser(jwt);
    return new TapsAuthentication(
        jwt,
        user,
        user.capabilities().stream()
            .map(capability -> new SimpleGrantedAuthority(capability.authority()))
            .toList());
  }

  TapsUser toUser(Jwt jwt) {
    // Recheck client claims if another decoder is supplied.
    if (JwtDecoderConfiguration.clientTokenValidator(clientId).validate(jwt).hasErrors()) {
      throw new InvalidBearerTokenException("A TAPS user access token is required");
    }
    IdentityProvider provider =
        IdentityProvider.fromClaim((String) jwt.getClaim("identity_provider"))
            .orElseThrow(() -> new InvalidBearerTokenException("Unsupported identity provider"));
    List<RoleGrant> grants = new ArrayList<>();
    for (String roleName : roleNames(jwt)) {
      if (!FamRoleName.isSidecar(roleName)) {
        RoleGrant.accept(FamRoleName.parse(roleName), provider)
            .filter(grant -> !grants.contains(grant))
            .ifPresent(grants::add);
      }
    }
    String userId = userId(jwt, provider);
    return new TapsUser(
        userId,
        firstText(jwt, "display_name", "name").orElse(userId),
        firstText(jwt, "email").orElse(null),
        provider,
        provider == IdentityProvider.BCEID_BUSINESS
            ? firstText(jwt, "bceid_business_name").orElse(null)
            : null,
        grants);
  }

  private List<String> roleNames(Jwt jwt) {
    if (jwt.hasClaim("client_roles")) {
      // Malformed CSS roles must grant nothing, without falling back to another mapper.
      return strings(jwt.getClaim("client_roles"));
    }
    if (jwt.getClaim("resource_access") instanceof Map<?, ?> byClient
        && byClient.get(clientId) instanceof Map<?, ?> client) {
      return strings(client.get("roles"));
    }
    return List.of();
  }

  private static List<String> strings(Object claim) {
    return claim instanceof Collection<?> values
        ? values.stream().filter(String.class::isInstance).map(String.class::cast).toList()
        : List.of();
  }

  /** Preserve legacy audit identities: IDIR\USER or BCEID\USER. */
  private static String userId(Jwt jwt, IdentityProvider provider) {
    String account =
        firstText(jwt, provider.usernameClaim())
            .or(() -> firstText(jwt, provider.guidClaim()))
            .orElseThrow(() -> new InvalidBearerTokenException("The token has no user identity"));
    return provider.auditPrefix() + "\\" + account.toUpperCase(Locale.ROOT);
  }

  private static Optional<String> firstText(Jwt jwt, String... claims) {
    for (String claim : claims) {
      if (jwt.getClaim(claim) instanceof String value && !value.isBlank()) {
        return Optional.of(value.trim());
      }
    }
    return Optional.empty();
  }
}
