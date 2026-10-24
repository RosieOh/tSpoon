package com.tspoon.eventservice.dto;

import com.tspoon.eventservice.entity.Event;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 일정은 한국 시간(현지 시각)으로 받는다. 예) 2026-11-20T14:00:00
 *
 * @param maxParticipants 비우면 정원 제한 없음
 */
public record EventRequest(
        @NotBlank(message = "제목은 필수입니다")
        @Size(max = 255, message = "제목은 255자 이하여야 합니다")
        String title,

        @NotBlank(message = "설명은 필수입니다")
        String description,

        @NotNull(message = "행사 유형은 필수입니다")
        Event.EventType type,

        @NotNull(message = "시작 일시는 필수입니다")
        LocalDateTime startDate,

        @NotNull(message = "종료 일시는 필수입니다")
        LocalDateTime endDate,

        LocalDateTime registrationStartDate,
        LocalDateTime registrationEndDate,
        String location,
        String onlineLink,

        @Min(value = 1, message = "정원은 1명 이상이어야 합니다")
        Integer maxParticipants,

        String imageUrl,
        String tags,
        String requirements,
        String contactInfo
) {

    @AssertTrue(message = "종료 일시는 시작 일시보다 뒤여야 합니다")
    public boolean isEndAfterStart() {
        return startDate == null || endDate == null || endDate.isAfter(startDate);
    }

    @AssertTrue(message = "신청 마감은 신청 시작보다 뒤, 행사 시작 이전이어야 합니다")
    public boolean isRegistrationPeriodValid() {
        if (registrationEndDate == null) {
            return true;
        }
        boolean afterStart = registrationStartDate == null || registrationEndDate.isAfter(registrationStartDate);
        boolean beforeEvent = startDate == null || !registrationEndDate.isAfter(startDate);
        return afterStart && beforeEvent;
    }
}
