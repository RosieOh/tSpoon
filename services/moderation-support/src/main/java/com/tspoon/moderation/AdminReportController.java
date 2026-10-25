package com.tspoon.moderation;

import com.tspoon.auth.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// /api/admin/** 는 SecurityConfig 에서 관리자만 허용한다
@Tag(name = "신고 관리 (관리자)", description = "대상별로 묶은 신고를 보고 숨김을 확정하거나 기각한다")
@RestController
@RequestMapping("/api/admin/reports/{collection}")
@RequiredArgsConstructor
public class AdminReportController {

    private final ModerationService moderationService;

    @Operation(summary = "신고된 대상 목록 — 마지막 신고가 최근인 순")
    @GetMapping
    public ResponseEntity<PagedModel<ReportedTargetResponse>> reportedTargets(
            @Parameter(description = "boards · comments · questions · answers") @PathVariable String collection,
            @RequestParam(defaultValue = "PENDING") ReportStatus status,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page 는 0 이상이어야 합니다") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 는 1 이상이어야 합니다")
            @Max(value = 100, message = "size 는 100 이하여야 합니다") int size) {
        return ResponseEntity.ok(new PagedModel<>(moderationService.reportedTargets(collection, status, page, size)));
    }

    @Operation(summary = "대상에 들어온 신고 (사유·설명)")
    @GetMapping("/{id}")
    public ResponseEntity<List<ReportResponse>> reports(@PathVariable String collection, @PathVariable long id) {
        return ResponseEntity.ok(moderationService.reports(collection, id));
    }

    @Operation(summary = "숨김 확정 — 검토 대기 신고를 받아들인다")
    @PostMapping("/{id}/hide")
    public ResponseEntity<Void> hide(@AuthenticationPrincipal AuthUser admin, @PathVariable String collection,
                                     @PathVariable long id) {
        moderationService.hide(collection, id, admin);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "기각 — 신고를 받아들이지 않고, 숨겨져 있으면 다시 공개한다")
    @PostMapping("/{id}/dismiss")
    public ResponseEntity<Void> dismiss(@AuthenticationPrincipal AuthUser admin, @PathVariable String collection,
                                        @PathVariable long id) {
        moderationService.dismiss(collection, id, admin);
        return ResponseEntity.noContent().build();
    }
}
