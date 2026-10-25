package com.tspoon.userservice.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 소셜 로그인 제공자. client-id 를 넣은 제공자만 쓸 수 있다 (키는 환경변수로).
 *
 * @param providers 제공자 이름(kakao, naver) → 설정
 */
@ConfigurationProperties("tspoon.user.oauth")
public record OAuthProperties(Map<String, Provider> providers) {

    public OAuthProperties {
        providers = providers == null ? Map.of() : Map.copyOf(providers);
    }

    public Optional<Provider> enabled(String name) {
        return Optional.ofNullable(providers.get(name)).filter(Provider::isEnabled);
    }

    public List<String> enabledNames() {
        return providers.entrySet().stream().filter(entry -> entry.getValue().isEnabled()).map(Map.Entry::getKey).sorted().toList();
    }

    /**
     * @param redirectUri 제공자에 등록한 주소. 클라이언트가 보내는 값 대신 이것만 쓴다 (다른 곳으로 코드를 빼돌리지 못하게)
     */
    public record Provider(String clientId, String clientSecret, String redirectUri, String tokenUri, String userInfoUri) {

        public boolean isEnabled() {
            return StringUtils.hasText(clientId);
        }
    }
}
