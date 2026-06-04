package com.bishe.server.bounty.repository;

import com.bishe.server.auth.repository.jpa.UserAccountJpaRepository;
import com.bishe.server.auth.repository.jpa.entity.UserAccountEntity;
import com.bishe.server.bounty.repository.jpa.BountySubmissionEventJpaRepository;
import com.bishe.server.bounty.repository.jpa.BountySubmissionJpaRepository;
import com.bishe.server.bounty.repository.jpa.BountyTaskJpaRepository;
import com.bishe.server.bounty.repository.jpa.entity.BountySubmissionEntity;
import com.bishe.server.bounty.repository.jpa.entity.BountySubmissionEventEntity;
import com.bishe.server.bounty.repository.jpa.entity.BountyTaskEntity;
import com.bishe.server.profile.repository.jpa.EnterpriseProfileJpaRepository;
import com.bishe.server.profile.repository.jpa.entity.EnterpriseProfileEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class BountyRepository {

    private static final List<String> PENDING_SUBMISSION_STATUSES = List.of("SUBMITTED", "REVIEWING");

    private final BountyTaskJpaRepository bountyTaskJpaRepository;
    private final BountySubmissionJpaRepository bountySubmissionJpaRepository;
    private final BountySubmissionEventJpaRepository bountySubmissionEventJpaRepository;
    private final UserAccountJpaRepository userAccountJpaRepository;
    private final EnterpriseProfileJpaRepository enterpriseProfileJpaRepository;

    public BountyRepository(
            BountyTaskJpaRepository bountyTaskJpaRepository,
            BountySubmissionJpaRepository bountySubmissionJpaRepository,
            BountySubmissionEventJpaRepository bountySubmissionEventJpaRepository,
            UserAccountJpaRepository userAccountJpaRepository,
            EnterpriseProfileJpaRepository enterpriseProfileJpaRepository
    ) {
        this.bountyTaskJpaRepository = bountyTaskJpaRepository;
        this.bountySubmissionJpaRepository = bountySubmissionJpaRepository;
        this.bountySubmissionEventJpaRepository = bountySubmissionEventJpaRepository;
        this.userAccountJpaRepository = userAccountJpaRepository;
        this.enterpriseProfileJpaRepository = enterpriseProfileJpaRepository;
    }

    public long createTask(long enterpriseUserId, String title, String description, String rewardDescription, Instant deadlineAt) {
        BountyTaskEntity entity = BountyTaskEntity.create(enterpriseUserId, title, description, rewardDescription, deadlineAt);
        bountyTaskJpaRepository.saveAndFlush(entity);
        if (entity.getId() == null) {
            throw new IllegalStateException("failed to create bounty task");
        }
        return entity.getId();
    }

    public void updateTask(long taskId, String title, String description, String rewardDescription, Instant deadlineAt) {
        BountyTaskEntity entity = requireTaskEntity(taskId);
        entity.updateTask(title, description, rewardDescription, deadlineAt);
        bountyTaskJpaRepository.saveAndFlush(entity);
    }

    public long countTasks(String keyword, String status, Long enterpriseUserId) {
        return bountyTaskJpaRepository.count(buildTaskSpecification(keyword, status, enterpriseUserId));
    }

    public List<TaskRow> findTasks(long viewerUserId, String keyword, String status, Long enterpriseUserId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        PageRequest pageRequest = PageRequest.of(
                safePage - 1,
                safeSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        List<BountyTaskEntity> tasks = bountyTaskJpaRepository.findAll(buildTaskSpecification(keyword, status, enterpriseUserId), pageRequest)
                .getContent();
        return toTaskRows(tasks, viewerUserId);
    }

    public Optional<TaskRow> findTaskById(long taskId, long viewerUserId) {
        return bountyTaskJpaRepository.findById(taskId)
                .map(task -> {
                    List<TaskRow> rows = toTaskRows(List.of(task), viewerUserId);
                    return rows.isEmpty() ? null : rows.get(0);
                });
    }

    public Optional<SubmissionRow> findSubmissionByTaskAndStudent(long taskId, long studentUserId) {
        return bountySubmissionJpaRepository.findByTaskIdAndStudentUserId(taskId, studentUserId)
                .map(this::toSubmissionRow);
    }

    public Set<Long> findSubmittedTaskIds(long studentUserId, List<Long> taskIds) {
        if (studentUserId <= 0 || taskIds == null || taskIds.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(bountySubmissionJpaRepository.findSubmittedTaskIds(studentUserId, taskIds));
    }

    public long createSubmission(long taskId, long studentUserId, String contentText, String attachmentLinks) {
        BountySubmissionEntity entity = BountySubmissionEntity.create(
                bountyTaskJpaRepository.getReferenceById(taskId),
                studentUserId,
                contentText,
                attachmentLinks
        );
        bountySubmissionJpaRepository.saveAndFlush(entity);
        if (entity.getId() == null) {
            throw new IllegalStateException("failed to create bounty submission");
        }
        return entity.getId();
    }

    public Optional<SubmissionRow> findSubmissionById(long submissionId) {
        return bountySubmissionJpaRepository.findById(submissionId).map(this::toSubmissionRow);
    }

    public List<SubmissionRow> findSubmissionsByTaskId(long taskId, String status) {
        String normalizedStatus = normalizeStatus(status);
        List<BountySubmissionEntity> submissions = normalizedStatus == null
                ? bountySubmissionJpaRepository.findByTaskIdOrderByCreatedAtDescIdDesc(taskId)
                : bountySubmissionJpaRepository.findByTaskIdAndStatusOrderByCreatedAtDescIdDesc(taskId, normalizedStatus);
        return toSubmissionRows(submissions);
    }

    public List<SubmissionRow> findSubmissionsByTaskIds(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        return toSubmissionRows(bountySubmissionJpaRepository.findByTaskIdInOrderByTaskIdAscCreatedAtDescIdDesc(taskIds));
    }

    public List<SubmissionRow> findOtherPendingSubmissions(long taskId, long excludedSubmissionId) {
        return toSubmissionRows(
                bountySubmissionJpaRepository.findByTaskIdAndIdNotAndStatusInOrderByCreatedAtDescIdDesc(
                        taskId,
                        excludedSubmissionId,
                        PENDING_SUBMISSION_STATUSES
                )
        );
    }

    public void reviewSubmission(
            long submissionId,
            String status,
            String comment,
            String contactIntent,
            String rejectTemplate,
            String reviewNote,
            long reviewerUserId
    ) {
        BountySubmissionEntity entity = requireSubmissionEntity(submissionId);
        entity.review(normalizeStatus(status), comment, contactIntent, rejectTemplate, reviewNote, reviewerUserId);
        bountySubmissionJpaRepository.saveAndFlush(entity);
    }

    public void rejectOtherPendingSubmissions(
            long taskId,
            long excludedSubmissionId,
            String comment,
            String rejectTemplate,
            long reviewerUserId
    ) {
        List<BountySubmissionEntity> submissions = bountySubmissionJpaRepository.findByTaskIdAndIdNotAndStatusInOrderByCreatedAtDescIdDesc(
                taskId,
                excludedSubmissionId,
                PENDING_SUBMISSION_STATUSES
        );
        if (submissions.isEmpty()) {
            return;
        }
        submissions.forEach(entity -> entity.reject(comment, rejectTemplate, reviewerUserId));
        bountySubmissionJpaRepository.saveAllAndFlush(submissions);
    }

    public void createSubmissionEvent(
            long submissionId,
            long taskId,
            Long actorUserId,
            String eventType,
            String comment,
            String contactIntent,
            String rejectTemplate,
            String note,
            Instant createdAt
    ) {
        bountySubmissionEventJpaRepository.saveAndFlush(
                BountySubmissionEventEntity.create(
                        submissionId,
                        taskId,
                        actorUserId,
                        eventType,
                        comment,
                        contactIntent,
                        rejectTemplate,
                        note,
                        createdAt
                )
        );
    }

    public List<SubmissionEventRow> findSubmissionEventsBySubmissionIds(List<Long> submissionIds) {
        if (submissionIds == null || submissionIds.isEmpty()) {
            return List.of();
        }
        return bountySubmissionEventJpaRepository.findBySubmissionIdInOrderBySubmissionIdAscCreatedAtAscIdAsc(submissionIds)
                .stream()
                .map(this::toSubmissionEventRow)
                .toList();
    }

    public List<TaskRow> findTasksByEnterpriseUserId(long enterpriseUserId) {
        return toTaskRows(
                bountyTaskJpaRepository.findByEnterpriseUserIdOrderByCreatedAtDescIdDesc(enterpriseUserId),
                0L
        );
    }

    public void updateTaskAcceptedSubmission(long taskId, long acceptedSubmissionId) {
        BountyTaskEntity entity = requireTaskEntity(taskId);
        entity.acceptSubmission(acceptedSubmissionId);
        bountyTaskJpaRepository.saveAndFlush(entity);
    }

    public void updateTaskStatus(long taskId, String status) {
        BountyTaskEntity entity = requireTaskEntity(taskId);
        entity.updateStatus(normalizeStatus(status));
        bountyTaskJpaRepository.saveAndFlush(entity);
    }

    public List<Long> findTaskIdsByEnterpriseUserId(long enterpriseUserId) {
        return bountyTaskJpaRepository.findIdsByEnterpriseUserId(enterpriseUserId);
    }

    private BountyTaskEntity requireTaskEntity(long taskId) {
        return bountyTaskJpaRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("bounty task not found: " + taskId));
    }

    private BountySubmissionEntity requireSubmissionEntity(long submissionId) {
        return bountySubmissionJpaRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalStateException("bounty submission not found: " + submissionId));
    }

    private Specification<BountyTaskEntity> buildTaskSpecification(String keyword, String status, Long enterpriseUserId) {
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedStatus = normalizeStatus(status);
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (normalizedKeyword != null) {
                String like = "%" + normalizedKeyword + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), like),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), like),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("rewardDescription")), like)
                ));
            }
            if (normalizedStatus != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), normalizedStatus));
            }
            if (enterpriseUserId != null) {
                predicates.add(criteriaBuilder.equal(root.get("enterpriseUserId"), enterpriseUserId));
            }
            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim().toLowerCase();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return status.trim().toUpperCase();
    }

    private List<TaskRow> toTaskRows(List<BountyTaskEntity> tasks, long viewerUserId) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        List<Long> taskIds = tasks.stream()
                .map(BountyTaskEntity::getId)
                .filter(Objects::nonNull)
                .toList();
        Set<Long> submittedTaskIds = viewerUserId > 0 ? findSubmittedTaskIds(viewerUserId, taskIds) : Set.of();
        Map<Long, Integer> submissionCountMap = loadSubmissionCountMap(taskIds);
        List<Long> enterpriseUserIds = tasks.stream()
                .map(BountyTaskEntity::getEnterpriseUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, UserAccountEntity> userMap = loadUserAccountMap(enterpriseUserIds);
        Map<Long, EnterpriseProfileEntity> enterpriseProfileMap = loadEnterpriseProfileMap(enterpriseUserIds);
        return tasks.stream()
                .map(task -> toTaskRow(
                        task,
                        submissionCountMap.getOrDefault(task.getId(), 0),
                        submittedTaskIds.contains(task.getId()),
                        userMap.get(task.getEnterpriseUserId()),
                        enterpriseProfileMap.get(task.getEnterpriseUserId())
                ))
                .toList();
    }

    private TaskRow toTaskRow(
            BountyTaskEntity task,
            int submissionCount,
            boolean submittedByMe,
            UserAccountEntity enterpriseUser,
            EnterpriseProfileEntity enterpriseProfile
    ) {
        return new TaskRow(
                longValue(task.getId()),
                longValue(task.getEnterpriseUserId()),
                resolveEnterpriseName(enterpriseProfile, enterpriseUser),
                enterpriseProfile == null ? null : enterpriseProfile.getLogoObjectKey(),
                enterpriseProfile == null ? null : enterpriseProfile.getLogoUpdatedAt(),
                task.getTitle(),
                task.getDescription(),
                task.getRewardDescription(),
                task.getStatus(),
                task.getAcceptedSubmissionId(),
                task.getDeadlineAt(),
                task.getClosedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                submissionCount,
                submittedByMe
        );
    }

    private List<SubmissionRow> toSubmissionRows(List<BountySubmissionEntity> submissions) {
        if (submissions == null || submissions.isEmpty()) {
            return List.of();
        }
        List<Long> studentUserIds = submissions.stream()
                .map(BountySubmissionEntity::getStudentUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, UserAccountEntity> userMap = loadUserAccountMap(studentUserIds);
        return submissions.stream()
                .map(entity -> toSubmissionRow(entity, userMap.get(entity.getStudentUserId())))
                .toList();
    }

    private SubmissionRow toSubmissionRow(BountySubmissionEntity entity) {
        return toSubmissionRow(
                entity,
                loadUserAccountMap(List.of(entity.getStudentUserId())).get(entity.getStudentUserId())
        );
    }

    private SubmissionRow toSubmissionRow(BountySubmissionEntity entity, UserAccountEntity studentUser) {
        return new SubmissionRow(
                longValue(entity.getId()),
                resolveTaskId(entity),
                longValue(entity.getStudentUserId()),
                studentUser == null ? "" : studentUser.getDisplayName(),
                entity.getContentText(),
                entity.getAttachmentLinks(),
                entity.getStatus(),
                entity.getReviewComment(),
                entity.getContactIntent(),
                entity.getRejectTemplate(),
                entity.getReviewNote(),
                entity.getReviewerUserId(),
                entity.getReviewedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private SubmissionEventRow toSubmissionEventRow(BountySubmissionEventEntity entity) {
        return new SubmissionEventRow(
                longValue(entity.getId()),
                longValue(entity.getSubmissionId()),
                longValue(entity.getTaskId()),
                entity.getActorUserId(),
                entity.getEventType(),
                entity.getCommentText(),
                entity.getContactIntent(),
                entity.getRejectTemplate(),
                entity.getNote(),
                entity.getCreatedAt()
        );
    }

    private Map<Long, Integer> loadSubmissionCountMap(Collection<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Map.of();
        }
        return bountySubmissionJpaRepository.countSubmissionByTaskIds(taskIds)
                .stream()
                .filter(item -> item.getTaskId() != null)
                .collect(Collectors.toMap(
                        BountySubmissionJpaRepository.TaskSubmissionCountView::getTaskId,
                        item -> Math.toIntExact(item.getSubmissionCount())
                ));
    }

    private Map<Long, UserAccountEntity> loadUserAccountMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userAccountJpaRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(
                        UserAccountEntity::getId,
                        entity -> entity,
                        (left, right) -> right
                ));
    }

    private Map<Long, EnterpriseProfileEntity> loadEnterpriseProfileMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return enterpriseProfileJpaRepository.findAllByUserIdIn(userIds)
                .stream()
                .collect(Collectors.toMap(
                        EnterpriseProfileEntity::getUserId,
                        entity -> entity,
                        (left, right) -> right
                ));
    }

    private String resolveEnterpriseName(EnterpriseProfileEntity enterpriseProfile, UserAccountEntity enterpriseUser) {
        if (enterpriseProfile != null && enterpriseProfile.getCompanyName() != null && !enterpriseProfile.getCompanyName().isBlank()) {
            return enterpriseProfile.getCompanyName();
        }
        if (enterpriseUser != null && enterpriseUser.getDisplayName() != null) {
            return enterpriseUser.getDisplayName();
        }
        return "";
    }

    private long longValue(Long value) {
        return value == null ? 0L : value;
    }

    private long resolveTaskId(BountySubmissionEntity entity) {
        Long taskId = entity.getTaskId();
        if (taskId != null) {
            return taskId;
        }
        if (entity.getTask() != null && entity.getTask().getId() != null) {
            return entity.getTask().getId();
        }
        return 0L;
    }

    public record TaskRow(
            long taskId,
            long enterpriseUserId,
            String enterpriseName,
            String enterpriseLogoObjectKey,
            Instant enterpriseLogoUpdatedAt,
            String title,
            String description,
            String rewardDescription,
            String status,
            Long acceptedSubmissionId,
            Instant deadlineAt,
            Instant closedAt,
            Instant createdAt,
            Instant updatedAt,
            int submissionCount,
            boolean submittedByMe
    ) {
    }

    public record SubmissionRow(
            long submissionId,
            long taskId,
            long studentUserId,
            String studentName,
            String contentText,
            String attachmentLinks,
            String status,
            String reviewComment,
            String contactIntent,
            String rejectTemplate,
            String reviewNote,
            Long reviewerUserId,
            Instant reviewedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record SubmissionEventRow(
            long eventId,
            long submissionId,
            long taskId,
            Long actorUserId,
            String eventType,
            String commentText,
            String contactIntent,
            String rejectTemplate,
            String note,
            Instant createdAt
    ) {
    }
}
