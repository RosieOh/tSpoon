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

@SpringBootTest(properties = {"eureka.client.enabled=false", "tspoon.outbox.relay-enabled=false",
        "spring.kafka.listener.auto-startup=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BoardApiIntegrationTest {

    private static final String BOARD_JSON = """
            {"title": "첫 글", "content": "안녕하세요", "category": "GENERAL"}
            """;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JwtUtil jwtUtil;

    @Test
    void 게시글_작성_조회_좋아요_삭제_흐름() throws Exception {
        String author = bearer(1L, "작성자");
        String reader = bearer(2L, "독자");

        // 작성: 작성자 이름은 JWT 에서 가져온다
        MvcTestResult created = mvc.post().uri("/api/boards")
                .header(HttpHeaders.AUTHORIZATION, author)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BOARD_JSON)
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).bodyJson().extractingPath("$.authorName").isEqualTo("작성자");
        Integer boardId = JsonPath.read(created.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");

        // 조회: 비로그인 허용, 조회할 때마다 조회수 증가
        assertThat(mvc.get().uri("/api/boards/{id}", boardId))
                .hasStatusOk().bodyJson().extractingPath("$.viewCount").isEqualTo(1);
        assertThat(mvc.get().uri("/api/boards/{id}", boardId))
                .hasStatusOk().bodyJson().extractingPath("$.viewCount").isEqualTo(2);

        // 좋아요: 중복 좋아요는 409
        assertThat(like(boardId, reader)).hasStatusOk();
        assertThat(like(boardId, reader)).hasStatus(HttpStatus.CONFLICT);
        assertThat(mvc.get().uri("/api/boards/{id}", boardId))
                .hasStatusOk().bodyJson().extractingPath("$.likeCount").isEqualTo(1);

        // 좋아요 취소: 두 번째 취소는 404
        assertThat(mvc.delete().uri("/api/boards/{id}/like", boardId).header(HttpHeaders.AUTHORIZATION, reader))
                .hasStatusOk();
        assertThat(mvc.delete().uri("/api/boards/{id}/like", boardId).header(HttpHeaders.AUTHORIZATION, reader))
                .hasStatus(HttpStatus.NOT_FOUND);

        // 작성자가 아니면 수정/삭제 불가
        assertThat(mvc.put().uri("/api/boards/{id}", boardId)
                .header(HttpHeaders.AUTHORIZATION, reader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BOARD_JSON))
                .hasStatus(HttpStatus.FORBIDDEN);

        // 삭제 후에는 조회되지 않는다 (soft delete)
        assertThat(mvc.delete().uri("/api/boards/{id}", boardId).header(HttpHeaders.AUTHORIZATION, author))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/boards/{id}", boardId))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void 비로그인_사용자는_글을_쓸_수_없다() {
        assertThat(mvc.post().uri("/api/boards")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BOARD_JSON))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 존재하지_않는_카테고리로_조회하면_500이_아니라_400() {
        assertThat(mvc.get().uri("/api/boards").param("category", "NOT_A_CATEGORY"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 목록은_페이지_정보와_함께_내려온다() {
        String author = bearer(3L, "목록작성자");
        for (int i = 0; i < 3; i++) {
            mvc.post().uri("/api/boards").header(HttpHeaders.AUTHORIZATION, author)
                    .contentType(MediaType.APPLICATION_JSON).content(BOARD_JSON).exchange();
        }

        MvcTestResult page = mvc.get().uri("/api/boards").param("page", "0").param("size", "2").exchange();

        assertThat(page).hasStatusOk().bodyJson().extractingPath("$.content.length()").isEqualTo(2);
        assertThat(page).bodyJson().extractingPath("$.page.size").isEqualTo(2);
        assertThat(page).bodyJson().extractingPath("$.page.number").isEqualTo(0);
        assertThat(page).bodyJson().extractingPath("$.page.totalElements").asNumber().satisfies(n -> assertThat(n.intValue()).isGreaterThanOrEqualTo(3));
    }

    @Test
    void 페이지_크기가_범위를_벗어나면_항목별_메시지와_함께_400() {
        assertThat(mvc.get().uri("/api/boards").param("size", "1000"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.errors.size").isEqualTo("size 는 100 이하여야 합니다");
    }

    @Test
    void 에러는_ProblemDetail_형식이고_표준_예외도_한국어로_설명한다() {
        assertThat(mvc.get().uri("/api/boards").param("category", "NOT_A_CATEGORY"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.detail").isEqualTo("'category' 의 값 'NOT_A_CATEGORY' 이(가) 올바르지 않습니다.");

        assertThat(mvc.get().uri("/api/boards/{id}", 999_999))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.title").isEqualTo("Not Found");
                    assertThat(json).extractingPath("$.detail").isEqualTo("게시글을 찾을 수 없습니다.");
                    assertThat(json).extractingPath("$.instance").isEqualTo("/api/boards/999999");
                });
    }

    @Test
    void 생성_시각은_시간대가_붙은_ISO_8601_로_내려온다() {
        MvcTestResult created = mvc.post().uri("/api/boards")
                .header(HttpHeaders.AUTHORIZATION, bearer(4L, "시간"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BOARD_JSON)
                .exchange();

        assertThat(created).bodyJson().extractingPath("$.createdAt").asString().endsWith("Z");
    }

    private MvcTestResult like(Integer boardId, String token) {
        return mvc.post().uri("/api/boards/{id}/like", boardId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .exchange();
    }

    private String bearer(Long userId, String name) {
        return "Bearer " + jwtUtil.generateAccessToken(
                new AuthUser(userId, "user" + userId + "@tspoon.com", name, "PARENT"));
    }
}
