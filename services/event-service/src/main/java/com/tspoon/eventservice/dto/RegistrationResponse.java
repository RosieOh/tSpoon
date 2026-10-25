package com.tspoon.eventservice.dto;

import com.tspoon.eventservice.entity.EventRegistration;

import java.time.Instant;
import java.time.LocalDateTime;

public record RegistrationResponse(
        Long id,
        Long eventId,
        String eventTitle,
        LocalDateTime eventStartDate,
        Long userId,
        String userName,
        EventRegistration.RegistrationStatus status,
        Long waitlistPosition, // 대기 중일 때만 — 1 이면 다음 차례
        String notes,
        String contactInfo,
        Instant createdAt
) {

    public static RegistrationResponse from(EventRegistration registration) {
        return from(registration, null);
    }

    public static RegistrationResponse from(EventRegistration registration, Long waitlistPosition) {
        return new RegistrationResponse(
                registration.getId(), registration.getEvent().getId(), registration.getEvent().getTitle(),
                registration.getEvent().getStartDate(), registration.getUserId(), registration.getUserName(),
                registration.getStatus(), waitlistPosition, registration.getNotes(), registration.getContactInfo(),
                registration.getCreatedAt());
    }
}
