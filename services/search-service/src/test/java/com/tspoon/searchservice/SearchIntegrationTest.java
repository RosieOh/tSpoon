package com.tspoon.searchservice;

import com.tspoon.core.test.KafkaTestRecords;
import com.tspoon.core.test.KafkaTestcontainersConfiguration;
import com.tspoon.event.CommunityEventTypes;
import com.tspoon.event.EducationEventTypes;
import com.tspoon.event.QnaEventTypes;
import com.tspoon.event.TspoonTopics;
import com.tspoon.event.UserEventTypes;
import com.tspoon.event.test.EventContracts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Import(KafkaTestcontainersConfiguration.class)
class SearchIntegrationTest {

    // compose·Kubernetes 와 같은 Dockerfile(OpenSearch + nori)로 만든다
    private static final GenericContainer<?> OPENSEARCH = new GenericContainer<>(
            new ImageFromDockerfile("tspoon/opensearch-test", false)
                    .withDockerfile(Path.of("../../docker/opensearch/Dockerfile")))
            .withEnv("discovery.type", "single-node")
            .withEnv("DISABLE_SECURITY_PLUGIN", "true")
            .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
            .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withExposedPorts(9200)
            .waitingFor(Wait.forHttp("/_cluster/health").forPort(9200).withStartupTimeout(Duration.ofMinutes(3)));

    private static final AtomicLong IDS = new AtomicLong(1_000);

    static {
        OPENSEARCH.start();
    }

