package com.tspoon.communityservice.repository;

import com.tspoon.communityservice.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    
    List<Comment> findByBoardIdAndStatusOrderByCreatedAtAsc(Long boardId, Comment.CommentStatus status);
    
    List<Comment> findByParentCommentIdAndStatusOrderByCreatedAtAsc(Long parentCommentId, Comment.CommentStatus status);
    
    List<Comment> findByAuthorIdAndStatusOrderByCreatedAtDesc(Long authorId, Comment.CommentStatus status);
    
    Page<Comment> findByStatusOrderByCreatedAtDesc(Comment.CommentStatus status, Pageable pageable);
}
