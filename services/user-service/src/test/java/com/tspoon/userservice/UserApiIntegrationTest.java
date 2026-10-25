package com.tspoon.userservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.auth.AuthUser;
import com.tspoon.auth.jwt.JwtUtil;
import com.tspoon.core.test.RedisTestcontainersConfiguration;
import com.tspoon.core.test.TestcontainersConfiguration;
import com.tspoon.event.test.EventContracts;
import com.tspoon.userservice.entity.User;
import com.tspoon.userservice.event.UserEvents;
import com.tspoon.userservice.repository.UserRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"eureka.client.enabled=false", "tspoon.outbox.relay-enabled=false"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class UserApiIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TokenService tokenService;

    @Test
    void 회원가입_후_로그인하면_발급된_JWT로_프로필을_조회할_수_있다() throws Exception {
        String email = uniqueEmail();

        assertThat(register(email, "password123", "김부모"))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.email").isEqualTo(email);
        TestAccounts.verifyEmail(jdbcTemplate, email);

        MvcTestResult login = login(email, "password123");
        assertThat(login).hasStatusOk();
        assertThat(login).bodyJson().extractingPath("$.tokenType").isEqualTo("Bearer");

        String token = JsonPath.read(login.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.accessToken");

        assertThat(mvc.get().uri("/api/users/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .hasStatusOk()
                .bodyJson().extractingPath("$.name").isEqualTo("김부모");
    }

    @Test
    void 이름을_바꾸면_다른_서비스에_알릴_이벤트를_남기고_기존_Access_Token_을_무효화한다() throws Exception {
        Long userId = registerAndGetId(uniqueEmail(), "옛이름");
        long version = tokenService.currentVersion(userId);

        // 이름이 그대로면 알리지 않는다
        assertThat(updateProfile(userId, "옛이름")).hasStatusOk();
        assertThat(profileEvents(userId)).isEmpty();
        assertThat(tokenService.currentVersion(userId)).isEqualTo(version);

        assertThat(updateProfile(userId, "새이름")).hasStatusOk().bodyJson().extractingPath("$.name").isEqualTo("새이름");
        assertThat(profileEvents(userId)).singleElement().satisfies(payload -> {
            EventContracts.assertPayloadConforms(UserEvents.PROFILE_UPDATED, payload);
            assertThat(JsonPath.<String>read(payload, "$.name")).isEqualTo("새이름");
        });
        // 옛 이름이 담긴 토큰으로 새 글을 쓰지 못하게 — 재발급하면 새 이름이 담긴다
        assertThat(tokenService.currentVersion(userId)).isGreaterThan(version);
    }

    @Test
    void 토큰_없이_보호된_API를_호출하면_401() {
        assertThat(mvc.get().uri("/api/users/profile"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.detail").isEqualTo("인증이 필요합니다.");
    }

    @Test
    void 위조된_토큰은_401() {
        assertThat(mvc.get().uri("/api/users/profile").header(HttpHeaders.AUTHORIZATION, "Bearer 1_1700000000000"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 로그인_실패_메시지는_계정_존재_여부와_무관하게_같다() {
        String email = uniqueEmail();
        register(email, "password123", "김부모");

        MvcTestResult wrongPassword = login(email, "wrong-password");
        MvcTestResult unknownEmail = login(uniqueEmail(), "password123");

        assertThat(wrongPassword).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getResponse().getContentAsByteArray())
                .isEqualTo(unknownEmail.getResponse().getContentAsByteArray());
    }

    @Test
    void 이미_가입된_이메일은_409() {
        String email = uniqueEmail();
        register(email, "password123", "김부모");

        assertThat(register(email, "password123", "다른사람")).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void 입력값_검증_실패는_400과_필드별_메시지를_반환한다() {
        assertThat(register("not-an-email", "short", ""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors")
                .asMap()
                .containsKeys("email", "password", "name");
    }

    @Test
    void 다른_회원을_조회하면_연락처를_뺀_공개_프로필만_보인다() throws Exception {
        Long userId = registerAndGetId(uniqueEmail(), "김부모");

        assertThat(mvc.get().uri("/api/users/{id}", userId).header(HttpHeaders.AUTHORIZATION, bearer(9_999L, "PARENT")))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$")
                .asMap()
                .containsEntry("name", "김부모")
                .doesNotContainKeys("email", "phone", "address", "status");
    }

    @Test
    void 정지된_회원은_공개_프로필도_조회되지_않는다() throws Exception {
        Long userId = registerAndGetId(uniqueEmail(), "김부모");
        User user = userRepository.findById(userId).orElseThrow();
        user.setStatus(User.UserStatus.SUSPENDED);
        userRepository.save(user);

        assertThat(mvc.get().uri("/api/users/{id}", userId).header(HttpHeaders.AUTHORIZATION, bearer(9_999L, "PARENT")))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void 전체_회원_목록은_관리자만_조회할_수_있다() {
        register(uniqueEmail(), "password123", "김부모");

        assertThat(mvc.get().uri("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(9_999L, "PARENT")))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.detail").isEqualTo("접근 권한이 없습니다.");

        assertThat(mvc.get().uri("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(1L, "ADMIN")))
                .hasStatusOk()
                .bodyJson().extractingPath("$.content[0].email").isNotNull();
    }

    @Test
    void 비로그인_사용자는_회원을_조회할_수_없다() {
        assertThat(mvc.get().uri("/api/users")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/users/{id}", 1)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void OpenAPI_문서에_JWT_인증과_공개_API가_구분돼_나온다() {
        MvcTestResult docs = mvc.get().uri("/v3/api-docs").exchange();

        assertThat(docs).hasStatusOk();
        assertThat(docs).bodyJson().extractingPath("$.components.securitySchemes.bearerAuth.scheme").isEqualTo("bearer");
        assertThat(docs).bodyJson().extractingPath("$.servers[0].url").isEqualTo("/");
        assertThat(docs).bodyJson().extractingPath("$.paths['/api/users/login'].post.security").asArray().isEmpty();
        // 로그인 사용자(@AuthenticationPrincipal)와 Authorization 헤더는 요청 파라미터로 노출하지 않는다
        assertThat(docs).bodyJson().doesNotHavePath("$.paths['/api/users/profile'].get.parameters");
        assertThat(docs).bodyJson().doesNotHavePath("$.paths['/api/users/logout'].post.parameters");
    }

    private MvcTestResult updateProfile(Long userId, String name) {
        return mvc.put().uri("/api/users/profile").header(HttpHeaders.AUTHORIZATION, bearer(userId, "PARENT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"%s\"}".formatted(name))
                .exchange();
    }

    private List<String> profileEvents(Long userId) {
        return jdbcTemplate.queryForList("""
                SELECT payload::text FROM outbox_events WHERE event_type = ? AND aggregate_id = ? ORDER BY created_at
                """, String.class, UserEvents.PROFILE_UPDATED, String.valueOf(userId));
    }

    private Long registerAndGetId(String email, String name) throws Exception {
        MvcTestResult result = register(email, "password123", name);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id")).longValue();
    }

    private String bearer(Long userId, String role) {
        return "Bearer " + jwtUtil.generateAccessToken(
                new AuthUser(userId, "user" + userId + "@tspoon.com", "테스트", role));
    }

    private MvcTestResult register(String email, String password, String name) {
        return mvc.post().uri("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s", "name": "%s"}
                        """.formatted(email, password, name))
                .exchange();
    }

    private MvcTestResult login(String email, String password) {
        return mvc.post().uri("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password))
                .exchange();
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@tspoon.com";
    }
}
