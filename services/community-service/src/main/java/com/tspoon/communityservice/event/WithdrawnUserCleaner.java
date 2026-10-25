package com.tspoon.communityservice.event;

import com.tspoon.event.UserEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴한 회원의 게시글·댓글은 대화가 끊기지 않도록 남기되 작성자를 익명화하고, 좋아요는 지운다.
 * 좋아요 수는 이번에 지운 행만큼만 줄이므로 같은 이벤트가 다시 와도 두 번 줄지 않는다.
 */
@Component
@RequiredArgsConstructor
public class WithdrawnUserCleaner implements UserEventHandler {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void onWithdrawn(long userId) {
        jdbcTemplate.update("UPDATE boards SET author_name = ? WHERE author_id = ? AND author_name <> ?",
                WITHDRAWN_USER_NAME, userId, WITHDRAWN_USER_NAME);
        jdbcTemplate.update("UPDATE comments SET author_name = ? WHERE author_id = ? AND author_name <> ?",
                WITHDRAWN_USER_NAME, userId, WITHDRAWN_USER_NAME);
        jdbcTemplate.update("""
                WITH removed AS (DELETE FROM board_likes WHERE user_id = ? RETURNING board_id)
                UPDATE boards b SET like_count = GREATEST(b.like_count - r.removed_count, 0)
                FROM (SELECT board_id, count(*) AS removed_count FROM removed GROUP BY board_id) r
                WHERE b.id = r.board_id
                """, userId);
    }
}
