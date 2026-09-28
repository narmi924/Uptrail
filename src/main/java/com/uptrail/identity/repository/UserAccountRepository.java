package com.uptrail.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uptrail.identity.domain.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
}
