package com.tspoon.userservice.controller;

import com.tspoon.auth.AuthUser;
import com.tspoon.userservice.dto.ChildRequest;
import com.tspoon.userservice.dto.ChildResponse;
import com.tspoon.userservice.service.ChildService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "자녀", description = "내 자녀 정보 — 본인 자녀만 조회·수정할 수 있다")
@RestController
@RequestMapping("/api/users/children")
@RequiredArgsConstructor
public class ChildController {

    private final ChildService childService;

    @Operation(summary = "내 자녀 목록")
    @GetMapping
    public ResponseEntity<List<ChildResponse>> getChildren(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(childService.getChildren(authUser.userId()));
    }

    @Operation(summary = "자녀 등록 (최대 10명)")
    @PostMapping
    public ResponseEntity<ChildResponse> create(@AuthenticationPrincipal AuthUser authUser,
                                                @Valid @RequestBody ChildRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(childService.create(authUser.userId(), request));
    }

    @Operation(summary = "자녀 정보 수정 — 관심사는 보낸 목록으로 바뀐다")
    @PutMapping("/{childId}")
    public ResponseEntity<ChildResponse> update(@AuthenticationPrincipal AuthUser authUser,
                                                @PathVariable Long childId,
                                                @Valid @RequestBody ChildRequest request) {
        return ResponseEntity.ok(childService.update(authUser.userId(), childId, request));
    }

    @Operation(summary = "자녀 정보 삭제")
    @DeleteMapping("/{childId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long childId) {
        childService.delete(authUser.userId(), childId);
        return ResponseEntity.noContent().build();
    }
}
