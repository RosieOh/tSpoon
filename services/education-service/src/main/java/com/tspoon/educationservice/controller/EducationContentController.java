package com.tspoon.educationservice.controller;

import com.tspoon.auth.AuthUser;
import com.tspoon.educationservice.dto.EducationContentRequest;
import com.tspoon.educationservice.dto.EducationContentResponse;
import com.tspoon.educationservice.dto.EducationContentSummary;
import com.tspoon.educationservice.entity.EducationContent;
import com.tspoon.educationservice.recommendation.RecommendationService;
import com.tspoon.educationservice.service.EducationContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "교육 콘텐츠", description = "콘텐츠 조회·작성(교사·전문가)·북마크")
@RestController
@RequestMapping("/api/contents")
@RequiredArgsConstructor
public class EducationContentController {

    private final EducationContentService contentService;
    private final RecommendationService recommendationService;

    @Operation(summary = "콘텐츠 목록 (최신순, 카테고리·유형 필터)")
    @SecurityRequirements
    @GetMapping
    public ResponseEntity<PagedModel<EducationContentSummary>> search(
            @RequestParam(required = false) EducationContent.ContentCategory category,
            @RequestParam(required = false) EducationContent.ContentType type,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page 는 0 이상이어야 합니다") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size 는 1 이상이어야 합니다")
            @Max(value = 100, message = "size 는 100 이하여야 합니다") int size) {
        return ResponseEntity.ok(new PagedModel<>(contentService.search(category, type, page, size)));
    }

    @Operation(summary = "맞춤 추천 — 자녀 학년의 교육 단계 → 육아 → 그 밖의 순(같은 순위는 인기순), 북마크한 것은 뺀다. "
            + "비로그인·자녀 정보가 없으면 인기순 (basedOn 이 비어 있음)")
    @SecurityRequirements
    @GetMapping("/recommendations")
    public ResponseEntity<RecommendationService.RecommendationResponse> recommendations(
            @AuthenticationPrincipal AuthUser viewer,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size 는 1 이상이어야 합니다")
            @Max(value = 50, message = "size 는 50 이하여야 합니다") int size) {
        return ResponseEntity.ok(recommendationService.recommend(viewer, size));
    }

    @Operation(summary = "콘텐츠 상세 — 조회수 증가. 임시저장 글은 작성자·관리자만")
    @SecurityRequirements
    @GetMapping("/{id}")
    public ResponseEntity<EducationContentResponse> getContent(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthUser viewer) {
        return ResponseEntity.ok(contentService.getContent(id, viewer));
    }

    @Operation(summary = "콘텐츠 작성 (교사·전문가·관리자)")
    @PreAuthorize("hasAnyRole('TEACHER', 'EXPERT', 'ADMIN')")
    @PostMapping
    public ResponseEntity<EducationContentResponse> create(
            @AuthenticationPrincipal AuthUser author,
            @Valid @RequestBody EducationContentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contentService.create(author, request));
    }

    @Operation(summary = "콘텐츠 수정 (작성자·관리자) — draft=false 로 임시저장 글 공개")
    @PutMapping("/{id}")
    public ResponseEntity<EducationContentResponse> update(
            @AuthenticationPrincipal AuthUser user,
            @PathVariable Long id,
            @Valid @RequestBody EducationContentRequest request) {
        return ResponseEntity.ok(contentService.update(user, id, request));
    }

    @Operation(summary = "콘텐츠 삭제 (작성자·관리자)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        contentService.delete(user, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "북마크")
    @PostMapping("/{id}/bookmark")
    public ResponseEntity<Void> bookmark(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        contentService.bookmark(user, id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "북마크 취소")
    @DeleteMapping("/{id}/bookmark")
    public ResponseEntity<Void> removeBookmark(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        contentService.removeBookmark(user, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내 북마크 (최근에 담은 순)")
    @GetMapping("/bookmarks/me")
    public ResponseEntity<PagedModel<EducationContentSummary>> getMyBookmarks(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page 는 0 이상이어야 합니다") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size 는 1 이상이어야 합니다")
            @Max(value = 100, message = "size 는 100 이하여야 합니다") int size) {
        return ResponseEntity.ok(new PagedModel<>(contentService.getMyBookmarks(user, page, size)));
    }
}
