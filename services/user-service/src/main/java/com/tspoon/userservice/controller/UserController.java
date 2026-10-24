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

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody SignupRequest request) {
        UserResponse response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(userService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody(required = false) LogoutRequest request) {
        userService.logout(authUser.userId(), authorization.substring("Bearer ".length()), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/profile")
    public ResponseEntity<UserResponse> getProfile(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(userService.getProfile(authUser.userId()));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(authUser.userId(), request));
    }

    @GetMapping("/children")
    public ResponseEntity<List<ChildResponse>> getChildren(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(userService.getChildren(authUser.userId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PublicUserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getPublicProfile(id));
    }

    // 연락처가 담긴 전체 목록이므로 관리자만
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedModel<UserResponse>> getAllUsers(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page 는 0 이상이어야 합니다") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size 는 1 이상이어야 합니다")
            @Max(value = 100, message = "size 는 100 이하여야 합니다") int size) {
        return ResponseEntity.ok(new PagedModel<>(userService.getAllUsers(page, size)));
    }
}
