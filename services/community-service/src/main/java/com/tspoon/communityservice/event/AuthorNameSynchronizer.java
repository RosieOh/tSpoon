package com.tspoon.communityservice.event;

import com.tspoon.event.UserEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 이름이 바뀌면 이 서비스에 복사해 둔 이름(게시글·댓글 작성자, 좋아요한 회원)을 함께 바꾼다.
 * 이름만 바꾸므로 수정 시각은 건드리지 않고, 같은 이벤트가 다시 와도 결과가 같다.
 */
@Component
@RequiredArgsConstructor
public class AuthorNameSynchronizer implements UserEventHandler {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void onProfileUpdated(long userId, String name) {
        jdbcTemplate.update("UPDATE boards SET author_name = ? WHERE author_id = ? AND author_name <> ?", name, userId, name);
        jdbcTemplate.update("UPDATE comments SET author_name = ? WHERE author_id = ? AND author_name <> ?", name, userId, name);
        jdbcTemplate.update("UPDATE board_likes SET user_name = ? WHERE user_id = ? AND user_name <> ?", name, userId, name);
    }
}
