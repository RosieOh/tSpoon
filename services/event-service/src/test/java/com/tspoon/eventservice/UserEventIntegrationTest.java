package com.tspoon.eventservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.auth.AuthUser;
import com.tspoon.auth.jwt.JwtUtil;
import com.tspoon.core.test.KafkaTestRecords;
import com.tspoon.core.test.KafkaTestcontainersConfiguration;
import com.tspoon.core.test.TestcontainersConfiguration;
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

@SpringBootTest(properties = "eureka.client.enabled=false")
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
        LocalDateTime start = LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(7).withNano(0);
        MvcTestResult created = mvc.post().uri("/api/events").header(HttpHeaders.AUTHORIZATION, bearer(720L, "옛주최자", "TEACHER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "이름 바꿀 행사", "description": "설명", "type": "SEMINAR",
                         "startDate": "%s", "endDate": "%s", "location": "서울", "maxParticipants": 10}
                        """.formatted(start, start.plusHours(2)))
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        long eventId = JsonPath.<Number>read(created.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id").longValue();
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

    private void rename(long userId, String name) {
        KafkaTestRecords.send(kafka.getBootstrapServers(), TspoonTopics.USER_EVENTS, String.valueOf(userId), KafkaTestRecords.envelope(
                UserEventTypes.PROFILE_UPDATED, "USER", userId, "{\"userId\": %d, \"name\": \"%s\"}".formatted(userId, name)));
    }

    private String bearer(long userId, String name, String role) {
        return "Bearer " + jwtUtil.generateAccessToken(new AuthUser(userId, "user" + userId + "@tspoon.com", name, role));
    }
}
