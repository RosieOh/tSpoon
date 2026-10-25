package com.tspoon.qnaservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.auth.AuthUser;
import com.tspoon.auth.jwt.JwtUtil;
import com.tspoon.core.test.TestcontainersConfiguration;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"eureka.client.enabled=false", "tspoon.outbox.relay-enabled=false",
        "spring.kafka.listener.auto-startup=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class QnaReportIntegrationTest {

    private static final AtomicLong USERS = new AtomicLong(30_000);
    private static final AuthUser ADMIN = new AuthUser(1L, "admin@tspoon.com", "관리자", "ADMIN");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JwtUtil jwtUtil;

    @Test
    void 숨긴_질문은_목록과_상세에서_빠지고_기각하면_답변_완료_상태로_돌아온다() throws Exception {
        AuthUser asker = newUser("PARENT");
        long questionId = ask(asker);
        long answerId = answer(newUser("EXPERT"), questionId);
        assertThat(post(asker, "/api/questions/%d/answers/%d/accept".formatted(questionId, answerId), null)).hasStatusOk();

        assertThat(post(ADMIN, "/api/admin/reports/questions/" + questionId + "/hide", null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/questions/{id}", questionId)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(questionIdsInList()).doesNotContain(questionId);
        // 숨긴 질문에는 답변을 달 수 없다
        assertThat(post(newUser("TEACHER"), "/api/questions/" + questionId + "/answers", "{\"content\": \"답변\"}"))
                .hasStatus(HttpStatus.NOT_FOUND);

        assertThat(post(ADMIN, "/api/admin/reports/questions/" + questionId + "/dismiss", null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/questions/{id}", questionId))
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("ANSWERED");
        assertThat(questionIdsInList()).contains(questionId);
    }

    @Test
    void 다섯_명이_신고한_답변은_숨겨져_답변_수에서_빠지고_관리자가_확정하면_검토_목록에서_빠진다() throws Exception {
        long questionId = ask(newUser("PARENT"));
        long answerId = answer(newUser("PARENT"), questionId);

        for (int i = 0; i < 5; i++) {
            assertThat(post(newUser("PARENT"), "/api/answers/" + answerId + "/reports", "{\"reason\": \"SPAM\"}"))
                    .hasStatus(HttpStatus.CREATED);
        }

        MvcTestResult detail = mvc.get().uri("/api/questions/{id}", questionId).exchange();
        assertThat(detail).bodyJson().extractingPath("$.answerCount").isEqualTo(0);
        assertThat(detail).bodyJson().extractingPath("$.answers").asArray().isEmpty();
        assertThat(pendingTargetIds("answers")).contains(answerId);

        assertThat(post(ADMIN, "/api/admin/reports/answers/" + answerId + "/hide", null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(pendingTargetIds("answers")).doesNotContain(answerId);
        assertThat(get(ADMIN, "/api/admin/reports/answers?status=ACCEPTED&size=100"))
                .bodyJson().extractingPath("$.content[?(@.targetId == %d)].targetStatus".formatted(answerId))
                .asArray().containsExactly("HIDDEN");
        assertThat(get(ADMIN, "/api/admin/reports/answers/" + answerId))
                .bodyJson().extractingPath("$[*].status").asArray().containsOnly("ACCEPTED");
    }

    private List<Long> questionIdsInList() throws Exception {
        List<Number> ids = JsonPath.read(body(mvc.get().uri("/api/questions?size=100").exchange()), "$.content[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    private List<Long> pendingTargetIds(String collection) throws Exception {
        List<Number> ids = JsonPath.read(body(get(ADMIN, "/api/admin/reports/" + collection + "?size=100")), "$.content[*].targetId");
        return ids.stream().map(Number::longValue).toList();
    }

    private long ask(AuthUser user) throws Exception {
        return id(post(user, "/api/questions", "{\"title\": \"신고 대상 질문\", \"content\": \"내용\", \"category\": \"LEARNING\"}"));
    }

    private long answer(AuthUser user, long questionId) throws Exception {
        return id(post(user, "/api/questions/" + questionId + "/answers", "{\"content\": \"광고성 답변\"}"));
    }

    private MvcTestResult post(AuthUser user, String uri, String json) {
        var request = mvc.post().uri(uri).header(HttpHeaders.AUTHORIZATION, bearer(user));
        return json == null ? request.exchange() : request.contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult get(AuthUser user, String uri) {
        return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, bearer(user)).exchange();
    }

    private static AuthUser newUser(String role) {
        long id = USERS.incrementAndGet();
        return new AuthUser(id, "user" + id + "@tspoon.com", "회원" + id, role);
    }

    private static long id(MvcTestResult result) throws Exception {
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.<Number>read(body(result), "$.id").longValue();
    }

    private static String body(MvcTestResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String bearer(AuthUser user) {
        return "Bearer " + jwtUtil.generateAccessToken(user);
    }
}
