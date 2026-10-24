package com.tspoon.communityservice.repository;

import com.tspoon.communityservice.entity.Board;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BoardRepository extends JpaRepository<Board, Long> {

    Optional<Board> findByIdAndStatus(Long id, Board.BoardStatus status);

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

    // 카운터는 읽고-쓰기 대신 DB 에서 원자적으로 증감한다 (동시 요청 시 갱신 유실 방지)
    // flushAutomatically: 아직 flush 안 된 변경(좋아요 삭제 등)이 clear 로 버려지지 않도록 먼저 반영한다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Board b SET b.viewCount = b.viewCount + 1 WHERE b.id = :id AND b.status = :status")
    int incrementViewCount(@Param("id") Long id, @Param("status") Board.BoardStatus status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Board b SET b.likeCount = b.likeCount + 1 WHERE b.id = :id")
    int incrementLikeCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Board b SET b.likeCount = b.likeCount - 1 WHERE b.id = :id AND b.likeCount > 0")
    int decrementLikeCount(@Param("id") Long id);
}
