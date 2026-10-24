package com.tspoon.communityservice.service;

import com.tspoon.auth.AuthUser;
import com.tspoon.communityservice.dto.CommentRequest;
import com.tspoon.communityservice.dto.CommentResponse;
import com.tspoon.communityservice.dto.CommentUpdateRequest;
import com.tspoon.communityservice.entity.Board;
import com.tspoon.communityservice.entity.Comment;
import com.tspoon.communityservice.repository.BoardRepository;
import com.tspoon.communityservice.repository.CommentRepository;
import com.tspoon.core.exception.BusinessException;
import com.tspoon.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 게시글 댓글. 답글은 한 단계만 허용하고, 목록은 최상위 댓글 아래에 답글을 묶어서 돌려준다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {

    private final CommentRepository commentRepository;
    private final BoardRepository boardRepository;

    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(Long boardId) {
        findActiveBoard(boardId);
        List<Comment> comments = commentRepository.findByBoardIdOrderByCreatedAtAsc(boardId);

        Map<Long, List<Comment>> activeReplies = comments.stream()
                .filter(comment -> comment.getParentCommentId() != null && isActive(comment))
                .collect(Collectors.groupingBy(Comment::getParentCommentId, LinkedHashMap::new, Collectors.toList()));

        // 삭제된 댓글은 답글이 남아 있을 때만 자리를 표시한다
        return comments.stream()
                .filter(comment -> comment.getParentCommentId() == null)
                .filter(comment -> isActive(comment) || activeReplies.containsKey(comment.getId()))
                .map(comment -> CommentResponse.of(comment, activeReplies.getOrDefault(comment.getId(), List.of())))
                .toList();
    }

    public CommentResponse create(AuthUser authUser, Long boardId, CommentRequest request) {
        Board board = findActiveBoard(boardId);
        if (request.parentCommentId() != null) {
            verifyReplyTarget(boardId, request.parentCommentId());
        }

        Comment comment = commentRepository.save(Comment.builder()
                .board(board)
                .content(request.content())
                .authorId(authUser.userId())
                .authorName(authUser.name())
                .parentCommentId(request.parentCommentId())
                .status(Comment.CommentStatus.ACTIVE)
                .build());
        boardRepository.incrementCommentCount(boardId);
        return CommentResponse.of(comment, List.of());
    }

    public CommentResponse update(AuthUser authUser, Long commentId, CommentUpdateRequest request) {
        Comment comment = findActiveComment(commentId);
        if (!comment.getAuthorId().equals(authUser.userId())) {
            throw new BusinessException("댓글을 수정할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
        comment.setContent(request.content());
        return CommentResponse.of(commentRepository.saveAndFlush(comment), List.of());
    }

    // 작성자 본인 또는 관리자(운영상 삭제)
    public void delete(AuthUser authUser, Long commentId) {
        Comment comment = findActiveComment(commentId);
        if (!comment.getAuthorId().equals(authUser.userId()) && !authUser.isAdmin()) {
            throw new BusinessException("댓글을 삭제할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
        comment.setStatus(Comment.CommentStatus.DELETED);
        boardRepository.decrementCommentCount(comment.getBoard().getId());
    }

    private void verifyReplyTarget(Long boardId, Long parentCommentId) {
        Comment parent = findActiveComment(parentCommentId);
        if (!parent.getBoard().getId().equals(boardId)) {
            throw new BusinessException("다른 게시글의 댓글에는 답글을 달 수 없습니다.");
        }
        if (parent.getParentCommentId() != null) {
            throw new BusinessException("답글에는 답글을 달 수 없습니다.");
        }
    }

    private Board findActiveBoard(Long boardId) {
        return boardRepository.findByIdAndStatus(boardId, Board.BoardStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("게시글을 찾을 수 없습니다."));
    }

    private Comment findActiveComment(Long commentId) {
        return commentRepository.findByIdAndStatus(commentId, Comment.CommentStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("댓글을 찾을 수 없습니다."));
    }

    private static boolean isActive(Comment comment) {
        return comment.getStatus() == Comment.CommentStatus.ACTIVE;
    }
}
