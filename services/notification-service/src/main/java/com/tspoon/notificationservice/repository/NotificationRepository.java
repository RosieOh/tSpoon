package com.tspoon.notificationservice.repository;

import com.tspoon.notificationservice.entity.Notification;
import com.tspoon.notificationservice.entity.Notification.NotificationChannel;
import com.tspoon.notificationservice.entity.Notification.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdAndChannelOrderByCreatedAtDesc(Long userId, NotificationChannel channel, Pageable pageable);

    Page<Notification> findByUserIdAndChannelAndStatusOrderByCreatedAtDesc(Long userId, NotificationChannel channel,
                                                                          NotificationStatus status, Pageable pageable);

    long countByUserIdAndChannelAndStatus(Long userId, NotificationChannel channel, NotificationStatus status);

    Optional<Notification> findByIdAndUserIdAndChannel(Long id, Long userId, NotificationChannel channel);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Notification n SET n.status = com.tspoon.notificationservice.entity.Notification.NotificationStatus.READ, n.readAt = :now
            WHERE n.userId = :userId
              AND n.channel = com.tspoon.notificationservice.entity.Notification.NotificationChannel.IN_APP
              AND n.status = com.tspoon.notificationservice.entity.Notification.NotificationStatus.UNREAD
            """)
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);

    // 이메일 발송 결과 기록 — 커밋 뒤 비동기로 호출되므로 자체 트랜잭션에서 실행한다
    @Transactional
    @Modifying
    @Query("UPDATE Notification n SET n.status = :status, n.sentAt = :sentAt WHERE n.id = :id")
    int updateDelivery(@Param("id") Long id, @Param("status") NotificationStatus status, @Param("sentAt") Instant sentAt);
}
