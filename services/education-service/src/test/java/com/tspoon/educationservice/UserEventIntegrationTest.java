package com.tspoon.educationservice;

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
    void 회원_이름이_바뀌면_콘텐츠와_북마크에_복사해_둔_이름도_바뀐다() throws Exception {
        String teacher = "Bearer " + jwtUtil.generateAccessToken(new AuthUser(710L, "teacher@tspoon.com", "옛이름", "TEACHER"));
        MvcTestResult created = mvc.post().uri("/api/contents").header(HttpHeaders.AUTHORIZATION, teacher)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "이름 바꿀 콘텐츠", "description": "설명", "content": "본문", "type": "ARTICLE",
                         "category": "MATH", "estimatedTime": 15, "targetAge": "초등 저학년", "draft": false}
                        """)
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        long contentId = JsonPath.<Number>read(created.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id").longValue();
        assertThat(mvc.post().uri("/api/contents/{id}/bookmark", contentId).header(HttpHeaders.AUTHORIZATION, teacher)).hasStatusOk();

        KafkaTestRecords.send(kafka.getBootstrapServers(), TspoonTopics.USER_EVENTS, "710", KafkaTestRecords.envelope(
                UserEventTypes.PROFILE_UPDATED, "USER", 710, "{\"userId\": 710, \"name\": \"새이름\"}"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(jdbcTemplate.queryForObject("SELECT author_name FROM education_contents WHERE id = ?", String.class, contentId))
                    .isEqualTo("새이름");
            assertThat(jdbcTemplate.queryForObject("SELECT user_name FROM content_bookmarks WHERE content_id = ?", String.class, contentId))
                    .isEqualTo("새이름");
        });
    }
}
