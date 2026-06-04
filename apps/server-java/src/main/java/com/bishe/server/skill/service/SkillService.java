package com.bishe.server.skill.service;

import com.bishe.server.common.TimePayloads;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.growth.service.GrowthCenterCacheService;
import com.bishe.server.profile.service.StudentPortraitRefreshService;
import com.bishe.server.skill.dto.SkillProgressUpdateRequest;
import com.bishe.server.skill.dto.SkillProgressUpdateResponse;
import com.bishe.server.skill.dto.SkillTreeResponse;
import com.bishe.server.skill.repository.SkillRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 技能树与学习进度服务。
 */
@Service
public class SkillService {

    private final SkillRepository skillRepository;
    private final SkillNodeCacheService skillNodeCacheService;
    private final StudentPortraitRefreshService studentPortraitRefreshService;
    private final GrowthCenterCacheService growthCenterCacheService;

    public SkillService(
            SkillRepository skillRepository,
            SkillNodeCacheService skillNodeCacheService,
            StudentPortraitRefreshService studentPortraitRefreshService,
            GrowthCenterCacheService growthCenterCacheService
    ) {
        this.skillRepository = skillRepository;
        this.skillNodeCacheService = skillNodeCacheService;
        this.studentPortraitRefreshService = studentPortraitRefreshService;
        this.growthCenterCacheService = growthCenterCacheService;
    }

    public SkillTreeResponse getSkillTree(long userId) {
        ensureStudentExists(userId);

        // 节点走缓存，用户进度、资源和关系实时合成，避免公共树被 viewer 状态污染。
        List<SkillRepository.SkillNodeRow> skillNodes = getCachedSkillNodes();
        Map<String, SkillRepository.SkillNodeRow> nodeMap = skillNodes.stream()
                .collect(Collectors.toMap(SkillRepository.SkillNodeRow::nodeCode, Function.identity()));
        Map<String, SkillRepository.SkillProgressRow> progressMap = skillRepository.findProgressByStudentUserId(userId).stream()
                .collect(Collectors.toMap(SkillRepository.SkillProgressRow::nodeCode, Function.identity()));
        Map<String, List<SkillTreeResponse.SkillResourceItem>> resourceMap = skillRepository.findAllSkillResources().stream()
                .filter(resource -> nodeMap.containsKey(resource.nodeCode()))
                .collect(Collectors.groupingBy(
                        SkillRepository.SkillResourceRow::nodeCode,
                        Collectors.mapping(this::toResourceItem, Collectors.toList())
                ));

        List<SkillTreeResponse.SkillNodeItem> nodes = skillNodes.stream()
                .sorted(Comparator.comparingInt(SkillRepository.SkillNodeRow::sortOrder).thenComparing(SkillRepository.SkillNodeRow::nodeCode))
                .map(node -> toNodeItem(node, nodeMap, progressMap, resourceMap))
                .toList();

        List<SkillTreeResponse.SkillRelationItem> relations = skillRepository.findAllSkillRelations().stream()
                .filter(relation -> nodeMap.containsKey(relation.sourceNodeCode()) && nodeMap.containsKey(relation.targetNodeCode()))
                .map(relation -> new SkillTreeResponse.SkillRelationItem(
                        relation.sourceNodeCode(),
                        relation.targetNodeCode(),
                        relation.relationType(),
                        relation.label(),
                        relation.sortOrder()
                ))
                .toList();

        SkillRepository.SkillProgressSummary summary = skillRepository.countProgressSummary(userId);
        int total = skillNodes.size();
        int notStarted = Math.max(total - summary.masteredCount() - summary.learningCount(), 0);

        return new SkillTreeResponse(
                nodes,
                relations,
                new SkillTreeResponse.ProgressSummary(total, summary.masteredCount(), summary.learningCount(), notStarted)
        );
    }

    public SkillProgressUpdateResponse updateProgress(long userId, SkillProgressUpdateRequest request) {
        ensureStudentExists(userId);

        // 更新进度前先拿全量树，前置技能和子节点回退保护都依赖同一份结构快照。
        SkillProgressStatus targetStatus = parseTargetStatus(request.targetStatus());
        List<SkillRepository.SkillNodeRow> allNodes = getCachedSkillNodes();
        Map<String, SkillRepository.SkillNodeRow> nodeMap = allNodes.stream()
                .collect(Collectors.toMap(SkillRepository.SkillNodeRow::nodeCode, Function.identity()));
        SkillRepository.SkillNodeRow node = nodeMap.get(request.nodeId());
        if (node == null) {
            throw new ApiException("BIZ-1002", "skill node not found", HttpStatus.NOT_FOUND);
        }
        Map<String, SkillProgressStatus> statusMap = skillRepository.findProgressByStudentUserId(userId).stream()
                .collect(Collectors.toMap(
                        SkillRepository.SkillProgressRow::nodeCode,
                        row -> parseStoredStatus(row.progressStatus())
                ));

        validatePrerequisite(node, nodeMap, statusMap, targetStatus);
        validateDescendants(node.nodeCode(), allNodes, statusMap, targetStatus);

        if (targetStatus == SkillProgressStatus.NOT_STARTED) {
            skillRepository.deleteProgress(userId, node.nodeCode());
        } else {
            skillRepository.saveProgress(userId, node.nodeCode(), targetStatus.name());
        }
        // 技能进度是画像和成长任务的重要事实源，写入后同步刷新并失效相关缓存。
        studentPortraitRefreshService.refreshNow(userId);
        growthCenterCacheService.evictDailyTasksNow(userId);
        growthCenterCacheService.evictDailyTasksAfterCommit(userId);

        return new SkillProgressUpdateResponse(node.nodeCode(), targetStatus.name(), true);
    }

