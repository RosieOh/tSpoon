package com.tspoon.educationservice.dto;

import com.tspoon.educationservice.entity.EducationContent;

import java.time.Instant;

/**
 * 목록용. 본문(content)은 상세 조회에서만 내려준다.
 */
public record EducationContentSummary(
        Long id,
        String title,
        String description,
        String authorName,
        EducationContent.ContentType type,
        EducationContent.ContentCategory category,
        String thumbnailUrl,
        Integer estimatedTime,
        String targetAge,
        Integer viewCount,
        Integer bookmarkCount,
        Instant createdAt
) {

    public static EducationContentSummary from(EducationContent content) {
        return new EducationContentSummary(
                content.getId(), content.getTitle(), content.getDescription(), content.getAuthorName(),
                content.getType(), content.getCategory(), content.getThumbnailUrl(), content.getEstimatedTime(),
                content.getTargetAge(), content.getViewCount(), content.getBookmarkCount(), content.getCreatedAt());
    }
}
