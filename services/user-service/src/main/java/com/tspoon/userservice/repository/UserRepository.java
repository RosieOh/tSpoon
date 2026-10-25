package com.tspoon.userservice.repository;

import com.tspoon.userservice.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByEmail(String email);
    
    boolean existsByEmail(String email);
    
    boolean existsByRole(User.UserRole role);
    
    Optional<User> findByIdAndStatus(Long id, User.UserStatus status);

    // 회원 단위로 직렬화해야 하는 작업(자녀 수 상한 등)에서 쓴다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findLockedById(Long id);
}
