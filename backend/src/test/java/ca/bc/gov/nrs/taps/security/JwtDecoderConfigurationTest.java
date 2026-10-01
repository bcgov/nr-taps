package ca.bc.gov.nrs.taps.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtDecoderConfigurationTest {
  @Test
  void missingFamConfigurationStopsStartup() {
    assertThatThrownBy(() -> new JwtDecoderConfiguration().jwtDecoder("", ""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TAPS_OIDC_ISSUER_URI");
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
