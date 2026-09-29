package com.uptrail.identity.web;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import com.uptrail.identity.service.UptrailUserPrincipal;

/**
 * Password authentication for one entry point. A correct password at the wrong entry fails exactly like a
 * wrong password, so the login page does not reveal which accounts exist or which roles they hold.
 */
public class EntryAuthenticationProvider implements AuthenticationProvider {

    private final DaoAuthenticationProvider passwordCheck;
    private final EntryPoint entryPoint;

    public EntryAuthenticationProvider(DaoAuthenticationProvider passwordCheck, EntryPoint entryPoint) {
        this.passwordCheck = passwordCheck;
        this.entryPoint = entryPoint;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Authentication verified = passwordCheck.authenticate(authentication);
        UptrailUserPrincipal principal = (UptrailUserPrincipal) verified.getPrincipal();
        if (!entryPoint.accepts(principal.getRoles())) {
            throw new BadCredentialsException("Bad credentials");
        }
        UsernamePasswordAuthenticationToken result = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.authoritiesWith(entryPoint.authority()));
        result.setDetails(authentication.getDetails());
        return result;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
