package com.tspoon.notificationservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Notification {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long userId; // User Service의 사용자 ID
    
    @Column(nullable = false)
    private String title;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status = NotificationStatus.UNREAD;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;
    
    @Column
    private String relatedEntityType; // 관련 엔티티 타입 (board, question, event 등)
    
    @Column
    private Long relatedEntityId; // 관련 엔티티 ID
    
    @Column
    private String actionUrl; // 클릭 시 이동할 URL
    
    @Column
    private String metadata; // 추가 메타데이터 (JSON 형태)
    
    @Column
    private LocalDateTime scheduledAt; // 예약 발송 시간
    
    @Column
    private LocalDateTime sentAt; // 실제 발송 시간
    
    @Column
    private LocalDateTime readAt; // 읽은 시간
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public enum NotificationType {
        SYSTEM,         // 시스템 알림
        COMMUNITY,      // 커뮤니티 알림 (댓글, 좋아요 등)
        EDUCATION,      // 교육 콘텐츠 알림
        QNA,           // Q&A 알림 (답변 등)
        EVENT,         // 이벤트 알림
        REMINDER,      // 리마인더
        ANNOUNCEMENT   // 공지사항
    }
    
    public enum NotificationStatus {
        PENDING,        // 발송 대기
        SENT,          // 발송 완료
        DELIVERED,     // 전달 완료
        READ,          // 읽음
        FAILED,        // 발송 실패
        UNREAD         // 읽지 않음
    }
    
    public enum NotificationChannel {
        IN_APP,        // 앱 내 알림
        EMAIL,         // 이메일
        SMS,           // SMS
        PUSH,          // 푸시 알림
        WEBHOOK        // 웹훅
    }
}
