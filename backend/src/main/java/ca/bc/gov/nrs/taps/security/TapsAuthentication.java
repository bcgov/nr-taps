package ca.bc.gov.nrs.taps.security;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public class TapsAuthentication extends AbstractAuthenticationToken {

  private final Jwt token;
  private final TapsUser user;

  public TapsAuthentication(
      Jwt token, TapsUser user, Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.token = token;
    this.user = user;
    setAuthenticated(true);
  }

  @Override
  public Jwt getCredentials() {
    return token;
  }

  @Override
  public TapsUser getPrincipal() {
    return user;
  }

  @Override
  public String getName() {
    return user.userId();
  }
}
