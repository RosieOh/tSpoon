package com.tspoon.userservice.repository;

import com.tspoon.userservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByEmail(String email);
    
    boolean existsByEmail(String email);
    
    boolean existsByRole(User.UserRole role);
    
    Optional<User> findByIdAndStatus(Long id, User.UserStatus status);
}
