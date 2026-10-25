package com.tspoon.qnaservice.event;

import com.tspoon.outbox.OutboxEventPublisher;
import com.tspoon.qnaservice.entity.Question;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 질문이 공개되거나(작성·수정·숨김 해제) 더 이상 공개되지 않으면(삭제·숨김) 이벤트를 남긴다 — 검색 색인이 받아 갱신한다.
 * 변경과 같은 트랜잭션에서 불러야 한다.
 */
@Component
@RequiredArgsConstructor
public class QuestionPublications {

    private final OutboxEventPublisher outboxEventPublisher;

    public void published(Question question) {
        outboxEventPublisher.publish(QnaEvents.TOPIC, QnaEvents.AGGREGATE_QUESTION, question.getId(),
                QnaEvents.QUESTION_PUBLISHED, QnaEvents.QuestionPublished.of(question));
    }

    public void removed(Long questionId) {
        outboxEventPublisher.publish(QnaEvents.TOPIC, QnaEvents.AGGREGATE_QUESTION, questionId,
                QnaEvents.QUESTION_REMOVED, new QnaEvents.QuestionRemoved(questionId));
    }
}
