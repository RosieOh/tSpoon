package com.tspoon.notificationservice;

import com.tspoon.auth.AuthUser;
import com.tspoon.auth.jwt.JwtUtil;
import com.tspoon.core.test.RedisTestcontainersConfiguration;
import com.tspoon.core.test.TestcontainersConfiguration;
import com.tspoon.notificationservice.dto.NotificationResponse;
import com.tspoon.notificationservice.entity.Notification;
import com.tspoon.notificationservice.service.NotificationService;
import com.tspoon.notificationservice.stream.NotificationPush;
import com.tspoon.notificationservice.stream.NotificationStreams;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false", "spring.kafka.listener.auto-startup=false", "tspoon.notification.stream.heartbeat=1s"})
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class NotificationStreamIntegrationTest {

    private static final AtomicLong IDS = new AtomicLong(90_000);

    @LocalServerPort
    int port;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    NotificationService notificationService;

    @Autowired
    NotificationStreams streams;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    JsonMapper jsonMapper;

    @Test
    void 연결하면_안_읽은_수를_받고_새_알림이_커밋되면_바로_받는다() throws Exception {
        long userId = IDS.incrementAndGet();
        createInApp(userId, "먼저 온 알림");

        try (SseClient client = SseClient.connect(port, bearer(userId))) {
            assertThat(client.status()).isEqualTo(200);
            client.await("event:unread-count"::equals);
            client.await("data:{\"count\":1}"::equals);

            createInApp(userId, "새 댓글이 달렸습니다");

            client.await("event:notification"::equals);
            assertThat(client.await(line -> line.startsWith("data:"))).contains("\"title\":\"새 댓글이 달렸습니다\"");
            client.await("data:{\"count\":2}"::equals);
        }
    }

    @Test
    void 다른_인스턴스에서_만든_알림도_Redis_로_받고_다른_회원의_알림은_받지_않는다() throws Exception {
        long userId = IDS.incrementAndGet();
        long otherUserId = IDS.incrementAndGet();

        try (SseClient client = SseClient.connect(port, bearer(userId))) {
            client.await("data:{\"count\":0}"::equals);

            publishFromAnotherInstance(otherUserId, "남의 알림");
            publishFromAnotherInstance(userId, "다른 인스턴스의 알림");

            String data = client.await(line -> line.startsWith("data:{\"id\""));
            assertThat(data).contains("다른 인스턴스의 알림").doesNotContain("남의 알림");
        }
    }

    @Test
    void 하트비트로_연결을_유지하고_떠난_연결은_정리한다() throws Exception {
        long userId = IDS.incrementAndGet();

        try (SseClient client = SseClient.connect(port, bearer(userId))) {
            client.await(":keep-alive"::equals);
            assertThat(streams.connectionCount(userId)).isEqualTo(1);
        }

        // 다음 하트비트에서 끊긴 연결을 알아채고 정리한다
        await().atMost(Duration.ofSeconds(10)).until(() -> streams.connectionCount(userId) == 0);
    }

    @Test
    void 종료할_때는_열린_스트림을_먼저_끝내고_새_연결은_바로_닫는다() throws Exception {
        long userId = IDS.incrementAndGet();

        try (SseClient client = SseClient.connect(port, bearer(userId))) {
            client.await("data:{\"count\":0}"::equals);

            streams.stop();
            try {
                // 웹 서버의 정상 종료가 이 응답을 기다리지 않도록 서버가 먼저 끝낸다
                assertThat(client.awaitEnd()).isTrue();
                try (SseClient late = SseClient.connect(port, bearer(userId))) {
                    assertThat(late.awaitEnd()).isTrue();
                }
                await().atMost(Duration.ofSeconds(10)).until(() -> streams.connectionCount(userId) == 0);
            } finally {
                streams.start();
            }
        }
    }

    @Test
    void 토큰_없이는_연결할_수_없다() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<Void> response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/notifications/stream"))
                    .header("Accept", "text/event-stream").build(), HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).isEqualTo(401);
        }
    }

    private void createInApp(long userId, String title) {
        notificationService.createInApp(userId, title, "내용", Notification.NotificationType.COMMUNITY, "/boards/1", "BOARD", 1L);
    }

    private void publishFromAnotherInstance(long userId, String title) {
        NotificationResponse notification = new NotificationResponse(IDS.incrementAndGet(), title, "내용",
                Notification.NotificationType.SYSTEM, Notification.NotificationChannel.IN_APP, Notification.NotificationStatus.UNREAD,
                false, null, null, null, Instant.now(), Instant.now(), null);
        redis.convertAndSend("tspoon:notifications:created", jsonMapper.writeValueAsString(new NotificationPush(userId, notification, 1)));
    }

    private String bearer(long userId) {
        return "Bearer " + jwtUtil.generateAccessToken(new AuthUser(userId, "user" + userId + "@tspoon.com", "회원", "PARENT"));
    }

    /** SSE 응답을 줄 단위로 읽는 테스트용 클라이언트. 닫으면 연결을 끊는다 */
    private static final class SseClient implements AutoCloseable {

        private final HttpClient client;
        private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        private final CountDownLatch ended = new CountDownLatch(1);
        private final int status;

        private SseClient(HttpClient client, HttpResponse<java.util.stream.Stream<String>> response) {
            this.client = client;
            this.status = response.statusCode();
            Thread.ofVirtual().start(() -> {
                try {
                    response.body().forEach(lines::add);
                } finally {
                    ended.countDown();
                }
            });
        }

        static SseClient connect(int port, String authorization) throws Exception {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/notifications/stream"))
                    .header("Authorization", authorization)
                    .header("Accept", "text/event-stream")
                    .build();
            return new SseClient(client, client.send(request, HttpResponse.BodyHandlers.ofLines()));
        }

        int status() {
            return status;
        }

        String await(Predicate<String> matches) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                String line = lines.poll(200, TimeUnit.MILLISECONDS);
                if (line != null && matches.test(line)) {
                    return line;
                }
            }
            throw new AssertionError("10초 안에 기다리던 SSE 줄을 받지 못했습니다.");
        }

        /** 서버가 10초 안에 응답을 끝냈는지 */
        boolean awaitEnd() throws InterruptedException {
            return ended.await(10, TimeUnit.SECONDS);
        }

        @Override
        public void close() {
            client.shutdownNow();
        }
    }
}
