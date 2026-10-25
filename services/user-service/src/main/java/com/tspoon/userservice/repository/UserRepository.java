package com.tspoon.userservice.repository;

import com.tspoon.userservice.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByEmail(String email);
    
    boolean existsByEmail(String email);
    
    boolean existsByRole(User.UserRole role);
    
    Optional<User> findByIdAndStatus(Long id, User.UserStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<User> findAllLockedByRoleAndStatus(User.UserRole role, User.UserStatus status);
    
    // 회원 단위로 직렬화해야 하는 작업(자녀 수 상한 등)에서 쓴다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findLockedById(Long id);
    
    // 미인증 회원은 로그인할 수 없어 자녀 등 딸린 데이터가 없지만, 혹시 있으면 건너뛴다
    @Modifying
    @Query("""
            DELETE FROM User u
            WHERE u.emailVerifiedAt IS NULL AND u.createdAt < :cutoff
              AND NOT EXISTS (SELECT c FROM Child c WHERE c.parent = u)
            """)
    int deleteUnverifiedCreatedBefore(Instant cutoff);
}
