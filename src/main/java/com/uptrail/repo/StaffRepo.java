package com.uptrail.repo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.uptrail.model.Staff;
public interface StaffRepo extends JpaRepository<Staff, Long> {
    Optional<Staff> findByStaffId(String staffId);
    Optional<Staff> findByUserName(String userName);
}
