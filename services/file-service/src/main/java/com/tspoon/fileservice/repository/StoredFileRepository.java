package com.tspoon.fileservice.repository;

import com.tspoon.fileservice.entity.StoredFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    Optional<StoredFile> findByIdAndOwnerId(UUID id, Long ownerId);

    Optional<StoredFile> findByIdAndStatus(UUID id, StoredFile.FileStatus status);

    Page<StoredFile> findByOwnerIdAndStatusOrderByCreatedAtDesc(Long ownerId, StoredFile.FileStatus status, Pageable pageable);

    List<StoredFile> findTop100ByStatusAndCreatedAtBefore(StoredFile.FileStatus status, Instant cutoff);
}
