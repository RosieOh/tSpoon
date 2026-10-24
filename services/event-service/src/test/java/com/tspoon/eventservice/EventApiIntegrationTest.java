package com.tspoon.eventservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.auth.AuthUser;
import com.tspoon.auth.jwt.JwtUtil;
import com.tspoon.core.exception.BusinessException;
import com.tspoon.core.test.TestcontainersConfiguration;
import com.tspoon.eventservice.dto.RegistrationRequest;
import com.tspoon.eventservice.entity.EventRegistration;
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

@SpringBootTest(properties = "eureka.client.enabled=false")
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

    @Test
    void 정원_10명_행사에_50명이_동시에_신청해도_10명만_성공한다() throws Exception {
        long eventId = createEvent(10, null, null);

        CountDownLatch start = new CountDownLatch(1);
        long succeeded;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> results = LongStream.rangeClosed(1, 50).mapToObj(i -> executor.submit(() -> {
                start.await();
                try {
                    eventService.register(new AuthUser(10_000 + i, "u" + i + "@tspoon.com", "신청자" + i, "PARENT"),
                            eventId, new RegistrationRequest(null, null));
                    return true;
                } catch (BusinessException e) {
                    return false;
                }
            })).toList();
            start.countDown();
            succeeded = results.stream().filter(EventApiIntegrationTest::get).count();
        }

        assertThat(succeeded).isEqualTo(10);
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
        assertThat(register(other, eventId)).hasStatus(HttpStatus.CONFLICT); // 정원 마감
        assertThat(mvc.get().uri("/api/events/{id}", eventId)).bodyJson().extractingPath("$.status").isEqualTo("FULL");

        assertThat(mvc.delete().uri("/api/events/{id}/registrations/me", eventId).header(HttpHeaders.AUTHORIZATION, bearer(PARENT)))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/events/{id}", eventId)).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");

        assertThat(register(other, eventId)).hasStatus(HttpStatus.CREATED);
        assertThat(mvc.delete().uri("/api/events/{id}/registrations/me", eventId).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(register(PARENT, eventId)).hasStatus(HttpStatus.CREATED); // 취소했던 신청을 되살림

        assertThat(mvc.get().uri("/api/events/registrations/me").header(HttpHeaders.AUTHORIZATION, bearer(PARENT)))
                .hasStatusOk().bodyJson().extractingPath("$.content[0].eventId").asNumber()
                .satisfies(id -> assertThat(id.longValue()).isEqualTo(eventId));
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

    private MvcTestResult register(AuthUser user, long eventId) {
        return mvc.post().uri("/api/events/{id}/registrations", eventId).header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"notes\": \"참석합니다\"}").exchange();
    }

    private static boolean get(Future<Boolean> future) {
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