    private void ensureStudentExists(long userId) {
        if (!skillRepository.existsStudentUser(userId)) {
            throw new ApiException("BIZ-1002", "student not found", HttpStatus.NOT_FOUND);
        }
    }

    private List<SkillRepository.SkillNodeRow> getCachedSkillNodes() {
        return skillNodeCacheService.getAllNodes(skillRepository::findAllSkillNodes);
    }

    private SkillTreeResponse.SkillNodeItem toNodeItem(
            SkillRepository.SkillNodeRow node,
            Map<String, SkillRepository.SkillNodeRow> nodeMap,
            Map<String, SkillRepository.SkillProgressRow> progressMap,
            Map<String, List<SkillTreeResponse.SkillResourceItem>> resourceMap
    ) {
        SkillRepository.SkillProgressRow progressRow = progressMap.get(node.nodeCode());
        String status = progressRow == null ? SkillProgressStatus.NOT_STARTED.name() : progressRow.progressStatus();
        boolean unlocked = isNodeUnlocked(node, nodeMap, progressMap);
        return new SkillTreeResponse.SkillNodeItem(
                node.nodeCode(),
                node.label(),
                node.description(),
                node.parentCode(),
                node.sortOrder(),
                status,
                unlocked,
                progressRow == null ? null : TimePayloads.toEpochMillis(progressRow.updatedAt()),
                resourceMap.getOrDefault(node.nodeCode(), List.of())
        );
    }

    private SkillTreeResponse.SkillResourceItem toResourceItem(SkillRepository.SkillResourceRow resource) {
        return new SkillTreeResponse.SkillResourceItem(
                resource.resourceCode(),
                resource.resourceType(),
                resource.title(),
                resource.sourceLabel(),
                resource.durationLabel(),
                resource.linkUrl(),
                resource.sortOrder()
        );
    }

    private boolean isNodeUnlocked(
            SkillRepository.SkillNodeRow node,
            Map<String, SkillRepository.SkillNodeRow> nodeMap,
            Map<String, SkillRepository.SkillProgressRow> progressMap
    ) {
        if (node.parentCode() == null || node.parentCode().isBlank()) {
            return true;
        }

        // 已有进度的节点保持可见，避免树结构调整后把历史学习记录隐藏掉。
        SkillRepository.SkillProgressRow currentProgress = progressMap.get(node.nodeCode());
        if (currentProgress != null && currentProgress.progressStatus() != null && !currentProgress.progressStatus().isBlank()) {
            return true;
        }

        // 一级子节点默认解锁，深层节点要求父节点掌握后再推进。
        SkillRepository.SkillNodeRow parentNode = nodeMap.get(node.parentCode());
        if (parentNode != null && (parentNode.parentCode() == null || parentNode.parentCode().isBlank())) {
            return true;
        }

        SkillRepository.SkillProgressRow parentProgress = progressMap.get(node.parentCode());
        return parentProgress != null && Objects.equals(parentProgress.progressStatus(), SkillProgressStatus.MASTERED.name());
    }

    private void validatePrerequisite(
            SkillRepository.SkillNodeRow node,
            Map<String, SkillRepository.SkillNodeRow> nodeMap,
            Map<String, SkillProgressStatus> statusMap,
            SkillProgressStatus targetStatus
    ) {
        if (targetStatus == SkillProgressStatus.NOT_STARTED) {
            return;
        }
        if (node.parentCode() == null || node.parentCode().isBlank()) {
            return;
        }

        SkillRepository.SkillNodeRow parentNode = nodeMap.get(node.parentCode());
        if (parentNode == null) {
            throw new ApiException("BIZ-1203", "skill tree broken: parent node missing", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        if (parentNode.parentCode() == null || parentNode.parentCode().isBlank()) {
            return;
        }

        // 非一级节点必须等直接父节点 MASTERED，保证星图推进顺序和前端解锁态一致。
        SkillProgressStatus parentStatus = statusMap.getOrDefault(parentNode.nodeCode(), SkillProgressStatus.NOT_STARTED);
        if (parentStatus != SkillProgressStatus.MASTERED) {
            throw new ApiException("BIZ-1201", "prerequisite skill not mastered", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateDescendants(
            String nodeCode,
            List<SkillRepository.SkillNodeRow> allNodes,
            Map<String, SkillProgressStatus> statusMap,
            SkillProgressStatus targetStatus
    ) {
        if (targetStatus != SkillProgressStatus.NOT_STARTED) {
            return;
        }

        // 子技能仍在学习或已掌握时不允许回退父节点，防止进度树出现倒挂。
        boolean hasActiveChildren = allNodes.stream()
                .filter(node -> nodeCode.equals(node.parentCode()))
                .map(SkillRepository.SkillNodeRow::nodeCode)
                .map(code -> statusMap.getOrDefault(code, SkillProgressStatus.NOT_STARTED))
                .anyMatch(status -> status == SkillProgressStatus.LEARNING || status == SkillProgressStatus.MASTERED);
        if (hasActiveChildren) {
            throw new ApiException("BIZ-1202", "child skills still active", HttpStatus.BAD_REQUEST);
        }
    }

    private SkillProgressStatus parseTargetStatus(String rawValue) {
        try {
            return SkillProgressStatus.from(rawValue);
        } catch (IllegalArgumentException ex) {
            throw new ApiException("BIZ-1001", "targetStatus invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private SkillProgressStatus parseStoredStatus(String rawValue) {
        try {
            return SkillProgressStatus.from(rawValue);
        } catch (IllegalArgumentException ex) {
            // 历史坏数据按未开始处理，页面仍可正常渲染并允许重新写入。
            return SkillProgressStatus.NOT_STARTED;
        }
    }
}
