package com.tspoon.educationservice.recommendation;

import com.tspoon.event.UserEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * 회원별 자녀 학년 — user-service 를 부르지 않고 추천하려고 이벤트로 받아 둔 읽기 모델.
 * 이벤트에는 지금 자녀들의 학년 전체가 오므로 그대로 덮어쓰되, 발생 시각이 더 이른 이벤트는 무시한다.
 * 탈퇴하면 지운다 (자녀 정보도 개인정보다).
 */
@Component
@RequiredArgsConstructor
public class ChildGradeProjection implements UserEventHandler {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void onChildrenChanged(long userId, List<String> grades, Instant occurredAt) {
        jdbcTemplate.update("""
                INSERT INTO member_child_grades (user_id, grades, occurred_at) VALUES (?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET grades = EXCLUDED.grades, occurred_at = EXCLUDED.occurred_at
                WHERE member_child_grades.occurred_at <= EXCLUDED.occurred_at
                """, ps -> {
            ps.setLong(1, userId);
            ps.setArray(2, ps.getConnection().createArrayOf("varchar", grades.toArray()));
            ps.setTimestamp(3, Timestamp.from(occurredAt));
        });
    }

    @Override
    @Transactional
    public void onWithdrawn(long userId) {
        jdbcTemplate.update("DELETE FROM member_child_grades WHERE user_id = ?", userId);
    }

    public List<String> gradesOf(long userId) {
        List<List<String>> found = jdbcTemplate.query("SELECT grades FROM member_child_grades WHERE user_id = ?",
                (rs, row) -> {
                    Array grades = rs.getArray("grades");
                    return Arrays.stream((Object[]) grades.getArray()).map(String::valueOf).toList();
                }, userId);
        return found.isEmpty() ? List.of() : found.getFirst();
    }
}
