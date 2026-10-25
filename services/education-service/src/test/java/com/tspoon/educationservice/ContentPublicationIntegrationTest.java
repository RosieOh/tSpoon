package com.tspoon.educationservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.auth.AuthUser;
import com.tspoon.auth.jwt.JwtUtil;
import com.tspoon.core.test.TestcontainersConfiguration;
import com.tspoon.educationservice.event.EducationEvents;
import com.tspoon.event.test.EventContracts;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"eureka.client.enabled=false", "tspoon.outbox.relay-enabled=false",
        "spring.kafka.listener.auto-startup=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ContentPublicationIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 공개_콘텐츠만_검색_색인용_이벤트를_남기고_임시저장으로_되돌리거나_지우면_제거를_알린다() throws Exception {
        String teacher = "Bearer " + jwtUtil.generateAccessToken(new AuthUser(900L, "t@tspoon.com", "김교사", "TEACHER"));

        long draftId = create(teacher, true);
        long contentId = create(teacher, false);
        assertThat(mvc.put().uri("/api/contents/{id}", contentId).header(HttpHeaders.AUTHORIZATION, teacher)
                .contentType(MediaType.APPLICATION_JSON).content(body(true))).hasStatusOk();          // 임시저장으로 되돌림
        assertThat(mvc.put().uri("/api/contents/{id}", contentId).header(HttpHeaders.AUTHORIZATION, teacher)
                .contentType(MediaType.APPLICATION_JSON).content(body(false))).hasStatusOk();         // 다시 공개
        assertThat(mvc.delete().uri("/api/contents/{id}", contentId).header(HttpHeaders.AUTHORIZATION, teacher))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(events(draftId)).isEmpty();
        List<String[]> events = events(contentId);
        assertThat(events).extracting(event -> event[0]).containsExactly(
                EducationEvents.CONTENT_PUBLISHED, EducationEvents.CONTENT_REMOVED,
                EducationEvents.CONTENT_PUBLISHED, EducationEvents.CONTENT_REMOVED);
        events.forEach(event -> EventContracts.assertPayloadConforms(event[0], event[1]));
        // 검색용 텍스트는 소개와 본문을 합친다
        assertThat(JsonPath.<String>read(events.getFirst()[1], "$.content")).isEqualTo("그림책 소개\n\n그림책으로 시작하는 영어");
    }

    private List<String[]> events(long contentId) {
        return jdbcTemplate.query("""
                SELECT event_type, payload::text FROM outbox_events WHERE aggregate_id = ? ORDER BY created_at
                """, (rs, row) -> new String[]{rs.getString(1), rs.getString(2)}, String.valueOf(contentId));
    }

    private long create(String token, boolean draft) throws Exception {
        MvcTestResult created = mvc.post().uri("/api/contents").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(body(draft)).exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return JsonPath.<Number>read(created.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id").longValue();
    }

    private static String body(boolean draft) {
        return """
                {"title": "놀이로 배우는 영어", "description": "그림책 소개", "content": "그림책으로 시작하는 영어", "type": "ARTICLE",
                 "category": "LANGUAGE", "estimatedTime": 15, "targetAge": "초등 저학년", "draft": %s}
                """.formatted(draft);
    }
}
