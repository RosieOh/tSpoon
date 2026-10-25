package com.tspoon.userservice.oauth;

import com.tspoon.userservice.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "소셜 로그인", description = "카카오·네이버 — 클라이언트가 받은 인가 코드를 보내면 가입·로그인하고 우리 토큰을 준다")
@RestController
@RequestMapping("/api/users/oauth")
@RequiredArgsConstructor
public class SocialLoginController {

    private final SocialLoginService socialLoginService;

    @Operation(summary = "쓸 수 있는 소셜 로그인 제공자")
    @SecurityRequirements
    @GetMapping("/providers")
    public ResponseEntity<List<String>> providers() {
        return ResponseEntity.ok(socialLoginService.providers());
    }

    @Operation(summary = "소셜 로그인 — 처음이면 가입(201), 이미 연결됐거나 연결하면 로그인(200)")
    @SecurityRequirements
    @PostMapping("/{provider}")
    public ResponseEntity<LoginResponse> login(@PathVariable String provider, @Valid @RequestBody SocialLoginRequest request) {
        SocialLoginService.SocialLoginResult result = socialLoginService.login(provider, request.code(), request.state());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.login());
    }

    /**
     * @param code  제공자가 리다이렉트로 넘겨준 인가 코드 (한 번만 쓸 수 있다)
     * @param state 인가 요청 때 클라이언트가 만든 값 — 클라이언트가 먼저 확인하고, 네이버처럼 요구하는 제공자에게 그대로 넘긴다
     */
    public record SocialLoginRequest(
            @NotBlank(message = "인가 코드는 필수입니다") @Size(max = 2000, message = "인가 코드가 너무 깁니다") String code,
            @Size(max = 500, message = "state 가 너무 깁니다") String state) {
    }
}
