package com.tspoon.eventservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.auth.AuthUser;
import com.tspoon.auth.jwt.JwtUtil;
import com.tspoon.core.test.TestcontainersConfiguration;
import com.tspoon.event.test.EventContracts;
import com.tspoon.eventservice.dto.RegistrationRequest;
import com.tspoon.eventservice.dto.RegistrationResponse;
import com.tspoon.eventservice.entity.EventRegistration;
import com.tspoon.eventservice.event.EventEvents;
import com.tspoon.eventservice.repository.EventRegistrationRepository;
import com.tspoon.eventservice.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.AbstractJsonContentAssert;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.kafka.listener.auto-startup=false",
        "tspoon.outbox.relay-enabled=false", "tspoon.event.reminder.enabled=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EventApiIntegrationTest {

    private static final AuthUser ORGANIZER = new AuthUser(300L, "teacher@tspoon.com", "주최교사", "TEACHER");
    private static final AuthUser PARENT = new AuthUser(301L, "parent@tspoon.com", "학부모", "PARENT");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    EventService eventService;

    @Autowired
    EventRegistrationRepository registrationRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 정원_10명_행사에_50명이_동시에_신청하면_10명은_확정되고_40명은_겹치지_않는_순번으로_대기한다() throws Exception {
        long eventId = createEvent(10, null, null);

        CountDownLatch start = new CountDownLatch(1);
        List<RegistrationResponse> responses;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<RegistrationResponse>> results = LongStream.rangeClosed(1, 50).mapToObj(i -> executor.submit(() -> {
                start.await();
                return eventService.register(new AuthUser(10_000 + i, "u" + i + "@tspoon.com", "신청자" + i, "PARENT"),
                        eventId, new RegistrationRequest(null, null));
            })).toList();
            start.countDown();
            responses = results.stream().map(EventApiIntegrationTest::get).toList();
        }

        assertThat(responses).filteredOn(r -> r.status() == EventRegistration.RegistrationStatus.REGISTERED).hasSize(10);
        assertThat(responses).filteredOn(r -> r.status() == EventRegistration.RegistrationStatus.WAITLISTED)
                .extracting(RegistrationResponse::waitlistPosition)
                .containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, 40).boxed().toList());
        assertThat(registrationRepository.countByEventIdAndStatus(eventId, EventRegistration.RegistrationStatus.REGISTERED))
                .isEqualTo(10);
        MvcTestResult event = mvc.get().uri("/api/events/{id}", eventId).exchange();
        assertThat(event).bodyJson().extractingPath("$.currentParticipants").isEqualTo(10);
        assertThat(event).bodyJson().extractingPath("$.remainingSeats").isEqualTo(0);
        assertThat(event).bodyJson().extractingPath("$.status").isEqualTo("FULL");
    }

    @Test
    void 신청을_취소하면_자리가_돌아와_다시_모집하고_취소한_사람도_다시_신청할_수_있다() throws Exception {
        long eventId = createEvent(1, null, null);
        AuthUser other = new AuthUser(302L, "other@tspoon.com", "다른학부모", "PARENT");

        assertThat(register(PARENT, eventId)).hasStatus(HttpStatus.CREATED);
        assertThat(register(PARENT, eventId)).hasStatus(HttpStatus.CONFLICT); // 중복
        assertThat(mvc.get().uri("/api/events/{id}", eventId)).bodyJson().extractingPath("$.status").isEqualTo("FULL");

        assertThat(cancelRegistration(PARENT, eventId)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/events/{id}", eventId)).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");

        assertThat(register(other, eventId)).hasStatus(HttpStatus.CREATED);
        assertThat(cancelRegistration(other, eventId)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(register(PARENT, eventId)).hasStatus(HttpStatus.CREATED); // 취소했던 신청을 되살림

        assertThat(mvc.get().uri("/api/events/registrations/me").header(HttpHeaders.AUTHORIZATION, bearer(PARENT)))
                .hasStatusOk().bodyJson().extractingPath("$.content[0].eventId").asNumber()
                .satisfies(id -> assertThat(id.longValue()).isEqualTo(eventId));
    }

    @Test
    void 정원이_차면_대기_순번을_받고_확정된_신청이_취소되면_먼저_대기한_사람이_자리를_넘겨받는다() throws Exception {
        long eventId = createEvent(1, null, null);
        AuthUser first = new AuthUser(310L, "w1@tspoon.com", "대기1", "PARENT");
        AuthUser second = new AuthUser(311L, "w2@tspoon.com", "대기2", "PARENT");
        AuthUser third = new AuthUser(312L, "w3@tspoon.com", "대기3", "PARENT");

        assertThat(register(PARENT, eventId)).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.status").isEqualTo("REGISTERED");
        assertThat(register(first, eventId)).hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.waitlistPosition").isEqualTo(1);
        assertThat(register(second, eventId)).bodyJson().extractingPath("$.waitlistPosition").isEqualTo(2);
        assertThat(register(second, eventId)).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").isEqualTo("이미 대기 중인 행사입니다.");

        // 대기를 취소하면 뒤 사람의 순번이 당겨진다
        assertThat(cancelRegistration(first, eventId)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(register(third, eventId)).bodyJson().extractingPath("$.waitlistPosition").isEqualTo(2);
        myRegistration(second).extractingPath("$.content[0].waitlistPosition").isEqualTo(1);

        // 확정된 신청을 취소하면 자리는 그대로 다음 대기자에게
        assertThat(cancelRegistration(PARENT, eventId)).hasStatus(HttpStatus.NO_CONTENT);
        myRegistration(second).extractingPath("$.content[0].status").isEqualTo("REGISTERED");
        myRegistration(third).extractingPath("$.content[0].waitlistPosition").isEqualTo(1);
        MvcTestResult event = mvc.get().uri("/api/events/{id}", eventId).exchange();
        assertThat(event).bodyJson().extractingPath("$.currentParticipants").isEqualTo(1);
        assertThat(event).bodyJson().extractingPath("$.status").isEqualTo("FULL");
        assertThat(promotedUserIds(eventId)).containsExactly(second.userId());
    }

    @Test
    void 정원을_늘리면_늘어난_자리만큼_대기자를_순서대로_확정한다() throws Exception {
        long eventId = createEvent(1, null, null);
        AuthUser first = new AuthUser(320L, "g1@tspoon.com", "대기1", "PARENT");
        AuthUser second = new AuthUser(321L, "g2@tspoon.com", "대기2", "PARENT");
        AuthUser third = new AuthUser(322L, "g3@tspoon.com", "대기3", "PARENT");
        register(PARENT, eventId);
        register(first, eventId);
        register(second, eventId);
        register(third, eventId);

        assertThat(mvc.put().uri("/api/events/{id}", eventId).header(HttpHeaders.AUTHORIZATION, bearer(ORGANIZER))
                .contentType(MediaType.APPLICATION_JSON).content(eventJson(3, null, null)))
                .hasStatusOk().bodyJson().extractingPath("$.currentParticipants").isEqualTo(3);

        myRegistration(first).extractingPath("$.content[0].status").isEqualTo("REGISTERED");
        myRegistration(second).extractingPath("$.content[0].status").isEqualTo("REGISTERED");
        myRegistration(third).extractingPath("$.content[0].waitlistPosition").isEqualTo(1);
        assertThat(promotedUserIds(eventId)).containsExactly(first.userId(), second.userId());
        assertThat(mvc.get().uri("/api/events/{id}", eventId)).bodyJson().extractingPath("$.status").isEqualTo("FULL");
    }

    @Test
    void 신청_기간_밖에는_신청할_수_없다() throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        long notYet = createEvent(null, now.plusDays(1), now.plusDays(2));
        long closed = createEvent(null, now.minusDays(2), now.minusDays(1));

        assertThat(register(PARENT, notYet)).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").isEqualTo("아직 신청 기간이 아닙니다.");
        assertThat(register(PARENT, closed)).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").isEqualTo("신청 기간이 끝났습니다.");
    }

    @Test
    void 행사_등록은_교사_전문가_관리자만_신청자_목록과_취소는_주최자만() throws Exception {
        assertThat(postEvent(PARENT, eventJson(5, null, null))).hasStatus(HttpStatus.FORBIDDEN);

        long eventId = createEvent(5, null, null);
        register(PARENT, eventId);
        assertThat(mvc.get().uri("/api/events/{id}/registrations", eventId).header(HttpHeaders.AUTHORIZATION, bearer(PARENT)))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/events/{id}/registrations", eventId).header(HttpHeaders.AUTHORIZATION, bearer(ORGANIZER)))
                .hasStatusOk().bodyJson().extractingPath("$[0].userName").isEqualTo("학부모");

        assertThat(mvc.post().uri("/api/events/{id}/cancel", eventId).header(HttpHeaders.AUTHORIZATION, bearer(ORGANIZER)))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(register(new AuthUser(303L, "late@tspoon.com", "늦은신청", "PARENT"), eventId))
                .hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.detail").isEqualTo("신청할 수 없는 행사입니다.");
    }

    @Test
    void 정원은_현재_신청_인원보다_줄일_수_없다() throws Exception {
        long eventId = createEvent(3, null, null);
        register(PARENT, eventId);
        register(new AuthUser(304L, "b@tspoon.com", "둘째", "PARENT"), eventId);

        assertThat(mvc.put().uri("/api/events/{id}", eventId).header(HttpHeaders.AUTHORIZATION, bearer(ORGANIZER))
                .contentType(MediaType.APPLICATION_JSON).content(eventJson(1, null, null)))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void 종료_일시가_시작보다_앞서면_400() {
        String json = """
                {"title": "행사", "description": "설명", "type": "SEMINAR",
                 "startDate": "2030-01-02T10:00:00", "endDate": "2030-01-01T10:00:00"}
                """;
        assertThat(postEvent(ORGANIZER, json)).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors.endAfterStart").isEqualTo("종료 일시는 시작 일시보다 뒤여야 합니다");
    }

    private long createEvent(Integer max, LocalDateTime registrationStart, LocalDateTime registrationEnd) throws Exception {
        MvcTestResult result = postEvent(ORGANIZER, eventJson(max, registrationStart, registrationEnd));
        assertThat(result).hasStatus(HttpStatus.CREATED);
        Number id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        return id.longValue();
    }

    private static String eventJson(Integer max, LocalDateTime registrationStart, LocalDateTime registrationEnd) {
        LocalDateTime start = LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(7).withNano(0);
        return """
                {"title": "학부모 교육 세미나", "description": "설명", "type": "SEMINAR",
                 "startDate": "%s", "endDate": "%s", "registrationStartDate": %s, "registrationEndDate": %s,
                 "location": "서울", "maxParticipants": %s}
                """.formatted(start, start.plusHours(2), quote(registrationStart), quote(registrationEnd), max);
    }

    private static String quote(LocalDateTime value) {
        return value == null ? "null" : "\"" + value.withNano(0) + "\"";
    }

    private MvcTestResult postEvent(AuthUser user, String json) {
        return mvc.post().uri("/api/events").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult cancelRegistration(AuthUser user, long eventId) {
        return mvc.delete().uri("/api/events/{id}/registrations/me", eventId).header(HttpHeaders.AUTHORIZATION, bearer(user)).exchange();
    }

    private AbstractJsonContentAssert<?> myRegistration(AuthUser user) {
        return assertThat(mvc.get().uri("/api/events/registrations/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .hasStatusOk().bodyJson();
    }

    private List<Long> promotedUserIds(long eventId) {
        List<String> payloads = jdbcTemplate.queryForList("""
                SELECT payload::text FROM outbox_events WHERE event_type = ? AND aggregate_id = ? ORDER BY created_at
                """, String.class, EventEvents.WAITLIST_PROMOTED, String.valueOf(eventId));
        payloads.forEach(payload -> EventContracts.assertPayloadConforms(EventEvents.WAITLIST_PROMOTED, payload));
        return payloads.stream().map(payload -> JsonPath.<Number>read(payload, "$.userId").longValue()).toList();
    }

    private MvcTestResult register(AuthUser user, long eventId) {
        return mvc.post().uri("/api/events/{id}/registrations", eventId).header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"notes\": \"참석합니다\"}").exchange();
    }

    private static <T> T get(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String bearer(AuthUser user) {
        return "Bearer " + jwtUtil.generateAccessToken(user);
    }
}
