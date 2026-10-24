package com.tspoon.educationservice.entity;

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
@Table(name = "TBL_EDUCATION_CONTENT")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class EducationContent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String title;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    @Column(nullable = false)
    private Long authorId; // User Service의 사용자 ID
    
    @Column(nullable = false)
    private String authorName; // 작성자 이름 (캐시용)
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentType type;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentCategory category;
    
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentStatus status = ContentStatus.ACTIVE;
    
    @Column
    private String tags; // 태그 (JSON 형태로 저장)
    
    @Column
    private String attachments; // 첨부파일 (JSON 형태로 저장)
    
    @Column
    private String thumbnailUrl; // 썸네일 이미지 URL
    
    @Column
    private Integer estimatedTime; // 예상 학습 시간 (분)
    
    @Column
    private String targetAge; // 대상 연령
    
    @Builder.Default
    @Column(nullable = false)
    private Integer viewCount = 0;
    
    @Builder.Default
    @Column(nullable = false)
    private Integer bookmarkCount = 0;
    
    @OneToMany(mappedBy = "content", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ContentBookmark> bookmarks = new ArrayList<>();
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public enum ContentType {
        ARTICLE,        // 글
        VIDEO,          // 동영상
        PDF,            // PDF 문서
        INTERACTIVE,    // 인터랙티브 콘텐츠
        QUIZ,           // 퀴즈
        WORKSHEET       // 학습지
    }
    
    public enum ContentCategory {
        EARLY_CHILDHOOD,    // 유아교육
        ELEMENTARY,         // 초등교육
        MIDDLE_SCHOOL,      // 중등교육
        HIGH_SCHOOL,        // 고등교육
        PARENTING,          // 육아
        SPECIAL_EDUCATION,  // 특수교육
        LANGUAGE,           // 언어교육
        MATH,               // 수학
        SCIENCE,            // 과학
        ART,                // 예술
        PHYSICAL,           // 체육
        SOCIAL,             // 사회
        HISTORY,            // 역사
        GEOGRAPHY,          // 지리
        COMPUTER,           // 컴퓨터
        MUSIC,              // 음악
        OTHER               // 기타
    }
    
    public enum ContentStatus {
        ACTIVE, DRAFT, HIDDEN, DELETED
    }
}
