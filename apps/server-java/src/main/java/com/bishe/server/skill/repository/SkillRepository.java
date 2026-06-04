package com.bishe.server.skill.repository;

import com.bishe.server.auth.model.UserRole;
import com.bishe.server.skill.repository.jpa.SkillNodeJpaRepository;
import com.bishe.server.skill.repository.jpa.SkillNodeResourceJpaRepository;
import com.bishe.server.skill.repository.jpa.SkillProgressJpaRepository;
import com.bishe.server.skill.repository.jpa.SkillRelationJpaRepository;
import com.bishe.server.skill.repository.jpa.UserAccountRefJpaRepository;
import com.bishe.server.skill.repository.jpa.entity.SkillNodeEntity;
import com.bishe.server.skill.repository.jpa.entity.SkillProgressEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 技能树与技能进度仓储。
 */
@Repository
public class SkillRepository {

    private final UserAccountRefJpaRepository userAccountRefJpaRepository;
    private final SkillNodeJpaRepository skillNodeJpaRepository;
    private final SkillRelationJpaRepository skillRelationJpaRepository;
    private final SkillNodeResourceJpaRepository skillNodeResourceJpaRepository;
    private final SkillProgressJpaRepository skillProgressJpaRepository;

    public SkillRepository(
            UserAccountRefJpaRepository userAccountRefJpaRepository,
            SkillNodeJpaRepository skillNodeJpaRepository,
            SkillRelationJpaRepository skillRelationJpaRepository,
            SkillNodeResourceJpaRepository skillNodeResourceJpaRepository,
            SkillProgressJpaRepository skillProgressJpaRepository
    ) {
        this.userAccountRefJpaRepository = userAccountRefJpaRepository;
        this.skillNodeJpaRepository = skillNodeJpaRepository;
        this.skillRelationJpaRepository = skillRelationJpaRepository;
        this.skillNodeResourceJpaRepository = skillNodeResourceJpaRepository;
        this.skillProgressJpaRepository = skillProgressJpaRepository;
    }

    public boolean existsStudentUser(long userId) {
        return userAccountRefJpaRepository.existsByIdAndRoleAndDeletedFalse(userId, UserRole.STUDENT);
    }

    public List<SkillNodeRow> findAllSkillNodes() {
        return skillNodeJpaRepository.findAllByOrderBySortOrderAscNodeCodeAsc().stream()
                .map(this::toNodeRow)
                .toList();
    }

    public Optional<SkillNodeRow> findSkillNode(String nodeCode) {
        return skillNodeJpaRepository.findOneByNodeCode(nodeCode).map(this::toNodeRow);
    }

    public List<SkillRelationRow> findAllSkillRelations() {
        return skillRelationJpaRepository.findAllByOrderBySortOrderAscSourceNode_NodeCodeAscTargetNode_NodeCodeAsc().stream()
                .map(relation -> new SkillRelationRow(
                        relation.getSourceNode().getNodeCode(),
                        relation.getTargetNode().getNodeCode(),
                        relation.getRelationType(),
                        relation.getLabel(),
                        relation.getSortOrder()
                ))
                .toList();
    }

    public List<SkillResourceRow> findAllSkillResources() {
        return skillNodeResourceJpaRepository.findAllByOrderByNode_NodeCodeAscSortOrderAscResourceCodeAsc().stream()
                .map(resource -> new SkillResourceRow(
                        resource.getResourceCode(),
                        resource.getNode().getNodeCode(),
                        resource.getResourceType(),
                        resource.getTitle(),
                        resource.getSourceLabel(),
                        resource.getDurationLabel(),
                        resource.getLinkUrl(),
                        resource.getSortOrder()
                ))
                .toList();
    }

    public List<SkillProgressRow> findProgressByStudentUserId(long userId) {
        return skillProgressJpaRepository.findByStudentUserId(userId).stream()
                .map(progress -> new SkillProgressRow(
                        progress.getNode().getNodeCode(),
                        progress.getProgressStatus(),
                        progress.getUpdatedAt()
                ))
                .toList();
    }

    public SkillProgressSummary countProgressSummary(long userId) {
        return new SkillProgressSummary(
                safeLongToInt(skillProgressJpaRepository.countByStudentUserIdAndProgressStatus(userId, "MASTERED")),
                safeLongToInt(skillProgressJpaRepository.countByStudentUserIdAndProgressStatus(userId, "LEARNING"))
        );
    }

    public void saveProgress(long userId, String nodeCode, String progressStatus) {
        SkillProgressEntity progress = skillProgressJpaRepository.findOneByStudentUserIdAndNode_NodeCode(userId, nodeCode)
                .orElseGet(() -> SkillProgressEntity.create(
                        userId,
                        skillNodeJpaRepository.getReferenceById(nodeCode)
                ));
        progress.setProgressStatus(progressStatus);
        skillProgressJpaRepository.save(progress);
    }

    public void deleteProgress(long userId, String nodeCode) {
        skillProgressJpaRepository.deleteByStudentUserIdAndNode_NodeCode(userId, nodeCode);
    }

    private SkillNodeRow toNodeRow(SkillNodeEntity entity) {
        return new SkillNodeRow(
                entity.getNodeCode(),
                entity.getLabel(),
                entity.getDescription(),
                entity.getParent() == null ? null : entity.getParent().getNodeCode(),
                entity.getSortOrder()
        );
    }

    private int safeLongToInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    public record SkillNodeRow(
            String nodeCode,
            String label,
            String description,
            String parentCode,
            int sortOrder
    ) {
    }

    public record SkillProgressRow(
            String nodeCode,
            String progressStatus,
            Instant updatedAt
    ) {
    }

    public record SkillProgressSummary(
            int masteredCount,
            int learningCount
    ) {
    }

    public record SkillRelationRow(
            String sourceNodeCode,
            String targetNodeCode,
            String relationType,
            String label,
            int sortOrder
    ) {
    }

    public record SkillResourceRow(
            String resourceCode,
            String nodeCode,
            String resourceType,
            String title,
            String sourceLabel,
            String durationLabel,
            String linkUrl,
            int sortOrder
    ) {
    }
}
