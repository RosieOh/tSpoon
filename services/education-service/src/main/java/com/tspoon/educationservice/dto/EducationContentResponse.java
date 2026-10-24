package com.tspoon.educationservice.dto;

import com.tspoon.educationservice.entity.EducationContent;

import java.time.Instant;

public record EducationContentResponse(
        Long id,
        String title,
        String description,
        String content,
        Long authorId,
        String authorName,
        EducationContent.ContentType type,
        EducationContent.ContentCategory category,
        EducationContent.ContentStatus status,
        String tags,
        String attachments,
        String thumbnailUrl,
        Integer estimatedTime,
        String targetAge,
        Integer viewCount,
        Integer bookmarkCount,
        Instant createdAt,
        Instant updatedAt
) {

    public static EducationContentResponse from(EducationContent content) {
        return new EducationContentResponse(
                content.getId(), content.getTitle(), content.getDescription(), content.getContent(),
                content.getAuthorId(), content.getAuthorName(), content.getType(), content.getCategory(), content.getStatus(),
                content.getTags(), content.getAttachments(), content.getThumbnailUrl(), content.getEstimatedTime(),
                content.getTargetAge(), content.getViewCount(), content.getBookmarkCount(),
                content.getCreatedAt(), content.getUpdatedAt());
    }
}
