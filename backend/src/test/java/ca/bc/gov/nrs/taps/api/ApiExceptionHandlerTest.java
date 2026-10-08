package ca.bc.gov.nrs.taps.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@ExtendWith(OutputCaptureExtension.class)
class ApiExceptionHandlerTest {
  private static final String ROUTE = "/test/ecas/999900000001/attachments";
  private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FailingReads())
      .setControllerAdvice(new ApiExceptionHandler()).build();

  @Test
  void failedReadIsAProblemDetailWithoutDatabaseDetails(CapturedOutput output) throws Exception {
    withDiagnostics(Level.INFO, () -> mvc.perform(get(ROUTE))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(503))
        .andExpect(jsonPath("$.title").value("Service temporarily unavailable"))
        .andExpect(jsonPath("$.detail")
            .value("The requested information is temporarily unavailable. Try again later."))
        .andExpect(jsonPath("$.code").value("READ_UNAVAILABLE"))
        .andExpect(content().string(not(containsString("SECRET_TABLE"))))
        .andExpect(content().string(not(containsString("ORA-00942")))));

    assertThat(output).doesNotContain("event=taps_read_failure", "SECRET_TABLE", "ORA-00942");
  }

  @Test
  void enabledDiagnosticsLogDatabaseCodesButNotMessagesOrIdentifiers(CapturedOutput output) throws Exception {
    withDiagnostics(Level.DEBUG, () -> {
      mvc.perform(get(ROUTE)).andExpect(status().isServiceUnavailable());
      mvc.perform(get("/test/mapping")).andExpect(status().isServiceUnavailable());
    });

    assertThat(output)
        .contains("event=taps_read_failure outcome=unavailable method=GET route=/test/ecas/:id/attachments "
            + "failureType=DataAccessResourceFailureException rootFailureType=SQLException "
            + "sqlState=42000 databaseErrorCode=942")
        .contains("event=taps_read_failure outcome=unavailable method=GET route=/test/mapping "
            + "failureType=IllegalStateException rootFailureType=IllegalStateException "
            + "sqlState=- databaseErrorCode=-")
        .doesNotContain("SECRET_TABLE", "ORA-00942", "unexpected stored value");
    assertThat(output.getAll().lines().filter(line -> line.contains("event=taps_read_failure")))
        .hasSize(2).noneMatch(line -> line.contains("999900000001"));
  }

  private static void withDiagnostics(Level level, ThrowingRunnable requests) throws Exception {
    Logger diagnostics = (Logger) LoggerFactory.getLogger("ca.bc.gov.nrs.taps.audit.failure");
    Level original = diagnostics.getLevel();
    diagnostics.setLevel(level);
    try {
      requests.run();
    } finally {
      diagnostics.setLevel(original);
    }
  }

  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @RestController
  static class FailingReads {
    @GetMapping("/test/ecas/{ecasId}/attachments")
    String attachments(@PathVariable String ecasId) {
      throw new ReadController.ReadUnavailableException(new DataAccessResourceFailureException(
          "SELECT * FROM SECRET_TABLE",
          new SQLException("ORA-00942: table or view does not exist", "42000", 942)));
    }

    @GetMapping("/test/mapping")
    String mapping() {
      throw new ReadController.ReadUnavailableException(new IllegalStateException("unexpected stored value"));
    }
  }
}
