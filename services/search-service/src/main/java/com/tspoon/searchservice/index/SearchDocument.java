package com.tspoon.searchservice.index;

import java.time.Instant;

/**
 * 색인 문서 하나. 문서 ID 는 "{종류}-{원본 ID}" (예: BOARD-12).
 *
 * @param url 화면에서 이동할 경로
 */
public record SearchDocument(
        SearchType type,
        long targetId,
        String title,
        String content,
        String category,
        Long authorId,
        String authorName,
        Instant createdAt,
        String url
) {

    public String documentId() {
        return documentId(type, targetId);
    }

    public static String documentId(SearchType type, long targetId) {
        return type.name() + "-" + targetId;
    }
}
