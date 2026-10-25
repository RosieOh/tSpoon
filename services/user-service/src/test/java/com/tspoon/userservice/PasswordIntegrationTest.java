package com.tspoon.userservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.auth.jwt.JwtUtil;
import com.tspoon.core.test.RedisTestcontainersConfiguration;
import com.tspoon.core.test.TestcontainersConfiguration;
import com.tspoon.userservice.event.UserEvents;
import com.tspoon.userservice.service.TokenService;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"eureka.client.enabled=false", "tspoon.outbox.relay-enabled=false"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class PasswordIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    TokenService tokenService;

    @Test
    void 재설정_링크로_비밀번호를_바꾸면_옛_비밀번호와_기존_로그인이_모두_무효가_된다() throws Exception {
        String email = registerVerified();
        String oldRefresh = read(login(email, "password123"), "$.refreshToken");

        assertThat(post("/api/users/password-reset/request", "{\"email\": \"%s\"}".formatted(email)))
                .hasStatus(HttpStatus.ACCEPTED);
        List<String> payloads = events(UserEvents.PASSWORD_RESET_REQUESTED, email);
        assertThat(payloads).hasSize(1);
        assertThat(Instant.parse(JsonPath.read(payloads.getFirst(), "$.expiresAt")))
                .isBetween(Instant.now().plus(Duration.ofMinutes(29)), Instant.now().plus(Duration.ofMinutes(31)));
        String token = JsonPath.read(payloads.getFirst(), "$.token");

        assertThat(confirm(token, "new-password-456")).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(login(email, "password123")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(login(email, "new-password-456")).hasStatusOk();
        assertThat(post("/api/users/token/refresh", "{\"refreshToken\": \"%s\"}".formatted(oldRefresh)))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(confirm(token, "another-password-789")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").isEqualTo("재설정 링크가 유효하지 않거나 만료되었습니다. 다시 요청해 주세요.");
        // 본인이 아닌 변경을 알아챌 수 있도록 변경 알림 메일을 요청한다
        assertThat(events(UserEvents.PASSWORD_CHANGED, email)).hasSize(1);
    }

    @Test
    void 재설정_요청은_가입_여부와_무관하게_202이고_인증한_회원에게만_대기_시간마다_한_번_보낸다() {
        String verified = registerVerified();
        String unverified = uniqueEmail();
        register(unverified);

        for (String email : List.of(verified, verified, unverified, uniqueEmail())) {
            assertThat(post("/api/users/password-reset/request", "{\"email\": \"%s\"}".formatted(email)))
                    .hasStatus(HttpStatus.ACCEPTED);
        }

        assertThat(events(UserEvents.PASSWORD_RESET_REQUESTED, verified)).hasSize(1);
        assertThat(events(UserEvents.PASSWORD_RESET_REQUESTED, unverified)).isEmpty();
    }

    @Test
    void 로그인한_회원은_현재_비밀번호를_확인한_뒤_바꾸고_새_토큰으로_계속_쓴다() throws Exception {
        String email = registerVerified();
        MvcTestResult login = login(email, "password123");
        String access = read(login, "$.accessToken");
        String oldRefresh = read(login, "$.refreshToken");

        assertThat(change(access, "wrong-password", "new-password-456"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_CURRENT_PASSWORD");

        MvcTestResult changed = change(access, "password123", "new-password-456");
        assertThat(changed).hasStatusOk();
        String newAccess = read(changed, "$.accessToken");
        long userId = ((Number) JsonPath.read(body(changed), "$.userId")).longValue();

        // 이전 Access Token 은 버전이 낮아 Gateway 에서 막히고, 새 토큰은 현재 버전이다
        assertThat(jwtUtil.verify(access).orElseThrow().version()).isLessThan(tokenService.currentVersion(userId));
        assertThat(jwtUtil.verify(newAccess).orElseThrow().version()).isEqualTo(tokenService.currentVersion(userId));
        assertThat(post("/api/users/token/refresh", "{\"refreshToken\": \"%s\"}".formatted(oldRefresh)))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(post("/api/users/token/refresh", "{\"refreshToken\": \"%s\"}".formatted(read(changed, "$.refreshToken"))))
                .hasStatusOk();
        assertThat(login(email, "new-password-456")).hasStatusOk();
        assertThat(events(UserEvents.PASSWORD_CHANGED, email)).hasSize(1);
    }

    @Test
    void 새_비밀번호가_짧으면_400() throws Exception {
        String email = registerVerified();
        String access = read(login(email, "password123"), "$.accessToken");

        assertThat(change(access, "password123", "short"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors.newPassword").isEqualTo("비밀번호는 8자 이상 72자 이하여야 합니다");
        assertThat(confirm("any-token", "short")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    private List<String> events(String eventType, String email) {
        return jdbcTemplate.queryForList("""
                SELECT payload::text FROM outbox_events WHERE event_type = ? AND payload ->> 'email' = ? ORDER BY created_at
                """, String.class, eventType, email);
    }

    private String registerVerified() {
        String email = uniqueEmail();
        register(email);
        TestAccounts.verifyEmail(jdbcTemplate, email);
        return email;
    }

    private void register(String email) {
        assertThat(post("/api/users/register", """
                {"email": "%s", "password": "password123", "name": "김부모"}
                """.formatted(email))).hasStatus(HttpStatus.CREATED);
    }

    private MvcTestResult login(String email, String password) {
        return post("/api/users/login", """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password));
    }

    private MvcTestResult confirm(String token, String newPassword) {
        return post("/api/users/password-reset/confirm", """
                {"token": "%s", "newPassword": "%s"}
                """.formatted(token, newPassword));
    }

    private MvcTestResult change(String accessToken, String currentPassword, String newPassword) {
        return mvc.put().uri("/api/users/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "%s", "newPassword": "%s"}
                        """.formatted(currentPassword, newPassword))
                .exchange();
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

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@tspoon.com";
    }
}
