package com.tspoon.moderation;

import java.time.Instant;
import java.util.Map;

/**
 * 관리자 신고 목록의 한 줄 — 같은 대상에 대한 신고를 묶어 보여 준다.
 *
 * @param targetStatus 대상의 지금 상태 (자동으로 숨겨졌으면 HIDDEN). 대상이 사라졌으면 null
 * @param reasons      사유별 신고 수
 */
public record ReportedTargetResponse(
        long targetId,
        String targetStatus,
        Long authorId,
        String preview,
        long reportCount,
        Map<ReportReason, Long> reasons,
        Instant lastReportedAt
) {
}
