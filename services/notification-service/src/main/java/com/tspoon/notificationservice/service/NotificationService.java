package com.tspoon.notificationservice.service;

import com.tspoon.auth.AuthUser;
import com.tspoon.core.exception.BusinessException;
import com.tspoon.core.exception.ResourceNotFoundException;
import com.tspoon.notificationservice.dto.CreateNotificationRequest;
import com.tspoon.notificationservice.dto.NotificationResponse;
import com.tspoon.notificationservice.entity.Notification;
import com.tspoon.notificationservice.entity.Notification.NotificationChannel;
import com.tspoon.notificationservice.entity.Notification.NotificationStatus;
import com.tspoon.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 알림 생성과 알림함. 앱 내 알림은 바로 알림함에 들어가고(UNREAD),
 * 이메일 알림은 커밋 뒤 {@link EmailNotificationSender} 가 비동기로 보낸다(PENDING → SENT/FAILED).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private static final Set<NotificationChannel> SUPPORTED_CHANNELS = Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL);

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public List<Long> create(CreateNotificationRequest request) {
        NotificationChannel channel = request.channel();
        if (!SUPPORTED_CHANNELS.contains(channel)) {
            throw new BusinessException("지원하지 않는 채널입니다: " + channel);
        }
        if (channel == NotificationChannel.EMAIL
                && request.recipients().stream().anyMatch(recipient -> recipient.email() == null || recipient.email().isBlank())) {
            throw new BusinessException("이메일 알림은 수신자마다 email 이 필요합니다.");
        }

        Instant now = Instant.now();
        List<Long> ids = new ArrayList<>();
        for (CreateNotificationRequest.Recipient recipient : request.recipients()) {
            Notification notification = notificationRepository.save(Notification.builder()
                    .userId(recipient.userId())
                    .title(request.title())
                    .content(request.content())
                    .type(request.type() == null ? Notification.NotificationType.ANNOUNCEMENT : request.type())
                    .channel(channel)
                    .status(channel == NotificationChannel.IN_APP ? NotificationStatus.UNREAD : NotificationStatus.PENDING)
                    .sentAt(channel == NotificationChannel.IN_APP ? now : null)
                    .actionUrl(request.actionUrl())
                    .relatedEntityType(request.relatedEntityType())
                    .relatedEntityId(request.relatedEntityId())
                    .build());
            ids.add(notification.getId());

            if (channel == NotificationChannel.EMAIL) {
                // 수신 주소는 저장하지 않고 발송에만 쓴다
                eventPublisher.publishEvent(new EmailNotificationRequested(
                        notification.getId(), recipient.email(), request.title(), request.content(), request.actionUrl()));
            } else {
                eventPublisher.publishEvent(InAppNotificationCreated.of(notification));
            }
        }
        return ids;
    }

    // 다른 서비스의 이벤트로 만드는 앱 내 알림 (DomainEventNotifier)
    public Long createInApp(Long userId, String title, String content, Notification.NotificationType type,
                            String actionUrl, String relatedEntityType, Long relatedEntityId) {
        Notification notification = notificationRepository.save(Notification.builder()
                .userId(userId)
                .title(title)
                .content(content)
                .type(type)
                .channel(NotificationChannel.IN_APP)
                .status(NotificationStatus.UNREAD)
                .sentAt(Instant.now())
                .actionUrl(actionUrl)
                .relatedEntityType(relatedEntityType)
                .relatedEntityId(relatedEntityId)
                .build());
        eventPublisher.publishEvent(InAppNotificationCreated.of(notification));
        return notification.getId();
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMyNotifications(AuthUser user, boolean unreadOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<Notification> notifications = unreadOnly
                ? notificationRepository.findByUserIdAndChannelAndStatusOrderByCreatedAtDesc(
                        user.userId(), NotificationChannel.IN_APP, NotificationStatus.UNREAD, pageable)
                : notificationRepository.findByUserIdAndChannelOrderByCreatedAtDesc(user.userId(), NotificationChannel.IN_APP, pageable);
        return notifications.map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long countUnread(AuthUser user) {
        return notificationRepository.countByUserIdAndChannelAndStatus(user.userId(), NotificationChannel.IN_APP, NotificationStatus.UNREAD);
    }

    // 다른 회원의 알림은 존재 여부도 드러내지 않도록 404
    public NotificationResponse markRead(AuthUser user, Long id) {
        Notification notification = notificationRepository.findByIdAndUserIdAndChannel(id, user.userId(), NotificationChannel.IN_APP)
                .orElseThrow(() -> new ResourceNotFoundException("알림을 찾을 수 없습니다."));
        if (notification.getStatus() == NotificationStatus.UNREAD) {
            notification.setStatus(NotificationStatus.READ);
            notification.setReadAt(Instant.now());
        }
        return NotificationResponse.from(notification);
    }

    public int markAllRead(AuthUser user) {
        return notificationRepository.markAllRead(user.userId(), Instant.now());
    }

    // 탈퇴한 회원의 알림은 남길 이유가 없다
    public int deleteAllOf(Long userId) {
        return notificationRepository.deleteAllByUserId(userId);
    }

    public record EmailNotificationRequested(Long notificationId, String email, String title, String content, String actionUrl) {
    }

    /** 앱 내 알림이 만들어졌다 — 커밋되면 실시간 스트림으로 전한다 */
    public record InAppNotificationCreated(Long userId, NotificationResponse notification) {

        static InAppNotificationCreated of(Notification notification) {
            return new InAppNotificationCreated(notification.getUserId(), NotificationResponse.from(notification));
        }
    }
}
