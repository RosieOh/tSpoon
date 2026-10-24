package com.tspoon.userservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.auth.AuthRedisKeys;
import com.tspoon.auth.jwt.JwtUtil;
import com.tspoon.auth.jwt.VerifiedToken;
import com.tspoon.core.test.RedisTestcontainersConfiguration;
import com.tspoon.core.test.TestcontainersConfiguration;
import com.tspoon.userservice.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
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
        "tspoon.admin.email=" + TokenApiIntegrationTest.ADMIN_EMAIL,
        "tspoon.admin.password=" + TokenApiIntegrationTest.ADMIN_PASSWORD
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class TokenApiIntegrationTest {

    static final String ADMIN_EMAIL = "token-admin@tspoon.com";
    static final String ADMIN_PASSWORD = "admin-password-123";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    TokenService tokenService;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void 로그인하면_30분짜리_Access_Token과_14일짜리_Refresh_Token을_받는다() throws Exception {
        MvcTestResult login = login(register());

        assertThat(login).hasStatusOk().bodyJson().extractingPath("$.expiresIn").isEqualTo(1800);
        assertThat(login).bodyJson().extractingPath("$.refreshExpiresIn").isEqualTo(1_209_600);
        String refreshToken = read(login, "$.refreshToken");
        assertThat(redis.keys("tspoon:auth:refresh:*")).noneMatch(key -> key.contains(refreshToken)); // 원문은 저장하지 않는다
    }

    @Test
    void 재발급하면_Access와_Refresh_Token이_모두_새로_바뀐다() throws Exception {
        MvcTestResult login = login(register());

        MvcTestResult refreshed = refresh(read(login, "$.refreshToken"));

        assertThat(refreshed).hasStatusOk();
        assertThat(read(refreshed, "$.accessToken")).isNotEqualTo(read(login, "$.accessToken"));
        assertThat(read(refreshed, "$.refreshToken")).isNotEqualTo(read(login, "$.refreshToken"));
    }

    @Test
    void 이미_쓴_Refresh_Token이_다시_오면_탈취로_보고_그_계열을_모두_폐기한다() throws Exception {
        String first = read(login(register()), "$.refreshToken");
        String second = read(refresh(first), "$.refreshToken");

        assertThat(refresh(first)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(refresh(second)).hasStatus(HttpStatus.UNAUTHORIZED); // 정상 사용자가 받은 다음 토큰도 함께 폐기
    }

    @Test
    void 로그아웃하면_Refresh_Token은_폐기되고_Access_Token은_차단_목록에_오른다() throws Exception {
        MvcTestResult login = login(register());
        String accessToken = read(login, "$.accessToken");
        String refreshToken = read(login, "$.refreshToken");

        assertThat(logout(accessToken, refreshToken)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(refresh(refreshToken)).hasStatus(HttpStatus.UNAUTHORIZED);
        String denylistKey = AuthRedisKeys.accessDenylist(jwtUtil.verify(accessToken).orElseThrow().tokenId());
        assertThat(redis.hasKey(denylistKey)).isTrue();
        assertThat(redis.getExpire(denylistKey)).isPositive().isLessThanOrEqualTo(1800);
    }

    @Test
    void 로그아웃할_때_다른_회원의_Refresh_Token은_폐기하지_않는다() throws Exception {
        String attacker = read(login(register()), "$.accessToken");
        String victimRefresh = read(login(register()), "$.refreshToken");

        assertThat(logout(attacker, victimRefresh)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(refresh(victimRefresh)).hasStatusOk();
    }

    @Test
    void 정지된_회원은_재발급할_수_없고_기존_Access_Token도_무효가_된다() throws Exception {
        String email = register();
        MvcTestResult login = login(email);
        VerifiedToken access = jwtUtil.verify(read(login, "$.accessToken")).orElseThrow();

        assertThat(changeStatus(read(login, "$.userId", Number.class).longValue(), "SUSPENDED")).hasStatusOk();

        assertThat(refresh(read(login, "$.refreshToken"))).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(access.version()).isLessThan(tokenService.currentVersion(access.user().userId()));
    }

    @Test
    void 역할이_바뀌면_기존_Access_Token은_낡고_재발급한_토큰에는_새_역할이_담긴다() throws Exception {
        MvcTestResult login = login(register());
        Long userId = read(login, "$.userId", Number.class).longValue();
        VerifiedToken before = jwtUtil.verify(read(login, "$.accessToken")).orElseThrow();

        assertThat(changeRole(userId, "TEACHER")).hasStatusOk();

        assertThat(before.version()).isLessThan(tokenService.currentVersion(userId));
        VerifiedToken after = jwtUtil.verify(read(refresh(read(login, "$.refreshToken")), "$.accessToken")).orElseThrow();
        assertThat(after.user().role()).isEqualTo("TEACHER");
        assertThat(after.version()).isEqualTo(tokenService.currentVersion(userId));
    }

    @Test
    void 존재하지_않는_Refresh_Token은_401() {
        assertThat(refresh("not-issued-token")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private String register() {
        String email = "user-" + UUID.randomUUID() + "@tspoon.com";
        assertThat(post("/api/users/register", null, """
                {"email": "%s", "password": "password123", "name": "회원"}
                """.formatted(email))).hasStatus(HttpStatus.CREATED);
        return email;
    }

    private MvcTestResult login(String email) {
        return post("/api/users/login", null, """
                {"email": "%s", "password": "%s"}
                """.formatted(email, email.equals(ADMIN_EMAIL) ? ADMIN_PASSWORD : "password123"));
    }

    private MvcTestResult refresh(String refreshToken) {
        return post("/api/users/token/refresh", null, """
                {"refreshToken": "%s"}
                """.formatted(refreshToken));
    }

    private MvcTestResult logout(String accessToken, String refreshToken) {
        return post("/api/users/logout", accessToken, """
                {"refreshToken": "%s"}
                """.formatted(refreshToken));
    }

    private MvcTestResult changeRole(Long userId, String role) throws Exception {
        return patch("/api/admin/users/" + userId + "/role", """
                {"role": "%s"}
                """.formatted(role));
    }

    private MvcTestResult changeStatus(Long userId, String status) throws Exception {
        return patch("/api/admin/users/" + userId + "/status", """
                {"status": "%s"}
                """.formatted(status));
    }

    private MvcTestResult patch(String uri, String json) throws Exception {
        String admin = read(login(ADMIN_EMAIL), "$.accessToken");
        return mvc.patch().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private MvcTestResult post(String uri, String accessToken, String json) {
        var request = mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json);
        if (accessToken != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return request.exchange();
    }

    private static String read(MvcTestResult result, String path) throws Exception {
        return read(result, path, String.class);
    }

    private static <T> T read(MvcTestResult result, String path, Class<T> type) throws Exception {
        return type.cast(JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), path));
    }
}
