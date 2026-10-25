package com.tspoon.moderation;

import com.tspoon.auth.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "신고", description = "부적절한 글 신고 — 서로 다른 회원의 신고가 쌓이면 관리자 검토 전이라도 숨겨진다")
@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ModerationService moderationService;

    @Operation(summary = "신고 — 같은 글은 한 번만, 자기 글은 신고할 수 없다")
    @PostMapping("/api/{collection}/{id}/reports")
    public ResponseEntity<Void> report(
            @AuthenticationPrincipal AuthUser authUser,
            @Parameter(description = "boards · comments · questions · answers") @PathVariable String collection,
            @PathVariable long id,
            @Valid @RequestBody ReportRequest request) {
        moderationService.report(collection, id, authUser, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
