package com.tspoon.eventservice.service;

import com.tspoon.eventservice.config.EventReminderProperties;
import com.tspoon.eventservice.entity.Event;
import com.tspoon.eventservice.entity.Event.EventStatus;
import com.tspoon.eventservice.entity.EventRegistration.RegistrationStatus;
import com.tspoon.eventservice.event.EventEvents;
import com.tspoon.eventservice.repository.EventRegistrationRepository;
import com.tspoon.eventservice.repository.EventRepository;
import com.tspoon.outbox.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 시작이 가까운 행사의 신청자에게 리마인더를 보낸다.
 * 행사마다 reminder_sent_at 이 비어 있을 때만 조건부 UPDATE 로 채워(선점) 보내므로, 인스턴스가 여러 대여도 행사당 한 번만 나간다.
 * 같은 행을 두 인스턴스가 동시에 UPDATE 하면 PostgreSQL 이 앞 트랜잭션이 끝날 때까지 기다렸다가 조건을 다시 평가한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventReminderService {

    private static final Set<EventStatus> REMINDABLE = EnumSet.of(EventStatus.ACTIVE, EventStatus.FULL);
    private static final int BATCH_SIZE = 100;

    private final EventRepository eventRepository;
    private final EventRegistrationRepository registrationRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final EventReminderProperties properties;
    private final Clock clock;

    /** @return 리마인더를 보낸 행사 수 */
    @Transactional
    public int sendDueReminders() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> dueEventIds = eventRepository.findIdsDueForReminder(REMINDABLE, now, now.plus(properties.leadTime()),
                PageRequest.of(0, BATCH_SIZE));

        int sent = 0;
        for (Long eventId : dueEventIds) {
            if (eventRepository.claimReminder(eventId, Instant.now(clock)) == 0) {
                continue; // 다른 인스턴스가 먼저 보냈다
            }
            List<Long> recipients = registrationRepository.findUserIds(eventId, Set.of(RegistrationStatus.REGISTERED));
            if (recipients.isEmpty()) {
                continue;
            }
            Event event = eventRepository.findById(eventId).orElseThrow();
            outboxEventPublisher.publish(EventEvents.TOPIC, EventEvents.AGGREGATE_EVENT, eventId, EventEvents.EVENT_REMINDER,
                    new EventEvents.EventReminder(eventId, event.getTitle(), event.getStartDate(), event.getLocation(), recipients));
            sent++;
        }
        if (sent > 0) {
            log.info("행사 {}건의 신청자에게 리마인더를 보냈습니다.", sent);
        }
        return sent;
    }
}
