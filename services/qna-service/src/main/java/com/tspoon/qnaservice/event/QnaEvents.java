package com.tspoon.qnaservice.event;

import com.tspoon.event.QnaEventTypes;
import com.tspoon.event.TspoonTopics;
import com.tspoon.qnaservice.entity.Answer;
import com.tspoon.qnaservice.entity.Question;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * qna-service 가 발행하는 이벤트. 키는 질문 ID 라서 같은 질문의 이벤트는 순서대로 들어간다.
 */
@Configuration(proxyBeanMethods = false)
public class QnaEvents {

    public static final String TOPIC = TspoonTopics.QNA_EVENTS;
    public static final String AGGREGATE_QUESTION = "QUESTION";
    public static final String ANSWER_CREATED = QnaEventTypes.ANSWER_CREATED;
    public static final String ANSWER_ACCEPTED = QnaEventTypes.ANSWER_ACCEPTED;

    private static final int PREVIEW_LENGTH = 100;

    // 로컬·CI 는 브로커가 하나라 복제본 1
    @Bean
    public NewTopic qnaEventsTopic() {
        return TopicBuilder.name(TOPIC).partitions(TspoonTopics.PARTITIONS).replicas(1).build();
    }

    public record AnswerCreated(
            Long answerId,
            Long questionId,
            String questionTitle,
            Long questionAuthorId,
            Long authorId,
            String authorName,
            Answer.AnswerType answerType,
            String contentPreview
    ) {

        public static AnswerCreated of(Question question, Answer answer) {
            String content = answer.getContent();
            return new AnswerCreated(
                    answer.getId(), question.getId(), question.getTitle(), question.getAuthorId(),
                    answer.getAuthorId(), answer.getAuthorName(), answer.getType(),
                    content.length() > PREVIEW_LENGTH ? content.substring(0, PREVIEW_LENGTH) + "…" : content);
        }
    }

    public record AnswerAccepted(
            Long answerId,
            Long questionId,
            String questionTitle,
            Long questionAuthorId,
            Long answerAuthorId
    ) {

        public static AnswerAccepted of(Question question, Answer answer) {
            return new AnswerAccepted(answer.getId(), question.getId(), question.getTitle(),
                    question.getAuthorId(), answer.getAuthorId());
        }
    }
}
