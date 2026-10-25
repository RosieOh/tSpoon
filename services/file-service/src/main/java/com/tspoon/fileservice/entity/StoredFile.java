package com.tspoon.fileservice.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "files")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id; // 추측할 수 없는 공개 식별자

    @Column(nullable = false)
    private Long ownerId; // User Service의 사용자 ID

    @Column(nullable = false)
    private String fileName; // 올린 사람이 붙인 원래 이름 (내려받을 때 쓴다)

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private Long size;

    @Column(nullable = false, unique = true)
    private String storageKey; // 저장소 안의 경로 — 원래 이름과 무관하게 서버가 정한다

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    public enum FileStatus {
        PENDING,    // 업로드 URL 만 받았다
        UPLOADED    // 저장소에 올라간 것을 확인했다
    }
}
