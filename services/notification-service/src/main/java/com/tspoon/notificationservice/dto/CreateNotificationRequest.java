package com.tspoon.notificationservice.dto;

import com.tspoon.notificationservice.entity.Notification;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @param channel IN_APP(알림함) 또는 EMAIL. EMAIL 이면 수신자마다 email 이 필요하다
 */
public record CreateNotificationRequest(
        @NotEmpty(message = "수신자는 한 명 이상이어야 합니다")
        @Size(max = 1000, message = "한 번에 1000명까지 보낼 수 있습니다")
        List<@Valid Recipient> recipients,

        @NotBlank(message = "제목은 필수입니다")
        @Size(max = 255, message = "제목은 255자 이하여야 합니다")
        String title,

        @NotBlank(message = "내용은 필수입니다")
        String content,

        Notification.NotificationType type,

        @NotNull(message = "채널은 필수입니다")
        Notification.NotificationChannel channel,

        String actionUrl,
        String relatedEntityType,
        Long relatedEntityId
) {

    public record Recipient(
            @NotNull(message = "수신자 ID 는 필수입니다")
            Long userId,

            @Email(message = "올바른 이메일 형식이 아닙니다")
            String email
    ) {
    }
}
