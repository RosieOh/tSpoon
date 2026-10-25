package com.tspoon.fileservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 호환 저장소 (로컬은 SeaweedFS, 운영은 S3 등).
 *
 * @param endpoint       서비스가 저장소에 닿는 주소
 * @param publicEndpoint Presigned URL 에 넣는 주소 — 클라이언트가 닿을 수 있어야 한다. 서명에 호스트가 들어가므로
 *                       컨테이너 안 주소(endpoint)와 다를 때 따로 둔다
 */
@ConfigurationProperties("tspoon.file.storage")
public record FileStorageProperties(
        String endpoint,
        String publicEndpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey
) {
}
