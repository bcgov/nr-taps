package ca.bc.gov.nrs.taps.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTProcessor;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
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
  private static final int JWKS_CONNECT_TIMEOUT_MILLIS = (int) Duration.ofSeconds(10).toMillis();
  private static final int JWKS_READ_TIMEOUT_MILLIS = (int) Duration.ofSeconds(15).toMillis();
  private static final int JWKS_SIZE_LIMIT_BYTES = 50 * 1024;

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
        new NimbusJwtDecoder(jwtProcessor(issuer + "/protocol/openid-connect/certs"));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), clientTokenValidator(clientId)));
    return decoder;
  }

  // Refresh SSO keys ahead of expiry with bounded, retried fetches so a slow key fetch can't 401 a
  // request. Claims are left to the token validators.
  private static JWTProcessor<SecurityContext> jwtProcessor(String jwkSetUri) {
    URL jwkSetUrl;
    try {
      jwkSetUrl = URI.create(jwkSetUri).toURL();
    } catch (IllegalArgumentException | MalformedURLException exception) {
      throw new IllegalStateException("TAPS_OIDC_ISSUER_URI must be an absolute URL", exception);
    }
    JWKSource<SecurityContext> jwkSource =
        JWKSourceBuilder.create(
                jwkSetUrl,
                new DefaultResourceRetriever(
                    JWKS_CONNECT_TIMEOUT_MILLIS, JWKS_READ_TIMEOUT_MILLIS, JWKS_SIZE_LIMIT_BYTES))
            .retrying(true)
            .refreshAheadCache(true)
            .build();
    DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
    processor.setJWTClaimsSetVerifier((claims, context) -> {});
    return processor;
  }

  static OAuth2TokenValidator<Jwt> clientTokenValidator(String clientId) {
    return jwt -> {
      // Do not coerce malformed claim types.
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
