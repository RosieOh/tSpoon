package com.tspoon.educationservice.event;

import com.tspoon.event.UserEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴한 회원의 콘텐츠는 남기되 작성자를 익명화하고, 북마크는 지운다.
 * 북마크 수는 이번에 지운 행만큼만 줄이므로 같은 이벤트가 다시 와도 두 번 줄지 않는다.
 */
@Component
@RequiredArgsConstructor
public class WithdrawnUserCleaner implements UserEventHandler {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void onWithdrawn(long userId) {
        jdbcTemplate.update("UPDATE education_contents SET author_name = ? WHERE author_id = ? AND author_name <> ?",
                WITHDRAWN_USER_NAME, userId, WITHDRAWN_USER_NAME);
        jdbcTemplate.update("""
                WITH removed AS (DELETE FROM content_bookmarks WHERE user_id = ? RETURNING content_id)
                UPDATE education_contents c SET bookmark_count = GREATEST(c.bookmark_count - r.removed_count, 0)
                FROM (SELECT content_id, count(*) AS removed_count FROM removed GROUP BY content_id) r
                WHERE c.id = r.content_id
                """, userId);
    }
}
