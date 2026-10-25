package com.tspoon.notificationservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 계정 메일(이메일 인증 등). 링크에 일회용 토큰이 들어가므로 알림함(notifications)에 내용을 남기지 않고 바로 보낸다.
 * 발송에 실패하면 예외를 그대로 던져 Kafka 소비자가 다시 시도하게 한다 (끝내 실패하면 DLT, 회원은 재발송을 요청할 수 있다).
 */
@Component
@RequiredArgsConstructor
public class AccountMailSender {

    private static final DateTimeFormatter EXPIRES_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Asia/Seoul"));

    private final JavaMailSender mailSender;

    @Value("${tspoon.notification.mail-from}")
    private String mailFrom;

    @Value("${tspoon.notification.web-base-url}")
    private String webBaseUrl;

    public void sendEmailVerification(String email, String name, String token, Instant expiresAt) {
        send(email, "이메일 주소를 확인해 주세요", """
                %s님, 티스푼에 가입해 주셔서 감사합니다.
                아래 링크를 열어 이메일 주소를 확인해 주세요. 링크는 %s(한국 시간)까지 한 번만 쓸 수 있습니다.

                %s

                본인이 가입하지 않았다면 이 메일을 무시해 주세요.
                """.formatted(name, EXPIRES_AT.format(expiresAt), link("/verify-email", token)));
    }

    private String link(String path, String token) {
        return UriComponentsBuilder.fromUriString(webBaseUrl).path(path).queryParam("token", token).encode().toUriString();
    }

    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        message.setSubject("[티스푼] " + subject);
        message.setText(text);
        mailSender.send(message);
    }
}
