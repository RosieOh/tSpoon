package com.tspoon.userservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.core.jwt.JwtUtil;
import com.tspoon.core.security.AuthUser;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "tspoon.admin.email=" + AdminUserApiIntegrationTest.ADMIN_EMAIL,
        "tspoon.admin.password=" + AdminUserApiIntegrationTest.ADMIN_PASSWORD
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminUserApiIntegrationTest {

    static final String ADMIN_EMAIL = "admin@tspoon.com";
    static final String ADMIN_PASSWORD = "admin-password-123";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JwtUtil jwtUtil;

    @Test
    void 기동_시_만든_초기_관리자로_로그인해_전체_회원_목록을_볼_수_있다() throws Exception {
        MvcTestResult login = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        assertThat(login).hasStatusOk().bodyJson().extractingPath("$.role").isEqualTo("ADMIN");

        assertThat(mvc.get().uri("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(login)))
                .hasStatusOk();
    }

    @Test
    void 관리자가_역할을_바꾸면_다시_로그인한_토큰에_새_역할이_담긴다() throws Exception {
        String email = uniqueEmail();
        Long userId = register(email);

        assertThat(patch("/api/admin/users/{id}/role", userId, adminToken(), """
                {"role": "TEACHER"}
                """))
                .hasStatusOk()
                .bodyJson().extractingPath("$.role").isEqualTo("TEACHER");

        String relogin = token(login(email, "password123"));
        assertThat(jwtUtil.parseAccessToken(relogin)).map(AuthUser::role).contains("TEACHER");
    }

    @Test
    void 정지된_회원은_로그인할_수_없다() throws Exception {
        String email = uniqueEmail();
        Long userId = register(email);

        assertThat(patch("/api/admin/users/{id}/status", userId, adminToken(), """
                {"status": "SUSPENDED"}
                """))
                .hasStatusOk();

        assertThat(login(email, "password123")).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void 관리자는_자기_자신의_역할을_바꿀_수_없다() throws Exception {
        MvcTestResult login = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        Number adminId = JsonPath.read(body(login), "$.userId");

        assertThat(patch("/api/admin/users/{id}/role", adminId.longValue(), token(login), """
                {"role": "PARENT"}
                """))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 일반_회원은_관리자_API를_호출할_수_없다() throws Exception {
        Long userId = register(uniqueEmail());
        String parent = jwtUtil.generateAccessToken(new AuthUser(9_999L, "p@tspoon.com", "학부모", "PARENT"));

        assertThat(patch("/api/admin/users/{id}/role", userId, parent, """
                {"role": "ADMIN"}
                """))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void 없는_역할_값은_400() throws Exception {
        Long userId = register(uniqueEmail());

        assertThat(patch("/api/admin/users/{id}/role", userId, adminToken(), """
                {"role": "SUPERUSER"}
                """))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    private MvcTestResult patch(String uri, Long id, String token, String json) {
        return mvc.patch().uri(uri, id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private Long register(String email) throws Exception {
        MvcTestResult result = mvc.post().uri("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "password123", "name": "회원"}
                        """.formatted(email))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return ((Number) JsonPath.read(body(result), "$.id")).longValue();
    }

    private MvcTestResult login(String email, String password) {
        return mvc.post().uri("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password))
                .exchange();
    }

    private String adminToken() throws Exception {
        return token(login(ADMIN_EMAIL, ADMIN_PASSWORD));
    }

    private static String token(MvcTestResult login) throws Exception {
        return JsonPath.read(body(login), "$.accessToken");
    }

    private static String body(MvcTestResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@tspoon.com";
    }
}
