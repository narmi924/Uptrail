package com.uptrail.identity.web;

import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.uptrail.identity.domain.Role;
import com.uptrail.identity.service.UptrailUserPrincipal;

/**
 * Exposes the signed-in user to every page layout (name, roles, workspace) without letting templates
 * touch the security principal directly.
 */
@ControllerAdvice(annotations = Controller.class)
public class CurrentUserAdvice {

    public record CurrentUserView(Long employeeId, String displayName, String rolesLabel, boolean adminWorkspace,
            boolean employee, boolean manager, boolean admin) {
    }

    @ModelAttribute("currentUser")
    public CurrentUserView currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UptrailUserPrincipal principal)) {
            return null;
        }
        boolean adminWorkspace = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(EntryPoint.ADMIN.authority()::equals);
        String roles = principal.getRoles().stream()
                .sorted()
                .map(Role::label)
                .collect(Collectors.joining(" · "));
        return new CurrentUserView(principal.getEmployeeId(), principal.getDisplayName(), roles, adminWorkspace,
                principal.hasRole(Role.EMPLOYEE), principal.hasRole(Role.MANAGER), principal.hasRole(Role.ADMIN));
    }
}
