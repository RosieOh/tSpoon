package com.tspoon.moderation;

import com.tspoon.auth.AuthUser;
import com.tspoon.core.exception.BusinessException;
import com.tspoon.core.exception.ResourceNotFoundException;
import com.tspoon.moderation.ModerationTarget.Snapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 신고 접수와 관리자 검토.
 * <ul>
 *   <li>공개 중인 남의 글만 신고할 수 있고, 같은 회원은 한 번만 신고할 수 있다</li>
 *   <li>서로 다른 회원의 검토 대기 신고가 기준 수에 닿으면 관리자 검토 전이라도 숨긴다 (신고는 검토 대기로 남는다)</li>
 *   <li>관리자는 숨김을 확정하거나(ACCEPTED), 기각해 다시 공개한다(DISMISSED). 기각된 신고는 다시 기준에 들어가지 않는다</li>
 * </ul>
 */
@Slf4j
@Transactional
public class ModerationService {

    private final Map<String, ModerationTarget> targets;
    private final ReportRepository reportRepository;
    private final ModerationProperties properties;

    public ModerationService(List<ModerationTarget> targets, ReportRepository reportRepository, ModerationProperties properties) {
        this.targets = targets.stream().collect(Collectors.toUnmodifiableMap(ModerationTarget::collection, Function.identity()));
        this.reportRepository = reportRepository;
        this.properties = properties;
    }

    public void report(String collection, long targetId, AuthUser reporter, ReportRequest request) {
        ModerationTarget target = target(collection);
        // 대상 행을 잠가 같은 대상에 대한 동시 신고를 줄 세운다 — 그래야 기준 수에 닿는 순간을 놓치지 않는다
        Snapshot snapshot = target.lockVisible(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("신고할 글을 찾을 수 없습니다."));
        if (reporter.userId().equals(snapshot.authorId())) {
            throw new BusinessException("자신이 쓴 글은 신고할 수 없습니다.");
        }
        if (!reportRepository.insert(collection, targetId, reporter.userId(), request.reason(), request.description())) {
            throw new BusinessException("이미 신고한 글입니다.", HttpStatus.CONFLICT);
        }

        long pending = reportRepository.countPending(collection, targetId);
        if (pending >= properties.autoHideThreshold()) {
            target.hide(targetId);
            log.info("신고 {}건이 쌓여 {} {} 을(를) 숨겼습니다.", pending, collection, targetId);
        }
    }

    @Transactional(readOnly = true)
    public Page<ReportedTargetResponse> reportedTargets(String collection, ReportStatus status, int page, int size) {
        ModerationTarget target = target(collection);
        Page<ReportRepository.ReportedTarget> grouped = reportRepository.findTargets(collection, status, PageRequest.of(page, size));
        Map<Long, Map<ReportReason, Long>> reasons = reportRepository.countReasons(collection, status,
                grouped.map(ReportRepository.ReportedTarget::targetId).getContent());

        return grouped.map(reported -> {
            Snapshot snapshot = target.find(reported.targetId()).orElse(null);
            return new ReportedTargetResponse(reported.targetId(),
                    snapshot == null ? null : snapshot.status(),
                    snapshot == null ? null : snapshot.authorId(),
                    snapshot == null ? null : snapshot.preview(),
                    reported.reportCount(), reasons.getOrDefault(reported.targetId(), Map.of()), reported.lastReportedAt());
        });
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> reports(String collection, long targetId) {
        target(collection);
        return reportRepository.findByTarget(collection, targetId);
    }

    /** 숨기고, 검토 대기 신고를 확정한다. 신고가 없어도 관리자는 숨길 수 있다 */
    public void hide(String collection, long targetId, AuthUser admin) {
        ModerationTarget target = existingTarget(collection, targetId);
        target.hide(targetId);
        reportRepository.resolve(collection, targetId, EnumSet.of(ReportStatus.PENDING), ReportStatus.ACCEPTED, admin.userId());
    }

    /** 신고를 기각하고, 숨겨져 있으면 다시 공개한다 */
    public void dismiss(String collection, long targetId, AuthUser admin) {
        ModerationTarget target = existingTarget(collection, targetId);
        target.restore(targetId);
        reportRepository.resolve(collection, targetId, EnumSet.of(ReportStatus.PENDING, ReportStatus.ACCEPTED),
                ReportStatus.DISMISSED, admin.userId());
    }

    private ModerationTarget existingTarget(String collection, long targetId) {
        ModerationTarget target = target(collection);
        target.find(targetId).orElseThrow(() -> new ResourceNotFoundException("대상을 찾을 수 없습니다."));
        return target;
    }

    private ModerationTarget target(String collection) {
        ModerationTarget target = targets.get(collection);
        if (target == null) {
            throw new ResourceNotFoundException("신고할 수 없는 종류입니다: " + collection);
        }
        return target;
    }
}
