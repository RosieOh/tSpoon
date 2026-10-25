package com.tspoon.userservice.controller;

import com.tspoon.auth.AuthUser;
import com.tspoon.userservice.dto.ChangePasswordRequest;
import com.tspoon.userservice.dto.ConfirmPasswordResetRequest;
import com.tspoon.userservice.dto.LoginResponse;
import com.tspoon.userservice.dto.PasswordResetRequest;
import com.tspoon.userservice.service.PasswordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "비밀번호", description = "비밀번호 재설정(메일 링크)과 변경 — 바뀌면 기존 로그인은 모두 끊긴다")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class PasswordController {

    private final PasswordService passwordService;

    @Operation(summary = "비밀번호 재설정 메일 요청 — 가입 여부와 무관하게 항상 202")
    @SecurityRequirements
    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordService.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "비밀번호 재설정 — 메일 링크의 토큰과 새 비밀번호 (한 번만 쓸 수 있음)")
    @SecurityRequirements
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmReset(@Valid @RequestBody ConfirmPasswordResetRequest request) {
        passwordService.reset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "비밀번호 변경 — 다른 기기의 로그인은 끊기고, 응답의 새 토큰으로 계속 쓴다")
    @PutMapping("/password")
    public ResponseEntity<LoginResponse> change(@AuthenticationPrincipal AuthUser authUser,
                                                @Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(passwordService.change(authUser.userId(), request.currentPassword(), request.newPassword()));
    }
}
