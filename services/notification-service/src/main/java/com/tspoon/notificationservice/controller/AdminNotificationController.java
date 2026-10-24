package com.tspoon.notificationservice.controller;

import com.tspoon.notificationservice.dto.CreateNotificationRequest;
import com.tspoon.notificationservice.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// 서비스 간 이벤트로 만드는 알림(#20) 전까지는 관리자 공지로 알림을 만든다
@Tag(name = "알림 보내기", description = "관리자 전용 — 앱 내·이메일 알림")
@RestController
@RequestMapping("/api/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "알림 보내기 — 이메일은 커밋 뒤 비동기로 발송되고 결과는 알림 상태(SENT·FAILED)로 남는다")
    @PostMapping
    public ResponseEntity<Map<String, List<Long>>> send(@Valid @RequestBody CreateNotificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("notificationIds", notificationService.create(request)));
    }
}
