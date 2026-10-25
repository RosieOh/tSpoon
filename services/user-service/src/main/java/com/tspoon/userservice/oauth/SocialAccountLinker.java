package com.tspoon.userservice.oauth;

import com.tspoon.core.exception.BusinessException;
import com.tspoon.userservice.entity.User;
import com.tspoon.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 소셜 계정을 회원에 연결한다.
 * <ol>
 *   <li>이미 연결된 제공자 계정이면 그 회원</li>
 *   <li>같은 이메일의 회원이 있으면 — 제공자와 우리 쪽 <b>모두 인증된 이메일일 때만</b> 연결한다.
 *       한쪽이라도 확인되지 않은 이메일로 연결하면, 남의 이메일로 만든 소셜 계정으로 그 회원 계정을 가로챌 수 있다</li>
 *   <li>없으면 새로 가입시킨다 (제공자가 인증한 이메일이라 인증 메일은 보내지 않는다)</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Transactional
public class SocialAccountLinker {

    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public LinkResult link(SocialProfile profile) {
        Optional<SocialAccount> linked = socialAccountRepository.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId());
        if (linked.isPresent()) {
            return new LinkResult(userRepository.findById(linked.get().getUserId()).orElseThrow(), false);
        }

        if (profile.email() == null || !profile.emailVerified()) {
            throw new BusinessException("소셜 계정의 이메일이 인증되지 않았거나 이메일 제공에 동의하지 않았습니다. 이메일 제공에 동의한 뒤 다시 시도해 주세요.",
                    HttpStatus.BAD_REQUEST, "SOCIAL_EMAIL_REQUIRED");
        }
        Optional<User> sameEmail = userRepository.findByEmail(profile.email());
        if (sameEmail.isPresent() && !sameEmail.get().isEmailVerified()) {
            throw new BusinessException("같은 이메일로 가입했지만 이메일 인증을 마치지 않은 계정이 있습니다. 인증을 마친 뒤 다시 시도해 주세요.",
                    HttpStatus.CONFLICT, "SOCIAL_LINK_NEEDS_VERIFIED_EMAIL");
        }

        User user = sameEmail.orElseGet(() -> userRepository.save(User.builder()
                .email(profile.email())
                // 비밀번호로는 로그인할 수 없다 — 원하면 비밀번호 재설정으로 만든다
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .name(profile.name())
                .role(User.UserRole.PARENT)
                .status(User.UserStatus.ACTIVE)
                .emailVerifiedAt(Instant.now())
                .build()));
        socialAccountRepository.save(new SocialAccount(user.getId(), profile.provider(), profile.providerUserId()));
        return new LinkResult(user, sameEmail.isEmpty());
    }

    public record LinkResult(User user, boolean created) {
    }
}
