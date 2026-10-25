package com.tspoon.fileservice.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 업로드 안내 — 클라이언트는 uploadUrl 로 method·headers 그대로 파일 바이트를 보낸 뒤 완료를 알린다.
 *
 * @param headers 반드시 함께 보내야 하는 헤더 (서명에 들어 있다)
 */
public record UploadTicket(UUID fileId, String uploadUrl, String method, Map<String, String> headers, Instant expiresAt) {
}
