package ca.bc.gov.nrs.taps.api;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger FAILURE_DIAGNOSTICS = LoggerFactory.getLogger("ca.bc.gov.nrs.taps.audit.failure");
  private static final int MAX_LOG_VALUE_LENGTH = 80;

  @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
      MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
  ResponseEntity<ProblemDetail> invalidInput(Exception exception) {
    return ApiError.INVALID_REQUEST.response();
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> denied() {
    return ApiError.ACCESS_DENIED.response();
  }

  @ExceptionHandler(ReadController.ReadNotFoundException.class)
  ResponseEntity<ProblemDetail> notFound() {
    return ApiError.NOT_FOUND.response();
  }

  @ExceptionHandler(ReadController.ReadUnavailableException.class)
  ResponseEntity<ProblemDetail> unavailable(ReadController.ReadUnavailableException exception,
      HttpServletRequest request) {
    logFailureDiagnostics(request, exception.getCause());
    return ApiError.READ_UNAVAILABLE.response();
  }

  // Database codes only; messages can carry SQL or connection details.
  private static void logFailureDiagnostics(HttpServletRequest request, Throwable failure) {
    if (!FAILURE_DIAGNOSTICS.isDebugEnabled()) return;
    SQLException sqlException = sqlException(failure);
    FAILURE_DIAGNOSTICS.debug(
        "event=taps_read_failure outcome=unavailable method={} route={} failureType={} "
            + "rootFailureType={} sqlState={} databaseErrorCode={}",
        safe(request.getMethod()), route(request), type(failure), type(rootCause(failure)),
        sqlException == null ? "-" : safe(sqlException.getSQLState()),
        sqlException == null ? "-" : sqlException.getErrorCode());
  }

  private static SQLException sqlException(Throwable failure) {
    for (Throwable current = failure; current != null && current.getCause() != current;
        current = current.getCause()) {
      if (current instanceof SQLException sqlException) return sqlException;
    }
    return null;
  }

  private static Throwable rootCause(Throwable failure) {
    Throwable current = failure;
    while (current != null && current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current;
  }

  private static String route(HttpServletRequest request) {
    String uri = request.getRequestURI();
    // Identifiers stay out of the log.
    return uri == null ? "-" : safe(uri.replaceAll("/[^/]*\\d[^/]*(?=/|$)", "/:id"));
  }

  private static String type(Throwable failure) {
    return failure == null ? "-" : safe(failure.getClass().getSimpleName());
  }

  private static String safe(String value) {
    if (value == null || value.isBlank()) return "-";
    StringBuilder safe = new StringBuilder();
    value.strip().codePoints().limit(MAX_LOG_VALUE_LENGTH).forEach(character ->
        safe.appendCodePoint(Character.isISOControl(character) || character == ' '
            || character == ' ' ? '_' : character));
    return safe.toString();
  }
}
