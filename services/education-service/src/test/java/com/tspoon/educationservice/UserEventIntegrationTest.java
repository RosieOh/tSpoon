package com.tspoon.educationservice;

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
        String teacher = bearer(710L, "옛이름", "TEACHER");
        long contentId = createContent(teacher);
        assertThat(mvc.post().uri("/api/contents/{id}/bookmark", contentId).header(HttpHeaders.AUTHORIZATION, teacher)).hasStatusOk();

        KafkaTestRecords.send(kafka.getBootstrapServers(), TspoonTopics.USER_EVENTS, "710", EventContracts.envelope(
                UserEventTypes.PROFILE_UPDATED, "USER", 710, "{\"userId\": 710, \"name\": \"새이름\"}"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(jdbcTemplate.queryForObject("SELECT author_name FROM education_contents WHERE id = ?", String.class, contentId))
                    .isEqualTo("새이름");
            assertThat(jdbcTemplate.queryForObject("SELECT user_name FROM content_bookmarks WHERE content_id = ?", String.class, contentId))
                    .isEqualTo("새이름");
        });
    }

    @Test
    void 탈퇴한_회원의_콘텐츠는_익명으로_남고_북마크는_지워져_북마크_수가_한_번만_줄어든다() throws Exception {
        String leaving = bearer(730L, "떠날교사", "TEACHER");
        long ownContent = createContent(leaving);
        long otherContent = createContent(bearer(731L, "남는교사", "TEACHER"));
        assertThat(mvc.post().uri("/api/contents/{id}/bookmark", otherContent).header(HttpHeaders.AUTHORIZATION, leaving)).hasStatusOk();
        assertThat(mvc.post().uri("/api/contents/{id}/bookmark", otherContent).header(HttpHeaders.AUTHORIZATION, bearer(732L, "회원", "PARENT")))
                .hasStatusOk();

        String withdrawn = EventContracts.envelope(UserEventTypes.WITHDRAWN, "USER", 730, "{\"userId\": 730, \"withdrawnAt\": \"2026-10-26T00:00:00Z\"}");
        KafkaTestRecords.send(kafka.getBootstrapServers(), TspoonTopics.USER_EVENTS, "730", withdrawn);
        KafkaTestRecords.send(kafka.getBootstrapServers(), TspoonTopics.USER_EVENTS, "730", withdrawn);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(jdbcTemplate.queryForObject("SELECT author_name FROM education_contents WHERE id = ?", String.class, ownContent))
                    .isEqualTo("탈퇴한 회원");
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM content_bookmarks WHERE user_id = 730", Long.class)).isZero();
        });
        Thread.sleep(1_000); // 두 번째 탈퇴 이벤트까지 처리될 시간
        assertThat(jdbcTemplate.queryForObject("SELECT bookmark_count FROM education_contents WHERE id = ?", Integer.class, otherContent))
                .isEqualTo(1);
    }

    private long createContent(String token) throws Exception {
        MvcTestResult created = mvc.post().uri("/api/contents").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "회원 이벤트 콘텐츠", "description": "설명", "content": "본문", "type": "ARTICLE",
                         "category": "MATH", "estimatedTime": 15, "targetAge": "초등 저학년", "draft": false}
                        """)
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return JsonPath.<Number>read(created.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id").longValue();
    }

    private String bearer(long userId, String name, String role) {
        return "Bearer " + jwtUtil.generateAccessToken(new AuthUser(userId, "user" + userId + "@tspoon.com", name, role));
    }
}
