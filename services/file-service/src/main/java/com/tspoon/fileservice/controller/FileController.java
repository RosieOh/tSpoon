package com.tspoon.fileservice.controller;

import com.tspoon.auth.AuthUser;
import com.tspoon.fileservice.dto.FileResponse;
import com.tspoon.fileservice.dto.UploadRequest;
import com.tspoon.fileservice.dto.UploadTicket;
import com.tspoon.fileservice.service.FileService;
import com.tspoon.fileservice.storage.FileStorage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@Tag(name = "파일", description = "첨부파일·썸네일 업로드 — 업로드 URL 을 받아 저장소로 직접 올린 뒤 완료를 알린다")
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final FileStorage fileStorage;

    @Operation(summary = "업로드 요청 — 업로드 URL(Presigned PUT) 발급. 이미지(jpeg·png·webp·gif)·PDF, 10MB 까지")
    @PostMapping("/uploads")
    public ResponseEntity<UploadTicket> requestUpload(@AuthenticationPrincipal AuthUser user,
                                                      @Valid @RequestBody UploadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fileService.requestUpload(user, request));
    }

    @Operation(summary = "업로드 완료 — 저장소에 올라간 크기·형식을 확인한다")
    @PostMapping("/{id}/complete")
    public ResponseEntity<FileResponse> complete(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return ResponseEntity.ok(fileService.complete(user, id));
    }

    @Operation(summary = "파일 정보와 내려받기 URL (잠시 동안만 유효)")
    @SecurityRequirements
    @GetMapping("/{id}")
    public ResponseEntity<FileResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(fileService.get(id));
    }

    @Operation(summary = "내가 올린 파일")
    @GetMapping
    public ResponseEntity<PagedModel<FileResponse>> myFiles(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page 는 0 이상이어야 합니다") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 는 1 이상이어야 합니다")
            @Max(value = 100, message = "size 는 100 이하여야 합니다") int size) {
        return ResponseEntity.ok(new PagedModel<>(fileService.myFiles(user, page, size)));
    }

    @Operation(summary = "파일 삭제 (올린 사람만)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        fileService.delete(user, id);
        return ResponseEntity.noContent().build();
    }

    // 기동할 때 버킷을 만들어 둔다. 저장소가 아직이면 첫 업로드 요청 때 다시 시도한다
    @EventListener(ApplicationReadyEvent.class)
    public void createBucket() {
        try {
            fileStorage.createBucketIfMissing();
        } catch (RuntimeException e) {
            log.warn("파일 버킷을 아직 만들지 못했습니다 (첫 요청 때 다시 시도): {}", e.getMessage());
        }
    }
}
