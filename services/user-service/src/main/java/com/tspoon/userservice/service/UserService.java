package com.tspoon.userservice.service;

import com.tspoon.userservice.dto.UserRequest;
import com.tspoon.userservice.dto.UserResponse;
import com.tspoon.userservice.entity.User;
import com.tspoon.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    public UserResponse register(UserRequest request) {
        // 이메일 중복 체크
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("이미 존재하는 이메일입니다.");
        }
        
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .phone(request.getPhone())
                .address(request.getAddress())
                .role(User.UserRole.PARENT)
                .status(User.UserStatus.ACTIVE)
                .build();
        
        User savedUser = userRepository.save(user);
        return UserResponse.from(savedUser);
    }
    
    public String login(UserRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }
        
        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw new RuntimeException("비활성화된 계정입니다.");
        }
        
        // JWT 토큰 생성 (실제 구현에서는 JWT 라이브러리 사용)
        return "Bearer " + user.getId() + "_" + System.currentTimeMillis();
    }
    
    @Transactional(readOnly = true)
    public UserResponse getProfile(String token) {
        Long userId = extractUserIdFromToken(token);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        return UserResponse.from(user);
    }
    
    public UserResponse updateProfile(String token, UserRequest request) {
        Long userId = extractUserIdFromToken(token);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        
        user.setName(request.getName());
        user.setPhone(request.getPhone());
        user.setAddress(request.getAddress());
        
        User updatedUser = userRepository.save(user);
        return UserResponse.from(updatedUser);
    }
    
    @Transactional(readOnly = true)
    public List<UserResponse> getChildren(String token) {
        Long userId = extractUserIdFromToken(token);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        
        return user.getChildren().stream()
                .map(child -> UserResponse.builder()
                        .id(child.getId())
                        .name(child.getName())
                        .build())
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        return UserResponse.from(user);
    }
    
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return userRepository.findAll(pageable).getContent().stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
    }
    
    private Long extractUserIdFromToken(String token) {
        // 실제 구현에서는 JWT 토큰을 파싱하여 사용자 ID 추출
        String cleanToken = token.replace("Bearer ", "");
        String[] parts = cleanToken.split("_");
        return Long.parseLong(parts[0]);
    }
}
