package com.tspoon.notificationservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.auth.AuthUser;
import com.tspoon.auth.jwt.JwtUtil;
import com.tspoon.core.test.TestcontainersConfiguration;
import com.tspoon.notificationservice.entity.Notification;
import com.tspoon.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.kafka.listener.auto-startup=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NotificationApiIntegrationTest {

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        Mailpit.register(registry);
    }

    private static final AuthUser ADMIN = new AuthUser(1L, "admin@tspoon.com", "관리자", "ADMIN");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    NotificationRepository notificationRepository;

    @Test
    void 관리자가_보낸_앱_내_알림이_내_알림함에_쌓이고_읽음_처리된다() throws Exception {
        AuthUser user = new AuthUser(400L, "user400@tspoon.com", "회원", "PARENT");
        List<Long> ids = send(inApp(400L, "첫 공지"));
        send(inApp(400L, "두 번째 공지"));

        assertThat(get(user, "/api/notifications")).hasStatusOk()
                .bodyJson().extractingPath("$.content[0].title").isEqualTo("두 번째 공지");
        assertThat(get(user, "/api/notifications/unread-count")).bodyJson().extractingPath("$.count").isEqualTo(2);

        assertThat(mvc.patch().uri("/api/notifications/{id}/read", ids.getFirst()).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .hasStatusOk().bodyJson().extractingPath("$.read").isEqualTo(true);
        assertThat(get(user, "/api/notifications/unread-count")).bodyJson().extractingPath("$.count").isEqualTo(1);
        assertThat(get(user, "/api/notifications?unreadOnly=true")).bodyJson().extractingPath("$.content.length()").isEqualTo(1);

        assertThat(mvc.patch().uri("/api/notifications/read-all").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .hasStatusOk().bodyJson().extractingPath("$.updated").isEqualTo(1);
        assertThat(get(user, "/api/notifications/unread-count")).bodyJson().extractingPath("$.count").isEqualTo(0);
    }

    @Test
    void 다른_회원의_알림은_읽을_수_없고_알림_보내기는_관리자만_할_수_있다() throws Exception {
        List<Long> ids = send(inApp(401L, "남의 알림"));
        AuthUser other = new AuthUser(402L, "other@tspoon.com", "다른회원", "PARENT");

        assertThat(mvc.patch().uri("/api/notifications/{id}/read", ids.getFirst()).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.post().uri("/api/admin/notifications").header(HttpHeaders.AUTHORIZATION, bearer(other))
                .contentType(MediaType.APPLICATION_JSON).content(inApp(402L, "몰래 보내기")))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/notifications")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 이메일_알림은_커밋_뒤_발송되고_상태가_SENT_로_바뀐다() throws Exception {
        List<Long> ids = send("""
                {"recipients": [{"userId": 403, "email": "parent403@tspoon.com"}],
                 "title": "행사 안내", "content": "다음 주 세미나에 초대합니다", "channel": "EMAIL", "type": "EVENT"}
                """);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            String messages = Mailpit.api("/api/v1/messages");
            assertThat(JsonPath.<List<String>>read(messages, "$.messages[*].Subject")).contains("[티스푼] 행사 안내");
            assertThat(JsonPath.<List<String>>read(messages, "$.messages[*].To[*].Address")).contains("parent403@tspoon.com");
            assertThat(notificationRepository.findById(ids.getFirst()).orElseThrow().getStatus())
                    .isEqualTo(Notification.NotificationStatus.SENT);
        });
        // 이메일 알림은 앱 내 알림함에 들어가지 않는다
        assertThat(get(new AuthUser(403L, "parent403@tspoon.com", "회원", "PARENT"), "/api/notifications/unread-count"))
                .bodyJson().extractingPath("$.count").isEqualTo(0);
    }

    @Test
    void 이메일_알림에_수신_주소가_없으면_400() {
        assertThat(mvc.post().uri("/api/admin/notifications").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"recipients": [{"userId": 404}], "title": "t", "content": "c", "channel": "EMAIL"}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    private List<Long> send(String json) throws Exception {
        MvcTestResult result = mvc.post().uri("/api/admin/notifications").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        List<Number> ids = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.notificationIds");
        return ids.stream().map(Number::longValue).toList();
    }

    private static String inApp(long userId, String title) {
        return """
                {"recipients": [{"userId": %d}], "title": "%s", "content": "내용", "channel": "IN_APP"}
                """.formatted(userId, title);
    }

    private MvcTestResult get(AuthUser user, String uri) {
        return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, bearer(user)).exchange();
    }

    private String bearer(AuthUser user) {
        return "Bearer " + jwtUtil.generateAccessToken(user);
    }
}
