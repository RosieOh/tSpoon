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
import com.tspoon.eventservice.event.EventEvents;
import com.tspoon.eventservice.repository.EventRegistrationRepository;
import com.tspoon.eventservice.repository.EventRepository;
import com.tspoon.outbox.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 행사와 참가 신청. 정원은 조건부 UPDATE(reserveSeat)로 확보해 동시 신청에서도 넘지 않는다.
 *
 * 정원이 차면 대기자로 받고, 확정된 신청이 취소되거나 정원이 늘면 먼저 대기한 순서대로 확정한다(자리를 넘겨받으므로 참가 인원은 그대로).
 * 신청·취소는 행사 행을 잠근 뒤 처리해, 자리가 나는 순간과 대기 등록이 엇갈려 자리가 빈 채로 대기자가 남는 일이 없게 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EventService {

    private static final Set<EventStatus> LISTED = EnumSet.of(EventStatus.ACTIVE, EventStatus.FULL);
    private static final Set<RegistrationStatus> HOLDING = EnumSet.of(RegistrationStatus.REGISTERED, RegistrationStatus.WAITLISTED);

    private final EventRepository eventRepository;
    private final EventRegistrationRepository registrationRepository;
    private final OutboxEventPublisher outboxEventPublisher;
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

    // 정원을 늘리면 늘어난 자리만큼 대기자를 순서대로 확정한다
    public EventResponse update(AuthUser user, Long id, EventRequest request) {
        Event event = findManageable(eventRepository.findLockedById(id), user, "행사를 수정할 권한이 없습니다.");
        if (request.maxParticipants() != null && request.maxParticipants() < event.getCurrentParticipants()) {
            throw new BusinessException("정원을 현재 신청 인원(" + event.getCurrentParticipants() + "명)보다 줄일 수 없습니다.",
                    HttpStatus.CONFLICT);
        }
        apply(event, request);
        if (event.getStatus() == EventStatus.FULL && (event.getMaxParticipants() == null
                || event.getCurrentParticipants() < event.getMaxParticipants())) {
            event.setStatus(EventStatus.ACTIVE); // 정원을 늘리면 다시 모집
        }
        eventRepository.saveAndFlush(event);

        Optional<EventRegistration> next;
        while ((next = firstWaitlisted(id)).isPresent() && eventRepository.reserveSeat(id) == 1) {
            promote(registrationRepository.findById(next.get().getId()).orElseThrow());
        }
        return EventResponse.from(findEvent(id));
    }

    // 신청자·대기자에게 취소를 알린다 (알림 서비스는 신청 내역을 모르므로 받을 회원을 담아 보낸다)
    public void cancel(AuthUser user, Long id) {
        Event event = findManageable(eventRepository.findById(id), user, "행사를 취소할 권한이 없습니다.");
        if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.COMPLETED) {
            throw new BusinessException("이미 취소했거나 끝난 행사입니다.", HttpStatus.CONFLICT);
        }
        event.setStatus(EventStatus.CANCELLED);

        List<Long> recipients = registrationRepository.findUserIds(id, HOLDING).stream()
                .filter(userId -> !userId.equals(event.getOrganizerId()))
                .toList();
        if (!recipients.isEmpty()) {
            outboxEventPublisher.publish(EventEvents.TOPIC, EventEvents.AGGREGATE_EVENT, id, EventEvents.EVENT_CANCELLED,
                    new EventEvents.EventCancelled(id, event.getTitle(), event.getStartDate(), recipients));
        }
    }

    /** 자리가 있으면 확정, 정원이 찼으면 대기자로 받는다 */
    public RegistrationResponse register(AuthUser user, Long eventId, RegistrationRequest request) {
        Event event = eventRepository.findLockedById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("행사를 찾을 수 없습니다."));
        verifyRegistrationOpen(event);

        Optional<EventRegistration> existing = registrationRepository.findByEventIdAndUserId(eventId, user.userId());
        if (existing.filter(registration -> registration.getStatus() == RegistrationStatus.REGISTERED).isPresent()) {
            throw new BusinessException("이미 신청한 행사입니다.", HttpStatus.CONFLICT);
        }
        if (existing.filter(registration -> registration.getStatus() == RegistrationStatus.WAITLISTED).isPresent()) {
            throw new BusinessException("이미 대기 중인 행사입니다.", HttpStatus.CONFLICT);
        }
        boolean seated = eventRepository.reserveSeat(eventId) == 1;

        // 취소했던 신청은 되살리고, 처음이면 새로 만든다 (같은 회원의 동시 신청은 (event_id, user_id) 유니크 제약이 막는다)
        EventRegistration registration = existing
                .flatMap(cancelled -> registrationRepository.findById(cancelled.getId()))
                .orElseGet(() -> EventRegistration.builder()
                        .event(eventRepository.getReferenceById(eventId))
                        .userId(user.userId())
                        .userName(user.name())
                        .build());
        registration.setStatus(seated ? RegistrationStatus.REGISTERED : RegistrationStatus.WAITLISTED);
        registration.setWaitlistedAt(seated ? null : Instant.now(clock));
        registration.setNotes(request.notes());
        registration.setContactInfo(request.contactInfo());
        return response(registrationRepository.saveAndFlush(registration));
    }

    /** 확정이든 대기든 취소한다. 확정을 취소하면 자리를 다음 대기자에게 넘긴다 */
    public void cancelRegistration(AuthUser user, Long eventId) {
        Event event = eventRepository.findLockedById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("행사를 찾을 수 없습니다."));
        if (!now().isBefore(event.getStartDate())) {
            throw new BusinessException("행사가 시작된 뒤에는 신청을 취소할 수 없습니다.", HttpStatus.CONFLICT);
        }
        EventRegistration registration = registrationRepository.findByEventIdAndUserId(eventId, user.userId())
                .filter(found -> HOLDING.contains(found.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("신청 내역을 찾을 수 없습니다."));
        cancel(registration);
    }

    /** 탈퇴한 회원의 아직 시작하지 않은 행사 신청을 모두 취소한다 (확정이면 자리를 다음 대기자에게) */
    public int cancelUpcomingRegistrationsOf(Long userId) {
        int cancelled = 0;
        for (Long registrationId : registrationRepository.findIdsByUserIdAndStatusIn(userId, HOLDING)) {
            EventRegistration found = registrationRepository.findById(registrationId).orElseThrow();
            Event event = eventRepository.findLockedById(found.getEvent().getId()).orElseThrow();
            // 잠금을 얻는 사이 바뀌었을 수 있으므로 다시 읽는다
            EventRegistration registration = registrationRepository.findById(registrationId).orElseThrow();
            if (HOLDING.contains(registration.getStatus()) && now().isBefore(event.getStartDate())) {
                cancel(registration);
                cancelled++;
            }
        }
        return cancelled;
    }

    @Transactional(readOnly = true)
    public List<RegistrationResponse> getRegistrations(AuthUser user, Long eventId) {
        findManageable(eventRepository.findById(eventId), user, "신청자 목록은 주최자만 볼 수 있습니다.");
        return registrationRepository.findByEventIdAndStatusOrderByCreatedAtAsc(eventId, RegistrationStatus.REGISTERED)
                .stream().map(RegistrationResponse::from).toList();
    }

    // 확정과 대기 모두 — 대기는 순번과 함께
    @Transactional(readOnly = true)
    public Page<RegistrationResponse> getMyRegistrations(AuthUser user, int page, int size) {
        return registrationRepository.findByUserIdAndStatusInOrderByCreatedAtDesc(user.userId(), HOLDING, PageRequest.of(page, size))
                .map(this::response);
    }

    private void cancel(EventRegistration registration) {
        boolean seated = registration.getStatus() == RegistrationStatus.REGISTERED;
        Long eventId = registration.getEvent().getId();
        registration.setStatus(RegistrationStatus.CANCELLED);
        registration.setWaitlistedAt(null);
        registrationRepository.flush();
        if (!seated) {
            return;
        }
        firstWaitlisted(eventId).ifPresentOrElse(this::promote, () -> eventRepository.releaseSeat(eventId));
    }

    // 자리를 넘겨받으므로 참가 인원은 그대로 두고 상태만 바꾼다
    private void promote(EventRegistration waiting) {
        waiting.setStatus(RegistrationStatus.REGISTERED);
        waiting.setWaitlistedAt(null);
        registrationRepository.flush();
        Event event = waiting.getEvent();
        outboxEventPublisher.publish(EventEvents.TOPIC, EventEvents.AGGREGATE_EVENT, event.getId(), EventEvents.WAITLIST_PROMOTED,
                new EventEvents.WaitlistPromoted(event.getId(), event.getTitle(), event.getStartDate(), waiting.getUserId()));
    }

    private Optional<EventRegistration> firstWaitlisted(Long eventId) {
        return registrationRepository.findFirstByEventIdAndStatusOrderByWaitlistedAtAscIdAsc(eventId, RegistrationStatus.WAITLISTED);
    }

    private RegistrationResponse response(EventRegistration registration) {
        Long position = registration.getStatus() == RegistrationStatus.WAITLISTED
                ? registrationRepository.waitlistPosition(registration.getEvent().getId(), registration.getWaitlistedAt(), registration.getId())
                : null;
        return RegistrationResponse.from(registration, position);
    }

    private void verifyRegistrationOpen(Event event) {
        if (!LISTED.contains(event.getStatus())) {
            throw new BusinessException("신청할 수 없는 행사입니다.", HttpStatus.CONFLICT);
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

    private Event findManageable(Optional<Event> found, AuthUser user, String forbiddenMessage) {
        Event event = found.orElseThrow(() -> new ResourceNotFoundException("행사를 찾을 수 없습니다."));
        if (!event.getOrganizerId().equals(user.userId()) && !user.isAdmin()) {
            throw new BusinessException(forbiddenMessage, HttpStatus.FORBIDDEN);
        }
        return event;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
