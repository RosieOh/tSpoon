package com.tspoon.userservice.service;

import com.tspoon.core.exception.BusinessException;
import com.tspoon.core.exception.ResourceNotFoundException;
import com.tspoon.outbox.OutboxEventPublisher;
import com.tspoon.userservice.config.PasswordResetProperties;
import com.tspoon.userservice.dto.LoginResponse;
import com.tspoon.userservice.entity.User;
import com.tspoon.userservice.event.UserEvents;
import com.tspoon.userservice.repository.UserRepository;
import com.tspoon.userservice.service.OneTimeTokenService.IssuedToken;
import com.tspoon.userservice.service.OneTimeTokenService.Purpose;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 비밀번호 재설정(메일 링크)과 변경. 어느 쪽이든 바뀌면 기존 로그인을 모두 끊고, 본인에게 변경 사실을 메일로 알린다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PasswordService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final OneTimeTokenService oneTimeTokenService;
    private final OutboxEventPublisher outboxEventPublisher;
    private final StringRedisTemplate redis;
    private final PasswordResetProperties properties;
    private final LoginAttemptService loginAttemptService;

    /**
     * 재설정 메일 요청. 가입 여부를 드러내지 않도록 호출한 쪽에는 결과와 무관하게 같은 응답을 준다.
     * 이메일을 인증한 활동 중인 회원에게만, 대기 시간마다 한 번 보낸다.
     */
    public void requestReset(String email) {
        userRepository.findByEmail(email)
                .filter(user -> user.getStatus() == User.UserStatus.ACTIVE && user.isEmailVerified())
                .filter(user -> Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                        UserRedisKeys.passwordResetCooldown(user.getId()), "1", properties.requestCooldown())))
                .ifPresent(user -> {
                    IssuedToken token = oneTimeTokenService.issue(Purpose.PASSWORD_RESET, user.getId(), properties.tokenTtl());
                    outboxEventPublisher.publish(UserEvents.TOPIC, UserEvents.AGGREGATE_USER, user.getId(),
                            UserEvents.PASSWORD_RESET_REQUESTED, new UserEvents.PasswordResetRequested(
                                    user.getId(), user.getEmail(), user.getName(), token.token(), token.expiresAt()));
                });
    }

    /** 메일 링크의 토큰으로 새 비밀번호를 정한다 */
    public void reset(String token, String newPassword) {
        User user = oneTimeTokenService.consume(Purpose.PASSWORD_RESET, token)
                .flatMap(userRepository::findById)
                .filter(found -> found.getStatus() == User.UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException("재설정 링크가 유효하지 않거나 만료되었습니다. 다시 요청해 주세요."));
        changePassword(user, newPassword);
        loginAttemptService.reset(user.getEmail()); // 잠긴 계정도 재설정하면 바로 로그인할 수 있다
    }

    /** 로그인한 회원의 비밀번호 변경. 다른 기기의 로그인은 모두 끊고, 지금 기기에는 새 토큰을 준다 */
    public LoginResponse change(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        // 401 은 "로그인이 풀렸다" 로 읽히므로 400 으로 알린다
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessException("현재 비밀번호가 올바르지 않습니다.", HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD");
        }
        changePassword(user, newPassword);
        return LoginResponse.of(tokenService.startSession(user), user);
    }

    private void changePassword(User user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));
        tokenService.revokeAll(user.getId());
        outboxEventPublisher.publish(UserEvents.TOPIC, UserEvents.AGGREGATE_USER, user.getId(), UserEvents.PASSWORD_CHANGED,
                new UserEvents.PasswordChanged(user.getId(), user.getEmail(), user.getName(), Instant.now()));
    }
}
