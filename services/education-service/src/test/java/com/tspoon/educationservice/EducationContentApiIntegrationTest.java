package com.tspoon.educationservice;

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

@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EducationContentApiIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JwtUtil jwtUtil;

    @Test
    void 교사가_콘텐츠를_올리면_누구나_목록과_상세를_보고_조회수가_오른다() throws Exception {
        long id = create(bearer(100L, "김교사", "TEACHER"), "초등 수학 놀이", "MATH", false);

        assertThat(mvc.get().uri("/api/contents").param("category", "MATH"))
                .hasStatusOk().bodyJson().extractingPath("$.content[?(@.id == %d)].title".formatted(id)).asArray().containsExactly("초등 수학 놀이");
        assertThat(mvc.get().uri("/api/contents/{id}", id))
                .hasStatusOk().bodyJson().extractingPath("$.viewCount").isEqualTo(1);
        assertThat(mvc.get().uri("/api/contents/{id}", id))
                .hasStatusOk().bodyJson().extractingPath("$.authorName").isEqualTo("김교사");
    }

    @Test
    void 학부모는_콘텐츠를_올릴_수_없다() {
        assertThat(post(bearer(101L, "학부모", "PARENT"), body("글", "LANGUAGE", false))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(post(null, body("글", "LANGUAGE", false))).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 임시저장_글은_목록에_없고_작성자와_관리자만_상세를_본다() throws Exception {
        String teacher = bearer(102L, "작성교사", "TEACHER");
        long id = create(teacher, "작성 중인 글", "SCIENCE", true);

        assertThat(mvc.get().uri("/api/contents").param("category", "SCIENCE").param("size", "100"))
                .bodyJson().extractingPath("$.content[?(@.id == %d)]".formatted(id)).asArray().isEmpty();
        assertThat(mvc.get().uri("/api/contents/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/contents/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(103L, "남", "TEACHER")))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/contents/{id}", id).header(HttpHeaders.AUTHORIZATION, teacher))
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("DRAFT");
        assertThat(mvc.get().uri("/api/contents/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(1L, "관리자", "ADMIN")))
                .hasStatusOk();

        // 공개로 바꾸면 누구나 본다
        assertThat(put(teacher, id, body("다 쓴 글", "SCIENCE", false))).hasStatusOk();
        assertThat(mvc.get().uri("/api/contents/{id}", id)).hasStatusOk().bodyJson().extractingPath("$.title").isEqualTo("다 쓴 글");
    }

    @Test
    void 수정과_삭제는_작성자와_관리자만_할_수_있다() throws Exception {
        String teacher = bearer(104L, "작성교사", "TEACHER");
        long id = create(teacher, "원래 제목", "ART", false);

        assertThat(put(bearer(105L, "다른교사", "TEACHER"), id, body("남이 고침", "ART", false))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(put(bearer(1L, "관리자", "ADMIN"), id, body("관리자가 고침", "ART", false))).hasStatusOk();
        assertThat(mvc.delete().uri("/api/contents/{id}", id).header(HttpHeaders.AUTHORIZATION, teacher))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/contents/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void 북마크하면_수가_늘고_내_북마크에_나오며_중복은_409() throws Exception {
        long id = create(bearer(106L, "교사", "TEACHER"), "북마크할 글", "PARENTING", false);
        String parent = bearer(107L, "학부모", "PARENT");

        assertThat(mvc.post().uri("/api/contents/{id}/bookmark", id).header(HttpHeaders.AUTHORIZATION, parent)).hasStatusOk();
        assertThat(mvc.post().uri("/api/contents/{id}/bookmark", id).header(HttpHeaders.AUTHORIZATION, parent))
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(mvc.get().uri("/api/contents/{id}", id)).bodyJson().extractingPath("$.bookmarkCount").isEqualTo(1);

        MvcTestResult mine = mvc.get().uri("/api/contents/bookmarks/me").header(HttpHeaders.AUTHORIZATION, parent).exchange();
        assertThat(mine).hasStatusOk().bodyJson().extractingPath("$.content[0].title").isEqualTo("북마크할 글");
        assertThat(mvc.get().uri("/api/contents/bookmarks/me")).hasStatus(HttpStatus.UNAUTHORIZED);

        assertThat(mvc.delete().uri("/api/contents/{id}/bookmark", id).header(HttpHeaders.AUTHORIZATION, parent))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.delete().uri("/api/contents/{id}/bookmark", id).header(HttpHeaders.AUTHORIZATION, parent))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/contents/{id}", id)).bodyJson().extractingPath("$.bookmarkCount").isEqualTo(0);
    }

    private long create(String token, String title, String category, boolean draft) throws Exception {
        MvcTestResult result = post(token, body(title, category, draft));
        assertThat(result).hasStatus(HttpStatus.CREATED);
        Number id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        return id.longValue();
    }

    private MvcTestResult post(String token, String json) {
        var request = mvc.post().uri("/api/contents").contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, token);
        }
        return request.exchange();
    }

    private MvcTestResult put(String token, long id, String json) {
        return mvc.put().uri("/api/contents/{id}", id).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String body(String title, String category, boolean draft) {
        return """
                {"title": "%s", "description": "설명", "content": "본문", "type": "ARTICLE",
                 "category": "%s", "estimatedTime": 15, "targetAge": "초등 저학년", "draft": %s}
                """.formatted(title, category, draft);
    }

    private String bearer(Long userId, String name, String role) {
        return "Bearer " + jwtUtil.generateAccessToken(new AuthUser(userId, "user" + userId + "@tspoon.com", name, role));
    }
}
