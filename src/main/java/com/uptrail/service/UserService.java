package com.uptrail.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.model.User;
import com.uptrail.repo.UserRepo;

/**
 * Loads login identities from the database. A disabled account or an inactive (archived) employee cannot
 * sign in at either entry point.
 */
@Service
public class UserService implements UserDetailsService {

    private final UserRepo users;

    public UserService(UserRepo users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Unknown user");
        }
        User account = users.findByUserName(User.normaliseUsername(username))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        return new UptrailUserPrincipal(account.getUserId(), account.getUserName(),
                account.getPasswordHash(), account.getName(), account.getRoles(),
                account.isEnabled() && account.isActive());
    }
}
