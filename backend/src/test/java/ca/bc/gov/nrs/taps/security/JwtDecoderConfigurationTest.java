package ca.bc.gov.nrs.taps.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class JwtDecoderConfigurationTest {
  @Test
  void missingFamConfigurationStopsStartup() {
    assertThatThrownBy(() -> new JwtDecoderConfiguration().jwtDecoder("", ""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TAPS_OIDC_ISSUER_URI");
    assertThatThrownBy(() -> new JwtDecoderConfiguration().jwtDecoder("realms/taps", "taps"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("absolute URL");
  }

  @Test
  void signingKeyFetchSurvivesOneTransientSsoFailure() throws Exception {
    RSAKey key = new RSAKeyGenerator(2048).keyID("sso-key").generate();
    byte[] jwks = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
    AtomicInteger requests = new AtomicInteger();
    HttpServer sso = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    sso.createContext("/", exchange -> {
      if (requests.incrementAndGet() == 1) {
        exchange.sendResponseHeaders(503, -1);
        exchange.close();
        return;
      }
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, jwks.length);
      try (var response = exchange.getResponseBody()) {
        response.write(jwks);
      }
    });
    sso.start();
    try {
      String issuer = "http://127.0.0.1:" + sso.getAddress().getPort() + "/realms/standard";
      JwtDecoder decoder = new JwtDecoderConfiguration().jwtDecoder(issuer + "/", "taps-client");
      SignedJWT token = new SignedJWT(
          new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("sso-key").build(),
          new JWTClaimsSet.Builder()
              .issuer(issuer)
              .subject("user-123")
              .expirationTime(Date.from(Instant.now().plusSeconds(300)))
              .claim("azp", "taps-client")
              .claim("typ", "Bearer")
              .claim("identity_provider", "azureidir")
              .build());
      token.sign(new RSASSASigner(key));

      // Spring's default decoder rejects this token outright when the key fetch fails.
      assertThat(decoder.decode(token.serialize()).getSubject()).isEqualTo("user-123");
      assertThat(requests).hasValue(2);
    } finally {
      sso.stop(0);
    }
  }

  @Test
  void onlyAccessTokensForTheConfiguredClientPass() {
    Jwt valid = token("taps-client", "Bearer", "azureidir");
    assertThat(JwtDecoderConfiguration.clientTokenValidator("taps-client").validate(valid).hasErrors())
        .isFalse();
    assertThat(
            JwtDecoderConfiguration.clientTokenValidator("taps-client")
                .validate(token("other-client", "Bearer", "azureidir"))
                .hasErrors())
        .isTrue();
    assertThat(
            JwtDecoderConfiguration.clientTokenValidator("taps-client")
                .validate(token("taps-client", "ID", "azureidir"))
                .hasErrors())
        .isTrue();
  }

  @Test
  void onlyIdirAndBusinessBceidSignInsPass() {
    assertThat(
            JwtDecoderConfiguration.clientTokenValidator("taps-client")
                .validate(token("taps-client", "Bearer", "bceidbusiness"))
                .hasErrors())
        .isFalse();
    for (String provider :
        new String[] {
          "bceidbasic", "bceidboth", "bceidpersonal", "githubpublic", "service-account",
          "AzureIDIR", " azureidir ", null
        }) {
      assertThat(
              JwtDecoderConfiguration.clientTokenValidator("taps-client")
                  .validate(token("taps-client", "Bearer", provider))
                  .hasErrors())
          .as(String.valueOf(provider))
          .isTrue();
    }
  }

  @Test
  void malformedClaimTypesFailValidationWithoutThrowing() {
    for (String claim : List.of("azp", "typ", "identity_provider")) {
      for (Object malformed : List.of(123, List.of("taps-client"), Map.of("name", "azureidir"))) {
        Jwt token =
            Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("azp", "taps-client")
                .claim("typ", "Bearer")
                .claim("identity_provider", "azureidir")
                .claim(claim, malformed)
                .build();

        assertThat(
                JwtDecoderConfiguration.clientTokenValidator("taps-client")
                    .validate(token)
                    .hasErrors())
            .as(claim + " with malformed value")
            .isTrue();
      }
    }
  }

  @Test
  void anAccessTokenMustHaveAnExpiration() {
    Jwt token =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("user-123")
            .claim("azp", "taps-client")
            .claim("typ", "Bearer")
            .claim("identity_provider", "azureidir")
            .build();

    assertThat(JwtDecoderConfiguration.clientTokenValidator("taps-client").validate(token).hasErrors())
        .isTrue();
  }

  private Jwt token(String clientId, String type, String identityProvider) {
    Instant now = Instant.now();
    Jwt.Builder token =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("user-123")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .claim("azp", clientId)
            .claim("typ", type);
    if (identityProvider != null) {
      token.claim("identity_provider", identityProvider);
    }
    return token.build();
  }
}
