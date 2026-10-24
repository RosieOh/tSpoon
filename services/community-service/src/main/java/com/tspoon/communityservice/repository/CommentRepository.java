package com.tspoon.communityservice.repository;

import com.tspoon.communityservice.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 삭제된 부모 댓글도 답글이 남아 있으면 자리를 표시해야 하므로 상태와 무관하게 가져온다
    List<Comment> findByBoardIdOrderByCreatedAtAsc(Long boardId);

    Optional<Comment> findByIdAndStatus(Long id, Comment.CommentStatus status);
}
