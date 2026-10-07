package com.uptrail.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.uptrail.model.Role;
import com.uptrail.model.User;

public interface UserRepo extends JpaRepository<User, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :id")
    Optional<User> lockById(@Param("id") Long id);
    Optional<User> findByUserName(String userName);
    boolean existsByUserName(String userName);
    boolean existsByStaffId(String staffId);
    long countByActive(boolean active);
    List<User> findByUserIdIn(Collection<Long> ids);

    @Query("select u from User u where u.userId in :ids")
    List<User> findByIdIn(@Param("ids") Collection<Long> ids);

    @Query("select count(u) from User u join u.roles r where r = :role and u.enabled = true and u.active = true")
    long countActiveWithRole(@Param("role") Role role);
    @Query("select case when count(u) > 0 then true else false end from User u join u.roles r where u.userId = :id and r = :role and u.enabled = true")
    boolean userHasRole(@Param("id") Long id, @Param("role") Role role);
    @Query("select u from User u where (:active is null or u.active = :active) and (:q = '' or lower(u.name) like concat('%', :q, '%') or lower(u.staffId) like concat('%', :q, '%') or lower(u.department) like concat('%', :q, '%')) order by u.name, u.userId")
    Page<User> search(@Param("q") String query, @Param("active") Boolean active, Pageable pageable);
    @Query("select u from User u, ApprovalHierarchy a where a.employeeId = u.userId and a.managerId = :id order by u.name, u.userId")
    List<User> findDirectReports(@Param("id") Long managerId);
    @Query("select count(u) from User u join u.roles r where u.active = true and r = com.uptrail.model.Role.STAFF and not exists(select 1 from ApprovalHierarchy a where a.employeeId = u.userId)")
    long countActiveApplicantsWithoutApprover();
    @Query("select count(u) from User u join u.roles r where u.active = true and r = com.uptrail.model.Role.STAFF and not exists(select 1 from TrainingEntitlement t where t.employeeId = u.userId and t.calendarYear = :year)")
    long countActiveApplicantsWithoutAccount(@Param("year") int year);
    @Query("select u from User u join u.roles r where u.active = true and r = com.uptrail.model.Role.STAFF and (:q = '' or lower(u.name) like concat('%', :q, '%') or lower(u.staffId) like concat('%', :q, '%') or lower(u.department) like concat('%', :q, '%')) order by u.name, u.userId")
    Page<User> findActiveApplicants(@Param("q") String query, Pageable pageable);
    @Query("select u from User u join u.roles r where u.active = true and u.enabled = true and r = com.uptrail.model.Role.MANAGER order by u.name, u.userId")
    List<User> findActiveManagers();

    /** Preserve identity/history when an administrator changes the primary role. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update users set user_type = :type where id = :id", nativeQuery = true)
    void changeSubtype(@Param("id") Long id, @Param("type") String type);
}
