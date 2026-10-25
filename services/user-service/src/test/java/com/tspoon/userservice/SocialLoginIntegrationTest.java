package com.tspoon.userservice;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tspoon.core.test.RedisTestcontainersConfiguration;
import com.tspoon.core.test.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"eureka.client.enabled=false", "tspoon.outbox.relay-enabled=false"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class SocialLoginIntegrationTest {

    /**
     * 카카오·네이버 응답 형식을 흉내 내는 가짜 제공자. 인가 코드는 "{제공자 회원 번호}|{이메일}|{이메일 인증 여부}" 이고,
     * "bad" 로 시작하면 토큰 교환을 거절한다.
     */
    private static final HttpServer PROVIDER;
    private static final Map<String, Map<String, String>> LAST_TOKEN_REQUEST = new ConcurrentHashMap<>();

    static {
        try {
            PROVIDER = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        for (String provider : new String[]{"kakao", "naver"}) {
            PROVIDER.createContext("/" + provider + "/token", exchange -> {
                Map<String, String> form = form(exchange);
                LAST_TOKEN_REQUEST.put(provider, form);
                if (form.get("code").startsWith("bad")) {
                    respond(exchange, 400, "{\"error\": \"invalid_grant\"}");
                } else {
                    respond(exchange, 200, "{\"access_token\": \"token|%s\", \"token_type\": \"bearer\"}".formatted(form.get("code")));
                }
            });
            PROVIDER.createContext("/" + provider + "/me", exchange -> {
                String[] code = exchange.getRequestHeaders().getFirst("Authorization").substring("Bearer token|".length()).split("\\|");
                respond(exchange, 200, provider.equals("kakao") ? kakaoUser(code) : naverUser(code));
            });
        }
        PROVIDER.start();
    }

    @DynamicPropertySource
    static void providers(DynamicPropertyRegistry registry) {
        String base = "http://localhost:" + PROVIDER.getAddress().getPort();
        for (String provider : new String[]{"kakao", "naver"}) {
            String prefix = "tspoon.user.oauth.providers." + provider + ".";
            registry.add(prefix + "client-id", () -> provider + "-client");
            registry.add(prefix + "client-secret", () -> provider + "-secret");
            registry.add(prefix + "token-uri", () -> base + "/" + provider + "/token");
            registry.add(prefix + "user-info-uri", () -> base + "/" + provider + "/me");
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 처음이면_가입하고_다시_로그인하면_같은_회원이며_우리_토큰으로_API_를_쓴다() throws Exception {
        String email = uniqueEmail();
        String providerUserId = UUID.randomUUID().toString();

        MvcTestResult first = login("kakao", providerUserId + "|" + email + "|true");
        assertThat(first).hasStatus(HttpStatus.CREATED);
        MvcTestResult second = login("kakao", providerUserId + "|" + email + "|true");
        assertThat(second).hasStatus(HttpStatus.OK);
        assertThat(userId(second)).isEqualTo(userId(first));

        // 서버는 설정한 client_secret·redirect_uri 로 코드를 바꾼다 (클라이언트가 보낸 주소를 쓰지 않음)
        assertThat(LAST_TOKEN_REQUEST.get("kakao")).containsEntry("client_secret", "kakao-secret")
                .containsEntry("redirect_uri", "http://localhost:3000/oauth/kakao").containsEntry("grant_type", "authorization_code");
        String accessToken = JsonPath.read(body(second), "$.accessToken");
        assertThat(mvc.get().uri("/api/users/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .hasStatusOk().bodyJson().extractingPath("$.emailVerified").isEqualTo(true);
    }

    @Test
    void 인증된_같은_이메일의_기존_회원에게는_연결하고_미인증_회원에게는_연결하지_않는다() throws Exception {
        String verified = uniqueEmail();
        long existingId = register(verified);
        TestAccounts.verifyEmail(jdbcTemplate, verified);
        MvcTestResult linked = login("naver", UUID.randomUUID() + "|" + verified + "|true");
        assertThat(linked).hasStatus(HttpStatus.OK);
        assertThat(userId(linked)).isEqualTo(existingId);

        // 남의 이메일로 먼저 가입만 해 둔(미인증) 계정을 소셜 로그인으로 가로채지 못한다
        String unverified = uniqueEmail();
        register(unverified);
        assertThat(login("naver", UUID.randomUUID() + "|" + unverified + "|true")).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("SOCIAL_LINK_NEEDS_VERIFIED_EMAIL");
    }

    @Test
    void 제공자가_이메일을_인증하지_않았으면_가입하지_않는다() {
        assertThat(login("kakao", UUID.randomUUID() + "|" + uniqueEmail() + "|false")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("SOCIAL_EMAIL_REQUIRED");
    }

    @Test
    void 코드_교환에_실패하면_401_설정하지_않은_제공자는_404() {
        assertThat(login("kakao", "bad-code")).hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.code").isEqualTo("SOCIAL_LOGIN_FAILED");
        assertThat(login("google", "any")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/users/oauth/providers")).hasStatusOk()
                .bodyJson().extractingPath("$").asArray().containsExactly("kakao", "naver");
    }

    @Test
    void 네이버는_state_를_함께_넘긴다() {
        assertThat(mvc.post().uri("/api/users/oauth/naver").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"%s|%s|true\", \"state\": \"csrf-state-123\"}".formatted(UUID.randomUUID(), uniqueEmail())))
                .hasStatus(HttpStatus.CREATED);
        assertThat(LAST_TOKEN_REQUEST.get("naver")).containsEntry("state", "csrf-state-123");
    }

    private MvcTestResult login(String provider, String code) {
        return mvc.post().uri("/api/users/oauth/{provider}", provider).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"%s\"}".formatted(code)).exchange();
    }

    private long register(String email) throws Exception {
        MvcTestResult result = mvc.post().uri("/api/users/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"password123\", \"name\": \"기존회원\"}".formatted(email)).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return ((Number) JsonPath.read(body(result), "$.id")).longValue();
    }

    private static long userId(MvcTestResult result) throws Exception {
        return ((Number) JsonPath.read(body(result), "$.userId")).longValue();
    }

    private static String body(MvcTestResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String uniqueEmail() {
        return "social-" + UUID.randomUUID() + "@tspoon.com";
    }

    private static String kakaoUser(String[] code) {
        return """
                {"id": "%s", "kakao_account": {"email": "%s", "is_email_valid": true, "is_email_verified": %s,
                 "profile": {"nickname": "카카오친구"}}}
                """.formatted(code[0], code[1], code[2]);
    }

    private static String naverUser(String[] code) {
        // 네이버는 인증된 이메일만 준다 — 인증 안 됐으면 이메일을 빼고 보낸다
        String email = Boolean.parseBoolean(code[2]) ? ", \"email\": \"" + code[1] + "\"" : "";
        return """
                {"resultcode": "00", "message": "success", "response": {"id": "%s", "name": "네이버친구"%s}}
                """.formatted(code[0], email);
    }

    private static Map<String, String> form(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return Arrays.stream(body.split("&")).map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : ""));
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
