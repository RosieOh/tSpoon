package com.tspoon.moderation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportRequest(
        @NotNull(message = "신고 사유는 필수입니다")
        ReportReason reason,

        @Size(max = 500, message = "설명은 500자 이하여야 합니다")
        String description
) {

    @JsonIgnore
    @AssertTrue(message = "기타 사유는 설명이 필요합니다")
    public boolean isDescriptionPresentForOther() {
        return reason != ReportReason.OTHER || (description != null && !description.isBlank());
    }
}
