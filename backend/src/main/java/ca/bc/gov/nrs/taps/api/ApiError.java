package ca.bc.gov.nrs.taps.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;

public record ApiError(String code, String message) {
  public static void write(HttpServletResponse response, ObjectMapper mapper, int status,
      String code, String message) throws IOException {
    response.setStatus(status);
    if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    mapper.writeValue(response.getOutputStream(), new ApiError(code, message));
  }
}
