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
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questions")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class Question {
    
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
    private QuestionCategory category;
    
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionStatus status = QuestionStatus.OPEN;
    
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority = Priority.NORMAL;
    
    @Column
    private String tags; // 태그 (JSON 형태로 저장)
    
    @Column
    private String attachments; // 첨부파일 (JSON 형태로 저장)
    
    @Builder.Default
    @Column(nullable = false)
    private Integer viewCount = 0;
    
    @Builder.Default
    @Column(nullable = false)
    private Integer answerCount = 0;
    
    @Column
    private Long acceptedAnswerId; // 채택된 답변 ID
    
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Answer> answers = new ArrayList<>();
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public enum QuestionCategory {
        GENERAL,        // 일반 질문
        EDUCATION,      // 교육 관련
        PARENTING,      // 육아 관련
        SCHOOL,         // 학교 생활
        LEARNING,       // 학습 방법
        BEHAVIOR,       // 행동 문제
        DEVELOPMENT,    // 발달 관련
        SPECIAL_NEEDS,  // 특수 교육
        TECHNOLOGY,     // 교육 기술
        OTHER           // 기타
    }
    
    public enum QuestionStatus {
        OPEN,           // 답변 대기
        ANSWERED,       // 답변 완료
        CLOSED,         // 질문 종료
        DELETED         // 삭제됨
    }
    
    public enum Priority {
        LOW,            // 낮음
        NORMAL,         // 보통
        HIGH,           // 높음
        URGENT          // 긴급
    }
}
