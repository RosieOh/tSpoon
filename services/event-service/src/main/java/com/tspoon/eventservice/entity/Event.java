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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class Event {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String title;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;
    
    @Column(nullable = false)
    private Long organizerId; // User Service의 사용자 ID
    
    @Column(nullable = false)
    private String organizerName; // 주최자 이름 (캐시용)
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType type;
    
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status = EventStatus.ACTIVE;
    
    // 일정은 사람이 정하는 행사 현지 시각(한국 시간)이라 시간대 없이 둔다. 생성·수정 시각만 Instant
    @Column(nullable = false)
    private LocalDateTime startDate;
    
    @Column(nullable = false)
    private LocalDateTime endDate;
    
    @Column
    private LocalDateTime registrationStartDate;
    
    @Column
    private LocalDateTime registrationEndDate;
    
    @Column
    private String location;
    
    @Column
    private String onlineLink; // 온라인 이벤트 링크
    
    @Column
    private Integer maxParticipants; // 최대 참가자 수
    
    @Builder.Default
    @Column(nullable = false)
    private Integer currentParticipants = 0;
    
    @Column
    private String imageUrl; // 이벤트 이미지 URL
    
    @Column
    private String tags; // 태그 (JSON 형태로 저장)
    
    @Column
    private String requirements; // 참가 요구사항
    
    @Column
    private String contactInfo; // 연락처 정보
    
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<EventRegistration> registrations = new ArrayList<>();
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    public enum EventType {
        SEMINAR,        // 세미나
        WORKSHOP,       // 워크샵
        CONFERENCE,     // 컨퍼런스
        WEBINAR,        // 웨비나
        MEETUP,         // 모임
        COMPETITION,    // 대회
        EXHIBITION,     // 전시회
        CAMP,           // 캠프
        CLASS,          // 수업
        OTHER           // 기타
    }
    
    public enum EventStatus {
        ACTIVE,         // 활성
        CANCELLED,      // 취소됨
        COMPLETED,      // 완료됨
        POSTPONED,      // 연기됨
        FULL            // 정원 마감
    }
}
