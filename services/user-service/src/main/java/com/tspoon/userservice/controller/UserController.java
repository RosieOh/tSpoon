package com.tspoon.userservice.controller;

import com.tspoon.userservice.dto.UserRequest;
import com.tspoon.userservice.dto.UserResponse;
import com.tspoon.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;
    
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody UserRequest request) {
        UserResponse response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody UserRequest request) {
        String token = userService.login(request);
        return ResponseEntity.ok(token);
    }
    
    @GetMapping("/profile")
    public ResponseEntity<UserResponse> getProfile(@RequestHeader("Authorization") String token) {
        UserResponse response = userService.getProfile(token);
        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody UserRequest request) {
        UserResponse response = userService.updateProfile(token, request);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/children")
    public ResponseEntity<List<UserResponse>> getChildren(@RequestHeader("Authorization") String token) {
        List<UserResponse> children = userService.getChildren(token);
        return ResponseEntity.ok(children);
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
