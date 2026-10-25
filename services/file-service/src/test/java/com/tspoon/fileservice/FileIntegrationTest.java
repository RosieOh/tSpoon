package com.tspoon.fileservice;

import com.jayway.jsonpath.JsonPath;
import com.tspoon.auth.AuthUser;
import com.tspoon.auth.jwt.JwtUtil;
import com.tspoon.core.test.TestcontainersConfiguration;
import com.tspoon.fileservice.service.FileService;
import com.tspoon.fileservice.storage.FileStorage;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.kafka.listener.auto-startup=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FileIntegrationTest {

    // compose·Kubernetes 와 같은 SeaweedFS 이미지·S3 계정 설정
    private static final GenericContainer<?> SEAWEEDFS = new GenericContainer<>(DockerImageName.parse("chrislusf/seaweedfs:4.48"))
            .withCommand("server", "-ip.bind=0.0.0.0", "-dir=/data", "-s3", "-s3.port=8333", "-s3.config=/etc/seaweedfs/s3.json")
            .withCopyFileToContainer(MountableFile.forHostPath("../../docker/seaweedfs/s3.json"), "/etc/seaweedfs/s3.json")
            .withExposedPorts(8333)
            .waitingFor(Wait.forHttp("/healthz").forPort(8333).withStartupTimeout(Duration.ofMinutes(2)));

    private static final byte[] PNG = Arrays.copyOf("\u0089PNG tspoon test image".getBytes(StandardCharsets.ISO_8859_1), 68);
    private static final AtomicLong USERS = new AtomicLong(70_000);

    static {
        SEAWEEDFS.start();
    }

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        String endpoint = "http://" + SEAWEEDFS.getHost() + ":" + SEAWEEDFS.getMappedPort(8333);
        registry.add("tspoon.file.storage.endpoint", () -> endpoint);
        registry.add("tspoon.file.storage.public-endpoint", () -> endpoint);
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    FileService fileService;

    @Autowired
    FileStorage fileStorage;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void 업로드_URL_로_저장소에_직접_올리고_완료하면_누구나_내려받기_URL_로_같은_파일을_받는다() throws Exception {
        String owner = newUser();
        MvcTestResult ticket = requestUpload(owner, "썸네일 이미지.png", "image/png", PNG.length);
        assertThat(ticket).hasStatus(HttpStatus.CREATED);
        String fileId = read(ticket, "$.fileId");

        assertThat(put(read(ticket, "$.uploadUrl"), "image/png", PNG)).isEqualTo(200);
        assertThat(complete(owner, fileId)).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("UPLOADED");

        MvcTestResult info = mvc.get().uri("/api/files/{id}", fileId).exchange(); // 비로그인
        assertThat(info).hasStatusOk().bodyJson().extractingPath("$.fileName").isEqualTo("썸네일 이미지.png");
        HttpResponse<byte[]> downloaded = http.send(HttpRequest.newBuilder(URI.create(read(info, "$.downloadUrl"))).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(downloaded.statusCode()).isEqualTo(200);
        assertThat(downloaded.body()).isEqualTo(PNG);
        // 원래 이름으로 저장되게 한다 (한글은 RFC 5987 인코딩)
        assertThat(downloaded.headers().firstValue("Content-Disposition")).hasValueSatisfying(value ->
                assertThat(value).contains("filename*=UTF-8''%EC%8D%B8%EB%84%A4%EC%9D%BC%20%EC%9D%B4%EB%AF%B8%EC%A7%80.png"));
        assertThat(mvc.get().uri("/api/files").header(HttpHeaders.AUTHORIZATION, owner))
                .bodyJson().extractingPath("$.content[0].id").isEqualTo(fileId);
    }

    @Test
    void 내용으로_형식을_알_수_없는_바이트도_요청한_형식으로_올라간다() throws Exception {
        String owner = newUser();
        byte[] data = new byte[1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        MvcTestResult ticket = requestUpload(owner, "자료.pdf", "application/pdf", data.length);

        assertThat(put(read(ticket, "$.uploadUrl"), "application/pdf", data)).isEqualTo(200);
        assertThat(complete(owner, read(ticket, "$.fileId"))).hasStatusOk();
    }

    @Test
    void 서명에_든_형식과_다르게_올리면_저장소가_거절한다() throws Exception {
        String owner = newUser();
        MvcTestResult ticket = requestUpload(owner, "a.png", "image/png", PNG.length);

        assertThat(put(read(ticket, "$.uploadUrl"), "text/html", PNG)).isEqualTo(403);
        assertThat(complete(owner, read(ticket, "$.fileId"))).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void 허용하지_않는_형식이나_너무_큰_파일은_업로드_URL_을_주지_않는다() {
        String owner = newUser();
        assertThat(requestUpload(owner, "page.html", "text/html", 100)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(requestUpload(owner, "big.pdf", "application/pdf", 11L * 1024 * 1024)).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").isEqualTo("파일은 10MB 까지 올릴 수 있습니다.");
        assertThat(requestUpload(owner, "empty.png", "image/png", 0)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(requestUpload(owner, "../../etc/passwd", "image/png", 10)).hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.fileId").isNotNull();
        assertThat(mvc.post().uri("/api/files/uploads").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fileName\": \"a.png\", \"contentType\": \"image/png\", \"size\": 10}"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 파일_이름의_경로는_버리고_이름만_남긴다() throws Exception {
        String owner = newUser();
        String fileId = read(requestUpload(owner, "..\\..\\secret/../report.pdf", "application/pdf", PNG.length), "$.fileId");
        assertThat(jdbcTemplate.queryForObject("SELECT file_name FROM files WHERE id = ?::uuid", String.class, fileId))
                .isEqualTo("report.pdf");
    }

    @Test
    void 다른_회원의_파일은_완료하거나_지울_수_없고_올린_사람이_지우면_저장소에서도_사라진다() throws Exception {
        String owner = newUser();
        String other = newUser();
        MvcTestResult ticket = requestUpload(owner, "a.png", "image/png", PNG.length);
        String fileId = read(ticket, "$.fileId");
        put(read(ticket, "$.uploadUrl"), "image/png", PNG);

        assertThat(complete(other, fileId)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(complete(owner, fileId)).hasStatusOk();
        assertThat(mvc.delete().uri("/api/files/{id}", fileId).header(HttpHeaders.AUTHORIZATION, other)).hasStatus(HttpStatus.NOT_FOUND);

        String storageKey = jdbcTemplate.queryForObject("SELECT storage_key FROM files WHERE id = ?::uuid", String.class, fileId);
        assertThat(mvc.delete().uri("/api/files/{id}", fileId).header(HttpHeaders.AUTHORIZATION, owner)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/files/{id}", fileId)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(fileStorage.head(storageKey)).isEmpty();
    }

    @Test
    void 업로드_URL_만_받고_올리지_않은_요청은_보관_기간이_지나면_정리된다() throws Exception {
        String owner = newUser();
        String stale = read(requestUpload(owner, "stale.png", "image/png", PNG.length), "$.fileId");
        String fresh = read(requestUpload(owner, "fresh.png", "image/png", PNG.length), "$.fileId");
        jdbcTemplate.update("UPDATE files SET created_at = now() - interval '2 days' WHERE id = ?::uuid", stale);

        fileService.deleteAbandonedUploads();

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM files WHERE id IN (?::uuid, ?::uuid)", Long.class, stale, fresh))
                .isEqualTo(1);
    }

    private int put(String url, String contentType, byte[] body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private MvcTestResult requestUpload(String token, String fileName, String contentType, long size) {
        return mvc.post().uri("/api/files/uploads").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fileName\": \"%s\", \"contentType\": \"%s\", \"size\": %d}"
                        .formatted(fileName.replace("\\", "\\\\"), contentType, size))
                .exchange();
    }

    private MvcTestResult complete(String token, String fileId) {
        return mvc.post().uri("/api/files/{id}/complete", fileId).header(HttpHeaders.AUTHORIZATION, token).exchange();
    }

    private static String read(MvcTestResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), path);
    }

    private String newUser() {
        long id = USERS.incrementAndGet();
        return "Bearer " + jwtUtil.generateAccessToken(new AuthUser(id, "user" + id + "@tspoon.com", "회원", "PARENT"));
    }
}
