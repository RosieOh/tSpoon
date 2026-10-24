package com.tspoon.communityservice;

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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"eureka.client.enabled=false", "tspoon.outbox.relay-enabled=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CommentApiIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JwtUtil jwtUtil;

    private final String author = "작성자";

    @Test
    void 댓글과_답글을_달면_목록에서_답글이_댓글_아래로_묶이고_댓글_수가_늘어난다() throws Exception {
        String writer = bearer(10L, author, "PARENT");
        String reader = bearer(11L, "독자", "PARENT");
        long boardId = createBoard(writer);

        long commentId = createComment(reader, boardId, "좋은 글이네요", null);
        createComment(writer, boardId, "감사합니다", commentId);

        MvcTestResult list = mvc.get().uri("/api/boards/{id}/comments", boardId).exchange(); // 비로그인 조회
        assertThat(list).hasStatusOk().bodyJson().extractingPath("$.length()").isEqualTo(1);
        assertThat(list).bodyJson().extractingPath("$[0].content").isEqualTo("좋은 글이네요");
        assertThat(list).bodyJson().extractingPath("$[0].replies[0].content").isEqualTo("감사합니다");
        assertThat(list).bodyJson().extractingPath("$[0].replies[0].authorName").isEqualTo(author);

        assertThat(mvc.get().uri("/api/boards/{id}", boardId))
                .bodyJson().extractingPath("$.commentCount").isEqualTo(2);
    }

    @Test
    void 답글에는_다시_답글을_달_수_없다() throws Exception {
        String user = bearer(12L, "회원", "PARENT");
        long boardId = createBoard(user);
        long commentId = createComment(user, boardId, "댓글", null);
        long replyId = createComment(user, boardId, "답글", commentId);

        assertThat(postComment(user, boardId, "답글의 답글", replyId))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").isEqualTo("답글에는 답글을 달 수 없습니다.");
    }

    @Test
    void 다른_게시글의_댓글에는_답글을_달_수_없다() throws Exception {
        String user = bearer(13L, "회원", "PARENT");
        long boardA = createBoard(user);
        long boardB = createBoard(user);
        long commentOnA = createComment(user, boardA, "A 의 댓글", null);

        assertThat(postComment(user, boardB, "엉뚱한 답글", commentOnA)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 삭제한_댓글은_답글이_남아_있으면_자리만_남고_없으면_목록에서_빠진다() throws Exception {
        String user = bearer(14L, "회원", "PARENT");
        long boardId = createBoard(user);
        long withReply = createComment(user, boardId, "답글 있는 댓글", null);
        createComment(user, boardId, "답글", withReply);
        long alone = createComment(user, boardId, "답글 없는 댓글", null);

        assertThat(delete(user, withReply)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(delete(user, alone)).hasStatus(HttpStatus.NO_CONTENT);

        MvcTestResult list = mvc.get().uri("/api/boards/{id}/comments", boardId).exchange();
        assertThat(list).bodyJson().extractingPath("$.length()").isEqualTo(1);
        assertThat(list).bodyJson().extractingPath("$[0].deleted").isEqualTo(true);
        assertThat(list).bodyJson().extractingPath("$[0].content").isEqualTo("삭제된 댓글입니다.");
        assertThat(list).bodyJson().extractingPath("$[0].authorName").isNull();
        assertThat(list).bodyJson().extractingPath("$[0].replies.length()").isEqualTo(1);
        assertThat(mvc.get().uri("/api/boards/{id}", boardId)).bodyJson().extractingPath("$.commentCount").isEqualTo(1);
    }

    @Test
    void 수정은_작성자만_삭제는_작성자와_관리자만_할_수_있다() throws Exception {
        String writer = bearer(15L, "작성자", "PARENT");
        String other = bearer(16L, "다른회원", "PARENT");
        String admin = bearer(1L, "관리자", "ADMIN");
        long boardId = createBoard(writer);
        long commentId = createComment(writer, boardId, "원래 내용", null);

        assertThat(mvc.put().uri("/api/comments/{id}", commentId).header(HttpHeaders.AUTHORIZATION, other)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"남이 고침\"}"))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.put().uri("/api/comments/{id}", commentId).header(HttpHeaders.AUTHORIZATION, writer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"고친 내용\"}"))
                .hasStatusOk().bodyJson().extractingPath("$.content").isEqualTo("고친 내용");

        assertThat(delete(other, commentId)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(delete(admin, commentId)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(delete(writer, commentId)).hasStatus(HttpStatus.NOT_FOUND); // 이미 삭제됨
    }

    @Test
    void 관리자는_다른_회원의_게시글을_삭제할_수_있다() throws Exception {
        long boardId = createBoard(bearer(17L, "작성자", "PARENT"));

        assertThat(mvc.delete().uri("/api/boards/{id}", boardId).header(HttpHeaders.AUTHORIZATION, bearer(1L, "관리자", "ADMIN")))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/boards/{id}/comments", boardId)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void 비로그인_사용자는_댓글을_쓸_수_없다() throws Exception {
        long boardId = createBoard(bearer(18L, "작성자", "PARENT"));

        assertThat(mvc.post().uri("/api/boards/{id}/comments", boardId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"익명\"}"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private long createBoard(String token) throws Exception {
        MvcTestResult result = mvc.post().uri("/api/boards").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"글\", \"content\": \"본문\", \"category\": \"GENERAL\"}")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return id(result);
    }

    private long createComment(String token, long boardId, String content, Long parentId) throws Exception {
        MvcTestResult result = postComment(token, boardId, content, parentId);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return id(result);
    }

    private MvcTestResult postComment(String token, long boardId, String content, Long parentId) {
        String parent = parentId == null ? "null" : parentId.toString();
        return mvc.post().uri("/api/boards/{id}/comments", boardId).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"%s\", \"parentCommentId\": %s}".formatted(content, parent))
                .exchange();
    }

    private MvcTestResult delete(String token, long commentId) {
        return mvc.delete().uri("/api/comments/{id}", commentId).header(HttpHeaders.AUTHORIZATION, token).exchange();
    }

    private static long id(MvcTestResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        return id.longValue();
    }

    private String bearer(Long userId, String name, String role) {
        return "Bearer " + jwtUtil.generateAccessToken(new AuthUser(userId, "user" + userId + "@tspoon.com", name, role));
    }
}
