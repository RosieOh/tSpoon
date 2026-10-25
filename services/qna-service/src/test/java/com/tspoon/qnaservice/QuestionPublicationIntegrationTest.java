package com.tspoon.qnaservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.auth.AuthUser;
import com.tspoon.auth.jwt.JwtUtil;
import com.tspoon.core.test.TestcontainersConfiguration;
import com.tspoon.event.test.EventContracts;
import com.tspoon.qnaservice.event.QnaEvents;
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
class QuestionPublicationIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 질문의_공개_상태가_바뀔_때마다_검색_색인용_이벤트를_남긴다() throws Exception {
        String asker = bearer(new AuthUser(800L, "a@tspoon.com", "질문자", "PARENT"));
        String admin = bearer(new AuthUser(1L, "admin@tspoon.com", "관리자", "ADMIN"));

        MvcTestResult asked = mvc.post().uri("/api/questions").header(HttpHeaders.AUTHORIZATION, asker)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"영어 단어 외우기\", \"content\": \"방법이 있을까요\", \"category\": \"LEARNING\"}").exchange();
        assertThat(asked).hasStatus(HttpStatus.CREATED);
        long questionId = JsonPath.<Number>read(asked.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id").longValue();

        assertThat(mvc.put().uri("/api/questions/{id}", questionId).header(HttpHeaders.AUTHORIZATION, asker)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"영어 단어 외우기 (수정)\", \"content\": \"좋은 방법\", \"category\": \"LEARNING\"}"))
                .hasStatusOk();
        assertThat(mvc.post().uri("/api/admin/reports/questions/{id}/hide", questionId).header(HttpHeaders.AUTHORIZATION, admin))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.post().uri("/api/admin/reports/questions/{id}/dismiss", questionId).header(HttpHeaders.AUTHORIZATION, admin))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.delete().uri("/api/questions/{id}", questionId).header(HttpHeaders.AUTHORIZATION, asker))
                .hasStatus(HttpStatus.NO_CONTENT);

        List<String[]> events = jdbcTemplate.query("""
                SELECT event_type, payload::text FROM outbox_events WHERE aggregate_id = ? AND event_type IN (?, ?) ORDER BY created_at
                """, (rs, row) -> new String[]{rs.getString(1), rs.getString(2)}, String.valueOf(questionId),
                QnaEvents.QUESTION_PUBLISHED, QnaEvents.QUESTION_REMOVED);
        assertThat(events).extracting(event -> event[0]).containsExactly(
                QnaEvents.QUESTION_PUBLISHED, QnaEvents.QUESTION_PUBLISHED,   // 작성, 수정
                QnaEvents.QUESTION_REMOVED, QnaEvents.QUESTION_PUBLISHED,     // 숨김, 숨김 해제
                QnaEvents.QUESTION_REMOVED);                                  // 삭제
        events.forEach(event -> EventContracts.assertPayloadConforms(event[0], event[1]));
        assertThat(JsonPath.<String>read(events.get(1)[1], "$.title")).isEqualTo("영어 단어 외우기 (수정)");
    }

    private String bearer(AuthUser user) {
        return "Bearer " + jwtUtil.generateAccessToken(user);
    }
}
