package com.tspoon.qnaservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.auth.AuthUser;
import com.tspoon.auth.jwt.JwtUtil;
import com.tspoon.core.exception.BusinessException;
import com.tspoon.core.test.TestcontainersConfiguration;
import com.tspoon.qnaservice.service.QnaService;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"eureka.client.enabled=false", "tspoon.outbox.relay-enabled=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class QnaApiIntegrationTest {

    private static final AuthUser ASKER = new AuthUser(200L, "asker@tspoon.com", "질문자", "PARENT");
    private static final AuthUser TEACHER = new AuthUser(201L, "teacher@tspoon.com", "김교사", "TEACHER");
    private static final AuthUser EXPERT = new AuthUser(202L, "expert@tspoon.com", "이전문가", "EXPERT");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    QnaService qnaService;

    @Test
    void 답변_유형은_답변자의_역할로_정해지고_채택하면_답변_완료로_바뀌며_채택_답변이_먼저_나온다() throws Exception {
        long questionId = ask(ASKER, "아이 수학 공부 어떻게 시키나요?");
        long teacherAnswer = answer(TEACHER, questionId, "놀이로 시작하세요");
        long expertAnswer = answer(EXPERT, questionId, "개념 이해가 먼저입니다");

        assertThat(mvc.post().uri("/api/questions/{q}/answers/{a}/accept", questionId, expertAnswer)
                .header(HttpHeaders.AUTHORIZATION, bearer(ASKER)))
                .hasStatusOk();

        MvcTestResult detail = mvc.get().uri("/api/questions/{id}", questionId).exchange(); // 비로그인 조회
        assertThat(detail).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("ANSWERED");
        assertThat(detail).bodyJson().extractingPath("$.answerCount").isEqualTo(2);
        assertThat(detail).bodyJson().extractingPath("$.acceptedAnswerId").asNumber().satisfies(id -> assertThat(id.longValue()).isEqualTo(expertAnswer));
        assertThat(detail).bodyJson().extractingPath("$.answers[0].type").isEqualTo("EXPERT");
        assertThat(detail).bodyJson().extractingPath("$.answers[0].accepted").isEqualTo(true);
        assertThat(detail).bodyJson().extractingPath("$.answers[1].type").isEqualTo("TEACHER");
        assertThat(teacherAnswer).isPositive();

        assertThat(mvc.get().uri("/api/questions").param("status", "ANSWERED").param("size", "100"))
                .bodyJson().extractingPath("$.content[?(@.id == %d)].accepted".formatted(questionId)).asArray().containsExactly(true);
    }

    @Test
    void 채택은_질문자만_한_번_자기_답변은_안_되고_다른_질문의_답변도_안_된다() throws Exception {
        long questionId = ask(ASKER, "질문");
        long otherQuestion = ask(TEACHER, "다른 질문");
        long answerId = answer(TEACHER, questionId, "답변");
        long ownAnswer = answer(ASKER, questionId, "내가 단 답변");
        long otherAnswer = answer(EXPERT, otherQuestion, "다른 질문의 답변");

        assertThat(accept(TEACHER, questionId, answerId)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(accept(ASKER, questionId, ownAnswer)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(accept(ASKER, questionId, otherAnswer)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(accept(ASKER, questionId, answerId)).hasStatusOk();
        assertThat(accept(ASKER, questionId, answerId)).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void 서로_다른_답변을_동시에_채택해도_하나만_성공한다() throws Exception {
        long questionId = ask(ASKER, "동시 채택");
        List<Long> answers = List.of(answer(TEACHER, questionId, "답변1"), answer(EXPERT, questionId, "답변2"));

        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> results = answers.stream().map(answerId -> executor.submit(() -> {
                start.await();
                try {
                    qnaService.accept(ASKER, questionId, answerId);
                    return true;
                } catch (BusinessException e) {
                    return false;
                }
            })).toList();
            start.countDown();

            long succeeded = results.stream().filter(this::get).count();
            assertThat(succeeded).isEqualTo(1);
        }
        assertThat(mvc.get().uri("/api/questions/{id}", questionId))
                .bodyJson().extractingPath("$.answers[?(@.accepted == true)]").asArray().hasSize(1);
    }

    @Test
    void 채택된_답변은_지울_수_없고_답변이_달린_질문은_작성자가_아닌_관리자만_지운다() throws Exception {
        long questionId = ask(ASKER, "질문");
        long accepted = answer(TEACHER, questionId, "채택될 답변");
        long other = answer(EXPERT, questionId, "다른 답변");
        accept(ASKER, questionId, accepted);

        assertThat(mvc.delete().uri("/api/answers/{id}", accepted).header(HttpHeaders.AUTHORIZATION, bearer(TEACHER)))
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(mvc.delete().uri("/api/answers/{id}", other).header(HttpHeaders.AUTHORIZATION, bearer(EXPERT)))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/questions/{id}", questionId)).bodyJson().extractingPath("$.answerCount").isEqualTo(1);

        assertThat(mvc.delete().uri("/api/questions/{id}", questionId).header(HttpHeaders.AUTHORIZATION, bearer(ASKER)))
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(mvc.delete().uri("/api/questions/{id}", questionId)
                .header(HttpHeaders.AUTHORIZATION, bearer(new AuthUser(1L, "admin@tspoon.com", "관리자", "ADMIN"))))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/questions/{id}", questionId)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void 닫힌_질문에는_답변할_수_없다() throws Exception {
        long questionId = ask(ASKER, "닫을 질문");

        assertThat(mvc.post().uri("/api/questions/{id}/close", questionId).header(HttpHeaders.AUTHORIZATION, bearer(ASKER)))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(postAnswer(TEACHER, questionId, "늦은 답변")).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void 비로그인_사용자는_질문할_수_없다() {
        assertThat(mvc.post().uri("/api/questions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"t\", \"content\": \"c\", \"category\": \"GENERAL\"}"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private long ask(AuthUser user, String title) throws Exception {
        MvcTestResult result = mvc.post().uri("/api/questions").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"%s\", \"content\": \"내용\", \"category\": \"LEARNING\"}".formatted(title))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return id(result);
    }

    private long answer(AuthUser user, long questionId, String content) throws Exception {
        MvcTestResult result = postAnswer(user, questionId, content);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return id(result);
    }

    private MvcTestResult postAnswer(AuthUser user, long questionId, String content) {
        return mvc.post().uri("/api/questions/{id}/answers", questionId).header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"%s\"}".formatted(content))
                .exchange();
    }

    private MvcTestResult accept(AuthUser user, long questionId, long answerId) {
        return mvc.post().uri("/api/questions/{q}/answers/{a}/accept", questionId, answerId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user)).exchange();
    }

    private boolean get(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static long id(MvcTestResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        return id.longValue();
    }

    private String bearer(AuthUser user) {
        return "Bearer " + jwtUtil.generateAccessToken(user);
    }
}
