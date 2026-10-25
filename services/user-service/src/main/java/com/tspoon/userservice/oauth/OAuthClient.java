package com.tspoon.userservice.oauth;

import com.tspoon.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * OAuth 2.0 인가 코드 교환 — 클라이언트가 받은 인가 코드를 제공자의 Access Token 으로 바꾸고 회원 정보를 읽는다.
 * 제공자 토큰은 회원 정보를 읽는 데만 쓰고 저장하지 않는다.
 */
@Slf4j
@Component
public class OAuthClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final RestClient client;

    public OAuthClient() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        requestFactory.setReadTimeout(TIMEOUT);
        this.client = RestClient.builder().requestFactory(requestFactory).build();
    }

    public SocialProfile fetchProfile(String providerName, OAuthProperties.Provider provider, String code, String state) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code");
            form.add("client_id", provider.clientId());
            form.add("client_secret", provider.clientSecret());
            form.add("redirect_uri", provider.redirectUri());
            form.add("code", code);
            if (StringUtils.hasText(state)) {
                form.add("state", state);
            }
            JsonNode token = client.post().uri(provider.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).accept(MediaType.APPLICATION_JSON)
                    .body(form).retrieve().body(JsonNode.class);
            String accessToken = token == null ? null : token.path("access_token").asString(null);
            if (accessToken == null) {
                throw new IllegalStateException("access_token 이 없는 응답");
            }

            JsonNode userInfo = client.get().uri(provider.userInfoUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(JsonNode.class);
            return SocialProfile.from(providerName, userInfo);
        } catch (RestClientException | IllegalStateException | JacksonException e) {
            // 코드가 만료됐거나 이미 썼거나, 제공자가 응답하지 않는다 — 사용자는 처음부터 다시 로그인하면 된다
            log.info("{} 로그인 실패: {}", providerName, e.getMessage());
            throw new BusinessException("소셜 로그인에 실패했습니다. 다시 시도해 주세요.", HttpStatus.UNAUTHORIZED, "SOCIAL_LOGIN_FAILED");
        }
    }
}
