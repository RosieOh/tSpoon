package com.tspoon.userservice.service;

import com.tspoon.core.exception.BusinessException;
import com.tspoon.core.exception.ResourceNotFoundException;
import com.tspoon.core.jwt.JwtUtil;
import com.tspoon.core.security.AuthUser;
import com.tspoon.userservice.dto.ChildResponse;
import com.tspoon.userservice.dto.LoginRequest;
import com.tspoon.userservice.dto.LoginResponse;
import com.tspoon.userservice.dto.PublicUserResponse;
import com.tspoon.userservice.dto.SignupRequest;
import com.tspoon.userservice.dto.UpdateProfileRequest;
import com.tspoon.userservice.dto.UserResponse;
import com.tspoon.userservice.entity.User;
import com.tspoon.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    // 이메일 존재 여부를 노출하지 않도록 로그인 실패 메시지는 하나로 통일한다
    private static final String LOGIN_FAILED_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserResponse register(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("이미 존재하는 이메일입니다.", HttpStatus.CONFLICT);
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .phone(request.phone())
                .address(request.address())
                .role(User.UserRole.PARENT)
                .status(User.UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);
        return UserResponse.from(savedUser);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .filter(found -> passwordEncoder.matches(request.password(), found.getPassword()))
                .orElseThrow(() -> new BusinessException(LOGIN_FAILED_MESSAGE, HttpStatus.UNAUTHORIZED));

        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw new BusinessException("비활성화된 계정입니다.", HttpStatus.FORBIDDEN);
        }

        String accessToken = jwtUtil.generateAccessToken(
                new AuthUser(user.getId(), user.getEmail(), user.getName(), user.getRole().name()));
        return LoginResponse.of(accessToken, jwtUtil.getAccessTokenTtl().toSeconds(), user);
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        return UserResponse.from(findUser(userId));
    }

    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUser(userId);

        user.setName(request.name());
        user.setPhone(request.phone());
        user.setAddress(request.address());

        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public List<ChildResponse> getChildren(Long userId) {
        return findUser(userId).getChildren().stream()
                .map(ChildResponse::from)
                .toList();
    }

    // 비활성·정지된 회원은 공개 프로필도 보여 주지 않는다
    @Transactional(readOnly = true)
    public PublicUserResponse getPublicProfile(Long id) {
        return userRepository.findByIdAndStatus(id, User.UserStatus.ACTIVE)
                .map(PublicUserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return userRepository.findAll(pageable).getContent().stream()
                .map(UserResponse::from)
                .toList();
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
    }
}
