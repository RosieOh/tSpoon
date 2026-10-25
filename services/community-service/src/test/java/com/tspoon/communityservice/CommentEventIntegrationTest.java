package com.tspoon.communityservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.auth.AuthUser;
import com.tspoon.auth.jwt.JwtUtil;
import com.tspoon.communityservice.event.CommunityEvents;
import com.tspoon.core.test.KafkaTestRecords;
import com.tspoon.core.test.KafkaTestcontainersConfiguration;
import com.tspoon.core.test.TestcontainersConfiguration;
import com.tspoon.event.TspoonTopics;
import com.tspoon.event.UserEventTypes;
import com.tspoon.outbox.OutboxEventPublisher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {"eureka.client.enabled=false", "tspoon.outbox.relay-interval=200ms"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class CommentEventIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    KafkaContainer kafka;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    OutboxEventPublisher outboxEventPublisher;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void 댓글을_쓰면_outbox_를_거쳐_Kafka_에_사실이_담긴_이벤트가_발행된다() throws Exception {
        long boardId = id(post(bearer(500L, "글쓴이"), "/api/boards",
                "{\"title\": \"이벤트 글\", \"content\": \"본문\", \"category\": \"GENERAL\"}"));
        long commentId = id(post(bearer(501L, "댓글러"), "/api/boards/" + boardId + "/comments", "{\"content\": \"댓글입니다\"}"));

        ConsumerRecord<String, String> record = KafkaTestRecords.consumeUntil(kafka.getBootstrapServers(), CommunityEvents.TOPIC,
                value -> JsonPath.<Number>read(value, "$.payload.commentId").longValue() == commentId);

        assertThat(record.key()).isEqualTo(String.valueOf(boardId));
        assertThat(new String(record.headers().lastHeader("eventType").value(), StandardCharsets.UTF_8))
                .isEqualTo(CommunityEvents.COMMENT_CREATED);
        String value = record.value();
        assertThat(JsonPath.<String>read(value, "$.eventId")).isNotBlank();
        assertThat(JsonPath.<String>read(value, "$.eventType")).isEqualTo(CommunityEvents.COMMENT_CREATED);
        assertThat(JsonPath.<Number>read(value, "$.payload.boardAuthorId").longValue()).isEqualTo(500L);
        assertThat(JsonPath.<Number>read(value, "$.payload.authorId").longValue()).isEqualTo(501L);
        assertThat(JsonPath.<String>read(value, "$.payload.boardTitle")).isEqualTo("이벤트 글");
        assertThat(JsonPath.<String>read(value, "$.payload.contentPreview")).isEqualTo("댓글입니다");

        // 보낸 이벤트는 발행 시각이 기록된다
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE id = ?::uuid AND published_at IS NOT NULL", Integer.class,
                JsonPath.<String>read(value, "$.eventId"))).isEqualTo(1));
    }

    @Test
    void 회원_이름이_바뀌면_게시글_댓글_좋아요에_복사해_둔_이름도_바뀐다() throws Exception {
        String token = bearer(510L, "옛이름");
        long boardId = id(post(token, "/api/boards", "{\"title\": \"이름 바꿀 글\", \"content\": \"본문\", \"category\": \"GENERAL\"}"));
        post(token, "/api/boards/" + boardId + "/comments", "{\"content\": \"내 댓글\"}");
        assertThat(mvc.post().uri("/api/boards/{id}/like", boardId).header(HttpHeaders.AUTHORIZATION, token)).hasStatusOk();

        KafkaTestRecords.send(kafka.getBootstrapServers(), TspoonTopics.USER_EVENTS, "510", KafkaTestRecords.envelope(
                UserEventTypes.PROFILE_UPDATED, "USER", 510, "{\"userId\": 510, \"name\": \"새이름\"}"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(jdbcTemplate.queryForObject("SELECT author_name FROM boards WHERE id = ?", String.class, boardId))
                    .isEqualTo("새이름");
            assertThat(jdbcTemplate.queryForObject("SELECT author_name FROM comments WHERE board_id = ?", String.class, boardId))
                    .isEqualTo("새이름");
            assertThat(jdbcTemplate.queryForObject("SELECT user_name FROM board_likes WHERE board_id = ?", String.class, boardId))
                    .isEqualTo("새이름");
        });
    }

    @Test
    void 탈퇴한_회원의_글은_익명으로_남고_좋아요는_지워져_같은_이벤트가_다시_와도_한_번만_줄어든다() throws Exception {
        String leaving = bearer(530L, "떠날회원");
        long ownBoard = id(post(leaving, "/api/boards", "{\"title\": \"남길 글\", \"content\": \"본문\", \"category\": \"GENERAL\"}"));
        post(leaving, "/api/boards/" + ownBoard + "/comments", "{\"content\": \"남길 댓글\"}");
        long otherBoard = id(post(bearer(531L, "남는회원"), "/api/boards",
                "{\"title\": \"좋아요 받은 글\", \"content\": \"본문\", \"category\": \"GENERAL\"}"));
        assertThat(mvc.post().uri("/api/boards/{id}/like", otherBoard).header(HttpHeaders.AUTHORIZATION, leaving)).hasStatusOk();
        assertThat(mvc.post().uri("/api/boards/{id}/like", otherBoard).header(HttpHeaders.AUTHORIZATION, bearer(532L, "다른회원")))
                .hasStatusOk();

        String withdrawn = KafkaTestRecords.envelope(UserEventTypes.WITHDRAWN, "USER", 530, "{\"userId\": 530, \"withdrawnAt\": \"2026-10-26T00:00:00Z\"}");
        KafkaTestRecords.send(kafka.getBootstrapServers(), TspoonTopics.USER_EVENTS, "530", withdrawn);
        KafkaTestRecords.send(kafka.getBootstrapServers(), TspoonTopics.USER_EVENTS, "530", withdrawn);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(jdbcTemplate.queryForObject("SELECT author_name FROM boards WHERE id = ?", String.class, ownBoard))
                    .isEqualTo("탈퇴한 회원");
            assertThat(jdbcTemplate.queryForObject("SELECT author_name FROM comments WHERE board_id = ?", String.class, ownBoard))
                    .isEqualTo("탈퇴한 회원");
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM board_likes WHERE user_id = 530", Long.class)).isZero();
        });
        Thread.sleep(1_000); // 두 번째 탈퇴 이벤트까지 처리될 시간
        assertThat(jdbcTemplate.queryForObject("SELECT like_count FROM boards WHERE id = ?", Integer.class, otherBoard)).isEqualTo(1);
    }

    @Test
    void 트랜잭션이_롤백되면_이벤트도_함께_사라진다() {
        String marker = "rollback-" + UUID.randomUUID();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            outboxEventPublisher.publish(CommunityEvents.TOPIC, "TEST", marker, "TEST_EVENT", Map.of("marker", marker));
            throw new IllegalStateException("도메인 처리 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ?", Integer.class, marker))
                .isZero();
    }

    @Test
    void 트랜잭션_밖에서는_이벤트를_기록할_수_없다() {
        assertThatThrownBy(() -> outboxEventPublisher.publish(CommunityEvents.TOPIC, "TEST", 1, "TEST_EVENT", Map.of()))
                .hasMessageContaining("No existing transaction");
    }

    private MvcTestResult post(String token, String uri, String json) {
        MvcTestResult result = mvc.post().uri(uri).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return result;
    }

    private static long id(MvcTestResult result) throws Exception {
        return JsonPath.<Number>read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id").longValue();
    }

    private String bearer(Long userId, String name) {
        return "Bearer " + jwtUtil.generateAccessToken(new AuthUser(userId, "user" + userId + "@tspoon.com", name, "PARENT"));
    }
}
