package com.tspoon.communityservice.event;

import com.tspoon.communityservice.entity.Board;
import com.tspoon.communityservice.entity.Comment;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * community-service 가 발행하는 이벤트. 소비자는 사실만 받고 알릴 대상은 스스로 정한다.
 * 키는 게시글 ID 라서 같은 게시글의 이벤트는 같은 파티션에 순서대로 들어간다.
 */
@Configuration(proxyBeanMethods = false)
public class CommunityEvents {

    public static final String TOPIC = "tspoon.community.events";
    public static final String AGGREGATE_BOARD = "BOARD";
    public static final String COMMENT_CREATED = "COMMENT_CREATED";

    private static final int PREVIEW_LENGTH = 100;

    // 로컬·CI 는 브로커가 하나라 복제본 1
    @Bean
    public NewTopic communityEventsTopic() {
        return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build();
    }

    /**
     * @param parentCommentAuthorId 답글이면 부모 댓글 작성자, 아니면 null
     */
    public record CommentCreated(
            Long commentId,
            Long boardId,
            String boardTitle,
            Long boardAuthorId,
            Long parentCommentId,
            Long parentCommentAuthorId,
            Long authorId,
            String authorName,
            String contentPreview
    ) {

        public static CommentCreated of(Board board, Comment comment, Comment parent) {
            String content = comment.getContent();
            return new CommentCreated(
                    comment.getId(), board.getId(), board.getTitle(), board.getAuthorId(),
                    parent == null ? null : parent.getId(), parent == null ? null : parent.getAuthorId(),
                    comment.getAuthorId(), comment.getAuthorName(),
                    content.length() > PREVIEW_LENGTH ? content.substring(0, PREVIEW_LENGTH) + "…" : content);
        }
    }
}
