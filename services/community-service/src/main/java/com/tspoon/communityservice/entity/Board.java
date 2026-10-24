package com.tspoon.communityservice.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "boards")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class Board {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String title;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;
    
    @Column(nullable = false)
    private Long authorId; // User Service의 사용자 ID
    
    @Column(nullable = false)
    private String authorName; // 작성자 이름 (캐시용)
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BoardCategory category;
    
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BoardStatus status = BoardStatus.ACTIVE;
    
    @Column
    private String tags; // 태그 (JSON 형태로 저장)
    
    @Column
    private String attachments; // 첨부파일 (JSON 형태로 저장)
    
    @Builder.Default
    @Column(nullable = false)
    private Integer viewCount = 0;
    
    @Builder.Default
    @Column(nullable = false)
    private Integer likeCount = 0;
    
    @Builder.Default
    @Column(nullable = false)
    private Integer commentCount = 0;
    
    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Comment> comments = new ArrayList<>();
    
    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<BoardLike> likes = new ArrayList<>();
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public enum BoardCategory {
        GENERAL,        // 일반 게시판
        EDUCATION,      // 교육 정보
        PARENTING,      // 육아 정보
        REVIEW,         // 후기
        QUESTION,       // 질문
        ANNOUNCEMENT    // 공지사항
    }
    
    public enum BoardStatus {
        ACTIVE, DELETED, HIDDEN
    }
}
