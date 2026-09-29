package com.uptrail.identity.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uptrail.identity.domain.Role;
import com.uptrail.identity.domain.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findByEmployeeId(Long employeeId);

    List<UserAccount> findByEmployeeIdIn(Collection<Long> employeeIds);

    boolean existsByUsername(String username);

    @Query("""
            select count(distinct a.id) from UserAccount a join a.roles r, Employee e
            where e.id = a.employeeId and r = :role and a.enabled = true and e.active = true
            """)
    long countActiveWithRole(@Param("role") Role role);

    @Query("""
            select case when count(a) > 0 then true else false end from UserAccount a join a.roles r
            where a.employeeId = :employeeId and r = :role and a.enabled = true
            """)
    boolean employeeHasRole(@Param("employeeId") Long employeeId, @Param("role") Role role);
}
