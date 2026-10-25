package com.tspoon.userservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.core.test.RedisTestcontainersConfiguration;
import com.tspoon.core.test.TestcontainersConfiguration;
import com.tspoon.userservice.event.UserEvents;
import com.tspoon.userservice.repository.UserRepository;
import com.tspoon.userservice.service.EmailVerificationService;
import com.tspoon.userservice.service.UserRedisKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"eureka.client.enabled=false", "tspoon.outbox.relay-enabled=false"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class EmailVerificationIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EmailVerificationService emailVerificationService;

    @Test
    void 인증_전에는_로그인할_수_없고_메일_링크의_토큰으로_인증하면_로그인된다() throws Exception {
        String email = uniqueEmail();
        assertThat(register(email)).hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.emailVerified").isEqualTo(false);

        // 메일 발송 요청은 회원 저장과 같은 트랜잭션에 outbox 로 남는다
        List<Map<String, Object>> events = verificationEvents(email);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).containsEntry("topic", UserEvents.TOPIC);
        Map<String, Object> payload = JsonPath.read((String) events.getFirst().get("payload"), "$");
        assertThat(payload).containsEntry("email", email).containsEntry("name", "김부모").containsKeys("token", "expiresAt");
        assertThat(Instant.parse((String) payload.get("expiresAt")))
                .isBetween(Instant.now().plus(Duration.ofHours(23)), Instant.now().plus(Duration.ofHours(25)));

        assertThat(login(email)).hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.code").isEqualTo("EMAIL_NOT_VERIFIED");
        // 비밀번호가 틀리면 인증 여부를 알려 주지 않는다
        assertThat(mvc.post().uri("/api/users/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"wrong-password\"}".formatted(email)))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        String token = (String) payload.get("token");
        assertThat(confirm(token)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(login(email)).hasStatusOk();
        // 한 번 쓴 토큰은 다시 쓸 수 없다
        assertThat(confirm(token)).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").isEqualTo("인증 링크가 유효하지 않거나 만료되었습니다. 인증 메일을 다시 받아 주세요.");
        // Redis 에는 원문이 아니라 해시만 남는다
        assertThat(redis.keys("tspoon:user:one-time:*" + token + "*")).isEmpty();
    }

    @Test
    void 재발송은_대기_시간이_지나야_새_메일을_만들고_이전_토큰은_쓸_수_없게_된다() throws Exception {
        String email = uniqueEmail();
        long userId = id(register(email));

        // 가입 직후에는 대기 시간이라 새 메일을 만들지 않는다
        assertThat(resend(email)).hasStatus(HttpStatus.ACCEPTED);
        assertThat(verificationEvents(email)).hasSize(1);

        redis.delete(UserRedisKeys.verificationCooldown(userId)); // 대기 시간이 지난 것으로
        assertThat(resend(email)).hasStatus(HttpStatus.ACCEPTED);
        List<Map<String, Object>> events = verificationEvents(email);
        assertThat(events).hasSize(2);

        assertThat(confirm(tokenOf(events.get(0)))).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(confirm(tokenOf(events.get(1)))).hasStatus(HttpStatus.NO_CONTENT);

        // 이미 인증했으면 다시 보내지 않는다
        redis.delete(UserRedisKeys.verificationCooldown(userId));
        assertThat(resend(email)).hasStatus(HttpStatus.ACCEPTED);
        assertThat(verificationEvents(email)).hasSize(2);
    }

    @Test
    void 가입하지_않은_이메일로_재발송을_요청해도_같은_응답을_준다() {
        String email = uniqueEmail();

        assertThat(resend(email)).hasStatus(HttpStatus.ACCEPTED);
        assertThat(verificationEvents(email)).isEmpty();
        assertThat(resend("not-an-email")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 보관_기간이_지난_미인증_가입은_지워지고_같은_이메일로_다시_가입할_수_있다() throws Exception {
        String stale = uniqueEmail();
        String verified = uniqueEmail();
        String fresh = uniqueEmail();
        register(stale);
        register(verified);
        register(fresh);
        TestAccounts.verifyEmail(jdbcTemplate, verified);
        jdbcTemplate.update("UPDATE users SET created_at = now() - interval '8 days' WHERE email IN (?, ?)", stale, verified);

        assertThat(emailVerificationService.deleteExpiredUnverifiedUsers()).isGreaterThanOrEqualTo(1);

        assertThat(userRepository.existsByEmail(stale)).isFalse();
        assertThat(userRepository.existsByEmail(verified)).isTrue();
        assertThat(userRepository.existsByEmail(fresh)).isTrue();
        assertThat(register(stale)).hasStatus(HttpStatus.CREATED);
    }

    private List<Map<String, Object>> verificationEvents(String email) {
        return jdbcTemplate.queryForList("""
                SELECT topic, payload::text AS payload FROM outbox_events
                WHERE event_type = ? AND payload ->> 'email' = ? ORDER BY created_at
                """, UserEvents.EMAIL_VERIFICATION_REQUESTED, email);
    }

    private static String tokenOf(Map<String, Object> event) {
        return JsonPath.read((String) event.get("payload"), "$.token");
    }

    private MvcTestResult register(String email) {
        return post("/api/users/register", """
                {"email": "%s", "password": "password123", "name": "김부모"}
                """.formatted(email));
    }

    private MvcTestResult login(String email) {
        return post("/api/users/login", """
                {"email": "%s", "password": "password123"}
                """.formatted(email));
    }

    private MvcTestResult confirm(String token) {
        return post("/api/users/email-verification/confirm", "{\"token\": \"%s\"}".formatted(token));
    }

    private MvcTestResult resend(String email) {
        return post("/api/users/email-verification/resend", "{\"email\": \"%s\"}".formatted(email));
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static long id(MvcTestResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id")).longValue();
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@tspoon.com";
    }
}
