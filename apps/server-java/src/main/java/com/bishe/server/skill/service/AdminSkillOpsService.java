package com.bishe.server.skill.service;

import com.bishe.server.common.TimePayloads;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.common.util.TextListCodec;
import com.bishe.server.skill.dto.AdminSkillNodeCreateRequest;
import com.bishe.server.skill.dto.AdminSkillNodeUpdateRequest;
import com.bishe.server.skill.dto.AdminSkillRelationCreateRequest;
import com.bishe.server.skill.dto.AdminSkillRelationUpdateRequest;
import com.bishe.server.skill.dto.AdminSkillResourceCreateRequest;
import com.bishe.server.skill.dto.AdminSkillResourceUpdateRequest;
import com.bishe.server.skill.dto.SkillTreeResponse;
import com.bishe.server.skill.repository.AdminSkillOpsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AdminSkillOpsService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9_]{2,100}$");
    private static final Set<String> ALLOWED_RESOURCE_TYPES = Set.of("doc", "article", "video", "tool");
    private static final Set<String> ALLOWED_RELATION_TYPES = Set.of("ADVANCE_TO", "BRIDGE", "CO_LEARN");

    private final AdminSkillOpsRepository adminSkillOpsRepository;
    private final SkillNodeCacheService skillNodeCacheService;

    public AdminSkillOpsService(
            AdminSkillOpsRepository adminSkillOpsRepository,
            SkillNodeCacheService skillNodeCacheService
    ) {
        this.adminSkillOpsRepository = adminSkillOpsRepository;
        this.skillNodeCacheService = skillNodeCacheService;
    }

    public SkillOpsOverviewPayload getOverview() {
        List<SkillNodeOpsItem> nodes = listNodes().records();
        List<SkillResourceOpsItem> resources = listResources().records();
        long rootNodeCount = nodes.stream().filter(item -> item.parentCode() == null).count();
        long leafNodeCount = nodes.stream().filter(item -> item.childCount() == 0).count();
        long nodesWithoutResources = nodes.stream().filter(item -> item.resourceCount() == 0).count();
        long relationCount = nodes.stream().mapToLong(SkillNodeOpsItem::outboundRelationCount).sum();
        return new SkillOpsOverviewPayload(
                nodes.size(),
                rootNodeCount,
                leafNodeCount,
                relationCount,
                resources.size(),
                nodesWithoutResources
        );
    }

    public SkillNodeListPayload listNodes() {
        return new SkillNodeListPayload(
                adminSkillOpsRepository.findAllNodes().stream()
                        .map(this::toNodeItem)
                        .toList()
        );
    }

    public SkillTreeResponse getPreviewTree() {
        // 管理端预览复用学生星图响应结构，但所有节点默认解锁，便于直接检查布局和资源。
        List<SkillNodeOpsItem> nodes = listNodes().records().stream()
                .sorted(Comparator.comparingInt(SkillNodeOpsItem::sortOrder).thenComparing(SkillNodeOpsItem::nodeCode))
                .toList();
        Map<String, List<SkillTreeResponse.SkillResourceItem>> resourceMap = listResources().records().stream()
                .collect(Collectors.groupingBy(
                        SkillResourceOpsItem::nodeCode,
                        Collectors.mapping(this::toPreviewResourceItem, Collectors.toList())
                ));

        List<SkillTreeResponse.SkillNodeItem> previewNodes = nodes.stream()
                .map(node -> new SkillTreeResponse.SkillNodeItem(
                        node.nodeCode(),
                        node.label(),
                        node.description(),
                        node.parentCode(),
                        node.sortOrder(),
                        "NOT_STARTED",
                        true,
                        node.updatedAt(),
                        resourceMap.getOrDefault(node.nodeCode(), List.of())
                ))
                .toList();

        List<SkillTreeResponse.SkillRelationItem> previewRelations = adminSkillOpsRepository.findAllRelations().stream()
                .map(relation -> new SkillTreeResponse.SkillRelationItem(
                        relation.sourceNodeCode(),
                        relation.targetNodeCode(),
                        relation.relationType(),
                        relation.label(),
                        relation.sortOrder()
                ))
                .toList();

        return new SkillTreeResponse(
                previewNodes,
                previewRelations,
                new SkillTreeResponse.ProgressSummary(previewNodes.size(), 0, 0, previewNodes.size())
        );
    }

    public SkillResourceListPayload listResources() {
        return new SkillResourceListPayload(
                adminSkillOpsRepository.findAllResources().stream()
                        .map(this::toResourceItem)
                        .toList()
        );
    }

    @Transactional
    public SkillRelationOpsItem createRelation(AdminSkillRelationCreateRequest request) {
        String sourceNodeCode = normalizeCode(request.sourceNodeCode(), "sourceNodeCode");
        String targetNodeCode = normalizeCode(request.targetNodeCode(), "targetNodeCode");
        // 关系端点必须是两个不同节点，避免星图边线自环或指向空节点。
        validateRelationEndpoints(sourceNodeCode, targetNodeCode);
        ensureNodeExists(sourceNodeCode);
        ensureNodeExists(targetNodeCode);
        String relationType = normalizeRelationType(request.relationType());
        if (adminSkillOpsRepository.existsRelation(sourceNodeCode, targetNodeCode, relationType)) {
            throw new ApiException("BIZ-1001", "skill relation exists", HttpStatus.BAD_REQUEST);
        }
        String label = requireText(request.label(), 100, "label");
        int sortOrder = normalizeSortOrder(request.sortOrder());
        adminSkillOpsRepository.createRelation(sourceNodeCode, targetNodeCode, relationType, label, sortOrder);
        // 节点缓存包含关系拓扑，关系变更后需要立即失效学生端读取层。
        skillNodeCacheService.evictNow();
        skillNodeCacheService.evictAfterCommit();
        return toRelationItem(requireRelation(sourceNodeCode, targetNodeCode, relationType));
    }

    @Transactional
    public SkillRelationOpsItem updateRelation(
            String sourceNodeCode,
            String targetNodeCode,
            String relationType,
            AdminSkillRelationUpdateRequest request
    ) {
        String normalizedSourceNodeCode = normalizeCode(sourceNodeCode, "sourceNodeCode");
        String normalizedTargetNodeCode = normalizeCode(targetNodeCode, "targetNodeCode");
        String normalizedRelationType = normalizeRelationType(relationType);
        requireRelation(normalizedSourceNodeCode, normalizedTargetNodeCode, normalizedRelationType);

        // 更新允许同时改端点和类型，因此要按新三元组重新做唯一性校验。
        String nextSourceNodeCode = normalizeCode(request.sourceNodeCode(), "sourceNodeCode");
        String nextTargetNodeCode = normalizeCode(request.targetNodeCode(), "targetNodeCode");
        validateRelationEndpoints(nextSourceNodeCode, nextTargetNodeCode);
        ensureNodeExists(nextSourceNodeCode);
        ensureNodeExists(nextTargetNodeCode);
        String nextRelationType = normalizeRelationType(request.relationType());
        if (
                (!normalizedSourceNodeCode.equals(nextSourceNodeCode)
                        || !normalizedTargetNodeCode.equals(nextTargetNodeCode)
                        || !normalizedRelationType.equals(nextRelationType))
                        && adminSkillOpsRepository.existsRelation(nextSourceNodeCode, nextTargetNodeCode, nextRelationType)
        ) {
            throw new ApiException("BIZ-1001", "skill relation exists", HttpStatus.BAD_REQUEST);
        }
        String label = requireText(request.label(), 100, "label");
        int sortOrder = normalizeSortOrder(request.sortOrder());
        adminSkillOpsRepository.updateRelation(
                normalizedSourceNodeCode,
                normalizedTargetNodeCode,
                normalizedRelationType,
                nextSourceNodeCode,
                nextTargetNodeCode,
                nextRelationType,
                label,
                sortOrder
        );
        skillNodeCacheService.evictNow();
        skillNodeCacheService.evictAfterCommit();
        return toRelationItem(requireRelation(nextSourceNodeCode, nextTargetNodeCode, nextRelationType));
    }

    @Transactional
    public void deleteRelation(String sourceNodeCode, String targetNodeCode, String relationType) {
        String normalizedSourceNodeCode = normalizeCode(sourceNodeCode, "sourceNodeCode");
        String normalizedTargetNodeCode = normalizeCode(targetNodeCode, "targetNodeCode");
        String normalizedRelationType = normalizeRelationType(relationType);
        requireRelation(normalizedSourceNodeCode, normalizedTargetNodeCode, normalizedRelationType);
        adminSkillOpsRepository.deleteRelation(normalizedSourceNodeCode, normalizedTargetNodeCode, normalizedRelationType);
        skillNodeCacheService.evictNow();
        skillNodeCacheService.evictAfterCommit();
    }

    @Transactional
    public SkillNodeOpsItem createNode(AdminSkillNodeCreateRequest request) {
        String nodeCode = normalizeCode(request.nodeCode(), "nodeCode");
        if (adminSkillOpsRepository.existsNodeCode(nodeCode)) {
            throw new ApiException("BIZ-1001", "skill node code exists", HttpStatus.BAD_REQUEST);
        }
        String label = requireText(request.label(), 100, "label");
        String description = normalizeOptionalText(request.description(), 500);
        String parentCode = normalizeOptionalCode(request.parentCode(), "parentCode");
        if (parentCode != null) {
            // 父节点只保存一层 code 引用，创建时先挡住自引用和悬空父节点。
            if (nodeCode.equals(parentCode)) {
                throw new ApiException("BIZ-1001", "parentCode invalid", HttpStatus.BAD_REQUEST);
            }
            ensureNodeExists(parentCode);
        }
        int sortOrder = normalizeSortOrder(request.sortOrder());
        adminSkillOpsRepository.createNode(nodeCode, label, description, parentCode, sortOrder);
        skillNodeCacheService.evictNow();
        skillNodeCacheService.evictAfterCommit();
        return toNodeItem(requireNode(nodeCode));
    }

    @Transactional
    public SkillNodeOpsItem updateNode(String nodeCode, AdminSkillNodeUpdateRequest request) {
        String normalizedNodeCode = normalizeCode(nodeCode, "nodeCode");
        requireNode(normalizedNodeCode);
        String label = requireText(request.label(), 100, "label");
        String description = normalizeOptionalText(request.description(), 500);
        int sortOrder = normalizeSortOrder(request.sortOrder());
        adminSkillOpsRepository.updateNode(normalizedNodeCode, label, description, sortOrder);
        skillNodeCacheService.evictNow();
        skillNodeCacheService.evictAfterCommit();
        return toNodeItem(requireNode(normalizedNodeCode));
    }

    @Transactional
    public void deleteNode(String nodeCode) {
        String normalizedNodeCode = normalizeCode(nodeCode, "nodeCode");
        AdminSkillOpsRepository.AdminSkillNodeRow node = requireNode(normalizedNodeCode);
        // 删除节点前必须清空子节点、资源和入出边，保证学生进度和星图拓扑不残留孤儿数据。
        if (node.childCount() > 0) {
            throw new ApiException("BIZ-1001", "skill node has child nodes", HttpStatus.BAD_REQUEST);
        }
        if (node.resourceCount() > 0) {
            throw new ApiException("BIZ-1001", "skill node has resources", HttpStatus.BAD_REQUEST);
        }
        if (node.outboundRelationCount() > 0 || node.inboundRelationCount() > 0) {
            throw new ApiException("BIZ-1001", "skill node has relations", HttpStatus.BAD_REQUEST);
        }
        adminSkillOpsRepository.deleteNode(normalizedNodeCode);
        skillNodeCacheService.evictNow();
        skillNodeCacheService.evictAfterCommit();
    }

    @Transactional
    public SkillResourceOpsItem createResource(AdminSkillResourceCreateRequest request) {
        String resourceCode = normalizeCode(request.resourceCode(), "resourceCode");
        if (adminSkillOpsRepository.existsResourceCode(resourceCode)) {
            throw new ApiException("BIZ-1001", "skill resource code exists", HttpStatus.BAD_REQUEST);
        }
        // 资源只能挂到已存在节点，类型限定在前端可识别的展示集合里。
        String nodeCode = normalizeCode(request.nodeCode(), "nodeCode");
        ensureNodeExists(nodeCode);
        String resourceType = normalizeResourceType(request.resourceType());
        String title = requireText(request.title(), 200, "title");
        String sourceLabel = requireText(request.sourceLabel(), 100, "sourceLabel");
        String durationLabel = requireText(request.durationLabel(), 50, "durationLabel");
        String linkUrl = requireText(request.linkUrl(), 500, "linkUrl");
        int sortOrder = normalizeSortOrder(request.sortOrder());
        adminSkillOpsRepository.createResource(resourceCode, nodeCode, resourceType, title, sourceLabel, durationLabel, linkUrl, sortOrder);
        return toResourceItem(requireResource(resourceCode));
    }

    @Transactional
    public SkillResourceOpsItem updateResource(String resourceCode, AdminSkillResourceUpdateRequest request) {
        String normalizedResourceCode = normalizeCode(resourceCode, "resourceCode");
        requireResource(normalizedResourceCode);
        String nodeCode = normalizeCode(request.nodeCode(), "nodeCode");
        ensureNodeExists(nodeCode);
        String resourceType = normalizeResourceType(request.resourceType());
        String title = requireText(request.title(), 200, "title");
        String sourceLabel = requireText(request.sourceLabel(), 100, "sourceLabel");
        String durationLabel = requireText(request.durationLabel(), 50, "durationLabel");
        String linkUrl = requireText(request.linkUrl(), 500, "linkUrl");
        int sortOrder = normalizeSortOrder(request.sortOrder());
        adminSkillOpsRepository.updateResource(normalizedResourceCode, nodeCode, resourceType, title, sourceLabel, durationLabel, linkUrl, sortOrder);
        return toResourceItem(requireResource(normalizedResourceCode));
    }

    private AdminSkillOpsRepository.AdminSkillNodeRow requireNode(String nodeCode) {
        return adminSkillOpsRepository.findNodeByCode(nodeCode)
                .orElseThrow(() -> new ApiException("BIZ-1002", "skill node not found", HttpStatus.NOT_FOUND));
    }

    private AdminSkillOpsRepository.AdminSkillResourceRow requireResource(String resourceCode) {
        return adminSkillOpsRepository.findResourceByCode(resourceCode)
                .orElseThrow(() -> new ApiException("BIZ-1002", "skill resource not found", HttpStatus.NOT_FOUND));
    }

    private AdminSkillOpsRepository.AdminSkillRelationRow requireRelation(String sourceNodeCode, String targetNodeCode, String relationType) {
        return adminSkillOpsRepository.findRelation(sourceNodeCode, targetNodeCode, relationType)
                .orElseThrow(() -> new ApiException("BIZ-1002", "skill relation not found", HttpStatus.NOT_FOUND));
    }

    private void ensureNodeExists(String nodeCode) {
        if (!adminSkillOpsRepository.existsNodeCode(nodeCode)) {
            throw new ApiException("BIZ-1002", "skill node not found", HttpStatus.NOT_FOUND);
        }
    }

    private SkillNodeOpsItem toNodeItem(AdminSkillOpsRepository.AdminSkillNodeRow row) {
        return new SkillNodeOpsItem(
                row.nodeCode(),
                row.label(),
                row.description(),
                row.parentCode(),
                row.parentLabel(),
                row.sortOrder(),
                row.childCount(),
                row.resourceCount(),
                row.outboundRelationCount(),
                row.inboundRelationCount(),
                toTimePayload(row.updatedAt())
        );
    }

    private SkillResourceOpsItem toResourceItem(AdminSkillOpsRepository.AdminSkillResourceRow row) {
        return new SkillResourceOpsItem(
                row.resourceCode(),
                row.nodeCode(),
                row.nodeLabel(),
                row.resourceType(),
                row.title(),
                row.sourceLabel(),
                row.durationLabel(),
                row.linkUrl(),
                row.sortOrder(),
                toTimePayload(row.updatedAt())
        );
    }

    private SkillTreeResponse.SkillResourceItem toPreviewResourceItem(SkillResourceOpsItem item) {
        return new SkillTreeResponse.SkillResourceItem(
                item.resourceCode(),
                item.resourceType(),
                item.title(),
                item.sourceLabel(),
                item.durationLabel(),
                item.linkUrl(),
                item.sortOrder()
        );
    }

    private SkillRelationOpsItem toRelationItem(AdminSkillOpsRepository.AdminSkillRelationRow row) {
        return new SkillRelationOpsItem(
                row.sourceNodeCode(),
                row.sourceLabel(),
                row.targetNodeCode(),
                row.targetLabel(),
                row.relationType(),
                row.label(),
                row.sortOrder()
        );
    }

    private String normalizeCode(String value, String fieldName) {
        String normalized = TextListCodec.normalizeText(value);
        if (normalized == null || !CODE_PATTERN.matcher(normalized).matches()) {
            throw new ApiException("BIZ-1001", fieldName + " invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalCode(String value, String fieldName) {
        String normalized = TextListCodec.normalizeText(value);
        if (normalized == null) {
            return null;
        }
        return normalizeCode(normalized, fieldName);
    }

    private String normalizeResourceType(String value) {
        String normalized = TextListCodec.normalizeText(value);
        if (normalized == null) {
            throw new ApiException("BIZ-1001", "resourceType invalid", HttpStatus.BAD_REQUEST);
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!ALLOWED_RESOURCE_TYPES.contains(lower)) {
            throw new ApiException("BIZ-1001", "resourceType invalid", HttpStatus.BAD_REQUEST);
        }
        return lower;
    }

    private String normalizeRelationType(String value) {
        String normalized = TextListCodec.normalizeText(value);
        if (normalized == null) {
            throw new ApiException("BIZ-1001", "relationType invalid", HttpStatus.BAD_REQUEST);
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!ALLOWED_RELATION_TYPES.contains(upper)) {
            throw new ApiException("BIZ-1001", "relationType invalid", HttpStatus.BAD_REQUEST);
        }
        return upper;
    }

    private void validateRelationEndpoints(String sourceNodeCode, String targetNodeCode) {
        if (sourceNodeCode.equals(targetNodeCode)) {
            throw new ApiException("BIZ-1001", "skill relation endpoints invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private String requireText(String value, int maxLength, String fieldName) {
        String normalized = normalizeOptionalText(value, maxLength);
        if (normalized == null) {
            throw new ApiException("BIZ-1001", fieldName + " required", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeOptionalText(String value, int maxLength) {
        String normalized = TextListCodec.normalizeText(value);
        if (normalized == null) {
            return null;
        }
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private int normalizeSortOrder(Integer value) {
        if (value == null) {
            return 0;
        }
        return Math.max(0, value);
    }

    private Long toTimePayload(Instant value) {
        return TimePayloads.toEpochMillis(value);
    }

    public record SkillOpsOverviewPayload(
            long totalNodeCount,
            long rootNodeCount,
            long leafNodeCount,
            long relationCount,
            long totalResourceCount,
            long nodesWithoutResourceCount
    ) {
    }

    public record SkillNodeListPayload(
            List<SkillNodeOpsItem> records
    ) {
    }

    public record SkillResourceListPayload(
            List<SkillResourceOpsItem> records
    ) {
    }

    public record SkillNodeOpsItem(
            String nodeCode,
            String label,
            String description,
            String parentCode,
            String parentLabel,
            int sortOrder,
            int childCount,
            int resourceCount,
            int outboundRelationCount,
            int inboundRelationCount,
            Long updatedAt
    ) {
    }

    public record SkillResourceOpsItem(
            String resourceCode,
            String nodeCode,
            String nodeLabel,
            String resourceType,
            String title,
            String sourceLabel,
            String durationLabel,
            String linkUrl,
            int sortOrder,
            Long updatedAt
    ) {
    }

    public record SkillRelationOpsItem(
            String sourceNodeCode,
            String sourceLabel,
            String targetNodeCode,
            String targetLabel,
            String relationType,
            String label,
            int sortOrder
    ) {
    }
}
