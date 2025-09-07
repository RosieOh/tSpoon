package com.tspoon.communityservice.repository;

import com.tspoon.communityservice.entity.BoardLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BoardLikeRepository extends JpaRepository<BoardLike, Long> {
    
    Optional<BoardLike> findByBoardIdAndUserId(Long boardId, Long userId);
    
    boolean existsByBoardIdAndUserId(Long boardId, Long userId);
    
    long countByBoardId(Long boardId);
}
