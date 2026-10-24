package com.tspoon.userservice.controller;

import com.tspoon.auth.AuthUser;
import com.tspoon.userservice.dto.ChangeRoleRequest;
import com.tspoon.userservice.dto.ChangeStatusRequest;
import com.tspoon.userservice.dto.UserResponse;
import com.tspoon.userservice.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/admin/** 는 core 의 URL 규칙으로도 ADMIN 만 허용되지만, 경로 규칙이 바뀌어도 막히도록 메서드 보안을 함께 건다
@Tag(name = "회원 관리", description = "관리자 전용")
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "회원 역할 변경 — 기존 Access Token 무효화, 재발급하면 새 역할")
    @PatchMapping("/{id}/role")
    public ResponseEntity<UserResponse> changeRole(
            @AuthenticationPrincipal AuthUser admin,
            @PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequest request) {
        return ResponseEntity.ok(adminUserService.changeRole(admin, id, request.role()));
    }

    @Operation(summary = "회원 상태 변경 — 정지·비활성화 시 모든 토큰 폐기")
    @PatchMapping("/{id}/status")
    public ResponseEntity<UserResponse> changeStatus(
            @AuthenticationPrincipal AuthUser admin,
            @PathVariable Long id,
            @Valid @RequestBody ChangeStatusRequest request) {
        return ResponseEntity.ok(adminUserService.changeStatus(admin, id, request.status()));
    }
}
