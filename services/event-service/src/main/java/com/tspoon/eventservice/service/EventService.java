package com.tspoon.eventservice.service;

import com.tspoon.auth.AuthUser;
import com.tspoon.core.exception.BusinessException;
import com.tspoon.core.exception.ResourceNotFoundException;
import com.tspoon.eventservice.dto.EventRequest;
import com.tspoon.eventservice.dto.EventResponse;
import com.tspoon.eventservice.dto.RegistrationRequest;
import com.tspoon.eventservice.dto.RegistrationResponse;
import com.tspoon.eventservice.entity.Event;
import com.tspoon.eventservice.entity.Event.EventStatus;
import com.tspoon.eventservice.entity.EventRegistration;
import com.tspoon.eventservice.entity.EventRegistration.RegistrationStatus;
import com.tspoon.eventservice.repository.EventRegistrationRepository;
import com.tspoon.eventservice.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 행사와 참가 신청. 정원은 조건부 UPDATE(reserveSeat)로 확보해 동시 신청에서도 넘지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EventService {

    private static final Set<EventStatus> LISTED = EnumSet.of(EventStatus.ACTIVE, EventStatus.FULL);

    private final EventRepository eventRepository;
    private final EventRegistrationRepository registrationRepository;
    private final Clock clock;

    /**
     * @param status 비우면 모집 중(ACTIVE)·마감(FULL) 행사
     */
    @Transactional(readOnly = true)
    public Page<EventResponse> search(Event.EventType type, EventStatus status, int page, int size) {
        Set<EventStatus> statuses = status == null ? LISTED : Set.of(status);
        return eventRepository.search(statuses, type, PageRequest.of(page, size)).map(EventResponse::from);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Long id) {
        return EventResponse.from(findEvent(id));
    }

    public EventResponse create(AuthUser organizer, EventRequest request) {
        Event event = Event.builder()
                .organizerId(organizer.userId())
                .organizerName(organizer.name())
                .status(EventStatus.ACTIVE)
                .build();
        apply(event, request);
        return EventResponse.from(eventRepository.save(event));
    }

    public EventResponse update(AuthUser user, Long id, EventRequest request) {
        Event event = findManageable(id, user, "행사를 수정할 권한이 없습니다.");
        if (request.maxParticipants() != null && request.maxParticipants() < event.getCurrentParticipants()) {
            throw new BusinessException("정원을 현재 신청 인원(" + event.getCurrentParticipants() + "명)보다 줄일 수 없습니다.",
                    HttpStatus.CONFLICT);
        }
        apply(event, request);
        if (event.getStatus() == EventStatus.FULL && (event.getMaxParticipants() == null
                || event.getCurrentParticipants() < event.getMaxParticipants())) {
            event.setStatus(EventStatus.ACTIVE); // 정원을 늘리면 다시 모집
        }
        return EventResponse.from(eventRepository.saveAndFlush(event));
    }

    public void cancel(AuthUser user, Long id) {
        Event event = findManageable(id, user, "행사를 취소할 권한이 없습니다.");
        event.setStatus(EventStatus.CANCELLED);
    }

    public RegistrationResponse register(AuthUser user, Long eventId, RegistrationRequest request) {
        Event event = findEvent(eventId);
        verifyRegistrationOpen(event);

        Optional<EventRegistration> existing = registrationRepository.findByEventIdAndUserId(eventId, user.userId());
        if (existing.filter(registration -> registration.getStatus() == RegistrationStatus.REGISTERED).isPresent()) {
            throw new BusinessException("이미 신청한 행사입니다.", HttpStatus.CONFLICT);
        }
        if (eventRepository.reserveSeat(eventId) == 0) {
            throw new BusinessException("정원이 찼습니다.", HttpStatus.CONFLICT);
        }

        // 취소했던 신청은 되살리고, 처음이면 새로 만든다 (같은 회원의 동시 신청은 (event_id, user_id) 유니크 제약이 막는다)
        EventRegistration registration = existing
                .flatMap(cancelled -> registrationRepository.findById(cancelled.getId()))
                .orElseGet(() -> EventRegistration.builder()
                        .event(eventRepository.getReferenceById(eventId))
                        .userId(user.userId())
                        .userName(user.name())
                        .build());
        registration.setStatus(RegistrationStatus.REGISTERED);
        registration.setNotes(request.notes());
        registration.setContactInfo(request.contactInfo());
        return RegistrationResponse.from(registrationRepository.saveAndFlush(registration));
    }

    public void cancelRegistration(AuthUser user, Long eventId) {
        Event event = findEvent(eventId);
        if (!now().isBefore(event.getStartDate())) {
            throw new BusinessException("행사가 시작된 뒤에는 신청을 취소할 수 없습니다.", HttpStatus.CONFLICT);
        }
        EventRegistration registration = registrationRepository.findByEventIdAndUserId(eventId, user.userId())
                .filter(found -> found.getStatus() == RegistrationStatus.REGISTERED)
                .orElseThrow(() -> new ResourceNotFoundException("신청 내역을 찾을 수 없습니다."));
        registration.setStatus(RegistrationStatus.CANCELLED);
        eventRepository.releaseSeat(eventId);
    }

    @Transactional(readOnly = true)
    public List<RegistrationResponse> getRegistrations(AuthUser user, Long eventId) {
        findManageable(eventId, user, "신청자 목록은 주최자만 볼 수 있습니다.");
        return registrationRepository.findByEventIdAndStatusOrderByCreatedAtAsc(eventId, RegistrationStatus.REGISTERED)
                .stream().map(RegistrationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<RegistrationResponse> getMyRegistrations(AuthUser user, int page, int size) {
        return registrationRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                user.userId(), RegistrationStatus.REGISTERED, PageRequest.of(page, size)).map(RegistrationResponse::from);
    }

    private void verifyRegistrationOpen(Event event) {
        switch (event.getStatus()) {
            case ACTIVE -> { }
            case FULL -> throw new BusinessException("정원이 찼습니다.", HttpStatus.CONFLICT);
            default -> throw new BusinessException("신청할 수 없는 행사입니다.", HttpStatus.CONFLICT);
        }
        LocalDateTime now = now();
        LocalDateTime opensAt = event.getRegistrationStartDate();
        LocalDateTime closesAt = event.getRegistrationEndDate() != null ? event.getRegistrationEndDate() : event.getStartDate();
        if (opensAt != null && now.isBefore(opensAt)) {
            throw new BusinessException("아직 신청 기간이 아닙니다.", HttpStatus.CONFLICT);
        }
        if (!now.isBefore(closesAt)) {
            throw new BusinessException("신청 기간이 끝났습니다.", HttpStatus.CONFLICT);
        }
    }

    private static void apply(Event event, EventRequest request) {
        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setType(request.type());
        event.setStartDate(request.startDate());
        event.setEndDate(request.endDate());
        event.setRegistrationStartDate(request.registrationStartDate());
        event.setRegistrationEndDate(request.registrationEndDate());
        event.setLocation(request.location());
        event.setOnlineLink(request.onlineLink());
        event.setMaxParticipants(request.maxParticipants());
        event.setImageUrl(request.imageUrl());
        event.setTags(request.tags());
        event.setRequirements(request.requirements());
        event.setContactInfo(request.contactInfo());
    }

    private Event findEvent(Long id) {
        return eventRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("행사를 찾을 수 없습니다."));
    }

    private Event findManageable(Long id, AuthUser user, String forbiddenMessage) {
        Event event = findEvent(id);
        if (!event.getOrganizerId().equals(user.userId()) && !user.isAdmin()) {
            throw new BusinessException(forbiddenMessage, HttpStatus.FORBIDDEN);
        }
        return event;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
