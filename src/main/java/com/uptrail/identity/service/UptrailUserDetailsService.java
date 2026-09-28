package com.uptrail.identity.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.identity.domain.UserAccount;
import com.uptrail.identity.repository.UserAccountRepository;
import com.uptrail.organisation.domain.Employee;
import com.uptrail.organisation.repository.EmployeeRepository;

/**
 * Loads login identities from the database. A disabled account or an inactive (archived) employee cannot
 * sign in at either entry point.
 */
@Service
public class UptrailUserDetailsService implements UserDetailsService {

    private final UserAccountRepository accounts;
    private final EmployeeRepository employees;

    public UptrailUserDetailsService(UserAccountRepository accounts, EmployeeRepository employees) {
        this.accounts = accounts;
        this.employees = employees;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Unknown user");
        }
        UserAccount account = accounts.findByUsername(UserAccount.normaliseUsername(username))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        Employee employee = employees.findById(account.getEmployeeId())
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        return new UptrailUserPrincipal(account.getId(), employee.getId(), account.getUsername(),
                account.getPasswordHash(), employee.getFullName(), account.getRoles(),
                account.isEnabled() && employee.isActive());
    }
}
