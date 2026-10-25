package com.tspoon.eventservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.auth.AuthUser;
import com.tspoon.auth.jwt.JwtUtil;
import com.tspoon.core.test.KafkaTestRecords;
import com.tspoon.core.test.KafkaTestcontainersConfiguration;
import com.tspoon.core.test.TestcontainersConfiguration;
import com.tspoon.event.test.EventContracts;
import com.tspoon.event.TspoonTopics;
import com.tspoon.event.UserEventTypes;
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
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {"eureka.client.enabled=false", "tspoon.event.reminder.enabled=false"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class UserEventIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    KafkaContainer kafka;

    @Test
    void 회원_이름이_바뀌면_주최자와_신청자에_복사해_둔_이름도_바뀐다() throws Exception {
        long eventId = createEvent(720L, "옛주최자", 10);
        assertThat(mvc.post().uri("/api/events/{id}/registrations", eventId).header(HttpHeaders.AUTHORIZATION, bearer(721L, "옛신청자", "PARENT"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).hasStatus(HttpStatus.CREATED);

        rename(720L, "새주최자");
        rename(721L, "새신청자");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(jdbcTemplate.queryForObject("SELECT organizer_name FROM events WHERE id = ?", String.class, eventId))
                    .isEqualTo("새주최자");
            assertThat(jdbcTemplate.queryForObject("SELECT user_name FROM event_registrations WHERE event_id = ?", String.class, eventId))
                    .isEqualTo("새신청자");
        });
    }

    @Test
    void 탈퇴한_회원의_다가오는_신청은_취소돼_자리가_한_번만_돌아오고_신청_내역의_개인정보는_지워진다() throws Exception {
        long eventId = createEvent(740L, "떠날주최자", 1);
        assertThat(mvc.post().uri("/api/events/{id}/registrations", eventId).header(HttpHeaders.AUTHORIZATION, bearer(741L, "떠날회원", "PARENT"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"notes\": \"아이 둘\", \"contactInfo\": \"010-0000-0000\"}"))
                .hasStatus(HttpStatus.CREATED);
        assertThat(mvc.get().uri("/api/events/{id}", eventId)).bodyJson().extractingPath("$.status").isEqualTo("FULL");

        String withdrawn = EventContracts.envelope(UserEventTypes.WITHDRAWN, "USER", 741, "{\"userId\": 741, \"withdrawnAt\": \"2026-10-26T00:00:00Z\"}");
        KafkaTestRecords.send(kafka.getBootstrapServers(), TspoonTopics.USER_EVENTS, "741", withdrawn);
        KafkaTestRecords.send(kafka.getBootstrapServers(), TspoonTopics.USER_EVENTS, "741", withdrawn);
        KafkaTestRecords.send(kafka.getBootstrapServers(), TspoonTopics.USER_EVENTS, "740", EventContracts.envelope(UserEventTypes.WITHDRAWN, "USER", 740, "{\"userId\": 740, \"withdrawnAt\": \"2026-10-26T00:00:00Z\"}"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(jdbcTemplate.queryForMap("SELECT status, user_name, notes, contact_info FROM event_registrations WHERE event_id = ?", eventId))
                    .containsEntry("status", "CANCELLED").containsEntry("user_name", "탈퇴한 회원")
                    .containsEntry("notes", null).containsEntry("contact_info", null);
            assertThat(jdbcTemplate.queryForObject("SELECT organizer_name FROM events WHERE id = ?", String.class, eventId))
                    .isEqualTo("탈퇴한 회원");
        });
        Thread.sleep(1_000); // 두 번째 탈퇴 이벤트까지 처리될 시간
        assertThat(mvc.get().uri("/api/events/{id}", eventId)).bodyJson().extractingPath("$.currentParticipants").isEqualTo(0);
        assertThat(mvc.get().uri("/api/events/{id}", eventId)).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
    }

    @Test
    void 확정된_회원이_탈퇴하면_자리는_다음_대기자에게_넘어간다() throws Exception {
        long eventId = createEvent(750L, "주최자", 1);
        register(751L, "떠날회원", eventId);
        register(752L, "대기회원", eventId);

        KafkaTestRecords.send(kafka.getBootstrapServers(), TspoonTopics.USER_EVENTS, "751", EventContracts.envelope(
                UserEventTypes.WITHDRAWN, "USER", 751, "{\"userId\": 751, \"withdrawnAt\": \"2026-10-26T00:00:00Z\"}"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM event_registrations WHERE event_id = ? AND user_id = 752", String.class, eventId))
                .isEqualTo("REGISTERED"));
        assertThat(mvc.get().uri("/api/events/{id}", eventId)).bodyJson().extractingPath("$.currentParticipants").isEqualTo(1);
        assertThat(mvc.get().uri("/api/events/{id}", eventId)).bodyJson().extractingPath("$.status").isEqualTo("FULL");
    }

    private void register(long userId, String name, long eventId) {
        assertThat(mvc.post().uri("/api/events/{id}/registrations", eventId).header(HttpHeaders.AUTHORIZATION, bearer(userId, name, "PARENT"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).hasStatus(HttpStatus.CREATED);
    }

    private long createEvent(long organizerId, String organizerName, int maxParticipants) throws Exception {
        LocalDateTime start = LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(7).withNano(0);
        MvcTestResult created = mvc.post().uri("/api/events").header(HttpHeaders.AUTHORIZATION, bearer(organizerId, organizerName, "TEACHER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "회원 이벤트 행사", "description": "설명", "type": "SEMINAR",
                         "startDate": "%s", "endDate": "%s", "location": "서울", "maxParticipants": %d}
                        """.formatted(start, start.plusHours(2), maxParticipants))
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return JsonPath.<Number>read(created.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id").longValue();
    }

    private void rename(long userId, String name) {
        KafkaTestRecords.send(kafka.getBootstrapServers(), TspoonTopics.USER_EVENTS, String.valueOf(userId), EventContracts.envelope(
                UserEventTypes.PROFILE_UPDATED, "USER", userId, "{\"userId\": %d, \"name\": \"%s\"}".formatted(userId, name)));
    }

    private String bearer(long userId, String name, String role) {
        return "Bearer " + jwtUtil.generateAccessToken(new AuthUser(userId, "user" + userId + "@tspoon.com", name, role));
    }
}
