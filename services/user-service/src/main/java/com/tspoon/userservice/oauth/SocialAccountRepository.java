package com.tspoon.userservice.oauth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByProviderAndProviderUserId(String provider, String providerUserId);

    // 탈퇴하면 연결을 끊는다 — 같은 소셜 계정으로 다시 가입할 수 있다
    @Modifying
    @Query("DELETE FROM SocialAccount s WHERE s.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
