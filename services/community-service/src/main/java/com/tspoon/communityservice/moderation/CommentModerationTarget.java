package com.tspoon.communityservice.moderation;

import com.tspoon.communityservice.entity.Comment;
import com.tspoon.communityservice.repository.BoardRepository;
import com.tspoon.communityservice.repository.CommentRepository;
import com.tspoon.moderation.ModerationTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 댓글 신고·숨김. 숨긴 댓글은 게시글의 댓글 수에서 빠지고, 답글이 있으면 자리만 남는다 (삭제된 댓글과 같은 방식).
 */
@Component
@RequiredArgsConstructor
public class CommentModerationTarget implements ModerationTarget {

    private static final int PREVIEW_LENGTH = 100;

    private final CommentRepository commentRepository;
    private final BoardRepository boardRepository;

    @Override
    public String collection() {
        return "comments";
    }

    @Override
    public Optional<Snapshot> lockVisible(long id) {
        return commentRepository.findLockedByIdAndStatus(id, Comment.CommentStatus.ACTIVE).map(CommentModerationTarget::snapshot);
    }

    @Override
    public Optional<Snapshot> find(long id) {
        return commentRepository.findById(id).map(CommentModerationTarget::snapshot);
    }

    @Override
    public void hide(long id) {
        commentRepository.findByIdAndStatus(id, Comment.CommentStatus.ACTIVE).ifPresent(comment -> {
            comment.setStatus(Comment.CommentStatus.HIDDEN);
            boardRepository.decrementCommentCount(comment.getBoard().getId());
        });
    }

    @Override
    public void restore(long id) {
        commentRepository.findByIdAndStatus(id, Comment.CommentStatus.HIDDEN).ifPresent(comment -> {
            comment.setStatus(Comment.CommentStatus.ACTIVE);
            boardRepository.incrementCommentCount(comment.getBoard().getId());
        });
    }

    private static Snapshot snapshot(Comment comment) {
        String content = comment.getContent();
        return new Snapshot(comment.getId(), comment.getAuthorId(), comment.getStatus().name(),
                content.length() > PREVIEW_LENGTH ? content.substring(0, PREVIEW_LENGTH) + "…" : content);
    }
}
