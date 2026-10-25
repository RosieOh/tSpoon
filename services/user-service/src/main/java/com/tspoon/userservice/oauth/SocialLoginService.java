package com.tspoon.userservice.oauth;

import com.tspoon.core.exception.BusinessException;
import com.tspoon.core.exception.ResourceNotFoundException;
import com.tspoon.userservice.dto.LoginResponse;
import com.tspoon.userservice.entity.User;
import com.tspoon.userservice.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 소셜 로그인 — 클라이언트가 제공자에게서 받은 인가 코드로 로그인한다.
 * 제공자와 주고받는 HTTP 호출은 DB 트랜잭션 밖에서 하고(연결을 붙잡지 않도록), 회원 연결만 트랜잭션 안에서 한다.
 * state(CSRF 방지)는 인가 요청을 보낸 클라이언트가 확인하고, 제공자가 요구하면(네이버) 함께 넘긴다.
 */
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private final OAuthProperties properties;
    private final OAuthClient oauthClient;
    private final SocialAccountLinker linker;
    private final TokenService tokenService;

    public List<String> providers() {
        return properties.enabledNames();
    }

    public SocialLoginResult login(String providerName, String code, String state) {
        OAuthProperties.Provider provider = properties.enabled(providerName)
                .orElseThrow(() -> new ResourceNotFoundException("지원하지 않는 로그인 방식입니다: " + providerName));
        SocialProfile profile = oauthClient.fetchProfile(providerName, provider, code, state);

        SocialAccountLinker.LinkResult linked = linker.link(profile);
        User user = linked.user();
        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw new BusinessException("비활성화된 계정입니다.", HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED");
        }
        return new SocialLoginResult(LoginResponse.of(tokenService.startSession(user), user), linked.created());
    }

    /** @param created 이번에 새로 가입했는지 — 가입 직후 안내 화면을 보여 줄 때 쓴다 */
    public record SocialLoginResult(LoginResponse login, boolean created) {
    }
}
