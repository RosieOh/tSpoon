package com.tspoon.communityservice.repository;

import com.tspoon.communityservice.entity.Board;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BoardRepository extends JpaRepository<Board, Long> {
    
    Page<Board> findByStatusOrderByCreatedAtDesc(Board.BoardStatus status, Pageable pageable);
    
    Page<Board> findByCategoryAndStatusOrderByCreatedAtDesc(
            Board.BoardCategory category, 
            Board.BoardStatus status, 
            Pageable pageable);
    
    @Query("SELECT b FROM Board b WHERE b.status = :status AND " +
           "(b.title LIKE %:keyword% OR b.content LIKE %:keyword%) " +
           "ORDER BY b.createdAt DESC")
    Page<Board> findByKeywordAndStatus(@Param("keyword") String keyword, 
                                      @Param("status") Board.BoardStatus status, 
                                      Pageable pageable);
    
    List<Board> findByAuthorIdAndStatusOrderByCreatedAtDesc(Long authorId, Board.BoardStatus status);
    
    @Query("SELECT b FROM Board b WHERE b.status = :status ORDER BY b.likeCount DESC, b.createdAt DESC")
    Page<Board> findPopularBoards(@Param("status") Board.BoardStatus status, Pageable pageable);
}
