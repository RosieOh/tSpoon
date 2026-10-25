package com.tspoon.userservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.core.test.RedisTestcontainersConfiguration;
import com.tspoon.core.test.TestcontainersConfiguration;
import com.tspoon.event.test.EventContracts;
import com.tspoon.userservice.event.UserEvents;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "tspoon.outbox.relay-enabled=false",
        "tspoon.admin.email=" + WithdrawalIntegrationTest.ADMIN_EMAIL,
        "tspoon.admin.password=" + WithdrawalIntegrationTest.ADMIN_PASSWORD
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class WithdrawalIntegrationTest {

    static final String ADMIN_EMAIL = "withdrawal-admin@tspoon.com";
    static final String ADMIN_PASSWORD = "admin-password-123";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 비밀번호를_확인하고_탈퇴하면_개인정보와_로그인이_사라지고_같은_이메일로_다시_가입할_수_있다() throws Exception {
        String email = registerVerified();
        MvcTestResult login = login(email, "password123");
        String access = read(login, "$.accessToken");
        long userId = ((Number) JsonPath.read(body(login), "$.userId")).longValue();
        assertThat(mvc.post().uri("/api/users/children").header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"김하나\", \"grade\": \"ELEMENTARY_1\", \"interests\": [\"독서\"]}"))
                .hasStatus(HttpStatus.CREATED);

        assertThat(withdraw(access, "wrong-password")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_PASSWORD");
        assertThat(withdraw(access, "password123")).hasStatus(HttpStatus.NO_CONTENT);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT email, name, phone, status, withdrawn_at FROM users WHERE id = ?", userId);
        assertThat(row.get("email")).isEqualTo("withdrawn-" + userId + "@withdrawn.invalid");
        assertThat(row).containsEntry("name", "탈퇴한 회원").containsEntry("phone", null).containsEntry("status", "WITHDRAWN");
        assertThat(row.get("withdrawn_at")).isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM children WHERE parent_id = ?", Long.class, userId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM child_interests", Long.class)).isZero();
        EventContracts.assertPayloadConforms(UserEvents.WITHDRAWN, jdbcTemplate.queryForObject(
                "SELECT payload::text FROM outbox_events WHERE event_type = ? AND aggregate_id = ?",
                String.class, UserEvents.WITHDRAWN, String.valueOf(userId)));

        assertThat(login(email, "password123")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(post("/api/users/token/refresh", "{\"refreshToken\": \"%s\"}".formatted(read(login, "$.refreshToken"))))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/users/{id}", userId).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(register(email)).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void 마지막_관리자는_탈퇴할_수_없다() throws Exception {
        assertThat(withdraw(adminToken(), ADMIN_PASSWORD)).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void 관리자는_회원을_탈퇴시키거나_탈퇴한_회원의_상태를_바꿀_수_없다() throws Exception {
        String email = registerVerified();
        MvcTestResult login = login(email, "password123");
        long userId = ((Number) JsonPath.read(body(login), "$.userId")).longValue();

        assertThat(changeStatus(userId, "WITHDRAWN")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(withdraw(read(login, "$.accessToken"), "password123")).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(changeStatus(userId, "ACTIVE")).hasStatus(HttpStatus.CONFLICT);
    }

    private MvcTestResult changeStatus(long userId, String status) throws Exception {
        return mvc.patch().uri("/api/admin/users/{id}/status", userId).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"%s\"}".formatted(status)).exchange();
    }

    private MvcTestResult withdraw(String accessToken, String password) {
        return mvc.delete().uri("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\": \"%s\"}".formatted(password)).exchange();
    }

    private String adminToken() throws Exception {
        return read(login(ADMIN_EMAIL, ADMIN_PASSWORD), "$.accessToken");
    }

    private String registerVerified() {
        String email = "user-" + UUID.randomUUID() + "@tspoon.com";
        assertThat(register(email)).hasStatus(HttpStatus.CREATED);
        TestAccounts.verifyEmail(jdbcTemplate, email);
        return email;
    }

    private MvcTestResult register(String email) {
        return post("/api/users/register", """
                {"email": "%s", "password": "password123", "name": "김부모", "phone": "010-1234-5678"}
                """.formatted(email));
    }

    private MvcTestResult login(String email, String password) {
        return post("/api/users/login", """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password));
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String read(MvcTestResult result, String path) throws Exception {
        return JsonPath.read(body(result), path);
    }

    private static String body(MvcTestResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
