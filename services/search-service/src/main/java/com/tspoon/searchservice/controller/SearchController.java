package com.tspoon.searchservice.controller;

import com.tspoon.searchservice.index.SearchHit;
import com.tspoon.searchservice.index.SearchIndex;
import com.tspoon.searchservice.index.SearchType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "검색", description = "게시글·질문·교육 콘텐츠 통합 검색 (한국어 형태소 분석)")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchIndex searchIndex;

    @Operation(summary = "통합 검색 — 제목·본문에서 찾고, 검색어는 highlight 에 <em> 으로 감싸 준다(내용은 HTML 이스케이프)")
    @SecurityRequirements
    @GetMapping
    public ResponseEntity<PagedModel<SearchHit>> search(
            @RequestParam @NotBlank(message = "검색어를 입력해 주세요") @Size(max = 100, message = "검색어는 100자 이하여야 합니다") String q,
            @RequestParam(required = false) SearchType type,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page 는 0 이상이어야 합니다")
            @Max(value = 99, message = "page 는 99 이하여야 합니다") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size 는 1 이상이어야 합니다")
            @Max(value = 50, message = "size 는 50 이하여야 합니다") int size) {
        return ResponseEntity.ok(new PagedModel<>(searchIndex.search(q.strip(), type, page, size)));
    }
}
