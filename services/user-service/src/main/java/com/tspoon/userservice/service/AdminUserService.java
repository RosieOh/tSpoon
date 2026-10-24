package com.tspoon.userservice.service;

import com.tspoon.core.exception.BusinessException;
import com.tspoon.core.exception.ResourceNotFoundException;
import com.tspoon.core.security.AuthUser;
import com.tspoon.userservice.dto.UserResponse;
import com.tspoon.userservice.entity.User;
import com.tspoon.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자의 회원 역할·상태 변경.
 * 역할은 JWT 에 담기므로 바뀐 역할은 대상 회원이 다시 로그인한 뒤부터 적용된다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

    private final UserRepository userRepository;

    public UserResponse changeRole(AuthUser admin, Long userId, User.UserRole role) {
        User user = findOtherUser(admin, userId);
        user.setRole(role);
        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    public UserResponse changeStatus(AuthUser admin, Long userId, User.UserStatus status) {
        User user = findOtherUser(admin, userId);
        user.setStatus(status);
        return UserResponse.from(userRepository.saveAndFlush(user));
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
