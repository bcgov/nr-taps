package ca.bc.gov.nrs.taps.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.taps.TapsApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports.Binding;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/** Real HTTP, the production token decoder and Oracle, with throwaway keys and data. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OracleReadResilienceIT {
  private static final String CLIENT = "taps-resilience-test";
  @Container
  static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart")
      .withUsername("taps_fixture")
      .withPassword("TapsTest" + UUID.randomUUID().toString().replace("-", "").substring(0, 20))
      .withStartupTimeout(Duration.ofMinutes(4))
      .withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
          new PortBinding(Binding.bindIp("127.0.0.1"), new ExposedPort(1521))));

  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  private HttpServer issuerServer;
  private RSAKey signingKey;
  private String issuer;
  private String base;
  private JdbcTemplate fixtureJdbc;
  private ServletWebServerApplicationContext application;
  private HikariDataSource pool;
  private ObjectMapper mapper;

  @BeforeAll
  void startRealApplication() throws Exception {
    var fixtureSource = new DriverManagerDataSource(ORACLE.getJdbcUrl(), ORACLE.getUsername(), ORACLE.getPassword());
    new ResourceDatabasePopulator(new ClassPathResource("oracle/schema.sql"), new ClassPathResource("oracle/attachments-schema.sql"), new ClassPathResource("oracle/audit-schema.sql")).execute(fixtureSource);
    fixtureJdbc = new JdbcTemplate(fixtureSource);
    restoreClientNameHelper();
    new ResourceDatabasePopulator(new ClassPathResource("oracle/seed.sql"), new ClassPathResource("oracle/attachments-seed.sql"), new ClassPathResource("oracle/audit-seed.sql")).execute(fixtureSource);

    // Signed tokens over a real HTTP listener.
    signingKey = new RSAKeyGenerator(2048).keyID("synthetic-signing-key").generate();
    issuerServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    byte[] jwks = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
    issuerServer.createContext("/oidc/protocol/openid-connect/certs", exchange -> {
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, jwks.length);
      try (var response = exchange.getResponseBody()) { response.write(jwks); }
    });
    issuerServer.start();
    issuer = "http://127.0.0.1:" + issuerServer.getAddress().getPort() + "/oidc";
    // The fixture database is plain TCP, so the TCPS descriptor is replaced as for local runs.
    application = (ServletWebServerApplicationContext) new SpringApplicationBuilder(TapsApplication.class)
        .initializers(context -> TestPropertyValues.of(
            "server.address=127.0.0.1", "server.port=0", "spring.main.banner-mode=off",
            "taps.auth.issuer-uri=" + issuer, "taps.auth.client-id=" + CLIENT,
            "spring.datasource.url=" + ORACLE.getJdbcUrl(),
            "spring.datasource.username=" + ORACLE.getUsername(),
            "spring.datasource.password=" + ORACLE.getPassword(), "KEYSTORE_SECRET=unused",
            "spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.minimum-idle=0",
            "spring.datasource.hikari.connection-timeout=1000", "spring.datasource.hikari.validation-timeout=1000",
            "DATABASE_CONNECT_TIMEOUT_MS=1000", "DATABASE_READ_TIMEOUT_MS=2000",
            "DATABASE_QUERY_TIMEOUT_SECONDS=1")
            .applyTo(context.getEnvironment())).run("--spring.profiles.active=oracle");
    base = "http://127.0.0.1:" + application.getWebServer().getPort();
    pool = application.getBean(HikariDataSource.class);
    mapper = application.getBean(ObjectMapper.class);
    assertThat(application.getBean("jwtDecoder")).isInstanceOf(NimbusJwtDecoder.class);
  }

  @AfterAll
  void stopApplication() {
    if (application != null) application.close();
    if (issuerServer != null) issuerServer.stop(0);
  }

  @Test
  void signedTokensUseTheProductionDecoderAndRecordScope() throws Exception {
    String cariboo = token("TAPS_REGION_APPRAISER_REGION-CARIBOO");
    assertThat(send("/api/me", cariboo, null).statusCode()).isEqualTo(200);
    var page = send("/api/gas/worksheets", cariboo, null);
    assertThat(page.statusCode()).isEqualTo(200);
    assertThat(json(page).get("total").asInt()).isEqualTo(4);
    assertThat(send("/api/gas/worksheets/APPRAISED/102", cariboo, null).statusCode()).isEqualTo(404);
    assertThat(send("/api/me", null, null).statusCode()).isEqualTo(401);
    assertThat(send("/api/me", token("TAPS_ADMIN", Instant.now().minusSeconds(120), CLIENT, issuer, signingKey), null)
        .statusCode()).isEqualTo(401);
    assertThat(send("/api/me", token("TAPS_ADMIN", Instant.now().plusSeconds(300), "other-client", issuer, signingKey), null)
        .statusCode()).isEqualTo(401);
    assertThat(send("/api/me", token("TAPS_ADMIN", Instant.now().plusSeconds(300), CLIENT, issuer + "/other", signingKey), null)
        .statusCode()).isEqualTo(401);
    RSAKey wrongKey = new RSAKeyGenerator(2048).keyID(signingKey.getKeyID()).generate();
    assertThat(send("/api/me", token("TAPS_ADMIN", Instant.now().plusSeconds(300), CLIENT, issuer, wrongKey), null)
        .statusCode()).isEqualTo(401);
  }

  @Test
  void exhaustedPoolReturnsBounded503WithoutWithdrawingTheReplicaAndRecovers() throws Exception {
    String token = token("TAPS_ADMIN");
    try (var first = pool.getConnection(); var second = pool.getConnection()) {
      Instant start = Instant.now();
      assertUnavailable(send("/api/gas/worksheets", token, null));
      assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(5));
      assertHealthyProbes();
    }
    assertThat(send("/api/gas/worksheets", token, null).statusCode()).isEqualTo(200);
  }

  @Test
  void slowOracleStatementIsCancelledAndSubsequentReadRecovers() throws Exception {
    fixtureJdbc.execute("""
        CREATE OR REPLACE FUNCTION SIL_GET_CLIENT_NAME(client_no IN VARCHAR2)
        RETURN VARCHAR2 IS BEGIN
          DBMS_SESSION.SLEEP(5);
          RETURN 'Synthetic delayed client';
        END;
        """);
    try {
      Instant start = Instant.now();
      assertUnavailable(send("/api/ecas/inbox", token("TAPS_ADMIN"), "{\"mode\":\"ALL_SUBMISSIONS\"}"));
      assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(5));
      assertHealthyProbes();
    } finally {
      restoreClientNameHelper();
    }
    assertThat(send("/api/ecas/inbox", token("TAPS_ADMIN"), "{\"mode\":\"ALL_SUBMISSIONS\"}").statusCode()).isEqualTo(200);
  }

  @Test
  void oracleOutageIsBoundedAndRecoversWithoutRestartingTheApplication() throws Exception {
    String token = token("TAPS_ADMIN");
    assertThat(send("/api/gas/worksheets", token, null).statusCode()).isEqualTo(200);
    ORACLE.getDockerClient().pauseContainerCmd(ORACLE.getContainerId()).exec();
    try {
      Instant start = Instant.now();
      assertUnavailable(send("/api/gas/worksheets", token, null));
      assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(8));
      assertHealthyProbes();
      assertThat(send("/actuator/health", null, null).statusCode()).isEqualTo(503);
    } finally {
      ORACLE.getDockerClient().unpauseContainerCmd(ORACLE.getContainerId()).exec();
    }
    for (int attempt = 0; attempt < 10; attempt++) {
      if (send("/api/gas/worksheets", token, null).statusCode() == 200) return;
      Thread.sleep(500);
    }
    throw new AssertionError("Oracle reads did not recover after the disposable database resumed");
  }

  private void assertHealthyProbes() throws Exception {
    assertThat(send("/actuator/health/liveness", null, null).statusCode()).isEqualTo(200);
    assertThat(send("/actuator/health/readiness", null, null).statusCode()).isEqualTo(200);
  }

  private void assertUnavailable(HttpResponse<String> result) throws Exception {
    assertThat(result.statusCode()).isEqualTo(503);
    assertThat(json(result).get("code").asText()).isEqualTo("READ_UNAVAILABLE");
    assertThat(result.body()).doesNotContain("SELECT", "ORA-", "jdbc:", "password", "stackTrace");
  }

  private JsonNode json(HttpResponse<String> result) throws Exception { return mapper.readTree(result.body()); }

  private void restoreClientNameHelper() throws Exception {
    fixtureJdbc.execute(new ClassPathResource("oracle/client-name-function.sql").getContentAsString(StandardCharsets.UTF_8));
  }

  private HttpResponse<String> send(String path, String token, String body) throws Exception {
    var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(12));
    if (token != null) request.header("Authorization", "Bearer " + token);
    if (body != null) request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private String token(String role) throws Exception {
    return token(role, Instant.now().plusSeconds(300), CLIENT, issuer, signingKey);
  }

  private String token(String role, Instant expires, String client, String tokenIssuer, RSAKey key) throws Exception {
    var claims = new JWTClaimsSet.Builder().issuer(tokenIssuer).subject("synthetic-user")
        .audience(client).issueTime(new Date()).expirationTime(Date.from(expires))
        .claim("azp", client).claim("typ", "Bearer").claim("identity_provider", "azureidir")
        .claim("idir_username", "synthetic").claim("client_roles", List.of(role)).build();
    var signed = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
    signed.sign(new RSASSASigner(key));
    return signed.serialize();
  }
}
