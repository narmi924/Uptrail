package com.uptrail.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.uptrail.model.User;
import com.uptrail.repo.UserRepo;

/** One identity contract for login, Staff and Manager: session attribute "user". */
@Service
public class CurrentUserService {
    public static final String SESSION_KEY = "user";
    private final UserRepo users;

    public CurrentUserService(UserRepo users) { this.users = users; }

    @Transactional(readOnly = true)
    public User current(Authentication authentication, HttpSession session) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UptrailUserPrincipal principal)) {
            session.removeAttribute(SESSION_KEY);
            return null;
        }
        User user = users.findById(principal.getUserId())
                .filter(u -> u.isActive() && u.isEnabled()).map(User::sessionView).orElse(null);
        if (user == null) session.removeAttribute(SESSION_KEY);
        else session.setAttribute(SESSION_KEY, user);
        return user;
    }
}
