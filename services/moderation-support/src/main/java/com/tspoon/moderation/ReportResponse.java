package com.tspoon.moderation;

import java.time.Instant;

public record ReportResponse(
        long id,
        long reporterId,
        ReportReason reason,
        String description,
        ReportStatus status,
        Instant createdAt,
        Instant resolvedAt
) {
}
