package com.tspoon.fileservice.service;

import com.tspoon.auth.AuthUser;
import com.tspoon.core.exception.BusinessException;
import com.tspoon.core.exception.ResourceNotFoundException;
import com.tspoon.fileservice.config.FileUploadProperties;
import com.tspoon.fileservice.dto.FileResponse;
import com.tspoon.fileservice.dto.UploadRequest;
import com.tspoon.fileservice.dto.UploadTicket;
import com.tspoon.fileservice.entity.StoredFile;
import com.tspoon.fileservice.entity.StoredFile.FileStatus;
import com.tspoon.fileservice.repository.StoredFileRepository;
import com.tspoon.fileservice.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 파일 업로드 흐름.
 * <ol>
 *   <li>업로드 요청 — 형식·크기를 확인하고 Presigned PUT URL 을 준다 (형식·크기가 서명에 들어간다)</li>
 *   <li>클라이언트가 저장소로 직접 올린다 — 서비스는 바이트를 다루지 않는다</li>
 *   <li>완료 알림 — 저장소에 실제로 올라간 크기·형식을 다시 확인하고서야 쓸 수 있는 파일이 된다</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FileService {

    private final StoredFileRepository fileRepository;
    private final FileStorage storage;
    private final FileUploadProperties properties;

    public UploadTicket requestUpload(AuthUser owner, UploadRequest request) {
        String contentType = request.contentType().strip().toLowerCase(Locale.ROOT);
        if (!properties.allowedTypes().contains(contentType)) {
            throw new BusinessException("올릴 수 없는 파일 형식입니다. 허용: " + String.join(", ", properties.allowedTypes()));
        }
        if (request.size() > properties.maxSize().toBytes()) {
            throw new BusinessException("파일은 " + properties.maxSize().toMegabytes() + "MB 까지 올릴 수 있습니다.");
        }

        StoredFile file = fileRepository.save(StoredFile.builder()
                .ownerId(owner.userId())
                .fileName(safeFileName(request.fileName()))
                .contentType(contentType)
                .size(request.size())
                // 원래 이름은 경로에 쓰지 않는다 (경로 조작·충돌 방지)
                .storageKey("files/" + UUID.randomUUID())
                .status(FileStatus.PENDING)
                .build());

        PresignedPutObjectRequest presigned = storage.presignUpload(file.getStorageKey(), contentType, request.size(),
                properties.uploadUrlTtl());
        return new UploadTicket(file.getId(), presigned.url().toString(), "PUT", Map.of("Content-Type", contentType),
                presigned.expiration());
    }

    /** 저장소에 실제로 올라간 크기·형식이 요청과 같아야 완료된다. 다르면 올라간 것을 지우고 400 */
    public FileResponse complete(AuthUser owner, UUID id) {
        StoredFile file = fileRepository.findByIdAndOwnerId(id, owner.userId())
                .orElseThrow(() -> new ResourceNotFoundException("파일을 찾을 수 없습니다."));
        if (file.getStatus() == FileStatus.UPLOADED) {
            return response(file);
        }

        HeadObjectResponse uploaded = storage.head(file.getStorageKey())
                .orElseThrow(() -> new BusinessException("아직 저장소에 올라간 파일이 없습니다. 업로드 URL 로 먼저 올려 주세요.",
                        HttpStatus.CONFLICT));
        boolean sameSize = Objects.equals(uploaded.contentLength(), file.getSize()); // Long 끼리 == 는 참조 비교다
        boolean sameType = uploaded.contentType() != null
                && uploaded.contentType().toLowerCase(Locale.ROOT).startsWith(file.getContentType());
        if (!sameSize || !sameType) {
            log.info("파일 {} 확인 실패 — 요청 {}·{}바이트, 실제 {}·{}바이트", id, file.getContentType(), file.getSize(),
                    uploaded.contentType(), uploaded.contentLength());
            storage.delete(file.getStorageKey());
            throw new BusinessException("올라간 파일이 요청한 크기·형식과 다릅니다. 다시 올려 주세요.");
        }

        file.setStatus(FileStatus.UPLOADED);
        file.setUploadedAt(Instant.now());
        return response(file);
    }

    @Transactional(readOnly = true)
    public FileResponse get(UUID id) {
        return response(fileRepository.findByIdAndStatus(id, FileStatus.UPLOADED)
                .orElseThrow(() -> new ResourceNotFoundException("파일을 찾을 수 없습니다.")));
    }

    @Transactional(readOnly = true)
    public Page<FileResponse> myFiles(AuthUser owner, int page, int size) {
        return fileRepository.findByOwnerIdAndStatusOrderByCreatedAtDesc(owner.userId(), FileStatus.UPLOADED, PageRequest.of(page, size))
                .map(this::response);
    }

    public void delete(AuthUser owner, UUID id) {
        StoredFile file = fileRepository.findByIdAndOwnerId(id, owner.userId())
                .orElseThrow(() -> new ResourceNotFoundException("파일을 찾을 수 없습니다."));
        storage.delete(file.getStorageKey());
        fileRepository.delete(file);
    }

    /** 업로드 URL 만 받고 올리지 않았거나 완료를 알리지 않은 요청을 정리한다 (저장소에 올라간 것도 함께) */
    @Scheduled(cron = "${tspoon.file.upload.cleanup-cron:0 0 * * * *}")
    public int deleteAbandonedUploads() {
        int deleted = 0;
        for (StoredFile file : fileRepository.findTop100ByStatusAndCreatedAtBefore(FileStatus.PENDING,
                Instant.now().minus(properties.pendingRetention()))) {
            storage.delete(file.getStorageKey());
            fileRepository.delete(file);
            deleted++;
        }
        if (deleted > 0) {
            log.info("올리다 만 파일 요청 {}건을 정리했습니다.", deleted);
        }
        return deleted;
    }

    private FileResponse response(StoredFile file) {
        String downloadUrl = file.getStatus() == FileStatus.UPLOADED
                ? storage.presignDownload(file.getStorageKey(), file.getFileName(), properties.downloadUrlTtl()).toString()
                : null;
        return FileResponse.of(file, downloadUrl);
    }

    // 경로 구분자·제어 문자를 빼고 이름만 남긴다
    private static String safeFileName(String fileName) {
        String name = fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw new BusinessException("올바른 파일 이름이 아닙니다.");
        }
        return name;
    }
}
