package com.tspoon.userservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.core.test.RedisTestcontainersConfiguration;
import com.tspoon.core.test.TestcontainersConfiguration;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"eureka.client.enabled=false", "tspoon.outbox.relay-enabled=false"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class LoginLockIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 다섯_번_연속_틀리면_잠긴_동안은_올바른_비밀번호도_429() {
        String email = registerVerified();
        failFiveTimes(email);

        MvcTestResult locked = login(email, "password123");
        assertThat(locked).hasStatus(HttpStatus.TOO_MANY_REQUESTS)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo("LOGIN_LOCKED");
        assertThat(Long.parseLong(locked.getResponse().getHeader(HttpHeaders.RETRY_AFTER))).isBetween(840L, 900L);
    }

    @Test
    void 로그인에_성공하면_실패_횟수를_처음부터_다시_센다() {
        String email = registerVerified();
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < 4; i++) {
                assertThat(login(email, "wrong-password")).hasStatus(HttpStatus.UNAUTHORIZED);
            }
            assertThat(login(email, "password123")).hasStatusOk();
        }
    }

    @Test
    void 가입하지_않은_이메일도_똑같이_잠가_가입_여부를_드러내지_않는다() {
        String email = uniqueEmail();
        failFiveTimes(email);

        assertThat(login(email, "password123")).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        // 대소문자만 다른 주소로 우회할 수 없다
        assertThat(login(email.toUpperCase(), "password123")).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void 비밀번호를_재설정하면_잠금이_풀린다() {
        String email = registerVerified();
        failFiveTimes(email);
        assertThat(login(email, "password123")).hasStatus(HttpStatus.TOO_MANY_REQUESTS);

        post("/api/users/password-reset/request", "{\"email\": \"%s\"}".formatted(email));
        String token = JsonPath.read(jdbcTemplate.queryForObject("""
                SELECT payload::text FROM outbox_events WHERE event_type = ? AND payload ->> 'email' = ?
                """, String.class, UserEvents.PASSWORD_RESET_REQUESTED, email), "$.token");
        assertThat(post("/api/users/password-reset/confirm", """
                {"token": "%s", "newPassword": "new-password-456"}
                """.formatted(token))).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(login(email, "new-password-456")).hasStatusOk();
    }

    private void failFiveTimes(String email) {
        for (int i = 0; i < 5; i++) {
            assertThat(login(email, "wrong-password")).hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }

    private String registerVerified() {
        String email = uniqueEmail();
        assertThat(post("/api/users/register", """
                {"email": "%s", "password": "password123", "name": "김부모"}
                """.formatted(email))).hasStatus(HttpStatus.CREATED);
        TestAccounts.verifyEmail(jdbcTemplate, email);
        return email;
    }

    private MvcTestResult login(String email, String password) {
        return post("/api/users/login", """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password));
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@tspoon.com";
    }
}
