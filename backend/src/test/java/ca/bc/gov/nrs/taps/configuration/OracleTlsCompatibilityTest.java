package ca.bc.gov.nrs.taps.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OracleTlsCompatibilityTest {

  private static final String JAVA_21_0_12_DEFAULTS =
      "SSLv3, TLSv1, TLSv1.1, DTLSv1.0, RC4, DES, MD5withRSA, DH keySize < 1024, "
          + "EC keySize < 224, 3DES_EDE_CBC, anon, NULL, ECDH, TLS_RSA_*, "
          + "rsa_pkcs1_sha1 usage HandshakeSignature, ecdsa_sha1 usage HandshakeSignature, "
          + "dsa_sha1 usage HandshakeSignature";

  @Test
  void shouldReenableOnlyRsaKeyExchange() {
    assertThat(OracleTlsCompatibility.withoutRsaKeyExchange(JAVA_21_0_12_DEFAULTS))
        .isEqualTo(
            "SSLv3, TLSv1, TLSv1.1, DTLSv1.0, RC4, DES, MD5withRSA, DH keySize < 1024, "
                + "EC keySize < 224, 3DES_EDE_CBC, anon, NULL, ECDH, "
                + "rsa_pkcs1_sha1 usage HandshakeSignature, ecdsa_sha1 usage HandshakeSignature, "
                + "dsa_sha1 usage HandshakeSignature");
  }

  @Test
  void shouldLeaveListsWithoutRsaKeyExchangeUnchanged() {
    assertThat(OracleTlsCompatibility.withoutRsaKeyExchange("SSLv3, TLSv1, RC4"))
        .isEqualTo("SSLv3, TLSv1, RC4");
  }
}
