package com.tspoon.userservice.controller;

import com.tspoon.auth.AuthUser;
import com.tspoon.userservice.dto.ChildResponse;
import com.tspoon.userservice.dto.LoginRequest;
import com.tspoon.userservice.dto.LoginResponse;
import com.tspoon.userservice.dto.LogoutRequest;
import com.tspoon.userservice.dto.PublicUserResponse;
import com.tspoon.userservice.dto.RefreshTokenRequest;
import com.tspoon.userservice.dto.SignupRequest;
import com.tspoon.userservice.dto.UpdateProfileRequest;
import com.tspoon.userservice.dto.UserResponse;
import com.tspoon.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "회원", description = "가입·로그인·토큰 재발급·프로필")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "회원가입")
    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody SignupRequest request) {
        UserResponse response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "로그인 — Access·Refresh Token 발급")
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    @Operation(summary = "토큰 재발급 — Access·Refresh Token 을 둘 다 새로 받는다 (이전 Refresh 는 폐기)")
    @SecurityRequirements
    @PostMapping("/token/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(userService.refresh(request));
    }

    @Operation(summary = "로그아웃 — Refresh 계열 폐기, 지금 Access Token 차단")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal AuthUser authUser,
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody(required = false) LogoutRequest request) {
        userService.logout(authUser.userId(), authorization.substring("Bearer ".length()), request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내 정보")
    @GetMapping("/profile")
    public ResponseEntity<UserResponse> getProfile(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(userService.getProfile(authUser.userId()));
    }

    @Operation(summary = "내 정보 수정")
    @PutMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(authUser.userId(), request));
    }

    @Operation(summary = "내 자녀 목록")
    @GetMapping("/children")
    public ResponseEntity<List<ChildResponse>> getChildren(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(userService.getChildren(authUser.userId()));
    }

    @Operation(summary = "회원 공개 프로필 (연락처 제외)")
    @GetMapping("/{id}")
    public ResponseEntity<PublicUserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getPublicProfile(id));
    }

    // 연락처가 담긴 전체 목록이므로 관리자만
    @Operation(summary = "전체 회원 목록 (관리자)")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedModel<UserResponse>> getAllUsers(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page 는 0 이상이어야 합니다") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size 는 1 이상이어야 합니다")
            @Max(value = 100, message = "size 는 100 이하여야 합니다") int size) {
        return ResponseEntity.ok(new PagedModel<>(userService.getAllUsers(page, size)));
    }
}
