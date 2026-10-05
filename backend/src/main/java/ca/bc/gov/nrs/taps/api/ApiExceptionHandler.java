package ca.bc.gov.nrs.taps.api;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
      MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
  ResponseEntity<ApiError> invalidInput(Exception exception) {
    return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "Check the request fields and try again."));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiError> denied() {
    return ResponseEntity.status(403).body(new ApiError("ACCESS_DENIED", "You do not have access to this operation."));
  }

  @ExceptionHandler(ReadController.ReadNotFoundException.class)
  ResponseEntity<ApiError> notFound() {
    return ResponseEntity.status(404).body(new ApiError("NOT_FOUND", "The requested record was not found."));
  }

  @ExceptionHandler(ReadController.ReadUnavailableException.class)
  ResponseEntity<ApiError> unavailable() {
    return ResponseEntity.status(503).body(new ApiError("READ_UNAVAILABLE", "The requested information is temporarily unavailable. Try again later."));
  }
}
