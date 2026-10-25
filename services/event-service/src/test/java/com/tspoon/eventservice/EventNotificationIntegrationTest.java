package com.tspoon.eventservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.auth.AuthUser;
import com.tspoon.auth.jwt.JwtUtil;
import com.tspoon.core.test.TestcontainersConfiguration;
import com.tspoon.event.test.EventContracts;
import com.tspoon.eventservice.event.EventEvents;
import com.tspoon.eventservice.service.EventReminderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.kafka.listener.auto-startup=false",
        "tspoon.outbox.relay-enabled=false", "tspoon.event.reminder.enabled=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EventNotificationIntegrationTest {

    private static final AtomicLong USERS = new AtomicLong(50_000);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EventReminderService reminderService;

    @Test
    void 시작_24시간_안에_드는_행사의_신청자에게_리마인더를_한_번만_보낸다() throws Exception {
        long soon = createEvent(Duration.ofHours(3));
        long later = createEvent(Duration.ofDays(3));
        AuthUser staying = newUser();
        AuthUser leaving = newUser();
        register(staying, soon);
        register(leaving, soon);
        assertThat(mvc.delete().uri("/api/events/{id}/registrations/me", soon).header(HttpHeaders.AUTHORIZATION, bearer(leaving)))
                .hasStatus(HttpStatus.NO_CONTENT);
        register(newUser(), later);

        reminderService.sendDueReminders();

        List<String> reminders = events(EventEvents.EVENT_REMINDER, soon);
        assertThat(reminders).singleElement().satisfies(payload -> {
            EventContracts.assertPayloadConforms(EventEvents.EVENT_REMINDER, payload);
            assertThat(JsonPath.<List<Number>>read(payload, "$.recipientIds")).map(Number::longValue)
                    .containsExactly(staying.userId());
            assertThat(JsonPath.<String>read(payload, "$.location")).isEqualTo("서울");
        });
        assertThat(events(EventEvents.EVENT_REMINDER, later)).isEmpty();

        reminderService.sendDueReminders();
        assertThat(events(EventEvents.EVENT_REMINDER, soon)).hasSize(1);
    }

    @Test
    void 여러_인스턴스가_동시에_찾아도_리마인더는_한_번만_나간다() throws Exception {
        long soon = createEvent(Duration.ofHours(5));
        register(newUser(), soon);

        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> runs = IntStream.range(0, 5).mapToObj(i -> executor.submit(() -> {
                start.await();
                return reminderService.sendDueReminders();
            })).toList();
            start.countDown();
            for (Future<Integer> run : runs) {
                run.get();
            }
        }

        assertThat(events(EventEvents.EVENT_REMINDER, soon)).hasSize(1);
    }

    @Test
    void 행사를_취소하면_주최자를_뺀_신청자에게_알릴_이벤트를_남기고_다시_취소하면_409() throws Exception {
        AuthUser organizer = newUser();
        long eventId = createEvent(organizer, Duration.ofDays(2));
        AuthUser first = newUser();
        AuthUser second = newUser();
        register(first, eventId);
        register(second, eventId);
        register(organizer, eventId);

        assertThat(cancel(organizer, eventId)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(events(EventEvents.EVENT_CANCELLED, eventId)).singleElement().satisfies(payload -> {
            EventContracts.assertPayloadConforms(EventEvents.EVENT_CANCELLED, payload);
            assertThat(JsonPath.<List<Number>>read(payload, "$.recipientIds")).map(Number::longValue)
                    .containsExactly(first.userId(), second.userId());
        });
        assertThat(cancel(organizer, eventId)).hasStatus(HttpStatus.CONFLICT);
        // 취소된 행사는 리마인더 대상이 아니다
        reminderService.sendDueReminders();
        assertThat(events(EventEvents.EVENT_REMINDER, eventId)).isEmpty();
    }

    private List<String> events(String eventType, long eventId) {
        return jdbcTemplate.queryForList("""
                SELECT payload::text FROM outbox_events WHERE event_type = ? AND aggregate_id = ? ORDER BY created_at
                """, String.class, eventType, String.valueOf(eventId));
    }

    private MvcTestResult cancel(AuthUser organizer, long eventId) {
        return mvc.post().uri("/api/events/{id}/cancel", eventId).header(HttpHeaders.AUTHORIZATION, bearer(organizer)).exchange();
    }

    private void register(AuthUser user, long eventId) {
        assertThat(mvc.post().uri("/api/events/{id}/registrations", eventId).header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).hasStatus(HttpStatus.CREATED);
    }

    private long createEvent(Duration startsIn) throws Exception {
        return createEvent(newUser(), startsIn);
    }

    private long createEvent(AuthUser organizer, Duration startsIn) throws Exception {
        LocalDateTime start = LocalDateTime.now(ZoneId.of("Asia/Seoul")).plus(startsIn).withNano(0);
        MvcTestResult created = mvc.post().uri("/api/events").header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "알림 행사", "description": "설명", "type": "SEMINAR",
                         "startDate": "%s", "endDate": "%s", "location": "서울", "maxParticipants": 10}
                        """.formatted(start, start.plusHours(2)))
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return JsonPath.<Number>read(created.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id").longValue();
    }

    private static AuthUser newUser() {
        long id = USERS.incrementAndGet();
        return new AuthUser(id, "user" + id + "@tspoon.com", "회원" + id, "TEACHER");
    }

    private String bearer(AuthUser user) {
        return "Bearer " + jwtUtil.generateAccessToken(user);
    }
}
