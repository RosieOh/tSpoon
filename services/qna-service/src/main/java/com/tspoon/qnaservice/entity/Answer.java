package com.tspoon.qnaservice.entity;

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

@Entity
@Table(name = "answers")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class Answer {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;
    
    @Column(nullable = false)
    private Long authorId; // User Service의 사용자 ID
    
    @Column(nullable = false)
    private String authorName; // 작성자 이름 (캐시용)
    
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnswerStatus status = AnswerStatus.ACTIVE;
    
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnswerType type = AnswerType.GENERAL;
    
    @Column
    private String attachments; // 첨부파일 (JSON 형태로 저장)
    
    @Builder.Default
    @Column(nullable = false)
    private Integer likeCount = 0;
    
    @Builder.Default
    @Column(nullable = false)
    private Boolean isAccepted = false;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public enum AnswerStatus {
        ACTIVE, DELETED, HIDDEN
    }
    
    public enum AnswerType {
        GENERAL,        // 일반 답변
        EXPERT,         // 전문가 답변
        TEACHER,        // 교사 답변
        PARENT          // 학부모 답변
    }
}
