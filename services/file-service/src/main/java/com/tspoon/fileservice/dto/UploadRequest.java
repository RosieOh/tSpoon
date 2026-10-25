package com.tspoon.fileservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UploadRequest(
        @NotBlank(message = "파일 이름은 필수입니다")
        @Size(max = 200, message = "파일 이름은 200자 이하여야 합니다")
        String fileName,

        @NotBlank(message = "파일 형식(contentType)은 필수입니다")
        String contentType,

        @NotNull(message = "파일 크기는 필수입니다")
        @Positive(message = "빈 파일은 올릴 수 없습니다")
        Long size
) {
}
