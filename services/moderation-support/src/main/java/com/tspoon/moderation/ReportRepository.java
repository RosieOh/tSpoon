package com.tspoon.moderation;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * reports 테이블. 같은 회원은 한 대상을 한 번만 신고할 수 있다 (target_type, target_id, reporter_id 유니크).
 */
@RequiredArgsConstructor
public class ReportRepository {

    private final JdbcTemplate jdbcTemplate;

    /** 처음 신고면 true. 이미 신고했으면 (동시 요청 포함) 유니크 제약으로 걸러 false */
    public boolean insert(String targetType, long targetId, long reporterId, ReportReason reason, String description) {
        return jdbcTemplate.update("""
                        INSERT INTO reports (target_type, target_id, reporter_id, reason, description, status, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (target_type, target_id, reporter_id) DO NOTHING
                        """,
                targetType, targetId, reporterId, reason.name(), description, ReportStatus.PENDING.name(),
                Timestamp.from(Instant.now())) == 1;
    }

    public long countPending(String targetType, long targetId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reports WHERE target_type = ? AND target_id = ? AND status = ?",
                Long.class, targetType, targetId, ReportStatus.PENDING.name());
        return count == null ? 0 : count;
    }

    /** 대상별로 묶은 신고 — 마지막 신고가 최근인 대상부터 */
    public Page<ReportedTarget> findTargets(String targetType, ReportStatus status, Pageable pageable) {
        List<ReportedTarget> targets = jdbcTemplate.query("""
                        SELECT target_id, count(*) AS report_count, max(created_at) AS last_reported_at
                        FROM reports WHERE target_type = ? AND status = ?
                        GROUP BY target_id ORDER BY last_reported_at DESC, target_id DESC
                        LIMIT ? OFFSET ?
                        """,
                (rs, row) -> new ReportedTarget(rs.getLong("target_id"), rs.getLong("report_count"),
                        rs.getTimestamp("last_reported_at").toInstant()),
                targetType, status.name(), pageable.getPageSize(), pageable.getOffset());
        Long total = jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT target_id) FROM reports WHERE target_type = ? AND status = ?",
                Long.class, targetType, status.name());
        return new PageImpl<>(targets, pageable, total == null ? 0 : total);
    }

    public Map<Long, Map<ReportReason, Long>> countReasons(String targetType, ReportStatus status, Collection<Long> targetIds) {
        Map<Long, Map<ReportReason, Long>> reasons = new HashMap<>();
        if (targetIds.isEmpty()) {
            return reasons;
        }
        jdbcTemplate.query(connection -> {
            var statement = connection.prepareStatement("""
                    SELECT target_id, reason, count(*) AS report_count FROM reports
                    WHERE target_type = ? AND status = ? AND target_id = ANY (?)
                    GROUP BY target_id, reason
                    """);
            Array ids = connection.createArrayOf("bigint", targetIds.toArray());
            statement.setString(1, targetType);
            statement.setString(2, status.name());
            statement.setArray(3, ids);
            return statement;
        }, (ResultSet rs) -> {
            reasons.computeIfAbsent(rs.getLong("target_id"), id -> new EnumMap<>(ReportReason.class))
                    .put(ReportReason.valueOf(rs.getString("reason")), rs.getLong("report_count"));
        });
        return reasons;
    }

    public List<ReportResponse> findByTarget(String targetType, long targetId) {
        return jdbcTemplate.query("""
                        SELECT id, reporter_id, reason, description, status, created_at, resolved_at FROM reports
                        WHERE target_type = ? AND target_id = ? ORDER BY created_at DESC, id DESC
                        """,
                (rs, row) -> toResponse(rs), targetType, targetId);
    }

    /** 대상의 신고 중 from 상태인 것을 to 로 바꾼다 */
    public int resolve(String targetType, long targetId, Collection<ReportStatus> from, ReportStatus to, long adminId) {
        return jdbcTemplate.update("""
                        UPDATE reports SET status = ?, resolved_at = ?, resolved_by = ?
                        WHERE target_type = ? AND target_id = ? AND status = ANY (?)
                        """,
                ps -> {
                    ps.setString(1, to.name());
                    ps.setTimestamp(2, Timestamp.from(Instant.now()));
                    ps.setLong(3, adminId);
                    ps.setString(4, targetType);
                    ps.setLong(5, targetId);
                    ps.setArray(6, ps.getConnection().createArrayOf("varchar",
                            from.stream().map(ReportStatus::name).toArray()));
                });
    }

    private static ReportResponse toResponse(ResultSet rs) throws SQLException {
        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        return new ReportResponse(rs.getLong("id"), rs.getLong("reporter_id"), ReportReason.valueOf(rs.getString("reason")),
                rs.getString("description"), ReportStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(), resolvedAt == null ? null : resolvedAt.toInstant());
    }

    public record ReportedTarget(long targetId, long reportCount, Instant lastReportedAt) {
    }
}
