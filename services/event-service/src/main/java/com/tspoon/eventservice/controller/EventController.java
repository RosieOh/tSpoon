package com.tspoon.eventservice.controller;

import com.tspoon.auth.AuthUser;
import com.tspoon.eventservice.dto.EventRequest;
import com.tspoon.eventservice.dto.EventResponse;
import com.tspoon.eventservice.dto.RegistrationRequest;
import com.tspoon.eventservice.dto.RegistrationResponse;
import com.tspoon.eventservice.entity.Event;
import com.tspoon.eventservice.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "행사", description = "행사 조회·등록(교사·전문가)·참가 신청 (일정은 한국 시간)")
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @Operation(summary = "행사 목록 (다가오는 순) — 상태를 비우면 모집 중·마감")
    @SecurityRequirements
    @GetMapping
    public ResponseEntity<PagedModel<EventResponse>> search(
            @RequestParam(required = false) Event.EventType type,
            @RequestParam(required = false) Event.EventStatus status,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page 는 0 이상이어야 합니다") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size 는 1 이상이어야 합니다")
            @Max(value = 100, message = "size 는 100 이하여야 합니다") int size) {
        return ResponseEntity.ok(new PagedModel<>(eventService.search(type, status, page, size)));
    }

    @Operation(summary = "행사 상세 — 남은 자리 포함")
    @SecurityRequirements
    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEvent(id));
    }

    @Operation(summary = "행사 등록 (교사·전문가·관리자)")
    @PreAuthorize("hasAnyRole('TEACHER', 'EXPERT', 'ADMIN')")
    @PostMapping
    public ResponseEntity<EventResponse> create(@AuthenticationPrincipal AuthUser organizer,
                                                @Valid @RequestBody EventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.create(organizer, request));
    }

    @Operation(summary = "행사 수정 (주최자·관리자) — 정원은 현재 신청 인원보다 줄일 수 없음")
    @PutMapping("/{id}")
    public ResponseEntity<EventResponse> update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                                @Valid @RequestBody EventRequest request) {
        return ResponseEntity.ok(eventService.update(user, id, request));
    }

    @Operation(summary = "행사 취소 (주최자·관리자)")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        eventService.cancel(user, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "참가 신청 — 신청 기간·정원 확인 (동시 신청에서도 정원을 넘지 않음)")
    @PostMapping("/{id}/registrations")
    public ResponseEntity<RegistrationResponse> register(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                                         @Valid @RequestBody(required = false) RegistrationRequest request) {
        RegistrationRequest body = request == null ? new RegistrationRequest(null, null) : request;
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.register(user, id, body));
    }

    @Operation(summary = "내 참가 신청 취소 — 행사 시작 전까지, 자리를 돌려준다")
    @DeleteMapping("/{id}/registrations/me")
    public ResponseEntity<Void> cancelRegistration(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        eventService.cancelRegistration(user, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "신청자 목록 (주최자·관리자)")
    @GetMapping("/{id}/registrations")
    public ResponseEntity<List<RegistrationResponse>> getRegistrations(@AuthenticationPrincipal AuthUser user,
                                                                       @PathVariable Long id) {
        return ResponseEntity.ok(eventService.getRegistrations(user, id));
    }

    @Operation(summary = "내 참가 신청 목록")
    @GetMapping("/registrations/me")
    public ResponseEntity<PagedModel<RegistrationResponse>> getMyRegistrations(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page 는 0 이상이어야 합니다") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size 는 1 이상이어야 합니다")
            @Max(value = 100, message = "size 는 100 이하여야 합니다") int size) {
        return ResponseEntity.ok(new PagedModel<>(eventService.getMyRegistrations(user, page, size)));
    }
}
