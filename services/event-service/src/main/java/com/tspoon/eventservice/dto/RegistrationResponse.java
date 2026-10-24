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
        String notes,
        String contactInfo,
        Instant createdAt
) {

    public static RegistrationResponse from(EventRegistration registration) {
        return new RegistrationResponse(
                registration.getId(), registration.getEvent().getId(), registration.getEvent().getTitle(),
                registration.getEvent().getStartDate(), registration.getUserId(), registration.getUserName(),
                registration.getStatus(), registration.getNotes(), registration.getContactInfo(), registration.getCreatedAt());
    }
}
