package com.tspoon.eventservice.event;

import com.tspoon.event.UserEventHandler;
import com.tspoon.eventservice.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴한 회원의 아직 시작하지 않은 행사 신청(확정·대기)은 취소하고 — 확정이었으면 자리를 다음 대기자에게 넘기거나 돌려준다 —
 * 신청 내역의 이름·연락처·메모는 지운다. 주최한 행사는 신청자가 있을 수 있으므로 남기고 주최자 이름만 익명화한다.
 * 이미 취소한 신청은 건너뛰므로 같은 이벤트가 다시 와도 자리를 두 번 돌려주지 않는다.
 */
@Component
@RequiredArgsConstructor
public class WithdrawnUserCleaner implements UserEventHandler {

    private final JdbcTemplate jdbcTemplate;
    private final EventService eventService;

    @Override
    @Transactional
    public void onWithdrawn(long userId) {
        eventService.cancelUpcomingRegistrationsOf(userId);
        jdbcTemplate.update("""
                UPDATE event_registrations SET user_name = ?, contact_info = NULL, notes = NULL
                WHERE user_id = ? AND (user_name <> ? OR contact_info IS NOT NULL OR notes IS NOT NULL)
                """, WITHDRAWN_USER_NAME, userId, WITHDRAWN_USER_NAME);
        jdbcTemplate.update("UPDATE events SET organizer_name = ? WHERE organizer_id = ? AND organizer_name <> ?",
                WITHDRAWN_USER_NAME, userId, WITHDRAWN_USER_NAME);
    }
}
