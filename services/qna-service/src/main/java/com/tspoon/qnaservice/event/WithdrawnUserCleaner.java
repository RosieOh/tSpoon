package com.tspoon.qnaservice.event;

import com.tspoon.event.UserEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴한 회원의 질문·답변은 다른 회원에게 도움이 되므로 남기되 작성자를 익명화한다.
 */
@Component
@RequiredArgsConstructor
public class WithdrawnUserCleaner implements UserEventHandler {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void onWithdrawn(long userId) {
        jdbcTemplate.update("UPDATE questions SET author_name = ? WHERE author_id = ? AND author_name <> ?",
                WITHDRAWN_USER_NAME, userId, WITHDRAWN_USER_NAME);
        jdbcTemplate.update("UPDATE answers SET author_name = ? WHERE author_id = ? AND author_name <> ?",
                WITHDRAWN_USER_NAME, userId, WITHDRAWN_USER_NAME);
    }
}
