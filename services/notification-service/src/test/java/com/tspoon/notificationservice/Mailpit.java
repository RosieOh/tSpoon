package com.tspoon.notificationservice;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * 메일 발송을 확인하는 테스트용 Mailpit (SMTP 1025, 조회 API 8025). 테스트 클래스끼리 컨테이너 하나를 같이 쓴다.
 */
final class Mailpit {

    private static final GenericContainer<?> CONTAINER =
            new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.32")).withExposedPorts(1025, 8025);

    static {
        CONTAINER.start();
    }

    private Mailpit() {
    }

    static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", CONTAINER::getHost);
        registry.add("spring.mail.port", () -> CONTAINER.getMappedPort(1025));
    }

    static String api(String path) throws Exception {
        URI uri = URI.create("http://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(8025) + path);
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString()).body();
        }
    }

    /** 받는 사람으로 찾은 메일 목록 (Mailpit 검색 API 응답 JSON) */
    static String messagesTo(String address) throws Exception {
        return api("/api/v1/search?query=" + URLEncoder.encode("to:\"" + address + "\"", StandardCharsets.UTF_8));
    }
}
