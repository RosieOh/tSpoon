package com.tspoon.notificationservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.core.test.KafkaTestRecords;
import com.tspoon.core.test.KafkaTestcontainersConfiguration;
import com.tspoon.core.test.TestcontainersConfiguration;
import com.tspoon.notificationservice.entity.Notification;
import com.tspoon.notificationservice.event.DomainEventListener;
import com.tspoon.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "eureka.client.enabled=false")
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class DomainEventConsumerIntegrationTest {

    private static final AtomicLong IDS = new AtomicLong(7_000);

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    KafkaContainer kafka;

    @Test
    void 남의_게시글에_댓글이_달리면_게시글_작성자에게_알림이_간다() {
        long boardAuthor = IDS.incrementAndGet();
        send(DomainEventListener.COMMUNITY_TOPIC, "11", commentCreated(UUID.randomUUID(), 11, boardAuthor, null, IDS.incrementAndGet()));

        List<Notification> notifications = awaitNotifications(boardAuthor, 1);
        assertThat(notifications.getFirst().getTitle()).isEqualTo("내 게시글에 댓글이 달렸습니다");
        assertThat(notifications.getFirst().getContent()).isEqualTo("[이벤트 글] 댓글러: 좋은 글이네요");
        assertThat(notifications.getFirst().getActionUrl()).isEqualTo("/boards/11");
        assertThat(notifications.getFirst().getType()).isEqualTo(Notification.NotificationType.COMMUNITY);
    }

    @Test
    void 같은_이벤트가_두_번_와도_알림은_한_번만_만든다() {
        long boardAuthor = IDS.incrementAndGet();
        String event = commentCreated(UUID.randomUUID(), 12, boardAuthor, null, IDS.incrementAndGet());

        send(DomainEventListener.COMMUNITY_TOPIC, "12", event);
        send(DomainEventListener.COMMUNITY_TOPIC, "12", event);
        // 같은 키(파티션)로 표시용 이벤트를 뒤에 보내 앞의 두 메시지가 모두 처리됐음을 확인한다
        send(DomainEventListener.COMMUNITY_TOPIC, "12", commentCreated(UUID.randomUUID(), 12, boardAuthor, null, IDS.incrementAndGet()));

        awaitNotifications(boardAuthor, 2);
        assertThat(notificationsOf(boardAuthor)).hasSize(2);
    }

    @Test
    void 자기_글에_단_댓글은_알리지_않고_답글은_부모_댓글_작성자에게_알린다() {
        long boardAuthor = IDS.incrementAndGet();
        long commenter = IDS.incrementAndGet();

        send(DomainEventListener.COMMUNITY_TOPIC, "13", commentCreated(UUID.randomUUID(), 13, boardAuthor, null, boardAuthor));
        send(DomainEventListener.COMMUNITY_TOPIC, "13", commentCreated(UUID.randomUUID(), 13, boardAuthor, commenter, boardAuthor));

        List<Notification> toCommenter = awaitNotifications(commenter, 1);
        assertThat(toCommenter.getFirst().getTitle()).isEqualTo("내 댓글에 답글이 달렸습니다");
        assertThat(notificationsOf(boardAuthor)).isEmpty(); // 두 이벤트 모두 게시글 작성자 본인이 쓴 것
    }

    @Test
    void 답변이_달리면_질문자에게_채택되면_답변자에게_알린다() {
        long asker = IDS.incrementAndGet();
        long expert = IDS.incrementAndGet();

        send(DomainEventListener.QNA_TOPIC, "21", envelope(UUID.randomUUID(), "ANSWER_CREATED", """
                {"answerId": 1, "questionId": 21, "questionTitle": "수학 공부", "questionAuthorId": %d,
                 "authorId": %d, "authorName": "이전문가", "answerType": "EXPERT", "contentPreview": "개념부터"}
                """.formatted(asker, expert)));
        send(DomainEventListener.QNA_TOPIC, "21", envelope(UUID.randomUUID(), "ANSWER_ACCEPTED", """
                {"answerId": 1, "questionId": 21, "questionTitle": "수학 공부", "questionAuthorId": %d, "answerAuthorId": %d}
                """.formatted(asker, expert)));

        assertThat(awaitNotifications(asker, 1).getFirst().getContent()).isEqualTo("[수학 공부] 이전문가(전문가): 개념부터");
        assertThat(awaitNotifications(expert, 1).getFirst().getTitle()).isEqualTo("내 답변이 채택되었습니다");
    }

    @Test
    void 깨진_메시지는_DLT_로_보내고_뒤따르는_메시지는_계속_처리한다() {
        long boardAuthor = IDS.incrementAndGet();
        String marker = "broken-" + UUID.randomUUID();

        send(DomainEventListener.COMMUNITY_TOPIC, "14", "{not json " + marker);
        send(DomainEventListener.COMMUNITY_TOPIC, "14", commentCreated(UUID.randomUUID(), 14, boardAuthor, null, IDS.incrementAndGet()));

        awaitNotifications(boardAuthor, 1);
        assertThat(KafkaTestRecords.consumeUntil(kafka.getBootstrapServers(), DomainEventListener.COMMUNITY_TOPIC + ".DLT",
                value -> value.contains(marker)).value()).startsWith("{not json");
    }

    private List<Notification> awaitNotifications(long userId, int expected) {
        await().atMost(Duration.ofSeconds(20)).until(() -> notificationsOf(userId).size() >= expected);
        return notificationsOf(userId);
    }

    private List<Notification> notificationsOf(long userId) {
        return notificationRepository.findByUserIdAndChannelOrderByCreatedAtDesc(
                userId, Notification.NotificationChannel.IN_APP, Pageable.unpaged()).getContent();
    }

    private void send(String topic, String key, String value) {
        kafkaTemplate.send(topic, key, value).join();
    }

    private static String commentCreated(UUID eventId, long boardId, long boardAuthorId, Long parentAuthorId, long authorId) {
        return envelope(eventId, "COMMENT_CREATED", """
                {"commentId": 1, "boardId": %d, "boardTitle": "이벤트 글", "boardAuthorId": %d,
                 "parentCommentId": %s, "parentCommentAuthorId": %s, "authorId": %d, "authorName": "댓글러",
                 "contentPreview": "좋은 글이네요"}
                """.formatted(boardId, boardAuthorId, parentAuthorId == null ? "null" : "1",
                parentAuthorId == null ? "null" : parentAuthorId, authorId));
    }

    private static String envelope(UUID eventId, String eventType, String payload) {
        String json = """
                {"eventId": "%s", "eventType": "%s", "occurredAt": "2026-10-25T00:00:00Z",
                 "aggregateType": "TEST", "aggregateId": "1", "payload": %s}
                """.formatted(eventId, eventType, payload);
        JsonPath.parse(json); // 테스트 데이터가 올바른 JSON 인지 먼저 확인
        return json;
    }
}
