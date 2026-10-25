package com.tspoon.userservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.auth.AuthUser;
import com.tspoon.auth.jwt.JwtUtil;
import com.tspoon.core.exception.BusinessException;
import com.tspoon.core.test.RedisTestcontainersConfiguration;
import com.tspoon.core.test.TestcontainersConfiguration;
import com.tspoon.userservice.dto.ChildRequest;
import com.tspoon.userservice.entity.Child;
import com.tspoon.userservice.repository.ChildRepository;
import com.tspoon.userservice.service.ChildService;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class ChildApiIntegrationTest {

    private static final String CHILD_JSON = """
            {"name": "김하나", "birthDate": "2017-03-02", "grade": "ELEMENTARY_3", "school": "서울초등학교",
             "interests": ["독서", " 코딩 ", "독서"], "notes": "수학을 좋아함"}
            """;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    ChildService childService;

    @Autowired
    ChildRepository childRepository;

    @Test
    void 자녀를_등록하고_수정하고_삭제할_수_있다() throws Exception {
        String token = bearer(registerAndGetId());

        MvcTestResult created = create(token, CHILD_JSON);
        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).bodyJson().extractingPath("$.grade").isEqualTo("ELEMENTARY_3");
        // 앞뒤 공백을 지우고 중복을 뺀다
        assertThat(created).bodyJson().extractingPath("$.interests").asArray().containsExactly("독서", "코딩");
        long childId = idOf(created);

        assertThat(mvc.put().uri("/api/users/children/{id}", childId).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "김하나", "birthDate": "2017-03-02", "grade": "ELEMENTARY_4", "interests": ["과학"]}
                        """))
                .hasStatusOk()
                .bodyJson().extractingPath("$.interests").asArray().containsExactly("과학");

        MvcTestResult list = mvc.get().uri("/api/users/children").header(HttpHeaders.AUTHORIZATION, token).exchange();
        assertThat(list).hasStatusOk();
        assertThat(list).bodyJson().extractingPath("$[0].grade").isEqualTo("ELEMENTARY_4");
        assertThat(list).bodyJson().extractingPath("$[0].school").isNull();

        assertThat(mvc.delete().uri("/api/users/children/{id}", childId).header(HttpHeaders.AUTHORIZATION, token))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/users/children").header(HttpHeaders.AUTHORIZATION, token))
                .hasStatusOk()
                .bodyJson().extractingPath("$").asArray().isEmpty();
    }

    @Test
    void 다른_회원의_자녀는_수정하거나_삭제할_수_없다() throws Exception {
        String owner = bearer(registerAndGetId());
        String other = bearer(registerAndGetId());
        long childId = idOf(create(owner, CHILD_JSON));

        assertThat(mvc.put().uri("/api/users/children/{id}", childId).header(HttpHeaders.AUTHORIZATION, other)
                .contentType(MediaType.APPLICATION_JSON).content(CHILD_JSON))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.delete().uri("/api/users/children/{id}", childId).header(HttpHeaders.AUTHORIZATION, other))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/users/children").header(HttpHeaders.AUTHORIZATION, other))
                .bodyJson().extractingPath("$").asArray().isEmpty();
        assertThat(childRepository.findById(childId)).isPresent();
    }

    @Test
    void 입력값이_잘못되면_400과_항목별_메시지() throws Exception {
        String token = bearer(registerAndGetId());
        String tooManyInterests = IntStream.rangeClosed(1, 11).mapToObj(i -> "\"관심" + i + "\"")
                .reduce((a, b) -> a + "," + b).orElseThrow();

        assertThat(create(token, """
                {"name": "", "birthDate": "%s", "interests": [%s]}
                """.formatted(LocalDate.now().plusDays(1), tooManyInterests)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors").asMap()
                .containsKeys("name", "birthDate", "grade", "interests");
    }

    @Test
    void 자녀는_10명까지_등록할_수_있다() throws Exception {
        String token = bearer(registerAndGetId());
        for (int i = 0; i < 10; i++) {
            assertThat(create(token, CHILD_JSON)).hasStatus(HttpStatus.CREATED);
        }

        assertThat(create(token, CHILD_JSON))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").isEqualTo("자녀는 최대 10명까지 등록할 수 있습니다.");
    }

    @Test
    void 자리가_하나_남았을_때_동시에_등록하면_하나만_성공한다() throws Exception {
        long userId = registerAndGetId();
        ChildRequest request = new ChildRequest("김하나", null, Child.Grade.KINDERGARTEN, null, List.of(), null);
        for (int i = 0; i < 9; i++) {
            childService.create(userId, request);
        }

        CountDownLatch start = new CountDownLatch(1);
        long succeeded;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> results = IntStream.range(0, 10).mapToObj(i -> executor.submit(() -> {
                start.await();
                try {
                    childService.create(userId, request);
                    return true;
                } catch (BusinessException e) {
                    return false;
                }
            })).toList();
            start.countDown();
            succeeded = results.stream().filter(ChildApiIntegrationTest::get).count();
        }

        assertThat(succeeded).isEqualTo(1);
        assertThat(childRepository.countByParentId(userId)).isEqualTo(10);
    }

    private MvcTestResult create(String token, String json) {
        return mvc.post().uri("/api/users/children").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private long registerAndGetId() throws Exception {
        MvcTestResult result = mvc.post().uri("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "user-%s@tspoon.com", "password": "password123", "name": "김부모"}
                        """.formatted(UUID.randomUUID()))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return idOf(result);
    }

    private String bearer(long userId) {
        return "Bearer " + jwtUtil.generateAccessToken(new AuthUser(userId, "user" + userId + "@tspoon.com", "김부모", "PARENT"));
    }

    private static long idOf(MvcTestResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id")).longValue();
    }

    private static boolean get(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
