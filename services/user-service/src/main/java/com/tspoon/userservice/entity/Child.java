package com.tspoon.userservice.entity;

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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_CHILD")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class Child {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    private User parent;
    
    @Column(nullable = false)
    private String name;
    
    @Column
    private LocalDate birthDate;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Grade grade;
    
    @Column
    private String school;
    
    @Column
    private String interests; // 관심사 (JSON 형태로 저장)
    
    @Column
    private String notes; // 부모가 작성한 메모
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public enum Grade {
        KINDERGARTEN,   // 유치원
        ELEMENTARY_1,   // 초등 1학년
        ELEMENTARY_2,   // 초등 2학년
        ELEMENTARY_3,   // 초등 3학년
        ELEMENTARY_4,   // 초등 4학년
        ELEMENTARY_5,   // 초등 5학년
        ELEMENTARY_6,   // 초등 6학년
        MIDDLE_1,       // 중등 1학년
        MIDDLE_2,       // 중등 2학년
        MIDDLE_3,       // 중등 3학년
        HIGH_1,         // 고등 1학년
        HIGH_2,         // 고등 2학년
        HIGH_3          // 고등 3학년
    }
}
