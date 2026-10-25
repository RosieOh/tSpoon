package com.tspoon.qnaservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.auth.AuthUser;
import com.tspoon.auth.jwt.JwtUtil;
import com.tspoon.core.test.KafkaTestRecords;
import com.tspoon.core.test.KafkaTestcontainersConfiguration;
import com.tspoon.core.test.TestcontainersConfiguration;
import com.tspoon.event.TspoonTopics;
import com.tspoon.event.UserEventTypes;
import com.tspoon.qnaservice.event.QnaEvents;
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
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {"eureka.client.enabled=false", "tspoon.outbox.relay-interval=200ms"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class QnaEventIntegrationTest {

    private static final AuthUser ASKER = new AuthUser(600L, "asker@tspoon.com", "질문자", "PARENT");
    private static final AuthUser EXPERT = new AuthUser(601L, "expert@tspoon.com", "전문가", "EXPERT");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 회원_이름이_바뀌면_질문과_답변에_복사해_둔_이름도_바뀐다() throws Exception {
        AuthUser renamed = new AuthUser(610L, "renamed@tspoon.com", "옛이름", "EXPERT");
        long questionId = id(post(renamed, "/api/questions",
                "{\"title\": \"이름 바꿀 질문\", \"content\": \"내용\", \"category\": \"LEARNING\"}"));
        long answerId = id(post(renamed, "/api/questions/" + questionId + "/answers", "{\"content\": \"내 답변\"}"));

        KafkaTestRecords.send(kafka.getBootstrapServers(), TspoonTopics.USER_EVENTS, "610", KafkaTestRecords.envelope(
                UserEventTypes.PROFILE_UPDATED, "USER", 610, "{\"userId\": 610, \"name\": \"새이름\"}"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(jdbcTemplate.queryForObject("SELECT author_name FROM questions WHERE id = ?", String.class, questionId))
                    .isEqualTo("새이름");
            assertThat(jdbcTemplate.queryForObject("SELECT author_name FROM answers WHERE id = ?", String.class, answerId))
                    .isEqualTo("새이름");
        });
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    KafkaContainer kafka;

    @Test
    void 답변과_채택이_질문_ID_를_키로_Kafka_에_발행된다() throws Exception {
        long questionId = id(post(ASKER, "/api/questions",
                "{\"title\": \"이벤트 질문\", \"content\": \"내용\", \"category\": \"LEARNING\"}"));
        long answerId = id(post(EXPERT, "/api/questions/" + questionId + "/answers", "{\"content\": \"전문가 답변\"}"));
        assertThat(mvc.post().uri("/api/questions/{q}/answers/{a}/accept", questionId, answerId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ASKER))).hasStatusOk();

        ConsumerRecord<String, String> created = consume(QnaEvents.ANSWER_CREATED, answerId);
        assertThat(created.key()).isEqualTo(String.valueOf(questionId));
        assertThat(JsonPath.<Number>read(created.value(), "$.payload.questionAuthorId").longValue()).isEqualTo(600L);
        assertThat(JsonPath.<String>read(created.value(), "$.payload.answerType")).isEqualTo("EXPERT");

        ConsumerRecord<String, String> accepted = consume(QnaEvents.ANSWER_ACCEPTED, answerId);
        assertThat(accepted.key()).isEqualTo(String.valueOf(questionId));
        assertThat(JsonPath.<Number>read(accepted.value(), "$.payload.answerAuthorId").longValue()).isEqualTo(601L);
        assertThat(JsonPath.<String>read(accepted.value(), "$.payload.questionTitle")).isEqualTo("이벤트 질문");
        // 같은 키(질문)라 같은 파티션에서 답변 → 채택 순서가 지켜진다
        assertThat(accepted.partition()).isEqualTo(created.partition());
        assertThat(accepted.offset()).isGreaterThan(created.offset());
    }

    private ConsumerRecord<String, String> consume(String eventType, long answerId) {
        return KafkaTestRecords.consumeUntil(kafka.getBootstrapServers(), QnaEvents.TOPIC, value ->
                JsonPath.<String>read(value, "$.eventType").equals(eventType)
                        && JsonPath.<Number>read(value, "$.payload.answerId").longValue() == answerId);
    }

    private MvcTestResult post(AuthUser user, String uri, String json) {
        MvcTestResult result = mvc.post().uri(uri).header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return result;
    }

    private static long id(MvcTestResult result) throws Exception {
        return JsonPath.<Number>read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id").longValue();
    }

    private String bearer(AuthUser user) {
        return "Bearer " + jwtUtil.generateAccessToken(user);
    }
}
