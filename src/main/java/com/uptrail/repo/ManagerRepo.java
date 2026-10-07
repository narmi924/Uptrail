package com.uptrail.repo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.uptrail.model.Manager;
public interface ManagerRepo extends JpaRepository<Manager, Long> {
    Optional<Manager> findByStaffId(String staffId);
    Optional<Manager> findByUserName(String userName);
}
