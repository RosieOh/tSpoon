package com.tspoon.notificationservice.service;

import com.tspoon.notificationservice.entity.Notification.NotificationStatus;
import com.tspoon.notificationservice.repository.NotificationRepository;
import com.tspoon.notificationservice.service.NotificationService.EmailNotificationRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

/**
 * 알림이 DB 에 커밋된 뒤에만, 요청 스레드와 따로 메일을 보낸다.
 * SMTP 가 느리거나 실패해도 API 응답이 늦어지거나 실패하지 않고, 결과는 알림 상태(SENT/FAILED)로 남는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationSender {

    private final JavaMailSender mailSender;
    private final NotificationRepository notificationRepository;

    @Value("${tspoon.notification.mail-from}")
    private String mailFrom;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(EmailNotificationRequested request) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(request.email());
        message.setSubject("[티스푼] " + request.title());
        message.setText(request.actionUrl() == null ? request.content() : request.content() + "\n\n" + request.actionUrl());

        try {
            mailSender.send(message);
            notificationRepository.updateDelivery(request.notificationId(), NotificationStatus.SENT, Instant.now());
        } catch (MailException e) {
            log.warn("이메일 알림 {} 발송 실패: {}", request.notificationId(), e.getMessage());
            notificationRepository.updateDelivery(request.notificationId(), NotificationStatus.FAILED, null);
        }
    }
}
