package com.tspoon.userservice.service;

import com.tspoon.auth.AuthUser;
import com.tspoon.core.exception.BusinessException;
import com.tspoon.core.exception.ResourceNotFoundException;
import com.tspoon.userservice.dto.UserResponse;
import com.tspoon.userservice.entity.User;
import com.tspoon.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자의 회원 역할·상태 변경. 바뀐 내용이 이미 발급된 토큰에 남지 않도록 토큰도 함께 무효화한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

    private final UserRepository userRepository;
    private final TokenService tokenService;

    // 기존 Access Token 은 바로 무효화하고, Refresh 로 재발급하면 새 역할이 담긴다
    public UserResponse changeRole(AuthUser admin, Long userId, User.UserRole role) {
        User user = findOtherUser(admin, userId);
        user.setRole(role);
        UserResponse response = UserResponse.from(userRepository.saveAndFlush(user));
        tokenService.invalidateAccessTokens(userId);
        return response;
    }

    // 정지·비활성화하면 이미 발급된 Access·Refresh Token 을 모두 폐기한다
    public UserResponse changeStatus(AuthUser admin, Long userId, User.UserStatus status) {
        User user = findOtherUser(admin, userId);
        user.setStatus(status);
        UserResponse response = UserResponse.from(userRepository.saveAndFlush(user));
        if (status != User.UserStatus.ACTIVE) {
            tokenService.revokeAll(userId);
        }
        return response;
    }

    // 자기 자신을 강등·정지하면 관리자가 한 명도 남지 않을 수 있으므로 막는다
    private User findOtherUser(AuthUser admin, Long userId) {
        if (admin.userId().equals(userId)) {
            throw new BusinessException("자기 자신의 역할·상태는 바꿀 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
    }
}
