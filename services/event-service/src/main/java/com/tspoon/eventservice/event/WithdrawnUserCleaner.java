package com.tspoon.eventservice.event;

import com.tspoon.event.UserEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 탈퇴한 회원의 아직 시작하지 않은 행사 신청은 취소해 자리를 돌려주고, 신청 내역의 이름·연락처·메모는 지운다.
 * 주최한 행사는 신청자가 있을 수 있으므로 남기고 주최자 이름만 익명화한다.
 * 자리는 이번에 취소한 신청만큼만 돌려주므로 같은 이벤트가 다시 와도 두 번 돌려주지 않는다.
 */
@Component
@RequiredArgsConstructor
public class WithdrawnUserCleaner implements UserEventHandler {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Override
    @Transactional
    public void onWithdrawn(long userId) {
        jdbcTemplate.update("""
                WITH cancelled AS (
                    UPDATE event_registrations r SET status = 'CANCELLED'
                    FROM events e
                    WHERE r.event_id = e.id AND r.user_id = ? AND r.status = 'REGISTERED' AND e.start_date > ?
                    RETURNING r.event_id
                )
                UPDATE events e SET
                    current_participants = GREATEST(e.current_participants - c.cancelled_count, 0),
                    status = CASE WHEN e.status = 'FULL' THEN 'ACTIVE' ELSE e.status END
                FROM (SELECT event_id, count(*) AS cancelled_count FROM cancelled GROUP BY event_id) c
                WHERE e.id = c.event_id
                """, userId, Timestamp.valueOf(LocalDateTime.now(clock)));
        jdbcTemplate.update("""
                UPDATE event_registrations SET user_name = ?, contact_info = NULL, notes = NULL
                WHERE user_id = ? AND (user_name <> ? OR contact_info IS NOT NULL OR notes IS NOT NULL)
                """, WITHDRAWN_USER_NAME, userId, WITHDRAWN_USER_NAME);
        jdbcTemplate.update("UPDATE events SET organizer_name = ? WHERE organizer_id = ? AND organizer_name <> ?",
                WITHDRAWN_USER_NAME, userId, WITHDRAWN_USER_NAME);
    }
}
