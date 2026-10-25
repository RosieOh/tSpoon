package com.tspoon.educationservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.auth.AuthUser;
import com.tspoon.auth.jwt.JwtUtil;
import com.tspoon.core.test.KafkaTestRecords;
import com.tspoon.core.test.KafkaTestcontainersConfiguration;
import com.tspoon.core.test.TestcontainersConfiguration;
import com.tspoon.educationservice.recommendation.ChildGradeProjection;
import com.tspoon.event.TspoonTopics;
import com.tspoon.event.UserEventTypes;
import com.tspoon.event.test.EventContracts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {"eureka.client.enabled=false", "tspoon.outbox.relay-enabled=false"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class RecommendationIntegrationTest {

    private static final AtomicLong USERS = new AtomicLong(60_000);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    KafkaContainer kafka;

    @Autowired
    ChildGradeProjection childGrades;

    @Test
    void 자녀_학년의_교육_단계를_먼저_육아를_다음으로_추천하고_북마크한_것은_뺀다() throws Exception {
        String teacher = bearer(USERS.incrementAndGet(), "TEACHER");
        long elementary = createContent(teacher, "ELEMENTARY");
        long parenting = createContent(teacher, "PARENTING");
        long high = createContent(teacher, "HIGH_SCHOOL");
        // 고등 콘텐츠가 더 인기 있어도 자녀 단계가 먼저다
        for (int i = 0; i < 3; i++) {
            bookmark(bearer(USERS.incrementAndGet(), "PARENT"), high);
        }

        long parentId = USERS.incrementAndGet();
        childrenChanged(parentId, List.of("ELEMENTARY_3"), Instant.now());
        await().atMost(Duration.ofSeconds(20)).until(() -> childGrades.gradesOf(parentId).equals(List.of("ELEMENTARY_3")));

        MvcTestResult recommended = recommendations(bearer(parentId, "PARENT"));
        assertThat(recommended).hasStatusOk().bodyJson().extractingPath("$.basedOn").asArray().containsExactly("ELEMENTARY");
        assertThat(order(recommended, elementary, parenting, high)).containsExactly(elementary, parenting, high);

        bookmark(bearer(parentId, "PARENT"), elementary);
        assertThat(order(recommendations(bearer(parentId, "PARENT")), elementary, parenting, high)).containsExactly(parenting, high);
    }

    @Test
    void 비로그인이거나_자녀_정보가_없으면_인기순() throws Exception {
        String teacher = bearer(USERS.incrementAndGet(), "TEACHER");
        long quiet = createContent(teacher, "ELEMENTARY");
        long popular = createContent(teacher, "SCIENCE");
        bookmark(bearer(USERS.incrementAndGet(), "PARENT"), popular);
        bookmark(bearer(USERS.incrementAndGet(), "PARENT"), popular);

        MvcTestResult anonymous = mvc.get().uri("/api/contents/recommendations?size=50").exchange();
        assertThat(anonymous).hasStatusOk().bodyJson().extractingPath("$.basedOn").asArray().isEmpty();
        assertThat(order(anonymous, quiet, popular)).containsExactly(popular, quiet);
        assertThat(recommendations(bearer(USERS.incrementAndGet(), "PARENT")))
                .bodyJson().extractingPath("$.basedOn").asArray().isEmpty();
    }

    @Test
    void 늦게_도착한_옛_자녀_정보는_무시하고_탈퇴하면_지운다() {
        long parentId = USERS.incrementAndGet();
        Instant now = Instant.now();
        childrenChanged(parentId, List.of("HIGH_1"), now);
        childrenChanged(parentId, List.of("KINDERGARTEN"), now.minusSeconds(60)); // 더 이른 시각의 이벤트가 늦게 옴
        KafkaTestRecords.send(kafka.getBootstrapServers(), TspoonTopics.USER_EVENTS, String.valueOf(parentId),
                EventContracts.envelope(UserEventTypes.PROFILE_UPDATED, "USER", parentId,
                        "{\"userId\": %d, \"name\": \"표시\"}".formatted(parentId)));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(childGrades.gradesOf(parentId)).containsExactly("HIGH_1"));
        assertThat(recommendations(bearer(parentId, "PARENT"))).bodyJson().extractingPath("$.basedOn").asArray()
                .containsExactly("HIGH_SCHOOL");

        KafkaTestRecords.send(kafka.getBootstrapServers(), TspoonTopics.USER_EVENTS, String.valueOf(parentId),
                EventContracts.envelope(UserEventTypes.WITHDRAWN, "USER", parentId,
                        "{\"userId\": %d, \"withdrawnAt\": \"2026-10-26T00:00:00Z\"}".formatted(parentId)));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(childGrades.gradesOf(parentId)).isEmpty());
    }

    private void childrenChanged(long userId, List<String> grades, Instant occurredAt) {
        String json = grades.stream().map(grade -> "\"" + grade + "\"").reduce((a, b) -> a + "," + b).orElse("");
        KafkaTestRecords.send(kafka.getBootstrapServers(), TspoonTopics.USER_EVENTS, String.valueOf(userId),
                EventContracts.envelope(UserEventTypes.CHILDREN_CHANGED, "USER", userId,
                        "{\"userId\": %d, \"grades\": [%s]}".formatted(userId, json), occurredAt));
    }

    private static List<Long> order(MvcTestResult result, long... ids) throws Exception {
        List<Number> all = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.contents[*].id");
        List<Long> wanted = java.util.Arrays.stream(ids).boxed().toList();
        return all.stream().map(Number::longValue).filter(wanted::contains).toList();
    }

    private MvcTestResult recommendations(String token) {
        return mvc.get().uri("/api/contents/recommendations?size=50").header(HttpHeaders.AUTHORIZATION, token).exchange();
    }

    private void bookmark(String token, long contentId) {
        assertThat(mvc.post().uri("/api/contents/{id}/bookmark", contentId).header(HttpHeaders.AUTHORIZATION, token)).hasStatusOk();
    }

    private long createContent(String token, String category) throws Exception {
        MvcTestResult created = mvc.post().uri("/api/contents").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "추천 콘텐츠", "description": "설명", "content": "본문", "type": "ARTICLE",
                         "category": "%s", "estimatedTime": 15, "targetAge": "전체", "draft": false}
                        """.formatted(category))
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return JsonPath.<Number>read(created.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id").longValue();
    }

    private String bearer(long userId, String role) {
        return "Bearer " + jwtUtil.generateAccessToken(new AuthUser(userId, "user" + userId + "@tspoon.com", "회원" + userId, role));
    }
}