    @DynamicPropertySource
    static void opensearch(DynamicPropertyRegistry registry) {
        registry.add("tspoon.search.opensearch-url", () -> "http://" + OPENSEARCH.getHost() + ":" + OPENSEARCH.getMappedPort(9200));
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    KafkaContainer kafka;

    @Test
    void 게시글_질문_콘텐츠를_색인하고_한국어_형태소로_찾는다() {
        long boardId = IDS.incrementAndGet();
        long questionId = IDS.incrementAndGet();
        long contentId = IDS.incrementAndGet();
        publishBoard(boardId, "초등학생 수학 공부법을 공유합니다", "매일 10분씩 연산을 연습해요", 501, "김부모", Instant.now());
        send(TspoonTopics.QNA_EVENTS, questionId, QnaEventTypes.QUESTION_PUBLISHED, "QUESTION", """
                {"questionId": %d, "title": "아이 영어 단어 외우기", "content": "효과적인 방법이 있을까요?", "category": "LEARNING",
                 "authorId": 502, "authorName": "이질문", "createdAt": "2026-10-26T00:00:00Z"}
                """.formatted(questionId), Instant.now());
        send(TspoonTopics.EDUCATION_EVENTS, contentId, EducationEventTypes.CONTENT_PUBLISHED, "CONTENT", """
                {"contentId": %d, "title": "놀이로 배우는 영어", "content": "그림책으로 시작하는 영어 공부", "category": "LANGUAGE",
                 "authorId": 503, "authorName": "박교사", "createdAt": "2026-10-26T00:00:00Z"}
                """.formatted(contentId), Instant.now());

        // "공부법을" 이 형태소로 나뉘어 "공부" 로도 찾는다 (standard 분석기라면 못 찾는다)
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            MvcTestResult result = search("q=공부&type=BOARD");
            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.content[?(@.id == %d)].highlight".formatted(boardId)).asArray()
                    .singleElement().asString().contains("<em>공부</em>");
        });
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(search("q=영어")).bodyJson().extractingPath("$.content[*].type").asArray().contains("QUESTION", "CONTENT");
            assertThat(search("q=영어&type=QUESTION")).bodyJson().extractingPath("$.content[*].id").asArray()
                    .containsExactly((int) questionId);
        });
        assertThat(search("q=영어&type=QUESTION")).bodyJson().extractingPath("$.content[0].url").isEqualTo("/questions/" + questionId);
    }

    @Test
    void 지우면_검색에서_빠지고_늦게_도착한_옛_이벤트는_지운_글을_되살리지_못한다() {
        long boardId = IDS.incrementAndGet();
        Instant now = Instant.now();
        publishBoard(boardId, "곧 지울 과학 실험 글", "식초와 베이킹소다", 504, "회원", now);
        awaitHits("q=베이킹소다", boardId, 1);

        send(TspoonTopics.COMMUNITY_EVENTS, boardId, CommunityEventTypes.BOARD_REMOVED, "BOARD",
                "{\"boardId\": %d}".formatted(boardId), now.plusSeconds(2));
        // DLT 재처리 등으로 지우기 전 내용이 늦게 도착해도 무시한다
        publishBoard(boardId, "곧 지울 과학 실험 글", "식초와 베이킹소다", 504, "회원", now.plusSeconds(1));

        awaitHits("q=베이킹소다", boardId, 0);
        // 늦은 이벤트가 처리될 시간을 준 뒤에도 그대로 없다
        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(hitCount("q=베이킹소다", boardId)).isZero());
    }

    @Test
    void 회원_이름이_바뀌면_색인의_작성자_이름도_바뀐다() {
        long boardId = IDS.incrementAndGet();
        long authorId = IDS.incrementAndGet();
        publishBoard(boardId, "독서 습관 만들기", "잠들기 전 그림책 한 권", authorId, "옛이름", Instant.now());
        awaitHits("q=그림책", boardId, 1);

        KafkaTestRecords.send(kafka.getBootstrapServers(), TspoonTopics.USER_EVENTS, String.valueOf(authorId),
                EventContracts.envelope(UserEventTypes.PROFILE_UPDATED, "USER", authorId,
                        "{\"userId\": %d, \"name\": \"새이름\"}".formatted(authorId)));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(search("q=그림책"))
                .bodyJson().extractingPath("$.content[?(@.id == %d)].authorName".formatted(boardId)).asArray()
                .containsExactly("새이름"));
    }

    @Test
    void 강조_표시는_내용을_HTML_이스케이프한_뒤_감싼다() {
        long boardId = IDS.incrementAndGet();
        publishBoard(boardId, "질문 있어요", "<script>alert(1)</script> 미술 준비물", 505, "회원", Instant.now());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(search("q=미술"))
                .bodyJson().extractingPath("$.content[?(@.id == %d)].highlight".formatted(boardId)).asArray()
                .singleElement().asString().contains("&lt;script&gt;").contains("<em>미술</em>").doesNotContain("<script>"));
    }

    @Test
    void 검색어가_없거나_너무_길면_400() {
        assertThat(mvc.get().uri("/api/search?q=")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/api/search?q=" + "가".repeat(101))).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/api/search?q=수학&type=VIDEO")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    private void publishBoard(long boardId, String title, String content, long authorId, String authorName, Instant occurredAt) {
        send(TspoonTopics.COMMUNITY_EVENTS, boardId, CommunityEventTypes.BOARD_PUBLISHED, "BOARD", """
                {"boardId": %d, "title": "%s", "content": "%s", "category": "GENERAL", "authorId": %d, "authorName": "%s",
                 "createdAt": "2026-10-26T00:00:00Z"}
                """.formatted(boardId, title, content, authorId, authorName), occurredAt);
    }

    private void send(String topic, long key, String eventType, String aggregateType, String payload, Instant occurredAt) {
        KafkaTestRecords.send(kafka.getBootstrapServers(), topic, String.valueOf(key),
                EventContracts.envelope(eventType, aggregateType, key, payload, occurredAt));
    }

    private void awaitHits(String query, long boardId, int expected) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(hitCount(query, boardId)).isEqualTo(expected));
    }

    private int hitCount(String query, long boardId) throws Exception {
        String body = search(query).getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return com.jayway.jsonpath.JsonPath.<java.util.List<Object>>read(body, "$.content[?(@.id == %d)]".formatted(boardId)).size();
    }

    private MvcTestResult search(String query) {
        return mvc.get().uri("/api/search?" + query).exchange();
    }
}
