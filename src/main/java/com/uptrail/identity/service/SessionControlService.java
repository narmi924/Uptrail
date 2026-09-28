package com.uptrail.identity.service;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

/**
 * Ends the active sessions of a user, used after deactivation, role changes and password resets so that
 * old permissions do not survive in an open browser.
 */
@Service
public class SessionControlService {

    private final SessionRegistry sessionRegistry;

    public SessionControlService(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    public int expireSessionsOf(String username) {
        int expired = 0;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof UptrailUserPrincipal user && user.getUsername().equals(username)) {
                for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
                    session.expireNow();
                    expired++;
                }
            }
        }
        return expired;
    }
}
