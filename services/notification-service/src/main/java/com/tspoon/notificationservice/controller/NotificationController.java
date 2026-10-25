package com.tspoon.notificationservice.controller;

import com.tspoon.auth.AuthUser;
import com.tspoon.notificationservice.dto.NotificationResponse;
import com.tspoon.notificationservice.service.NotificationService;
import com.tspoon.notificationservice.stream.NotificationStreams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@Tag(name = "알림", description = "내 알림함 (앱 내 알림)")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationStreams notificationStreams;

    @Operation(summary = "실시간 알림 (SSE)", description = """
            event: unread-count {"count"} — 연결 직후와 새 알림마다 / event: notification {알림} — 새 알림.
            25초마다 주석 한 줄로 연결을 유지하고, 30분이 지나면 닫히므로 다시 연결한다.
            브라우저 EventSource 는 Authorization 헤더를 보낼 수 없으므로 fetch 기반 SSE 클라이언트를 쓴다.""")
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AuthUser user) {
        return notificationStreams.open(user.userId(), notificationService.countUnread(user));
    }

    @Operation(summary = "내 알림 목록 (최신순) — unreadOnly=true 면 안 읽은 것만")
    @GetMapping
    public ResponseEntity<PagedModel<NotificationResponse>> getMyNotifications(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page 는 0 이상이어야 합니다") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 는 1 이상이어야 합니다")
            @Max(value = 100, message = "size 는 100 이하여야 합니다") int size) {
        return ResponseEntity.ok(new PagedModel<>(notificationService.getMyNotifications(user, unreadOnly, page, size)));
    }

    @Operation(summary = "안 읽은 알림 개수")
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> countUnread(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(Map.of("count", notificationService.countUnread(user)));
    }

    @Operation(summary = "알림 읽음 처리")
    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markRead(user, id));
    }

    @Operation(summary = "모두 읽음 처리 — 처리한 개수를 돌려준다")
    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(Map.of("updated", notificationService.markAllRead(user)));
    }
}
