package com.tspoon.communityservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.auth.AuthUser;
import com.tspoon.auth.jwt.JwtUtil;
import com.tspoon.core.test.TestcontainersConfiguration;
import com.tspoon.moderation.ModerationService;
import com.tspoon.moderation.ReportReason;
import com.tspoon.moderation.ReportRequest;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"eureka.client.enabled=false", "tspoon.outbox.relay-enabled=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReportIntegrationTest {

    private static final AtomicLong USERS = new AtomicLong(20_000);
    private static final String ADMIN = "admin";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    ModerationService moderationService;

    @Test
    void 서로_다른_다섯_명이_신고하면_게시글이_숨겨지고_관리자가_기각하면_다시_보인다() throws Exception {
        long boardId = createBoard(newUser());
        for (int i = 0; i < 4; i++) {
            assertThat(report(newUser(), "boards", boardId, "SPAM")).hasStatus(HttpStatus.CREATED);
        }
        assertThat(mvc.get().uri("/api/boards/{id}", boardId)).hasStatusOk(); // 4건까지는 그대로
        assertThat(report(newUser(), "boards", boardId, "ABUSE")).hasStatus(HttpStatus.CREATED);

        assertThat(mvc.get().uri("/api/boards/{id}", boardId)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(boardIdsInList()).doesNotContain(boardId);

        MvcTestResult pending = adminGet("/api/admin/reports/boards");
        assertThat(pending).hasStatusOk();
        assertThat(pending).bodyJson().extractingPath("$.content[0].targetId").asNumber().isEqualTo((int) boardId);
        assertThat(pending).bodyJson().extractingPath("$.content[0].targetStatus").isEqualTo("HIDDEN");
        assertThat(pending).bodyJson().extractingPath("$.content[0].reportCount").isEqualTo(5);
        assertThat(pending).bodyJson().extractingPath("$.content[0].reasons.SPAM").isEqualTo(4);
        assertThat(pending).bodyJson().extractingPath("$.content[0].reasons.ABUSE").isEqualTo(1);
        assertThat(adminGet("/api/admin/reports/boards/" + boardId)).bodyJson().extractingPath("$.length()").isEqualTo(5);

        assertThat(adminPost("/api/admin/reports/boards/" + boardId + "/dismiss")).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/boards/{id}", boardId)).hasStatusOk();
        assertThat(boardIdsInList()).contains(boardId);
        // 기각된 신고는 검토 목록에서 빠지고 기준에도 다시 들어가지 않는다
        assertThat(pendingTargetIds("boards")).doesNotContain(boardId);
        assertThat(report(newUser(), "boards", boardId, "SPAM")).hasStatus(HttpStatus.CREATED);
        assertThat(mvc.get().uri("/api/boards/{id}", boardId)).hasStatusOk();
    }

    @Test
    void 같은_글은_한_번만_자기_글은_신고할_수_없고_사라진_글은_404() throws Exception {
        String writer = newUser();
        String reporter = newUser();
        long boardId = createBoard(writer);

        assertThat(report(reporter, "boards", boardId, "SPAM")).hasStatus(HttpStatus.CREATED);
        assertThat(report(reporter, "boards", boardId, "ABUSE")).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").isEqualTo("이미 신고한 글입니다.");
        assertThat(report(writer, "boards", boardId, "SPAM")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").isEqualTo("자신이 쓴 글은 신고할 수 없습니다.");
        assertThat(report(reporter, "boards", 999_999, "SPAM")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(report(reporter, "videos", boardId, "SPAM")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.post().uri("/api/boards/{id}/reports", boardId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"SPAM\"}")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 사유가_없거나_기타_사유에_설명이_없으면_400() throws Exception {
        long boardId = createBoard(newUser());

        assertThat(post(newUser(), "/api/boards/" + boardId + "/reports", "{}"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.errors.reason").isEqualTo("신고 사유는 필수입니다");
        assertThat(post(newUser(), "/api/boards/" + boardId + "/reports", "{\"reason\": \"OTHER\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors.descriptionPresentForOther").isEqualTo("기타 사유는 설명이 필요합니다");
        assertThat(post(newUser(), "/api/boards/" + boardId + "/reports", "{\"reason\": \"OTHER\", \"description\": \"홍보 링크\"}"))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void 관리자가_숨긴_댓글은_답글이_있으면_자리만_남고_댓글_수에서_빠진다() throws Exception {
        String writer = newUser();
        long boardId = createBoard(writer);
        long commentId = id(post(newUser(), "/api/boards/" + boardId + "/comments", "{\"content\": \"광고 댓글\"}"));
        post(writer, "/api/boards/" + boardId + "/comments", "{\"content\": \"답글\", \"parentCommentId\": %d}".formatted(commentId));

        assertThat(adminPost("/api/admin/reports/comments/" + commentId + "/hide")).hasStatus(HttpStatus.NO_CONTENT);

        MvcTestResult comments = mvc.get().uri("/api/boards/{id}/comments", boardId).exchange();
        assertThat(comments).bodyJson().extractingPath("$[0].content").isEqualTo("운영 정책에 따라 숨겨진 댓글입니다.");
        assertThat(comments).bodyJson().extractingPath("$[0].hidden").isEqualTo(true);
        assertThat(comments).bodyJson().extractingPath("$[0].authorId").isNull();
        assertThat(comments).bodyJson().extractingPath("$[0].replies[0].content").isEqualTo("답글");
        assertThat(mvc.get().uri("/api/boards/{id}", boardId)).bodyJson().extractingPath("$.commentCount").isEqualTo(1);
        // 숨긴 댓글은 다시 신고할 수 없다
        assertThat(report(newUser(), "comments", commentId, "SPAM")).hasStatus(HttpStatus.NOT_FOUND);

        assertThat(adminPost("/api/admin/reports/comments/" + commentId + "/dismiss")).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/boards/{id}/comments", boardId))
                .bodyJson().extractingPath("$[0].content").isEqualTo("광고 댓글");
        assertThat(mvc.get().uri("/api/boards/{id}", boardId)).bodyJson().extractingPath("$.commentCount").isEqualTo(2);
    }

    @Test
    void 다섯_명이_동시에_신고해도_기준에_닿는_순간을_놓치지_않는다() throws Exception {
        long boardId = createBoard(newUser());

        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Object>> results = LongStream.range(0, 5).mapToObj(i -> executor.submit(() -> {
                start.await();
                moderationService.report("boards", boardId, new AuthUser(USERS.incrementAndGet(), "u@tspoon.com", "신고자", "PARENT"),
                        new ReportRequest(ReportReason.SPAM, null));
                return null;
            })).toList();
            start.countDown();
            for (Future<Object> result : results) {
                result.get();
            }
        }

        assertThat(mvc.get().uri("/api/boards/{id}", boardId)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void 신고_관리는_관리자만_할_수_있다() throws Exception {
        long boardId = createBoard(newUser());
        String member = newUser();

        assertThat(mvc.get().uri("/api/admin/reports/boards").header(HttpHeaders.AUTHORIZATION, member))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.post().uri("/api/admin/reports/boards/{id}/hide", boardId).header(HttpHeaders.AUTHORIZATION, member))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(adminPost("/api/admin/reports/boards/999999/hide")).hasStatus(HttpStatus.NOT_FOUND);
    }

    private List<Long> boardIdsInList() throws Exception {
        MvcTestResult list = mvc.get().uri("/api/boards?size=100").exchange();
        List<Number> ids = JsonPath.read(list.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.content[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    private List<Long> pendingTargetIds(String collection) throws Exception {
        List<Number> ids = JsonPath.read(adminGet("/api/admin/reports/" + collection + "?size=100")
                .getResponse().getContentAsString(StandardCharsets.UTF_8), "$.content[*].targetId");
        return ids.stream().map(Number::longValue).toList();
    }

    private long createBoard(String token) throws Exception {
        return id(post(token, "/api/boards", "{\"title\": \"신고 대상 글\", \"content\": \"본문\", \"category\": \"GENERAL\"}"));
    }

    private MvcTestResult report(String token, String collection, long id, String reason) {
        return post(token, "/api/" + collection + "/" + id + "/reports", "{\"reason\": \"%s\"}".formatted(reason));
    }

    private MvcTestResult post(String token, String uri, String json) {
        return mvc.post().uri(uri).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult adminGet(String uri) {
        return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, bearer(1L, ADMIN, "ADMIN")).exchange();
    }

    private MvcTestResult adminPost(String uri) {
        return mvc.post().uri(uri).header(HttpHeaders.AUTHORIZATION, bearer(1L, ADMIN, "ADMIN")).exchange();
    }

    private String newUser() {
        return bearer(USERS.incrementAndGet(), "회원", "PARENT");
    }

    private static long id(MvcTestResult result) throws Exception {
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.<Number>read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id").longValue();
    }

    private String bearer(Long userId, String name, String role) {
        return "Bearer " + jwtUtil.generateAccessToken(new AuthUser(userId, "user" + userId + "@tspoon.com", name, role));
    }
}
