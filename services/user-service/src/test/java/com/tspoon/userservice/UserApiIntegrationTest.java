package com.tspoon.userservice;

import com.jayway.jsonpath.JsonPath;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserApiIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void 회원가입_후_로그인하면_발급된_JWT로_프로필을_조회할_수_있다() throws Exception {
        String email = uniqueEmail();

        assertThat(register(email, "password123", "김부모"))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.email").isEqualTo(email);

        MvcTestResult login = login(email, "password123");
        assertThat(login).hasStatusOk();
        assertThat(login).bodyJson().extractingPath("$.tokenType").isEqualTo("Bearer");

        String token = JsonPath.read(login.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.accessToken");

        assertThat(mvc.get().uri("/api/users/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .hasStatusOk()
                .bodyJson().extractingPath("$.name").isEqualTo("김부모");
    }

    @Test
    void 토큰_없이_보호된_API를_호출하면_401() {
        assertThat(mvc.get().uri("/api/users/profile"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.success").isEqualTo(false);
    }

    @Test
    void 위조된_토큰은_401() {
        assertThat(mvc.get().uri("/api/users/profile").header(HttpHeaders.AUTHORIZATION, "Bearer 1_1700000000000"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 로그인_실패_메시지는_계정_존재_여부와_무관하게_같다() {
        String email = uniqueEmail();
        register(email, "password123", "김부모");

        MvcTestResult wrongPassword = login(email, "wrong-password");
        MvcTestResult unknownEmail = login(uniqueEmail(), "password123");

        assertThat(wrongPassword).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getResponse().getContentAsByteArray())
                .isEqualTo(unknownEmail.getResponse().getContentAsByteArray());
    }

    @Test
    void 이미_가입된_이메일은_409() {
        String email = uniqueEmail();
        register(email, "password123", "김부모");

        assertThat(register(email, "password123", "다른사람")).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void 입력값_검증_실패는_400과_필드별_메시지를_반환한다() {
        assertThat(register("not-an-email", "short", ""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.data")
                .asMap()
                .containsKeys("email", "password", "name");
    }

    private MvcTestResult register(String email, String password, String name) {
        return mvc.post().uri("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s", "name": "%s"}
                        """.formatted(email, password, name))
                .exchange();
    }

    private MvcTestResult login(String email, String password) {
        return mvc.post().uri("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password))
                .exchange();
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@tspoon.com";
    }
}
