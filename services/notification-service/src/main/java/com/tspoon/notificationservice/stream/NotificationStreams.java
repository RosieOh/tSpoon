package com.tspoon.notificationservice.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 이 인스턴스에 연결된 실시간 알림 스트림(SSE). 회원 한 명이 여러 기기(탭)로 연결할 수 있다.
 * 알림은 Redis pub/sub 으로 모든 인스턴스에 퍼지고({@link NotificationPushPublisher}), 각 인스턴스는 자기 연결에만 보낸다.
 *
 * <pre>
 * event: unread-count   data: {"count": 3}          연결 직후와 새 알림마다
 * event: notification   data: {NotificationResponse} 새 알림
 * : keep-alive                                         하트비트 (프록시가 유휴 연결을 끊지 않도록)
 * </pre>
 */
@Slf4j
@Component
public class NotificationStreams implements SmartLifecycle {

    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final NotificationStreamProperties properties;
    private volatile boolean accepting = true;

    public NotificationStreams(NotificationStreamProperties properties) {
        this.properties = properties;
    }

    public SseEmitter open(Long userId, long unreadCount) {
        // 제한 시간이 지나면 연결을 닫는다 — 클라이언트는 다시 연결해 그동안의 알림은 목록 API 로 채운다
        SseEmitter emitter = new SseEmitter(properties.timeout().toMillis());
        emitters.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(error -> remove(userId, emitter));
        if (!accepting) {
            // 종료 중인 인스턴스다 — 바로 닫아 클라이언트가 다른 인스턴스로 다시 연결하게 한다
            emitter.complete();
            return emitter;
        }
        send(userId, emitter, unreadCount(unreadCount));
        return emitter;
    }

    /**
     * 종료할 때 열린 스트림을 모두 닫는다. 기본 단계(가장 늦게 시작·가장 먼저 멈춤)라 웹 서버의 정상 종료보다 먼저 불린다 —
     * 스트림을 열어 둔 채면 정상 종료가 끝나지 않는 요청을 제한 시간까지 기다리다 강제 종료된다.
     * 클라이언트(EventSource)는 끊기면 다시 연결하므로 다른 인스턴스로 옮겨 간다.
     */
    @Override
    public void stop() {
        accepting = false;
        emitters.values().forEach(targets -> targets.forEach(SseEmitter::complete));
    }

    @Override
    public void start() {
        accepting = true;
    }

    @Override
    public boolean isRunning() {
        return accepting;
    }

    /** 다른 인스턴스(또는 이 인스턴스)에서 만든 알림을, 이 인스턴스에 연결된 그 회원의 스트림으로 보낸다 */
    public void deliver(NotificationPush push) {
        Set<SseEmitter> targets = emitters.get(push.userId());
        if (targets == null) {
            return;
        }
        for (SseEmitter emitter : targets) {
            send(push.userId(), emitter, SseEmitter.event()
                    .name("notification")
                    .id(String.valueOf(push.notification().id()))
                    .data(push.notification()));
            send(push.userId(), emitter, unreadCount(push.unreadCount()));
        }
    }

    public int connectionCount(Long userId) {
        return emitters.getOrDefault(userId, Set.of()).size();
    }

    @Scheduled(fixedDelayString = "${tspoon.notification.stream.heartbeat:25s}")
    void heartbeat() {
        emitters.forEach((userId, targets) -> targets.forEach(emitter ->
                send(userId, emitter, SseEmitter.event().comment("keep-alive"))));
    }

    private void send(Long userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            // 클라이언트가 떠났다 — 연결을 정리한다
            log.debug("회원 {} 의 알림 스트림을 닫습니다: {}", userId, e.getMessage());
            remove(userId, emitter);
            emitter.completeWithError(e);
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (id, targets) -> {
            targets.remove(emitter);
            return targets.isEmpty() ? null : targets;
        });
    }

    private static SseEmitter.SseEventBuilder unreadCount(long count) {
        return SseEmitter.event().name("unread-count").data(Map.of("count", count));
    }
}
