package com.bishe.server.bounty.service;

import com.bishe.server.bounty.dto.BountyEnterpriseTaskCenterResponse;
import com.bishe.server.bounty.dto.BountySubmissionCreateRequest;
import com.bishe.server.bounty.dto.BountySubmissionCreateResponse;
import com.bishe.server.bounty.dto.BountySubmissionHistoryItem;
import com.bishe.server.bounty.dto.BountySubmissionListResponse;
import com.bishe.server.bounty.dto.BountySubmissionReviewRequest;
import com.bishe.server.bounty.dto.BountySubmissionReviewResponse;
import com.bishe.server.bounty.dto.BountyStudentAvatarPayload;
import com.bishe.server.bounty.dto.BountyTaskCreateRequest;
import com.bishe.server.bounty.dto.BountyTaskCreateResponse;
import com.bishe.server.bounty.dto.BountyTaskDetailResponse;
import com.bishe.server.bounty.dto.BountyTaskListResponse;
import com.bishe.server.bounty.dto.BountyTaskManageRequest;
import com.bishe.server.bounty.dto.BountyTaskManageResponse;
import com.bishe.server.bounty.dto.BountyTaskUpdateRequest;
import com.bishe.server.bounty.dto.BountyTaskUpdateResponse;
import com.bishe.server.bounty.repository.BountyRepository;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.common.util.TextListCodec;
import com.bishe.server.notification.model.NotificationCategory;
import com.bishe.server.notification.model.NotificationPriority;
import com.bishe.server.notification.service.NotificationService;
import com.bishe.server.notification.service.PlatformNotificationPublishService;
import com.bishe.server.profile.dto.StudentProfileResponse;
import com.bishe.server.profile.dto.StudentProfileSocialLinkItem;
import com.bishe.server.profile.repository.EnterpriseProfileRepository;
import com.bishe.server.profile.repository.StudentProfileRepository;
import com.bishe.server.profile.service.EnterpriseLogoUrlSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class BountyService {

    private static final TypeReference<List<StudentProfileResponse.PortraitTagItem>> PORTRAIT_TAG_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<StudentProfileSocialLinkItem>> SOCIAL_LINK_LIST_TYPE = new TypeReference<>() {
    };

    private final BountyRepository bountyRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final BountyTaskListCacheService bountyTaskListCacheService;
    private final BountyTaskDetailCacheService bountyTaskDetailCacheService;

    public BountyService(
            BountyRepository bountyRepository,
            StudentProfileRepository studentProfileRepository,
            EnterpriseProfileRepository enterpriseProfileRepository,
            NotificationService notificationService,
            ObjectMapper objectMapper,
            BountyTaskListCacheService bountyTaskListCacheService,
            BountyTaskDetailCacheService bountyTaskDetailCacheService
    ) {
        this.bountyRepository = bountyRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.enterpriseProfileRepository = enterpriseProfileRepository;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.bountyTaskListCacheService = bountyTaskListCacheService;
        this.bountyTaskDetailCacheService = bountyTaskDetailCacheService;
    }

    @Transactional
    public BountyTaskCreateResponse createTask(long enterpriseUserId, BountyTaskCreateRequest request) {
        // 发布前先校验企业认证，避免未通过主体绕过前端直接发任务。
        ensureEnterpriseCanPublishTasks(enterpriseUserId, false);
        String title = requireText(request.title(), "title");
        String description = requireText(request.description(), "description");
        String rewardDescription = requireText(request.rewardDescription(), "rewardDescription");
        Instant deadlineAt = parseOptionalInstant(request.deadlineAt(), "deadlineAt");
        long taskId = bountyRepository.createTask(enterpriseUserId, title, description, rewardDescription, deadlineAt);
        evictBountyTaskListCaches();
        BountyRepository.TaskRow task = requireTask(taskId, enterpriseUserId);
        return new BountyTaskCreateResponse(taskId, task.status(), toIso(task.createdAt()));
    }

    @Transactional
    public BountyTaskUpdateResponse updateTask(long enterpriseUserId, long taskId, BountyTaskUpdateRequest request) {
        BountyRepository.TaskRow task = requireTask(taskId, enterpriseUserId);
        ensureTaskOwner(task, enterpriseUserId);
        if (task.acceptedSubmissionId() != null) {
            throw new ApiException("BIZ-1001", "accepted task cannot be edited", HttpStatus.BAD_REQUEST);
        }
        String title = requireText(request.title(), "title");
        String description = requireText(request.description(), "description");
        String rewardDescription = requireText(request.rewardDescription(), "rewardDescription");
        Instant deadlineAt = parseOptionalInstant(request.deadlineAt(), "deadlineAt");
        bountyRepository.updateTask(taskId, title, description, rewardDescription, deadlineAt);
        evictBountyTaskCaches(taskId);
        BountyRepository.TaskRow updated = requireTask(taskId, enterpriseUserId);
        return new BountyTaskUpdateResponse(updated.taskId(), updated.status(), toIso(updated.updatedAt()));
    }

    public BountyTaskListResponse listTasks(long viewerUserId, String viewerRole, int page, int size, String keyword, String status, boolean mineOnly) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        // 企业“只看我的”不走公共缓存，因为它本身就是私有工作台视角。
        Long enterpriseUserId = mineOnly && "ENTERPRISE".equals(viewerRole) ? viewerUserId : null;
        String normalizedStatus = normalizeStatus(status, List.of("OPEN", "CLOSED"), "status");
        if (enterpriseUserId != null) {
            return loadTaskList(viewerUserId, keyword, normalizedStatus, enterpriseUserId, safePage, safeSize);
        }
        // 公共任务列表可缓存；当前学生是否已提交等 viewer 态在返回前再合成。
        BountyTaskListResponse publicSnapshot = bountyTaskListCacheService.getTaskList(
                keyword,
                normalizedStatus,
                safePage,
                safeSize,
                () -> loadTaskList(0L, keyword, normalizedStatus, null, safePage, safeSize)
        );
        return mergeSubmittedState(viewerUserId, viewerRole, publicSnapshot);
    }

    public BountyTaskDetailResponse getTaskDetail(long viewerUserId, String viewerRole, long taskId) {
        // 详情主体按公共任务缓存，mine/mySubmission 等 viewer 态在返回前再合成。
        BountyTaskDetailResponse publicSnapshot = bountyTaskDetailCacheService.getTaskDetail(
                taskId,
                () -> toPublicTaskDetail(requireTask(taskId, 0L))
        );
        return mergeTaskDetailViewerState(viewerUserId, viewerRole, taskId, publicSnapshot);
    }

    public BountyEnterpriseTaskCenterResponse getEnterpriseTaskCenter(long enterpriseUserId) {
        List<BountyRepository.TaskRow> tasks = bountyRepository.findTasksByEnterpriseUserId(enterpriseUserId);
        if (tasks.isEmpty()) {
            return new BountyEnterpriseTaskCenterResponse(List.of(), 0);
        }

        List<Long> taskIds = tasks.stream().map(BountyRepository.TaskRow::taskId).toList();
        Map<Long, List<BountyRepository.SubmissionRow>> submissionsByTaskId = new LinkedHashMap<>();
        // 企业中心一次性拉回任务下的提交，用内存聚合生成待处理/已联系/已审核摘要。
        for (BountyRepository.SubmissionRow row : bountyRepository.findSubmissionsByTaskIds(taskIds)) {
            submissionsByTaskId.computeIfAbsent(row.taskId(), key -> new ArrayList<>()).add(row);
        }

        List<BountyEnterpriseTaskCenterResponse.TaskCenterTaskItem> records = new ArrayList<>();
        for (BountyRepository.TaskRow task : tasks) {
            List<BountyRepository.SubmissionRow> taskSubmissions = submissionsByTaskId.getOrDefault(task.taskId(), List.of());
            int pendingCount = 0;
            int contactedCount = 0;
            int reviewedCount = 0;
            for (BountyRepository.SubmissionRow submission : taskSubmissions) {
                if (isPendingSubmissionStatus(submission.status())) {
                    pendingCount += 1;
                }
                if ("ACCEPTED".equals(submission.status())) {
                    contactedCount += 1;
                    reviewedCount += 1;
                } else if ("REJECTED".equals(submission.status())) {
                    reviewedCount += 1;
                }
            }

            List<BountyEnterpriseTaskCenterResponse.SnapshotSubmissionItem> recentSubmissions = taskSubmissions.stream()
                    .limit(6)
                    .map(this::toSubmissionView)
                    .map(this::toTaskCenterSnapshotSubmissionItem)
                    .toList();

            records.add(new BountyEnterpriseTaskCenterResponse.TaskCenterTaskItem(
                    task.taskId(),
                    task.enterpriseUserId(),
                    task.enterpriseName(),
                    resolveEnterpriseLogoUrl(task),
                    task.title(),
                    summarize(task.description(), 120),
                    task.rewardDescription(),
                    task.status(),
                    task.submissionCount(),
                    task.acceptedSubmissionId(),
                    toIso(task.deadlineAt()),
                    toIso(task.createdAt()),
                    toIso(task.updatedAt()),
                    pendingCount,
                    contactedCount,
                    reviewedCount,
                    recentSubmissions
            ));
        }
        return new BountyEnterpriseTaskCenterResponse(records, records.size());
    }

    @Transactional
    public BountySubmissionCreateResponse createSubmission(long studentUserId, long taskId, BountySubmissionCreateRequest request) {
        BountyRepository.TaskRow task = requireTask(taskId, studentUserId);
        if (!"OPEN".equals(task.status())) {
            throw new ApiException("BIZ-1001", "task is closed", HttpStatus.BAD_REQUEST);
        }
        if (bountyRepository.findSubmissionByTaskAndStudent(taskId, studentUserId).isPresent()) {
            throw new ApiException("BIZ-1001", "submission already exists", HttpStatus.BAD_REQUEST);
        }

        String contentText = TextListCodec.normalizeText(request.contentText());
        List<String> attachmentLinks = TextListCodec.normalize(request.attachmentLinks());
        if (contentText == null && attachmentLinks.isEmpty()) {
            throw new ApiException("BIZ-1001", "contentText or attachmentLinks is required", HttpStatus.BAD_REQUEST);
        }
        long submissionId = bountyRepository.createSubmission(taskId, studentUserId, contentText, TextListCodec.join(attachmentLinks));
        evictBountyTaskCaches(taskId);
        BountyRepository.SubmissionRow submission = bountyRepository.findSubmissionById(submissionId)
                .orElseThrow(() -> new IllegalStateException("created submission not found"));
        // 提交事件单独留痕，企业审核工作区可以展示完整处理历史。
        bountyRepository.createSubmissionEvent(
                submissionId,
                taskId,
                studentUserId,
                "SUBMITTED",
                null,
                null,
                null,
                summarizeSubmission(contentText, attachmentLinks),
                submission.createdAt()
        );
        notificationService.createNotification(
                task.enterpriseUserId(),
                "BOUNTY_SUBMITTED",
                "有新的学生成果提交到悬赏任务，请及时审核。",
                String.valueOf(taskId)
        );
        return new BountySubmissionCreateResponse(submissionId, taskId, submission.status(), toIso(submission.createdAt()));
    }

    public BountySubmissionListResponse listTaskSubmissions(
            long enterpriseUserId,
            long taskId,
            int page,
            int size,
            String status,
            String portraitTag,
            Long minCommunityScore7d
    ) {
        BountyRepository.TaskRow task = requireTask(taskId, enterpriseUserId);
        ensureTaskOwner(task, enterpriseUserId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        String normalizedStatus = normalizeStatus(status, List.of("SUBMITTED", "REVIEWING", "ACCEPTED", "REJECTED"), "status");
        List<String> portraitFilters = TextListCodec.normalize(TextListCodec.split(portraitTag));

        // 画像标签和社区分属于展示筛选，先取任务全部提交再做内存过滤。
        List<SubmissionView> allRecords = bountyRepository.findSubmissionsByTaskId(taskId, normalizedStatus)
                .stream()
                .map(this::toSubmissionView)
                .filter(item -> matchesPortraitTags(item, portraitFilters))
                .filter(item -> minCommunityScore7d == null || item.communityScore7d() >= minCommunityScore7d)
                .sorted(Comparator.comparing(SubmissionView::createdAt).reversed())
                .toList();

        int fromIndex = Math.min((safePage - 1) * safeSize, allRecords.size());
        int toIndex = Math.min(fromIndex + safeSize, allRecords.size());
        List<SubmissionView> pageRecords = allRecords.subList(fromIndex, toIndex);
        Map<Long, List<BountySubmissionHistoryItem>> historyMap = loadSubmissionHistoryMap(
                pageRecords.stream().map(SubmissionView::submissionId).toList()
        );
        List<BountySubmissionListResponse.SubmissionItem> records = pageRecords
                .stream()
                .map(item -> toSubmissionItem(item, historyMap.get(item.submissionId())))
                .toList();
        return new BountySubmissionListResponse(records, allRecords.size(), safePage, safeSize);
    }

    @Transactional
    public BountySubmissionReviewResponse reviewSubmission(long enterpriseUserId, long submissionId, BountySubmissionReviewRequest request) {
        BountyRepository.SubmissionRow submission = bountyRepository.findSubmissionById(submissionId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "submission not found", HttpStatus.NOT_FOUND));
        BountyRepository.TaskRow task = requireTask(submission.taskId(), enterpriseUserId);
        ensureTaskOwner(task, enterpriseUserId);
        if ("ACCEPTED".equals(submission.status()) || "REJECTED".equals(submission.status())) {
            throw new ApiException("BIZ-1001", "submission already reviewed", HttpStatus.BAD_REQUEST);
        }
        if ("CLOSED".equals(task.status())) {
            throw new ApiException("BIZ-1001", "task is closed", HttpStatus.BAD_REQUEST);
        }
        String decision = normalizeStatus(request.decision(), List.of("ACCEPT", "REJECT"), "decision");
        String contactIntent = TextListCodec.normalizeText(request.contactIntent());
        String rejectTemplate = TextListCodec.normalizeText(request.rejectTemplate());
        String reviewNote = TextListCodec.normalizeText(request.actionNote());
        if ("ACCEPT".equals(decision) && contactIntent == null) {
            throw new ApiException("BIZ-1001", "contactIntent must not be blank when decision=ACCEPT", HttpStatus.BAD_REQUEST);
        }
        if ("REJECT".equals(decision) && rejectTemplate == null) {
            throw new ApiException("BIZ-1001", "rejectTemplate must not be blank when decision=REJECT", HttpStatus.BAD_REQUEST);
        }
        String comment = TextListCodec.normalizeText(request.comment());
        if (comment == null) {
            comment = buildReviewComment(decision, contactIntent, rejectTemplate, reviewNote);
        }
        boolean syncEmailReminder = Boolean.TRUE.equals(request.syncEmailReminder());
        if ("ACCEPT".equals(decision) && task.acceptedSubmissionId() != null && !task.acceptedSubmissionId().equals(submissionId)) {
            throw new ApiException("BIZ-1001", "task already accepted another submission", HttpStatus.BAD_REQUEST);
        }

        String nextStatus = "ACCEPT".equals(decision) ? "ACCEPTED" : "REJECTED";
        // 企业先更新当前提交，再按采纳/拒绝分支写事件和通知。
        bountyRepository.reviewSubmission(
                submissionId,
                nextStatus,
                comment,
                contactIntent,
                rejectTemplate,
                reviewNote,
                enterpriseUserId
        );
        if ("ACCEPTED".equals(nextStatus)) {
            List<BountyRepository.SubmissionRow> otherPending = bountyRepository.findOtherPendingSubmissions(task.taskId(), submissionId);
            String autoRejectTemplate = "任务已结束，已有中选方案。";
            // 一个任务只允许一个中选提交，采纳后自动拒绝其他待处理方案并固定任务结果。
            bountyRepository.rejectOtherPendingSubmissions(task.taskId(), submissionId, autoRejectTemplate, autoRejectTemplate, enterpriseUserId);
            bountyRepository.updateTaskAcceptedSubmission(task.taskId(), submissionId);
            bountyRepository.createSubmissionEvent(
                    submissionId,
                    task.taskId(),
                    enterpriseUserId,
                    "CONTACT_SENT",
                    comment,
                    contactIntent,
                    null,
                    reviewNote,
                    Instant.now()
            );
            publishSubmissionReviewNotification(
                    enterpriseUserId,
                    submission.studentUserId(),
                    task.taskId(),
                    submission.submissionId(),
                    nextStatus,
                    "你的悬赏提交已被企业采纳，任务已结束。",
                    syncEmailReminder,
                    true
            );
            for (BountyRepository.SubmissionRow item : otherPending) {
                bountyRepository.createSubmissionEvent(
                        item.submissionId(),
                        item.taskId(),
                        enterpriseUserId,
                        "AUTO_REJECTED_TASK_CLOSED",
                        autoRejectTemplate,
                        null,
                        autoRejectTemplate,
                        "任务已结束，企业已选定其他方案。",
                        Instant.now()
                );
                publishSubmissionReviewNotification(
                        enterpriseUserId,
                        item.studentUserId(),
                        task.taskId(),
                        item.submissionId(),
                        "REJECTED",
                        "当前悬赏任务已结束，本次提交未被采纳。",
                        syncEmailReminder,
                        true
                );
            }
            bountyRepository.createSubmissionEvent(
                    submissionId,
                    task.taskId(),
                    enterpriseUserId,
                    "TASK_CLOSED_AFTER_ACCEPT",
                    null,
                    null,
                    null,
                    "任务已结束，当前结果留痕已固定在工作区中。",
                    Instant.now()
            );
        } else {
            bountyRepository.createSubmissionEvent(
                    submissionId,
                    task.taskId(),
                    enterpriseUserId,
                    "REJECT_SENT",
                    comment,
                    null,
                    rejectTemplate,
                    reviewNote,
                    Instant.now()
            );
            publishSubmissionReviewNotification(
                    enterpriseUserId,
                    submission.studentUserId(),
                    task.taskId(),
                    submission.submissionId(),
                    nextStatus,
                    "企业已完成对你的悬赏提交审核，请查看结果。",
                    syncEmailReminder,
                    false
            );
        }

        evictBountyTaskCaches(task.taskId());
        BountyRepository.SubmissionRow updated = bountyRepository.findSubmissionById(submissionId)
                .orElseThrow(() -> new IllegalStateException("reviewed submission not found"));
        BountyRepository.TaskRow updatedTask = requireTask(task.taskId(), enterpriseUserId);
        Map<Long, List<BountySubmissionHistoryItem>> historyMap = loadSubmissionHistoryMap(List.of(submissionId));
        StructuredReview structuredReview = resolveStructuredReview(updated);
        return new BountySubmissionReviewResponse(
                updated.submissionId(),
                updated.taskId(),
                updated.status(),
                updatedTask.status(),
                toIso(updated.reviewedAt()),
                updated.reviewComment(),
                structuredReview.contactIntent(),
                structuredReview.rejectTemplate(),
                structuredReview.reviewNote(),
                toIso(updated.updatedAt()),
                historyMap.getOrDefault(submissionId, List.of())
        );
    }

    @Transactional
    public BountyTaskManageResponse manageTask(long enterpriseUserId, long taskId, BountyTaskManageRequest request) {
        BountyRepository.TaskRow task = requireTask(taskId, enterpriseUserId);
        ensureTaskOwner(task, enterpriseUserId);
        String action = normalizeStatus(request.action(), List.of("CLOSE", "REOPEN"), "action");
        if ("REOPEN".equals(action)) {
            ensureEnterpriseCanPublishTasks(enterpriseUserId, true);
        }
        if ("REOPEN".equals(action) && task.acceptedSubmissionId() != null) {
            throw new ApiException("BIZ-1001", "accepted task cannot be reopened", HttpStatus.BAD_REQUEST);
        }
        // 已采纳任务不能重开；普通关闭/重开只改变任务状态，不改历史提交结果。
        bountyRepository.updateTaskStatus(taskId, "REOPEN".equals(action) ? "OPEN" : "CLOSED");
        evictBountyTaskCaches(taskId);
        BountyRepository.TaskRow updated = requireTask(taskId, enterpriseUserId);
        return new BountyTaskManageResponse(taskId, updated.status(), toIso(updated.updatedAt()));
    }

    private void publishSubmissionReviewNotification(
            long enterpriseUserId,
            long studentUserId,
            long taskId,
            long submissionId,
            String reviewStatus,
            String content,
            boolean syncEmailReminder,
            boolean taskClosedByAcceptance
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        // 通知 payload 保留 taskId/submissionId，前端可直接回流到审核或结果位置。
        payload.put("taskId", taskId);
        payload.put("submissionId", submissionId);
        payload.put("reviewStatus", reviewStatus);
        if (taskClosedByAcceptance) {
            payload.put("taskClosedByAcceptance", true);
        }

        notificationService.publish(new PlatformNotificationPublishService.NotificationPublishCommand(
                "BOUNTY_REVIEWED",
                NotificationCategory.BOUNTY,
                "BOUNTY_TASK",
                String.valueOf(taskId),
                enterpriseUserId,
                List.of(studentUserId),
                syncEmailReminder ? NotificationPriority.HIGH : NotificationPriority.NORMAL,
                null,
                content,
                null,
                String.valueOf(taskId),
                null,
                payload,
                syncEmailReminder,
                null,
                Instant.now()
        ));
    }

    private void ensureEnterpriseCanPublishTasks(long enterpriseUserId, boolean reopening) {
        enterpriseProfileRepository.createDefaultProfileIfAbsent(enterpriseUserId, null, null);
        String approvalStatus = enterpriseProfileRepository.findApprovalStatusByUserId(enterpriseUserId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "enterprise profile not found", HttpStatus.NOT_FOUND));
        if ("APPROVED".equals(approvalStatus)) {
            return;
        }

        String actionLabel = reopening ? "重新开放任务" : "发布新任务";
        String message;
        if ("REJECTED".equals(approvalStatus)) {
            message = "当前企业认证待补件，暂不能" + actionLabel + "。请先到资料页处理认证问题。";
        } else if ("PENDING".equals(approvalStatus)) {
            message = "当前企业认证审核中，暂不能" + actionLabel + "。可先完善资料并等待审核结果。";
        } else {
            message = "当前企业认证状态暂不支持" + actionLabel + "。";
        }
        throw new ApiException("BIZ-1001", message, HttpStatus.BAD_REQUEST);
    }

    private BountyRepository.TaskRow requireTask(long taskId, long viewerUserId) {
        return bountyRepository.findTaskById(taskId, viewerUserId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "task not found", HttpStatus.NOT_FOUND));
    }

    private BountyTaskListResponse loadTaskList(
            long viewerUserId,
            String keyword,
            String normalizedStatus,
            Long enterpriseUserId,
            int page,
            int size
    ) {
        long total = bountyRepository.countTasks(keyword, normalizedStatus, enterpriseUserId);
        List<BountyTaskListResponse.TaskItem> records = bountyRepository.findTasks(
                        viewerUserId,
                        keyword,
                        normalizedStatus,
                        enterpriseUserId,
                        page,
                        size
                ).stream()
                .map(this::toTaskItem)
                .toList();
        return new BountyTaskListResponse(records, total, page, size);
    }

    private BountyTaskListResponse mergeSubmittedState(long viewerUserId, String viewerRole, BountyTaskListResponse publicSnapshot) {
        if (publicSnapshot == null || !"STUDENT".equals(viewerRole) || viewerUserId <= 0
                || publicSnapshot.records() == null || publicSnapshot.records().isEmpty()) {
            return publicSnapshot;
        }
        // 学生是否已提交是个人态，必须在公共列表缓存外即时合成。
        List<Long> taskIds = publicSnapshot.records().stream()
                .map(BountyTaskListResponse.TaskItem::taskId)
                .toList();
        var submittedTaskIds = bountyRepository.findSubmittedTaskIds(viewerUserId, taskIds);
        List<BountyTaskListResponse.TaskItem> records = publicSnapshot.records().stream()
                .map(item -> new BountyTaskListResponse.TaskItem(
                        item.taskId(),
                        item.enterpriseUserId(),
                        item.enterpriseName(),
                        item.enterpriseLogoUrl(),
                        item.title(),
                        item.descriptionSummary(),
                        item.rewardDescription(),
                        item.status(),
                        item.submissionCount(),
                        item.acceptedSubmissionId(),
                        submittedTaskIds.contains(item.taskId()),
                        item.deadlineAt(),
                        item.createdAt(),
                        item.updatedAt()
                ))
                .toList();
        return new BountyTaskListResponse(records, publicSnapshot.total(), publicSnapshot.page(), publicSnapshot.size());
    }

    private BountyTaskDetailResponse mergeTaskDetailViewerState(
            long viewerUserId,
            String viewerRole,
            long taskId,
            BountyTaskDetailResponse publicSnapshot
    ) {
        if (publicSnapshot == null) {
            return null;
        }
        boolean mine = "ENTERPRISE".equals(viewerRole) && publicSnapshot.enterpriseUserId() == viewerUserId;
        BountyTaskDetailResponse.MySubmission mySubmission = null;
        if ("STUDENT".equals(viewerRole) && viewerUserId > 0) {
            // 学生详情页只拼自己的提交，避免把其他学生成果暴露到公共详情。
            mySubmission = bountyRepository.findSubmissionByTaskAndStudent(taskId, viewerUserId)
                    .map(this::toMySubmission)
                    .orElse(null);
        }
        return new BountyTaskDetailResponse(
                publicSnapshot.taskId(),
                publicSnapshot.enterpriseUserId(),
                publicSnapshot.enterpriseName(),
                publicSnapshot.enterpriseLogoUrl(),
                publicSnapshot.title(),
                publicSnapshot.description(),
                publicSnapshot.rewardDescription(),
                publicSnapshot.status(),
                publicSnapshot.submissionCount(),
                publicSnapshot.acceptedSubmissionId(),
                mine,
                publicSnapshot.deadlineAt(),
                publicSnapshot.closedAt(),
                publicSnapshot.createdAt(),
                publicSnapshot.updatedAt(),
                mySubmission
        );
    }

    private void evictBountyTaskListCaches() {
        bountyTaskListCacheService.evictAllNow();
        bountyTaskListCacheService.evictAllAfterCommit();
    }

    private void evictBountyTaskDetailCache(long taskId) {
        bountyTaskDetailCacheService.evictNow(taskId);
        bountyTaskDetailCacheService.evictAfterCommit(taskId);
    }

    private void evictBountyTaskCaches(long taskId) {
        evictBountyTaskListCaches();
        evictBountyTaskDetailCache(taskId);
    }

    private void ensureTaskOwner(BountyRepository.TaskRow task, long enterpriseUserId) {
        if (task.enterpriseUserId() != enterpriseUserId) {
            throw new ApiException("AUTH-1004", "permission denied", HttpStatus.FORBIDDEN);
        }
    }

    private BountyTaskListResponse.TaskItem toTaskItem(BountyRepository.TaskRow row) {
        return new BountyTaskListResponse.TaskItem(
                row.taskId(),
                row.enterpriseUserId(),
                row.enterpriseName(),
                resolveEnterpriseLogoUrl(row),
                row.title(),
                summarize(row.description(), 120),
                row.rewardDescription(),
                row.status(),
                row.submissionCount(),
                row.acceptedSubmissionId(),
                row.submittedByMe(),
                toIso(row.deadlineAt()),
                toIso(row.createdAt()),
                toIso(row.updatedAt())
        );
    }

    private BountyEnterpriseTaskCenterResponse.SnapshotSubmissionItem toTaskCenterSnapshotSubmissionItem(SubmissionView row) {
        return new BountyEnterpriseTaskCenterResponse.SnapshotSubmissionItem(
                row.submissionId(),
                row.studentUserId(),
                row.studentName(),
                row.studentAvatar(),
                row.status(),
                summarizeSubmission(row.contentText(), row.attachmentLinks()),
                row.communityScore7d(),
                row.portraitTags(),
                row.reviewComment(),
                row.reviewedAt(),
                toIso(row.createdAt())
        );
    }

    private BountyTaskDetailResponse.MySubmission toMySubmission(BountyRepository.SubmissionRow row) {
        return new BountyTaskDetailResponse.MySubmission(
                row.submissionId(),
                row.status(),
                row.contentText(),
                TextListCodec.split(row.attachmentLinks()),
                row.reviewComment(),
                toIso(row.reviewedAt()),
                toIso(row.createdAt()),
                toIso(row.updatedAt())
        );
    }

    private BountyTaskDetailResponse toPublicTaskDetail(BountyRepository.TaskRow task) {
        return new BountyTaskDetailResponse(
                task.taskId(),
                task.enterpriseUserId(),
                task.enterpriseName(),
                resolveEnterpriseLogoUrl(task),
                task.title(),
                task.description(),
                task.rewardDescription(),
                task.status(),
                task.submissionCount(),
                task.acceptedSubmissionId(),
                false,
                toIso(task.deadlineAt()),
                toIso(task.closedAt()),
                toIso(task.createdAt()),
                toIso(task.updatedAt()),
                null
        );
    }

    private SubmissionView toSubmissionView(BountyRepository.SubmissionRow row) {
        // 企业审核列表把学生公开画像、社区分和联系方式合成在一个 view 里，前端不用多接口拼装。
        StudentProfileRepository.CommunityStatsRow stats = studentProfileRepository.countCommunityStats7d(
                row.studentUserId(),
                Instant.now().minus(7, ChronoUnit.DAYS)
        );
        long communityScore7d = calculateCommunityScore(stats);
        Optional<StudentProfileRepository.PortraitSnapshotRow> snapshot = studentProfileRepository.findPortraitSnapshot(row.studentUserId());
        List<String> portraitTags = parsePortraitTagLabels(snapshot.map(StudentProfileRepository.PortraitSnapshotRow::portraitTagsJson).orElse(null));
        Long portraitUpdatedAt = snapshot.map(StudentProfileRepository.PortraitSnapshotRow::updatedAt).map(this::toIso).orElse(null);
        StudentProfileRepository.StudentProfileRow profileRow = studentProfileRepository.findStudentProfileByUserId(row.studentUserId()).orElse(null);
        BountyStudentAvatarPayload studentAvatar = buildStudentAvatarPayload(profileRow);
        BountySubmissionListResponse.ContactInfo contactInfo = buildContactInfo(row.studentUserId(), profileRow);
        StructuredReview structuredReview = resolveStructuredReview(row);
        return new SubmissionView(
                row.submissionId(),
                row.studentUserId(),
                row.studentName(),
                studentAvatar,
                row.status(),
                row.contentText(),
                TextListCodec.split(row.attachmentLinks()),
                communityScore7d,
                portraitTags,
                portraitUpdatedAt,
                row.reviewComment(),
                structuredReview.contactIntent(),
                structuredReview.rejectTemplate(),
                structuredReview.reviewNote(),
                toIso(row.reviewedAt()),
                row.createdAt(),
                row.updatedAt(),
                contactInfo
        );
    }

    private BountySubmissionListResponse.SubmissionItem toSubmissionItem(
            SubmissionView row,
            List<BountySubmissionHistoryItem> history
    ) {
        return new BountySubmissionListResponse.SubmissionItem(
                row.submissionId(),
                row.studentUserId(),
                row.studentName(),
                row.studentAvatar(),
                row.status(),
                summarizeSubmission(row.contentText(), row.attachmentLinks()),
                row.contentText(),
                row.attachmentLinks(),
                row.communityScore7d(),
                row.portraitTags(),
                row.portraitUpdatedAt(),
                row.reviewComment(),
                row.contactIntent(),
                row.rejectTemplate(),
                row.reviewNote(),
                row.reviewedAt(),
                toIso(row.createdAt()),
                toIso(row.updatedAt()),
                row.contact(),
                history == null ? List.of() : history
        );
    }

    private Map<Long, List<BountySubmissionHistoryItem>> loadSubmissionHistoryMap(List<Long> submissionIds) {
        if (submissionIds == null || submissionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<BountySubmissionHistoryItem>> result = new LinkedHashMap<>();
        for (BountyRepository.SubmissionEventRow row : bountyRepository.findSubmissionEventsBySubmissionIds(submissionIds)) {
            result.computeIfAbsent(row.submissionId(), key -> new ArrayList<>()).add(toSubmissionHistoryItem(row));
        }
        for (Long submissionId : submissionIds) {
            result.putIfAbsent(submissionId, List.of());
        }
        return result;
    }

    private BountySubmissionHistoryItem toSubmissionHistoryItem(BountyRepository.SubmissionEventRow row) {
        StructuredReview structuredReview = resolveStructuredReview(
                row.commentText(),
                row.contactIntent(),
                row.rejectTemplate(),
                row.note()
        );
        return new BountySubmissionHistoryItem(
                row.eventId(),
                row.eventType(),
                toIso(row.createdAt()),
                row.commentText(),
                structuredReview.contactIntent(),
                structuredReview.rejectTemplate(),
                structuredReview.reviewNote()
        );
    }

    private boolean matchesPortraitTags(SubmissionView item, List<String> portraitFilters) {
        if (portraitFilters.isEmpty()) {
            return true;
        }
        List<String> normalizedItemTags = item.portraitTags().stream()
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .toList();
        for (String filter : portraitFilters) {
            String normalized = filter.toLowerCase(Locale.ROOT);
            if (normalizedItemTags.stream().anyMatch(tag -> tag.contains(normalized))) {
                return true;
            }
        }
        return false;
    }

    private List<String> parsePortraitTagLabels(String portraitTagsJson) {
        if (portraitTagsJson == null || portraitTagsJson.isBlank()) {
            return List.of();
        }
        try {
            List<StudentProfileResponse.PortraitTagItem> items = objectMapper.readValue(portraitTagsJson, PORTRAIT_TAG_LIST_TYPE);
            List<String> labels = new ArrayList<>();
            for (StudentProfileResponse.PortraitTagItem item : items) {
                String label = TextListCodec.normalizeText(item.label());
                if (label != null) {
                    labels.add(label);
                    continue;
                }
                String code = TextListCodec.normalizeText(item.code());
                if (code != null) {
                    labels.add(code);
                }
            }
            return List.copyOf(labels);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to parse portrait tags json", ex);
        }
    }

    private BountyStudentAvatarPayload buildStudentAvatarPayload(StudentProfileRepository.StudentProfileRow profileRow) {
        if (profileRow == null) {
            return new BountyStudentAvatarPayload(false, null, null);
        }
        boolean uploaded = profileRow.avatarObjectKey() != null && !profileRow.avatarObjectKey().isBlank();
        return new BountyStudentAvatarPayload(
                uploaded,
                profileRow.avatarContentType(),
                toIso(profileRow.avatarUpdatedAt())
        );
    }

    private BountySubmissionListResponse.ContactInfo buildContactInfo(
            long studentUserId,
            StudentProfileRepository.StudentProfileRow profileRow
    ) {
        if (profileRow == null) {
            return new BountySubmissionListResponse.ContactInfo(
                    "当前还没有读取到学生资料，暂时无法展示联系方式。",
                    false,
                    null,
                    false,
                    null,
                    false,
                    null,
                    false,
                    List.of()
            );
        }

        JsonNode privacyRoot = studentProfileRepository.findPrivacySettingsJson(studentUserId)
                .map(this::readJsonSafely)
                .orElseGet(() -> objectMapper.createObjectNode());

        boolean emailVisible = isEnterpriseVisible(privacyRoot, "email", true);
        boolean phoneVisible = isEnterpriseVisible(privacyRoot, "phone", true);
        boolean wechatVisible = isEnterpriseVisible(privacyRoot, "wechat", true);
        boolean socialVisible = isEnterpriseVisible(privacyRoot, "social", true);
        List<StudentProfileSocialLinkItem> socialLinks = socialVisible ? parseSocialLinks(profileRow) : List.of();

        String hint;
        if (!emailVisible && !phoneVisible && !wechatVisible && !socialVisible) {
            hint = "学生当前未向企业开放任何联系方式。";
        } else {
            hint = "当前仅展示学生向企业开放的联系方式与公开主页。";
        }

        return new BountySubmissionListResponse.ContactInfo(
                hint,
                emailVisible,
                emailVisible ? profileRow.email() : null,
                phoneVisible,
                phoneVisible ? profileRow.phone() : null,
                wechatVisible,
                wechatVisible ? profileRow.wechat() : null,
                socialVisible,
                socialLinks
        );
    }

    private List<StudentProfileSocialLinkItem> parseSocialLinks(StudentProfileRepository.StudentProfileRow profileRow) {
        if (profileRow.socialLinksJson() != null && !profileRow.socialLinksJson().isBlank()) {
            try {
                return objectMapper.readValue(profileRow.socialLinksJson(), SOCIAL_LINK_LIST_TYPE);
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("failed to parse student social links json", ex);
            }
        }

        List<StudentProfileSocialLinkItem> fallback = new ArrayList<>();
        String github = TextListCodec.normalizeText(profileRow.github());
        String portfolio = TextListCodec.normalizeText(profileRow.portfolio());
        if (github != null) {
            fallback.add(new StudentProfileSocialLinkItem("GITHUB", github));
        }
        if (portfolio != null) {
            fallback.add(new StudentProfileSocialLinkItem("PORTFOLIO", portfolio));
        }
        return List.copyOf(fallback);
    }

    private JsonNode readJsonSafely(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to parse privacy settings json", ex);
        }
    }

    private boolean isEnterpriseVisible(JsonNode privacyRoot, String fieldName, boolean fallback) {
        if (privacyRoot == null || privacyRoot.isMissingNode()) {
            return fallback;
        }
        JsonNode fieldNode = privacyRoot.path(fieldName);
        if (fieldNode.isMissingNode()) {
            return fallback;
        }
        JsonNode enterpriseNode = fieldNode.get("enterprise");
        if (enterpriseNode == null || enterpriseNode.isNull()) {
            return fallback;
        }
        return enterpriseNode.asBoolean(fallback);
    }

    private Instant parseOptionalInstant(String value, String fieldName) {
        String normalized = TextListCodec.normalizeText(value);
        if (normalized == null) {
            return null;
        }
        try {
            return Instant.parse(normalized);
        } catch (Exception ex) {
            throw new ApiException("BIZ-1001", fieldName + " must be ISO-8601 UTC instant", HttpStatus.BAD_REQUEST);
        }
    }

    private String requireText(String value, String fieldName) {
        String normalized = TextListCodec.normalizeText(value);
        if (normalized == null) {
            throw new ApiException("BIZ-1001", fieldName + " must not be blank", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeStatus(String value, List<String> allowed, String fieldName) {
        String normalized = TextListCodec.normalizeText(value);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!allowed.contains(upper)) {
            throw new ApiException("BIZ-1001", fieldName + " is invalid", HttpStatus.BAD_REQUEST);
        }
        return upper;
    }

    private long calculateCommunityScore(StudentProfileRepository.CommunityStatsRow stats) {
        return stats.postCount() * 5L + stats.commentCount() * 2L + stats.likesReceivedCount();
    }

    private String summarize(String text, int limit) {
        String normalized = TextListCodec.normalizeText(text);
        if (normalized == null) {
            return "";
        }
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, limit - 1)) + "…";
    }

    private String summarizeSubmission(String contentText, List<String> attachmentLinks) {
        StringBuilder builder = new StringBuilder();
        String normalizedText = TextListCodec.normalizeText(contentText);
        if (normalizedText != null) {
            builder.append(summarize(normalizedText, 60));
        }
        if (!attachmentLinks.isEmpty()) {
            if (!builder.isEmpty()) {
                builder.append(" + ");
            }
            builder.append(attachmentLinks.size()).append(" 个链接");
        }
        return builder.isEmpty() ? "-" : builder.toString();
    }

    private String resolveEnterpriseLogoUrl(BountyRepository.TaskRow row) {
        String objectKey = TextListCodec.normalizeText(row.enterpriseLogoObjectKey());
        if (objectKey == null) {
            return null;
        }
        return EnterpriseLogoUrlSupport.buildPublicLogoUrl(row.enterpriseUserId(), row.enterpriseLogoUpdatedAt());
    }

    private StructuredReview resolveStructuredReview(BountyRepository.SubmissionRow row) {
        return resolveStructuredReview(
                row.reviewComment(),
                row.contactIntent(),
                row.rejectTemplate(),
                row.reviewNote()
        );
    }

    private StructuredReview resolveStructuredReview(
            String reviewComment,
            String contactIntent,
            String rejectTemplate,
            String reviewNote
    ) {
        String normalizedContactIntent = TextListCodec.normalizeText(contactIntent);
        String normalizedRejectTemplate = TextListCodec.normalizeText(rejectTemplate);
        String normalizedReviewNote = TextListCodec.normalizeText(reviewNote);
        if (normalizedContactIntent != null || normalizedRejectTemplate != null || normalizedReviewNote != null) {
            return new StructuredReview(normalizedContactIntent, normalizedRejectTemplate, normalizedReviewNote);
        }

        String normalizedComment = TextListCodec.normalizeText(reviewComment);
        if (normalizedComment == null) {
            return new StructuredReview(null, null, null);
        }

        List<String> lines = normalizedComment.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
        if (lines.isEmpty()) {
            return new StructuredReview(null, null, null);
        }

        String firstLine = lines.get(0);
        String derivedNote = lines.stream()
                .skip(1)
                .map(line -> line.replaceFirst("^补充说明：", "").trim())
                .filter(line -> !line.isBlank())
                .reduce((left, right) -> left + " " + right)
                .orElse(null);
        if (firstLine.startsWith("继续接触意向：")) {
            return new StructuredReview(
                    TextListCodec.normalizeText(firstLine.replace("继续接触意向：", "")),
                    null,
                    derivedNote
            );
        }
        if (firstLine.startsWith("未入选原因：")) {
            return new StructuredReview(
                    null,
                    TextListCodec.normalizeText(firstLine.replace("未入选原因：", "")),
                    derivedNote
            );
        }
        return new StructuredReview(null, null, normalizedComment);
    }

    private String buildReviewComment(String decision, String contactIntent, String rejectTemplate, String reviewNote) {
        String normalizedReviewNote = TextListCodec.normalizeText(reviewNote);
        if ("ACCEPT".equals(decision)) {
            if (normalizedReviewNote == null) {
                return "继续接触意向：" + contactIntent;
            }
            return "继续接触意向：" + contactIntent + "\n补充说明：" + normalizedReviewNote;
        }
        if (normalizedReviewNote == null) {
            return "未入选原因：" + rejectTemplate;
        }
        return "未入选原因：" + rejectTemplate + "\n补充说明：" + normalizedReviewNote;
    }

    private boolean isPendingSubmissionStatus(String status) {
        return "SUBMITTED".equals(status) || "REVIEWING".equals(status);
    }

    private Long toIso(Instant instant) {
        return com.bishe.server.common.TimePayloads.toEpochMillis(instant);
    }

    private record SubmissionView(
            long submissionId,
            long studentUserId,
            String studentName,
            BountyStudentAvatarPayload studentAvatar,
            String status,
            String contentText,
            List<String> attachmentLinks,
            long communityScore7d,
            List<String> portraitTags,
            Long portraitUpdatedAt,
            String reviewComment,
            String contactIntent,
            String rejectTemplate,
            String reviewNote,
            Long reviewedAt,
            Instant createdAt,
            Instant updatedAt,
            BountySubmissionListResponse.ContactInfo contact
    ) {
    }

    private record StructuredReview(
            String contactIntent,
            String rejectTemplate,
            String reviewNote
    ) {
    }
}
