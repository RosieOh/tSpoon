package com.tspoon.fileservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;

/**
 * @param maxSize          파일 하나의 최대 크기
 * @param allowedTypes     올릴 수 있는 형식 (이미지·PDF)
 * @param uploadUrlTtl     업로드 URL 유효 시간
 * @param downloadUrlTtl   내려받기 URL 유효 시간
 * @param pendingRetention 업로드 URL 만 받고 올리지 않은 요청을 지우기까지의 기간
 */
@ConfigurationProperties("tspoon.file.upload")
public record FileUploadProperties(
        @DefaultValue("10MB") DataSize maxSize,
        @DefaultValue({"image/jpeg", "image/png", "image/webp", "image/gif", "application/pdf"}) List<String> allowedTypes,
        @DefaultValue("10m") Duration uploadUrlTtl,
        @DefaultValue("10m") Duration downloadUrlTtl,
        @DefaultValue("1d") Duration pendingRetention
) {
}
