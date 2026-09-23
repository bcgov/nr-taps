package ca.bc.gov.nrs.taps.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
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
    Jwt valid = token("taps-client", "Bearer");
    assertThat(JwtDecoderConfiguration.clientTokenValidator("taps-client").validate(valid).hasErrors())
        .isFalse();
    assertThat(
            JwtDecoderConfiguration.clientTokenValidator("taps-client")
                .validate(token("other-client", "Bearer"))
                .hasErrors())
        .isTrue();
    assertThat(
            JwtDecoderConfiguration.clientTokenValidator("taps-client")
                .validate(token("taps-client", "ID"))
                .hasErrors())
        .isTrue();
  }

  private Jwt token(String clientId, String type) {
    Instant now = Instant.now();
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject("user-123")
        .issuedAt(now)
        .expiresAt(now.plusSeconds(300))
        .claim("azp", clientId)
        .claim("typ", type)
        .build();
  }
}
