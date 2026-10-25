package com.tspoon.eventservice.event;

import com.tspoon.event.EventEventTypes;
import com.tspoon.event.TspoonTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * event-service 가 발행하는 이벤트. 키는 행사 ID 라서 같은 행사의 이벤트는 같은 파티션에 순서대로 들어간다.
 * 받을 회원(recipientIds)은 발행 시점에 정해 담는다 — 알림 서비스는 신청 내역을 모른다.
 */
@Configuration(proxyBeanMethods = false)
public class EventEvents {

    public static final String TOPIC = TspoonTopics.EVENT_EVENTS;
    public static final String AGGREGATE_EVENT = "EVENT";
    public static final String EVENT_REMINDER = EventEventTypes.EVENT_REMINDER;
    public static final String EVENT_CANCELLED = EventEventTypes.EVENT_CANCELLED;
    public static final String WAITLIST_PROMOTED = EventEventTypes.WAITLIST_PROMOTED;

    // 로컬·CI 는 브로커가 하나라 복제본 1
    @Bean
    public NewTopic eventEventsTopic() {
        return TopicBuilder.name(TOPIC).partitions(TspoonTopics.PARTITIONS).replicas(1).build();
    }

    /** @param startDate 행사 현지(한국) 시각 */
    public record EventReminder(Long eventId, String title, LocalDateTime startDate, String location, List<Long> recipientIds) {
    }

    public record EventCancelled(Long eventId, String title, LocalDateTime startDate, List<Long> recipientIds) {
    }

    /** 대기하던 회원의 신청이 확정됐다 */
    public record WaitlistPromoted(Long eventId, String title, LocalDateTime startDate, Long userId) {
    }
}
