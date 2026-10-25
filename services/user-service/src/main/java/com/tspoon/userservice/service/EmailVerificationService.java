package com.tspoon.userservice.service;

import com.tspoon.core.exception.BusinessException;
import com.tspoon.outbox.OutboxEventPublisher;
import com.tspoon.userservice.config.EmailVerificationProperties;
import com.tspoon.userservice.entity.User;
import com.tspoon.userservice.event.UserEvents;
import com.tspoon.userservice.repository.UserRepository;
import com.tspoon.userservice.service.OneTimeTokenService.IssuedToken;
import com.tspoon.userservice.service.OneTimeTokenService.Purpose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 이메일 인증. 메일은 notification-service 가 보낸다 — 여기서는 토큰을 만들고 발송 요청 이벤트를 outbox 에 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EmailVerificationService {

    private final OneTimeTokenService oneTimeTokenService;
    private final OutboxEventPublisher outboxEventPublisher;
    private final UserRepository userRepository;
    private final StringRedisTemplate redis;
    private final EmailVerificationProperties properties;

    /** 새 인증 토큰을 만들고(이전 토큰은 폐기) 메일 발송 요청을 회원 변경과 같은 트랜잭션에 기록한다 */
    public void requestVerification(User user) {
        IssuedToken token = oneTimeTokenService.issue(Purpose.EMAIL_VERIFICATION, user.getId(), properties.tokenTtl());
        redis.opsForValue().set(UserRedisKeys.verificationCooldown(user.getId()), "1", properties.resendCooldown());
        outboxEventPublisher.publish(UserEvents.TOPIC, UserEvents.AGGREGATE_USER, user.getId(),
                UserEvents.EMAIL_VERIFICATION_REQUESTED, new UserEvents.EmailVerificationRequested(
                        user.getId(), user.getEmail(), user.getName(), token.token(), token.expiresAt()));
    }

    public void confirm(String token) {
        User user = oneTimeTokenService.consume(Purpose.EMAIL_VERIFICATION, token)
                .flatMap(userRepository::findById)
                .orElseThrow(() -> new BusinessException("인증 링크가 유효하지 않거나 만료되었습니다. 인증 메일을 다시 받아 주세요."));
        if (!user.isEmailVerified()) {
            user.setEmailVerifiedAt(Instant.now());
        }
    }

    /**
     * 인증 메일 재발송. 가입·인증 여부를 드러내지 않도록 호출한 쪽에는 결과와 무관하게 같은 응답을 준다.
     * 대기 시간 안에 다시 요청하면 새 메일을 만들지 않는다.
     */
    public void resend(String email) {
        userRepository.findByEmail(email)
                .filter(user -> !user.isEmailVerified() && user.getStatus() == User.UserStatus.ACTIVE)
                .filter(user -> Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                        UserRedisKeys.verificationCooldown(user.getId()), "1", properties.resendCooldown())))
                .ifPresent(this::requestVerification);
    }

    /** 보관 기간이 지나도록 인증하지 않은 가입을 지운다. 지워지면 그 이메일로 다시 가입할 수 있다 */
    @Scheduled(cron = "${tspoon.user.email-verification.cleanup-cron:0 30 4 * * *}", zone = "Asia/Seoul")
    public int deleteExpiredUnverifiedUsers() {
        int deleted = userRepository.deleteUnverifiedCreatedBefore(Instant.now().minus(properties.unverifiedRetention()));
        if (deleted > 0) {
            log.info("인증하지 않은 가입 {}건을 정리했습니다.", deleted);
        }
        return deleted;
    }
}
