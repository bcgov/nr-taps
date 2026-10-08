package ca.bc.gov.nrs.taps.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Lets the deployment smoke check confirm which backend image is serving. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ImageReferenceHeaderFilter extends OncePerRequestFilter {
  private final String imageReference;

  ImageReferenceHeaderFilter(@Value("${taps.image-reference:local}") String imageReference) {
    this.imageReference = imageReference;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    // Set before the security chain so 401/403 responses carry it too.
    response.setHeader("X-TAPS-Backend-Image", imageReference);
    chain.doFilter(request, response);
  }
}
