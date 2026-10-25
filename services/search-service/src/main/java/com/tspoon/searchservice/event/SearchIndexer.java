package com.tspoon.searchservice.event;

import com.tspoon.event.CommunityEventTypes;
import com.tspoon.event.EducationEventTypes;
import com.tspoon.event.EventEnvelope;
import com.tspoon.event.QnaEventTypes;
import com.tspoon.event.TspoonTopics;
import com.tspoon.searchservice.index.SearchDocument;
import com.tspoon.searchservice.index.SearchIndex;
import com.tspoon.searchservice.index.SearchType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

/**
 * 게시글·질문·교육 콘텐츠의 공개 상태가 바뀔 때마다 오는 이벤트로 색인을 갱신한다.
 * *_PUBLISHED 는 공개 중인 지금 모습 전체(스냅숏)라 그대로 덮어쓰고, *_REMOVED 는 지운다 — 같은 이벤트가 다시 와도 결과가 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexer {

    private final SearchIndex index;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = {TspoonTopics.COMMUNITY_EVENTS, TspoonTopics.QNA_EVENTS, TspoonTopics.EDUCATION_EVENTS},
            groupId = "${spring.application.name}")
    public void onEvent(ConsumerRecord<String, String> record) {
        EventEnvelope event = EventEnvelope.parse(jsonMapper, record.value());
        JsonNode payload = event.payload();
        Instant version = event.occurredAt();
        switch (event.eventType()) {
            case CommunityEventTypes.BOARD_PUBLISHED -> index.put(document(SearchType.BOARD, payload.required("boardId").asLong(),
                    payload, "/boards/"), version);
            case CommunityEventTypes.BOARD_REMOVED -> index.delete(SearchType.BOARD, payload.required("boardId").asLong(), version);
            case QnaEventTypes.QUESTION_PUBLISHED -> index.put(document(SearchType.QUESTION, payload.required("questionId").asLong(),
                    payload, "/questions/"), version);
            case QnaEventTypes.QUESTION_REMOVED -> index.delete(SearchType.QUESTION, payload.required("questionId").asLong(), version);
            case EducationEventTypes.CONTENT_PUBLISHED -> index.put(document(SearchType.CONTENT, payload.required("contentId").asLong(),
                    payload, "/contents/"), version);
            case EducationEventTypes.CONTENT_REMOVED -> index.delete(SearchType.CONTENT, payload.required("contentId").asLong(), version);
            default -> log.trace("검색과 관계없는 이벤트 {} — 건너뜁니다", event.eventType());
        }
    }

    private static SearchDocument document(SearchType type, long id, JsonNode payload, String path) {
        return new SearchDocument(type, id,
                payload.required("title").asString(),
                payload.required("content").asString(),
                payload.path("category").asString(null),
                payload.required("authorId").asLong(),
                payload.required("authorName").asString(),
                Instant.parse(payload.required("createdAt").asString()),
                path + id);
    }
}
