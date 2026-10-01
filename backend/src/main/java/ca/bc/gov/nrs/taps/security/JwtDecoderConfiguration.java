package ca.bc.gov.nrs.taps.security;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

@Configuration
public class JwtDecoderConfiguration {
  @Bean
  JwtDecoder jwtDecoder(
      @Value("${taps.auth.issuer-uri:}") String issuerUri,
      @Value("${taps.auth.client-id:}") String clientId) {
    if (!StringUtils.hasText(issuerUri) || !StringUtils.hasText(clientId)) {
      throw new IllegalStateException("TAPS_OIDC_ISSUER_URI and TAPS_OIDC_CLIENT_ID are required");
    }
    String issuer = issuerUri.replaceAll("/+$", "");
    URI parsedIssuer = URI.create(issuer);
    if (!parsedIssuer.isAbsolute() || parsedIssuer.getHost() == null) {
      throw new IllegalStateException("TAPS_OIDC_ISSUER_URI must be an absolute URL");
    }

    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withJwkSetUri(issuer + "/protocol/openid-connect/certs").build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), clientTokenValidator(clientId)));
    return decoder;
  }

  // TAPS is a public browser client, so every accepted token belongs to an IDIR or Business BCeID
  // sign-in; a token from any other provider in the shared realm is refused.
  static OAuth2TokenValidator<Jwt> clientTokenValidator(String clientId) {
    return jwt -> {
      // Read raw values: getClaimAsString can coerce malformed claims or throw for object values.
      if (jwt.getExpiresAt() != null
          && clientId.equals(jwt.getClaim("azp"))
          && "Bearer".equals(jwt.getClaim("typ"))
          && jwt.getClaim("identity_provider") instanceof String provider
          && IdentityProvider.fromClaim(provider).isPresent()) {
        return OAuth2TokenValidatorResult.success();
      }
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error("invalid_token", "A TAPS user access token is required.", null));
    };
  }
}
