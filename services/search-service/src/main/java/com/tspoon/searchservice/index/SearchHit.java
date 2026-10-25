package com.tspoon.searchservice.index;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * 검색 결과 한 건.
 *
 * @param highlight 검색어가 &lt;em&gt; 으로 감싸진 본문(없으면 제목) 일부. 내용은 HTML 이스케이프돼 있다
 */
public record SearchHit(
        SearchType type,
        long id,
        String title,
        String highlight,
        String category,
        String authorName,
        Instant createdAt,
        String url
) {

    static SearchHit of(SearchDocument document, JsonNode highlight) {
        String fragment = highlight.path("content").path(0).asString(null);
        if (fragment == null) {
            fragment = highlight.path("title").path(0).asString(null);
        }
        return new SearchHit(document.type(), document.targetId(), document.title(), fragment, document.category(),
                document.authorName(), document.createdAt(), document.url());
    }
}
