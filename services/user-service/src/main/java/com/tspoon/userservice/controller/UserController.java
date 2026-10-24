package com.tspoon.userservice.controller;

import com.tspoon.core.security.AuthUser;
import com.tspoon.userservice.dto.ChildResponse;
import com.tspoon.userservice.dto.LoginRequest;
import com.tspoon.userservice.dto.LoginResponse;
import com.tspoon.userservice.dto.SignupRequest;
import com.tspoon.userservice.dto.UpdateProfileRequest;
import com.tspoon.userservice.dto.UserResponse;
import com.tspoon.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        UserResponse response = userService.getUserById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<UserResponse> users = userService.getAllUsers(page, size);
        return ResponseEntity.ok(users);
    }
}
