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

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "children")
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
    
    @ElementCollection
    @CollectionTable(name = "child_interests", joinColumns = @JoinColumn(name = "child_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "interest", nullable = false, length = 30)
    @Builder.Default
    private List<String> interests = new ArrayList<>(); // 관심사 (입력 순서 유지)
    
    @Column(length = 1000)
    private String notes; // 부모가 작성한 메모
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    // Hibernate 가 추적하는 컬렉션 인스턴스를 유지한 채 내용만 바꾼다
    public void replaceInterests(List<String> newInterests) {
        interests.clear();
        interests.addAll(newInterests);
    }
    
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
