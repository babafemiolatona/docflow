package com.tech.docflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.tech.docflow.models.User;
import com.tech.docflow.models.UserRole;

public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByEmail(String email);
    Boolean existsByEmail(String email);

    List<User> findByRoleAndIsActive(UserRole role, Boolean isActive);
}
