package com.tspoon.eventservice.entity;

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

import java.time.Instant;

@Entity
@Table(name = "event_registrations", uniqueConstraints = {
    @UniqueConstraint(name = "uk_event_registrations_event_id_user_id", columnNames = {"event_id", "user_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class EventRegistration {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;
    
    @Column(nullable = false)
    private Long userId; // User Service의 사용자 ID
    
    @Column(nullable = false)
    private String userName; // 사용자 이름 (캐시용)
    
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegistrationStatus status = RegistrationStatus.REGISTERED;
    
    @Column
    private String notes; // 참가자 메모
    
    @Column
    private String contactInfo; // 연락처 정보

    @Column(name = "waitlisted_at")
    private Instant waitlistedAt; // 대기자로 들어온 시각 — 대기 순번의 기준
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    public enum RegistrationStatus {
        REGISTERED,     // 등록됨
        WAITLISTED,     // 정원이 차서 대기 중 — 자리가 나면 먼저 대기한 순서대로 확정
        CANCELLED,      // 취소됨
        ATTENDED,       // 참석함
        NO_SHOW         // 불참
    }
}
