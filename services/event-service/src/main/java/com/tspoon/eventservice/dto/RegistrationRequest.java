package com.tspoon.eventservice.dto;

import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @Size(max = 255, message = "메모는 255자 이하여야 합니다")
        String notes,

        @Size(max = 255, message = "연락처는 255자 이하여야 합니다")
        String contactInfo
) {
}
