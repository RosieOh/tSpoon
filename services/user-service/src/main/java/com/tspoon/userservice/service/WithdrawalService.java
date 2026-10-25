package com.tspoon.userservice.service;

import com.tspoon.core.exception.BusinessException;
import com.tspoon.core.exception.ResourceNotFoundException;
import com.tspoon.outbox.OutboxEventPublisher;
import com.tspoon.userservice.entity.User;
import com.tspoon.userservice.event.UserEvents;
import com.tspoon.userservice.repository.ChildRepository;
import com.tspoon.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 회원 탈퇴. 다른 회원과 주고받은 글의 흐름이 끊기지 않도록 글은 남기되 작성자는 "탈퇴한 회원" 으로 익명화한다.
 * <ul>
 *   <li>여기서는 개인정보(이메일·이름·연락처·소개·자녀)를 지우고, 같은 이메일로 다시 가입할 수 있게 이메일을 비운다</li>
 *   <li>모든 로그인을 끊는다</li>
 *   <li>다른 서비스는 USER_WITHDRAWN 을 받아 자기 데이터를 정리한다 (작성자 익명화, 좋아요·북마크·다가오는 신청·알림 정리)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class WithdrawalService {

    static final String WITHDRAWN_NAME = "탈퇴한 회원";

    private final UserRepository userRepository;
    private final ChildRepository childRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final OutboxEventPublisher outboxEventPublisher;

    public void withdraw(Long userId, String password) {
        User user = userRepository.findLockedById(userId)
                .filter(found -> found.getStatus() != User.UserStatus.WITHDRAWN)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException("비밀번호가 올바르지 않습니다.", HttpStatus.BAD_REQUEST, "INVALID_PASSWORD");
        }
        if (user.getRole() == User.UserRole.ADMIN && isLastActiveAdmin(user)) {
            throw new BusinessException("마지막 관리자는 탈퇴할 수 없습니다. 다른 관리자를 먼저 지정해 주세요.", HttpStatus.CONFLICT);
        }

        Instant now = Instant.now();
        childRepository.deleteByParentId(userId);
        user.setEmail("withdrawn-" + userId + "@withdrawn.invalid");
        user.setName(WITHDRAWN_NAME);
        user.setPhone(null);
        user.setAddress(null);
        user.setBio(null);
        user.setProfileImage(null);
        user.setPassword(passwordEncoder.encode(SecureTokens.generate())); // 아무도 모르는 비밀번호로
        user.setStatus(User.UserStatus.WITHDRAWN);
        user.setWithdrawnAt(now);

        tokenService.revokeAll(userId);
        outboxEventPublisher.publish(UserEvents.TOPIC, UserEvents.AGGREGATE_USER, userId, UserEvents.WITHDRAWN,
                new UserEvents.Withdrawn(userId, now));
    }

    // 관리자 행을 모두 잠근 뒤 세서, 두 관리자가 동시에 탈퇴해 관리자가 없어지는 일을 막는다
    private boolean isLastActiveAdmin(User user) {
        return userRepository.findAllLockedByRoleAndStatus(User.UserRole.ADMIN, User.UserStatus.ACTIVE).stream()
                .noneMatch(admin -> !admin.getId().equals(user.getId()));
    }
}
