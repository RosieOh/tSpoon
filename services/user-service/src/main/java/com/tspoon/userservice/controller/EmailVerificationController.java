package com.tspoon.userservice.controller;

import com.tspoon.userservice.dto.ConfirmEmailVerificationRequest;
import com.tspoon.userservice.dto.ResendEmailVerificationRequest;
import com.tspoon.userservice.service.EmailVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "이메일 인증", description = "가입 후 받은 메일의 링크로 이메일 주소를 확인한다. 인증 전에는 로그인할 수 없다")
@RestController
@RequestMapping("/api/users/email-verification")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    @Operation(summary = "이메일 인증 — 메일 링크의 토큰을 보낸다 (한 번만 쓸 수 있음)")
    @SecurityRequirements
    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody ConfirmEmailVerificationRequest request) {
        emailVerificationService.confirm(request.token());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "인증 메일 다시 받기 — 가입 여부와 무관하게 항상 202")
    @SecurityRequirements
    @PostMapping("/resend")
    public ResponseEntity<Void> resend(@Valid @RequestBody ResendEmailVerificationRequest request) {
        emailVerificationService.resend(request.email());
        return ResponseEntity.accepted().build();
    }
}
