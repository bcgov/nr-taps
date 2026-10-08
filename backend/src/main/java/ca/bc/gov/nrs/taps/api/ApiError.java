package ca.bc.gov.nrs.taps.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/** Error responses are problem details; the {@code code} property is the stable value clients match. */
public enum ApiError {
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request", "Check the request fields and try again."),
  AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication required", "Sign in to continue."),
  ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied", "You do not have access to this operation."),
  NOT_FOUND(HttpStatus.NOT_FOUND, "Record not found", "The requested record was not found."),
  READ_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service temporarily unavailable",
      "The requested information is temporarily unavailable. Try again later.");

  private final HttpStatus status;
  private final String title;
  private final String detail;

  ApiError(HttpStatus status, String title, String detail) {
    this.status = status;
    this.title = title;
    this.detail = detail;
  }

  public ProblemDetail problem() {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    problem.setProperty("code", name());
    return problem;
  }

  public ResponseEntity<ProblemDetail> response() {
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem());
  }

  /** For responses written outside MVC, such as the security filter chain's 401 and 403. */
  public void write(HttpServletResponse response, ObjectMapper mapper) throws IOException {
    response.setStatus(status.value());
    if (status == HttpStatus.UNAUTHORIZED) response.setHeader("WWW-Authenticate", "Bearer");
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    mapper.writeValue(response.getOutputStream(), problem());
  }
}
