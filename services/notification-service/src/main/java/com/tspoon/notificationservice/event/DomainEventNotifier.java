package com.tspoon.notificationservice.event;

import com.tspoon.notificationservice.entity.Notification.NotificationType;
import com.tspoon.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * 다른 서비스의 도메인 이벤트를 받아 알릴 대상을 정하고 앱 내 알림을 만든다.
 *
 * 최소 1회 전달이라 같은 이벤트가 다시 올 수 있으므로, 처리한 eventId 를 알림과 같은 트랜잭션에 기록해 한 번만 만든다.
 * 자기 글에 자기가 남긴 댓글·답변처럼 스스로에게 알릴 일은 만들지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventNotifier {

    private final NotificationService notificationService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void handle(JsonNode envelope) {
        UUID eventId = UUID.fromString(envelope.required("eventId").asString());
        String eventType = envelope.required("eventType").asString();
        if (!markProcessed(eventId, eventType)) {
            log.debug("이미 처리한 이벤트 {} ({}) — 건너뜁니다", eventId, eventType);
            return;
        }

        JsonNode payload = envelope.required("payload");
        switch (eventType) {
            case "COMMENT_CREATED" -> onCommentCreated(payload);
            case "ANSWER_CREATED" -> onAnswerCreated(payload);
            case "ANSWER_ACCEPTED" -> onAnswerAccepted(payload);
            default -> log.debug("알림 대상이 아닌 이벤트 {} — 건너뜁니다", eventType);
        }
    }

    private void onCommentCreated(JsonNode event) {
        Long authorId = event.required("authorId").asLong();
        Long boardId = event.required("boardId").asLong();
        Long boardAuthorId = event.required("boardAuthorId").asLong();
        Long parentAuthorId = longOrNull(event.get("parentCommentAuthorId"));
        String message = event.required("authorName").asString() + ": " + event.required("contentPreview").asString();
        String boardTitle = event.required("boardTitle").asString();

        if (!boardAuthorId.equals(authorId)) {
            notify(boardAuthorId, "내 게시글에 댓글이 달렸습니다", "[" + boardTitle + "] " + message,
                    NotificationType.COMMUNITY, "/boards/" + boardId, "BOARD", boardId);
        }
        // 답글이면 부모 댓글 작성자에게도 — 게시글 작성자와 같은 사람이면 위에서 이미 알렸다
        if (parentAuthorId != null && !parentAuthorId.equals(authorId) && !parentAuthorId.equals(boardAuthorId)) {
            notify(parentAuthorId, "내 댓글에 답글이 달렸습니다", "[" + boardTitle + "] " + message,
                    NotificationType.COMMUNITY, "/boards/" + boardId, "BOARD", boardId);
        }
    }

    private void onAnswerCreated(JsonNode event) {
        Long authorId = event.required("authorId").asLong();
        Long questionAuthorId = event.required("questionAuthorId").asLong();
        if (questionAuthorId.equals(authorId)) {
            return;
        }
        Long questionId = event.required("questionId").asLong();
        String who = event.required("authorName").asString() + answerTypeLabel(event.path("answerType").asString(""));
        notify(questionAuthorId, "내 질문에 답변이 달렸습니다",
                "[" + event.required("questionTitle").asString() + "] " + who + ": " + event.required("contentPreview").asString(),
                NotificationType.QNA, "/questions/" + questionId, "QUESTION", questionId);
    }

    private void onAnswerAccepted(JsonNode event) {
        Long questionId = event.required("questionId").asLong();
        notify(event.required("answerAuthorId").asLong(), "내 답변이 채택되었습니다",
                "[" + event.required("questionTitle").asString() + "] 질문자가 답변을 채택했습니다.",
                NotificationType.QNA, "/questions/" + questionId, "QUESTION", questionId);
    }

    private void notify(Long userId, String title, String content, NotificationType type, String actionUrl,
                        String relatedEntityType, Long relatedEntityId) {
        notificationService.createInApp(userId, title, content, type, actionUrl, relatedEntityType, relatedEntityId);
    }

    // 처음 보는 이벤트면 true. 동시에 같은 이벤트를 처리해도 기본키 충돌로 한쪽만 true
    private boolean markProcessed(UUID eventId, String eventType) {
        return jdbcTemplate.update("""
                INSERT INTO processed_events (event_id, event_type, processed_at) VALUES (?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, eventId, eventType, Timestamp.from(Instant.now())) == 1;
    }

    private static String answerTypeLabel(String answerType) {
        return switch (answerType) {
            case "EXPERT" -> "(전문가)";
            case "TEACHER" -> "(교사)";
            default -> "";
        };
    }

    private static Long longOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asLong();
    }
}
