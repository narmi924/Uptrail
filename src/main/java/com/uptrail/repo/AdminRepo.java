package com.uptrail.repo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.uptrail.model.Admin;
public interface AdminRepo extends JpaRepository<Admin, Long> {
    Optional<Admin> findByUserName(String userName);
}
