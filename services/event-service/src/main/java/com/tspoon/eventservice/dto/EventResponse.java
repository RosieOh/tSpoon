package com.tspoon.eventservice.dto;

import com.tspoon.eventservice.entity.Event;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * @param remainingSeats 정원이 없으면 null
 */
public record EventResponse(
        Long id,
        String title,
        String description,
        Long organizerId,
        String organizerName,
        Event.EventType type,
        Event.EventStatus status,
        LocalDateTime startDate,
        LocalDateTime endDate,
        LocalDateTime registrationStartDate,
        LocalDateTime registrationEndDate,
        String location,
        String onlineLink,
        Integer maxParticipants,
        Integer currentParticipants,
        Integer remainingSeats,
        String imageUrl,
        String tags,
        String requirements,
        String contactInfo,
        Instant createdAt,
        Instant updatedAt
) {

    public static EventResponse from(Event event) {
        Integer remaining = event.getMaxParticipants() == null ? null
                : Math.max(0, event.getMaxParticipants() - event.getCurrentParticipants());
        return new EventResponse(
                event.getId(), event.getTitle(), event.getDescription(), event.getOrganizerId(), event.getOrganizerName(),
                event.getType(), event.getStatus(), event.getStartDate(), event.getEndDate(),
                event.getRegistrationStartDate(), event.getRegistrationEndDate(), event.getLocation(), event.getOnlineLink(),
                event.getMaxParticipants(), event.getCurrentParticipants(), remaining, event.getImageUrl(), event.getTags(),
                event.getRequirements(), event.getContactInfo(), event.getCreatedAt(), event.getUpdatedAt());
    }
}
