package com.tspoon.fileservice.dto;

import com.tspoon.fileservice.entity.StoredFile;

import java.time.Instant;
import java.util.UUID;

/**
 * @param downloadUrl 잠시 동안만 쓸 수 있는 내려받기 주소 (필요할 때마다 새로 받는다)
 */
public record FileResponse(
        UUID id,
        String fileName,
        String contentType,
        long size,
        StoredFile.FileStatus status,
        String downloadUrl,
        Instant createdAt,
        Instant uploadedAt
) {

    public static FileResponse of(StoredFile file, String downloadUrl) {
        return new FileResponse(file.getId(), file.getFileName(), file.getContentType(), file.getSize(), file.getStatus(),
                downloadUrl, file.getCreatedAt(), file.getUploadedAt());
    }
}
