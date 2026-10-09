package ca.bc.gov.nrs.taps.security;

import ca.bc.gov.nrs.taps.api.ApiError;
import ca.bc.gov.nrs.taps.configuration.OracleProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

// New business routes must enforce both capability and record scope.
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, TapsAuthenticationConverter authenticationConverter,
      ObjectMapper mapper, Environment environment) throws Exception {
    boolean readApiEnabled = OracleProfile.active(environment);
    return http
        .csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(requests -> {
          // Let error dispatches keep their real status instead of a 401/403.
          requests.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
              .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
              .requestMatchers(HttpMethod.GET, "/api/me").authenticated();
          if (readApiEnabled) {
            requests.requestMatchers(HttpMethod.POST, "/api/ecas/inbox")
                .hasAuthority(TapsCapability.ECAS_SUBMISSION_VIEW.authority())
                .requestMatchers(HttpMethod.GET, "/api/ecas/references/*/*", "/api/ecas/lookups",
                    "/api/ecas/audit/*", "/api/ecas/audit/*/events/*", "/api/ecas/*/attachments")
                .hasAuthority(TapsCapability.ECAS_SUBMISSION_VIEW.authority())
                .requestMatchers(HttpMethod.GET, "/api/gas/worksheets", "/api/gas/worksheets/*/*",
                    "/api/gas/worksheets/NON_APPRAISED/*/history",
                    "/api/gas/worksheets/APPRAISED/*/history",
                    "/api/gas/appraised/by-ecas/*", "/api/gas/licences/*/marks", "/api/gas/licence-information",
                    "/api/gas/lookups")
                .hasAuthority(TapsCapability.GAS_APPRAISAL_VIEW.authority());
          }
          requests.anyRequest().denyAll();
        })
        .exceptionHandling(errors -> errors
            .authenticationEntryPoint((request, response, exception) ->
                ApiError.AUTHENTICATION_REQUIRED.write(response, mapper))
            .accessDeniedHandler((request, response, exception) ->
                ApiError.ACCESS_DENIED.write(response, mapper)))
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter))
            .authenticationEntryPoint((request, response, exception) ->
                ApiError.AUTHENTICATION_REQUIRED.write(response, mapper))
            .accessDeniedHandler((request, response, exception) ->
                ApiError.ACCESS_DENIED.write(response, mapper)))
        .build();
  }
}
