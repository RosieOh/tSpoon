package com.tspoon.educationservice.event;

import com.tspoon.educationservice.entity.EducationContent;
import com.tspoon.outbox.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 콘텐츠가 공개되거나(공개 작성·수정) 더 이상 공개되지 않으면(삭제·임시저장으로 되돌림) 이벤트를 남긴다 — 검색 색인이 받아 갱신한다.
 * 변경과 같은 트랜잭션에서 불러야 한다.
 */
@Component
@RequiredArgsConstructor
public class ContentPublications {

    private final OutboxEventPublisher outboxEventPublisher;

    public void published(EducationContent content) {
        outboxEventPublisher.publish(EducationEvents.TOPIC, EducationEvents.AGGREGATE_CONTENT, content.getId(),
                EducationEvents.CONTENT_PUBLISHED, EducationEvents.ContentPublished.of(content));
    }

    public void removed(Long contentId) {
        outboxEventPublisher.publish(EducationEvents.TOPIC, EducationEvents.AGGREGATE_CONTENT, contentId,
                EducationEvents.CONTENT_REMOVED, new EducationEvents.ContentRemoved(contentId));
    }
}
