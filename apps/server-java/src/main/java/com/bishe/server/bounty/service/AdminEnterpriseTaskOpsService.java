package com.bishe.server.bounty.service;

import com.bishe.server.bounty.dto.BountyTaskManageRequest;
import com.bishe.server.bounty.dto.BountyTaskManageResponse;
import com.bishe.server.bounty.repository.AdminBountyOpsRepository;
import com.bishe.server.bounty.repository.BountyRepository;
import com.bishe.server.common.TimePayloads;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.profile.service.EnterpriseLogoUrlSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class AdminEnterpriseTaskOpsService {

    private final AdminBountyOpsRepository adminBountyOpsRepository;
    private final BountyRepository bountyRepository;

    public AdminEnterpriseTaskOpsService(
            AdminBountyOpsRepository adminBountyOpsRepository,
            BountyRepository bountyRepository
    ) {
        this.adminBountyOpsRepository = adminBountyOpsRepository;
        this.bountyRepository = bountyRepository;
    }

    public EnterpriseTaskOpsOverviewPayload getOverview() {
        List<TaskOpsRecord> records = buildTaskOpsRecords(adminBountyOpsRepository.findTaskOps(null, null));
        long riskyTaskCount = records.stream().filter(item -> item.highestRiskLevel() != null).count();
        long highRiskTaskCount = records.stream()
                .filter(item -> "CRITICAL".equals(item.highestRiskLevel()) || "HIGH".equals(item.highestRiskLevel()))
                .count();
        long staleReviewTaskCount = records.stream()
                .filter(item -> hasRiskCode(item.riskSignals(), "STALE_PENDING_REVIEW"))
                .count();
        long deadlinePressureTaskCount = records.stream()
                .filter(item ->
                        hasRiskCode(item.riskSignals(), "OVERDUE_OPEN")
                                || hasRiskCode(item.riskSignals(), "DEADLINE_SOON_EMPTY")
                                || hasRiskCode(item.riskSignals(), "DEADLINE_NEAR_EMPTY"))
                .count();
        long closedWithoutAcceptedTaskCount = records.stream()
                .filter(item -> hasRiskCode(item.riskSignals(), "CLOSED_WITHOUT_ACCEPTED"))
                .count();

        return new EnterpriseTaskOpsOverviewPayload(
                records.size(),
                records.stream().filter(item -> "OPEN".equals(item.status())).count(),
                riskyTaskCount,
                highRiskTaskCount,
                staleReviewTaskCount,
                deadlinePressureTaskCount,
                closedWithoutAcceptedTaskCount
        );
    }

    public EnterpriseTaskOpsListPayload listTasks(int page, int size, String keyword, String status, String riskLevel) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        String normalizedStatus = normalizeStatus(status, List.of("OPEN", "CLOSED"), "status");
        String normalizedRiskLevel = normalizeStatus(riskLevel, List.of("CRITICAL", "HIGH", "MEDIUM", "LOW"), "riskLevel");

        List<TaskOpsRecord> records = buildTaskOpsRecords(adminBountyOpsRepository.findTaskOps(keyword, normalizedStatus));
        if (normalizedRiskLevel != null) {
            records = records.stream()
                    .filter(item -> normalizedRiskLevel.equals(item.highestRiskLevel()))
                    .toList();
        }

        int fromIndex = Math.min((safePage - 1) * safeSize, records.size());
        int toIndex = Math.min(fromIndex + safeSize, records.size());
        List<TaskOpsRecord> pageRecords = records.subList(fromIndex, toIndex);
        return new EnterpriseTaskOpsListPayload(pageRecords, records.size(), safePage, safeSize);
    }

    public TaskOpsRecord getTaskDetail(long taskId) {
        return toTaskOpsRecord(requireTask(taskId));
    }

    public BountyTaskManageResponse manageTask(long taskId, BountyTaskManageRequest request) {
        AdminBountyOpsRepository.AdminTaskOpsRow task = requireTask(taskId);
        String action = normalizeStatus(request.action(), List.of("CLOSE", "REOPEN"), "action");
        if ("REOPEN".equals(action) && task.acceptedSubmissionId() != null) {
            throw new ApiException("BIZ-1001", "accepted task cannot be reopened", HttpStatus.BAD_REQUEST);
        }
        String nextStatus = "REOPEN".equals(action) ? "OPEN" : "CLOSED";
        bountyRepository.updateTaskStatus(taskId, nextStatus);
        AdminBountyOpsRepository.AdminTaskOpsRow updated = requireTask(taskId);
        return new BountyTaskManageResponse(taskId, updated.status(), toTimePayload(updated.updatedAt()));
    }

    private List<TaskOpsRecord> buildTaskOpsRecords(List<AdminBountyOpsRepository.AdminTaskOpsRow> rows) {
        return rows.stream()
                .map(this::toTaskOpsRecord)
                .sorted(Comparator.comparing(TaskOpsRecord::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(TaskOpsRecord::taskId, Comparator.reverseOrder()))
                .toList();
    }

    private TaskOpsRecord toTaskOpsRecord(AdminBountyOpsRepository.AdminTaskOpsRow row) {
        List<RiskSignalItem> riskSignals = buildRiskSignals(row);
        String highestRiskLevel = riskSignals.stream()
                .map(RiskSignalItem::level)
                .min(Comparator.comparingInt(this::riskOrder))
                .orElse(null);
        int reviewedSubmissionCount = row.acceptedSubmissionCount() + row.rejectedSubmissionCount();

        return new TaskOpsRecord(
                row.taskId(),
                row.enterpriseUserId(),
                row.enterpriseName(),
                resolveEnterpriseLogoUrl(row),
                row.enterpriseApprovalStatus(),
                row.title(),
                summarize(row.description(), 100),
                summarize(row.description(), 240),
                row.rewardDescription(),
                row.status(),
                row.acceptedSubmissionId(),
                row.submissionCount(),
                row.pendingSubmissionCount(),
                row.acceptedSubmissionCount(),
                row.rejectedSubmissionCount(),
                reviewedSubmissionCount,
                toTimePayload(row.deadlineAt()),
                toTimePayload(row.closedAt()),
                toTimePayload(row.createdAt()),
                toTimePayload(row.updatedAt()),
                toTimePayload(row.latestSubmissionAt()),
                highestRiskLevel,
                riskSignals
        );
    }

    private List<RiskSignalItem> buildRiskSignals(AdminBountyOpsRepository.AdminTaskOpsRow row) {
        Instant now = Instant.now();
        List<RiskSignalItem> signals = new ArrayList<>();

        if ("OPEN".equals(row.status()) && row.deadlineAt() != null) {
            if (row.deadlineAt().isBefore(now)) {
                signals.add(new RiskSignalItem(
                        "OVERDUE_OPEN",
                        "CRITICAL",
                        "已超截止仍开放",
                        "任务截止时间已过，但任务仍处于开放状态，平台应确认是否需要强制关闭或协助企业处理。"
                ));
            } else if (row.submissionCount() == 0) {
                long hoursUntilDeadline = Math.max(0, Duration.between(now, row.deadlineAt()).toHours());
                if (hoursUntilDeadline <= 24) {
                    signals.add(new RiskSignalItem(
                            "DEADLINE_SOON_EMPTY",
                            "HIGH",
                            "24 小时内截止且零提交",
                            "任务即将截止但仍无任何学生提交，需确认需求描述、奖励设置或是否需要平台介入。"
                    ));
                } else if (hoursUntilDeadline <= 72) {
                    signals.add(new RiskSignalItem(
                            "DEADLINE_NEAR_EMPTY",
                            "MEDIUM",
                            "72 小时内截止且零提交",
                            "任务进入临期阶段但尚未收到提交，可优先纳入巡检队列。"
                    ));
                }
            }
        }

        if ("CLOSED".equals(row.status()) && row.acceptedSubmissionId() == null && row.submissionCount() > 0) {
            signals.add(new RiskSignalItem(
                    "CLOSED_WITHOUT_ACCEPTED",
                    "HIGH",
                    "已关闭但未采纳结果",
                    "任务已关闭且存在学生提交，但没有明确采纳结果，可能导致双方预期不一致。"
            ));
        }

        if ("OPEN".equals(row.status())
                && row.pendingSubmissionCount() >= 3
                && row.latestSubmissionAt() != null
                && row.latestSubmissionAt().isBefore(now.minus(72, ChronoUnit.HOURS))) {
            signals.add(new RiskSignalItem(
                    "STALE_PENDING_REVIEW",
                    "HIGH",
                    "提交积压超过 72 小时",
                    "任务已有多份待处理提交，且最近一次提交超过 72 小时未见收口，建议平台跟进企业处理节奏。"
            ));
        }

        if ("OPEN".equals(row.status()) && !"APPROVED".equals(row.enterpriseApprovalStatus())) {
            signals.add(new RiskSignalItem(
                    "UNAPPROVED_ENTERPRISE_OPEN_TASK",
                    "MEDIUM",
                    "企业认证异常仍有开放任务",
                    "当前企业认证不是已通过状态，但任务仍对外开放，建议平台确认是否需要临时下线。"
            ));
        }

        if ("OPEN".equals(row.status())
                && row.submissionCount() == 0
                && row.createdAt() != null
                && row.createdAt().isBefore(now.minus(7, ChronoUnit.DAYS))) {
            signals.add(new RiskSignalItem(
                    "LONG_RUNNING_WITHOUT_SUBMISSION",
                    "LOW",
                    "长期开放但零提交",
                    "任务已开放超过 7 天仍无任何提交，可作为平台巡检的低优先级关注项。"
            ));
        }

        return signals.stream()
                .sorted(Comparator.comparingInt(item -> riskOrder(item.level())))
                .toList();
    }

    private AdminBountyOpsRepository.AdminTaskOpsRow requireTask(long taskId) {
        return adminBountyOpsRepository.findTaskOpsById(taskId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "task not found", HttpStatus.NOT_FOUND));
    }

    private boolean hasRiskCode(List<RiskSignalItem> signals, String code) {
        return signals.stream().anyMatch(item -> code.equals(item.code()));
    }

    private int riskOrder(String level) {
        if ("CRITICAL".equals(level)) {
            return 0;
        }
        if ("HIGH".equals(level)) {
            return 1;
        }
        if ("MEDIUM".equals(level)) {
            return 2;
        }
        return 3;
    }

    private String normalizeStatus(String value, List<String> allowedValues, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowedValues.contains(normalized)) {
            throw new ApiException("BIZ-1001", fieldName + " is invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String summarize(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "暂无描述";
        }
        String normalized = text.replace('\r', '\n')
                .replaceAll("\\n+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 1)).trim() + "…";
    }

    private String resolveEnterpriseLogoUrl(AdminBountyOpsRepository.AdminTaskOpsRow row) {
        if (row.enterpriseLogoObjectKey() == null || row.enterpriseLogoObjectKey().isBlank()) {
            return null;
        }
        return EnterpriseLogoUrlSupport.buildPublicLogoUrl(row.enterpriseUserId(), row.enterpriseLogoUpdatedAt());
    }

    private Long toTimePayload(Instant value) {
        return TimePayloads.toEpochMillis(value);
    }

    public record EnterpriseTaskOpsOverviewPayload(
            long totalTaskCount,
            long openTaskCount,
            long riskyTaskCount,
            long highRiskTaskCount,
            long staleReviewTaskCount,
            long deadlinePressureTaskCount,
            long closedWithoutAcceptedTaskCount
    ) {
    }

    public record EnterpriseTaskOpsListPayload(
            List<TaskOpsRecord> records,
            int total,
            int page,
            int size
    ) {
    }

    public record TaskOpsRecord(
            long taskId,
            long enterpriseUserId,
            String enterpriseName,
            String enterpriseLogoUrl,
            String enterpriseApprovalStatus,
            String title,
            String descriptionSummary,
            String descriptionPreview,
            String rewardDescription,
            String status,
            Long acceptedSubmissionId,
            int submissionCount,
            int pendingSubmissionCount,
            int acceptedSubmissionCount,
            int rejectedSubmissionCount,
            int reviewedSubmissionCount,
            Long deadlineAt,
            Long closedAt,
            Long createdAt,
            Long updatedAt,
            Long latestSubmissionAt,
            String highestRiskLevel,
            List<RiskSignalItem> riskSignals
    ) {
    }

    public record RiskSignalItem(
            String code,
            String level,
            String label,
            String description
    ) {
    }
}
