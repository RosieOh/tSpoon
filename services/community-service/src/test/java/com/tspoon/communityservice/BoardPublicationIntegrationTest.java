package com.tspoon.communityservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.auth.AuthUser;
import com.tspoon.auth.jwt.JwtUtil;
import com.tspoon.communityservice.event.CommunityEvents;
import com.tspoon.core.test.TestcontainersConfiguration;
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
class BoardPublicationIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 게시글의_공개_상태가_바뀔_때마다_검색_색인용_이벤트를_남긴다() throws Exception {
        String writer = bearer(new AuthUser(700L, "w@tspoon.com", "글쓴이", "PARENT"));
        String admin = bearer(new AuthUser(1L, "admin@tspoon.com", "관리자", "ADMIN"));

        MvcTestResult created = mvc.post().uri("/api/boards").header(HttpHeaders.AUTHORIZATION, writer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"수학 공부법\", \"content\": \"매일 연산\", \"category\": \"EDUCATION\"}").exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        long boardId = JsonPath.<Number>read(created.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id").longValue();

        assertThat(mvc.put().uri("/api/boards/{id}", boardId).header(HttpHeaders.AUTHORIZATION, writer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"수학 공부법 (수정)\", \"content\": \"매일 연산 10분\", \"category\": \"EDUCATION\"}"))
                .hasStatusOk();
        assertThat(mvc.post().uri("/api/admin/reports/boards/{id}/hide", boardId).header(HttpHeaders.AUTHORIZATION, admin))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.post().uri("/api/admin/reports/boards/{id}/dismiss", boardId).header(HttpHeaders.AUTHORIZATION, admin))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.delete().uri("/api/boards/{id}", boardId).header(HttpHeaders.AUTHORIZATION, writer))
                .hasStatus(HttpStatus.NO_CONTENT);

        List<String[]> events = jdbcTemplate.query("""
                SELECT event_type, payload::text FROM outbox_events WHERE aggregate_id = ? AND event_type <> ? ORDER BY created_at
                """, (rs, row) -> new String[]{rs.getString(1), rs.getString(2)}, String.valueOf(boardId), CommunityEvents.COMMENT_CREATED);
        assertThat(events).extracting(event -> event[0]).containsExactly(
                CommunityEvents.BOARD_PUBLISHED, CommunityEvents.BOARD_PUBLISHED,   // 작성, 수정
                CommunityEvents.BOARD_REMOVED, CommunityEvents.BOARD_PUBLISHED,     // 숨김, 숨김 해제
                CommunityEvents.BOARD_REMOVED);                                     // 삭제
        events.forEach(event -> EventContracts.assertPayloadConforms(event[0], event[1]));
        assertThat(JsonPath.<String>read(events.get(1)[1], "$.title")).isEqualTo("수학 공부법 (수정)");
        assertThat(JsonPath.<String>read(events.get(1)[1], "$.authorName")).isEqualTo("글쓴이");
    }

    private String bearer(AuthUser user) {
        return "Bearer " + jwtUtil.generateAccessToken(user);
    }
}
