package com.bishe.server.skill.repository;

import com.bishe.server.skill.repository.jpa.SkillNodeJpaRepository;
import com.bishe.server.skill.repository.jpa.SkillNodeResourceJpaRepository;
import com.bishe.server.skill.repository.jpa.SkillProgressJpaRepository;
import com.bishe.server.skill.repository.jpa.SkillRelationJpaRepository;
import com.bishe.server.skill.repository.jpa.entity.SkillNodeEntity;
import com.bishe.server.skill.repository.jpa.entity.SkillNodeResourceEntity;
import com.bishe.server.skill.repository.jpa.entity.SkillRelationEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 管理员技能资源治理仓储。
 */
@Repository
public class AdminSkillOpsRepository {

    private final SkillNodeJpaRepository skillNodeJpaRepository;
    private final SkillNodeResourceJpaRepository skillNodeResourceJpaRepository;
    private final SkillRelationJpaRepository skillRelationJpaRepository;
    @SuppressWarnings("unused")
    private final SkillProgressJpaRepository skillProgressJpaRepository;

    public AdminSkillOpsRepository(
            SkillNodeJpaRepository skillNodeJpaRepository,
            SkillNodeResourceJpaRepository skillNodeResourceJpaRepository,
            SkillRelationJpaRepository skillRelationJpaRepository,
            SkillProgressJpaRepository skillProgressJpaRepository
    ) {
        this.skillNodeJpaRepository = skillNodeJpaRepository;
        this.skillNodeResourceJpaRepository = skillNodeResourceJpaRepository;
        this.skillRelationJpaRepository = skillRelationJpaRepository;
        this.skillProgressJpaRepository = skillProgressJpaRepository;
    }

    public List<AdminSkillNodeRow> findAllNodes() {
        List<SkillNodeEntity> nodes = skillNodeJpaRepository.findAllByOrderBySortOrderAscNodeCodeAsc();
        List<SkillNodeResourceEntity> resources = skillNodeResourceJpaRepository.findAllByOrderByNode_NodeCodeAscSortOrderAscResourceCodeAsc();
        List<SkillRelationEntity> relations = skillRelationJpaRepository.findAllByOrderBySortOrderAscSourceNode_NodeCodeAscTargetNode_NodeCodeAsc();

        Map<String, Integer> childCountMap = nodes.stream()
                .filter(node -> node.getParent() != null)
                .collect(Collectors.toMap(
                        node -> node.getParent().getNodeCode(),
                        node -> 1,
                        AdminSkillOpsRepository::sumIntegers
                ));
        Map<String, Integer> resourceCountMap = resources.stream()
                .collect(Collectors.toMap(
                        resource -> resource.getNode().getNodeCode(),
                        resource -> 1,
                        AdminSkillOpsRepository::sumIntegers
                ));
        Map<String, Integer> outboundRelationCountMap = relations.stream()
                .collect(Collectors.toMap(
                        relation -> relation.getSourceNode().getNodeCode(),
                        relation -> 1,
                        AdminSkillOpsRepository::sumIntegers
                ));
        Map<String, Integer> inboundRelationCountMap = relations.stream()
                .collect(Collectors.toMap(
                        relation -> relation.getTargetNode().getNodeCode(),
                        relation -> 1,
                        AdminSkillOpsRepository::sumIntegers
                ));

        return nodes.stream()
                .map(node -> new AdminSkillNodeRow(
                        node.getNodeCode(),
                        node.getLabel(),
                        node.getDescription(),
                        node.getParent() == null ? null : node.getParent().getNodeCode(),
                        node.getParent() == null ? null : node.getParent().getLabel(),
                        node.getSortOrder(),
                        childCountMap.getOrDefault(node.getNodeCode(), 0),
                        resourceCountMap.getOrDefault(node.getNodeCode(), 0),
                        outboundRelationCountMap.getOrDefault(node.getNodeCode(), 0),
                        inboundRelationCountMap.getOrDefault(node.getNodeCode(), 0),
                        node.getUpdatedAt()
                ))
                .toList();
    }

    public Optional<AdminSkillNodeRow> findNodeByCode(String nodeCode) {
        return skillNodeJpaRepository.findOneByNodeCode(nodeCode)
                .map(node -> new AdminSkillNodeRow(
                        node.getNodeCode(),
                        node.getLabel(),
                        node.getDescription(),
                        node.getParent() == null ? null : node.getParent().getNodeCode(),
                        node.getParent() == null ? null : node.getParent().getLabel(),
                        node.getSortOrder(),
                        safeLongToInt(skillNodeJpaRepository.countByParent_NodeCode(nodeCode)),
                        safeLongToInt(skillNodeResourceJpaRepository.countByNode_NodeCode(nodeCode)),
                        safeLongToInt(skillRelationJpaRepository.countBySourceNode_NodeCode(nodeCode)),
                        safeLongToInt(skillRelationJpaRepository.countByTargetNode_NodeCode(nodeCode)),
                        node.getUpdatedAt()
                ));
    }

    public List<AdminSkillResourceRow> findAllResources() {
        return skillNodeResourceJpaRepository.findAllByOrderByNode_NodeCodeAscSortOrderAscResourceCodeAsc().stream()
                .map(this::toResourceRow)
                .toList();
    }

    public Optional<AdminSkillResourceRow> findResourceByCode(String resourceCode) {
        return skillNodeResourceJpaRepository.findOneByResourceCode(resourceCode).map(this::toResourceRow);
    }

    public List<AdminSkillRelationRow> findAllRelations() {
        return skillRelationJpaRepository.findAllByOrderBySortOrderAscSourceNode_NodeCodeAscTargetNode_NodeCodeAsc().stream()
                .map(this::toRelationRow)
                .toList();
    }

    public Optional<AdminSkillRelationRow> findRelation(String sourceNodeCode, String targetNodeCode, String relationType) {
        return skillRelationJpaRepository
                .findOneBySourceNode_NodeCodeAndTargetNode_NodeCodeAndRelationType(sourceNodeCode, targetNodeCode, relationType)
                .map(this::toRelationRow);
    }

    public boolean existsNodeCode(String nodeCode) {
        return skillNodeJpaRepository.existsByNodeCode(nodeCode);
    }

    public boolean existsResourceCode(String resourceCode) {
        return skillNodeResourceJpaRepository.existsByResourceCode(resourceCode);
    }

    public boolean existsRelation(String sourceNodeCode, String targetNodeCode, String relationType) {
        return skillRelationJpaRepository.existsBySourceNode_NodeCodeAndTargetNode_NodeCodeAndRelationType(
                sourceNodeCode,
                targetNodeCode,
                relationType
        );
    }

    public boolean createNode(String nodeCode, String label, String description, String parentCode, int sortOrder) {
        SkillNodeEntity node = SkillNodeEntity.create(
                nodeCode,
                label,
                description,
                parentCode == null ? null : skillNodeJpaRepository.getReferenceById(parentCode),
                sortOrder
        );
        skillNodeJpaRepository.save(node);
        return true;
    }

    public boolean updateNode(String nodeCode, String label, String description, int sortOrder) {
        SkillNodeEntity node = skillNodeJpaRepository.findOneByNodeCode(nodeCode).orElse(null);
        if (node == null) {
            return false;
        }
        node.setLabel(label);
        node.setDescription(description);
        node.setSortOrder(sortOrder);
        skillNodeJpaRepository.save(node);
        return true;
    }

    public boolean deleteNode(String nodeCode) {
        if (!skillNodeJpaRepository.existsByNodeCode(nodeCode)) {
            return false;
        }
        skillNodeJpaRepository.deleteById(nodeCode);
        return true;
    }

    public boolean createResource(
            String resourceCode,
            String nodeCode,
            String resourceType,
            String title,
            String sourceLabel,
            String durationLabel,
            String linkUrl,
            int sortOrder
    ) {
        SkillNodeResourceEntity resource = SkillNodeResourceEntity.create(
                resourceCode,
                skillNodeJpaRepository.getReferenceById(nodeCode),
                resourceType,
                title,
                sourceLabel,
                durationLabel,
                linkUrl,
                sortOrder
        );
        skillNodeResourceJpaRepository.save(resource);
        return true;
    }

    public boolean updateResource(
            String resourceCode,
            String nodeCode,
            String resourceType,
            String title,
            String sourceLabel,
            String durationLabel,
            String linkUrl,
            int sortOrder
    ) {
        SkillNodeResourceEntity resource = skillNodeResourceJpaRepository.findOneByResourceCode(resourceCode).orElse(null);
        if (resource == null) {
            return false;
        }
        resource.setNode(skillNodeJpaRepository.getReferenceById(nodeCode));
        resource.setResourceType(resourceType);
        resource.setTitle(title);
        resource.setSourceLabel(sourceLabel);
        resource.setDurationLabel(durationLabel);
        resource.setLinkUrl(linkUrl);
        resource.setSortOrder(sortOrder);
        skillNodeResourceJpaRepository.save(resource);
        return true;
    }

    public boolean createRelation(
            String sourceNodeCode,
            String targetNodeCode,
            String relationType,
            String label,
            int sortOrder
    ) {
        SkillRelationEntity relation = SkillRelationEntity.create(
                skillNodeJpaRepository.getReferenceById(sourceNodeCode),
                skillNodeJpaRepository.getReferenceById(targetNodeCode),
                relationType,
                label,
                sortOrder
        );
        skillRelationJpaRepository.save(relation);
        return true;
    }

    public boolean updateRelation(
            String sourceNodeCode,
            String targetNodeCode,
            String relationType,
            String nextSourceNodeCode,
            String nextTargetNodeCode,
            String nextRelationType,
            String label,
            int sortOrder
    ) {
        SkillRelationEntity relation = skillRelationJpaRepository
                .findOneBySourceNode_NodeCodeAndTargetNode_NodeCodeAndRelationType(sourceNodeCode, targetNodeCode, relationType)
                .orElse(null);
        if (relation == null) {
            return false;
        }
        relation.setSourceNode(skillNodeJpaRepository.getReferenceById(nextSourceNodeCode));
        relation.setTargetNode(skillNodeJpaRepository.getReferenceById(nextTargetNodeCode));
        relation.setRelationType(nextRelationType);
        relation.setLabel(label);
        relation.setSortOrder(sortOrder);
        skillRelationJpaRepository.save(relation);
        return true;
    }

    public boolean deleteRelation(String sourceNodeCode, String targetNodeCode, String relationType) {
        SkillRelationEntity relation = skillRelationJpaRepository
                .findOneBySourceNode_NodeCodeAndTargetNode_NodeCodeAndRelationType(sourceNodeCode, targetNodeCode, relationType)
                .orElse(null);
        if (relation == null) {
            return false;
        }
        skillRelationJpaRepository.delete(relation);
        return true;
    }

    private AdminSkillResourceRow toResourceRow(SkillNodeResourceEntity resource) {
        return new AdminSkillResourceRow(
                resource.getResourceCode(),
                resource.getNode().getNodeCode(),
                resource.getNode().getLabel(),
                resource.getResourceType(),
                resource.getTitle(),
                resource.getSourceLabel(),
                resource.getDurationLabel(),
                resource.getLinkUrl(),
                resource.getSortOrder(),
                resource.getUpdatedAt()
        );
    }

    private AdminSkillRelationRow toRelationRow(SkillRelationEntity relation) {
        return new AdminSkillRelationRow(
                relation.getSourceNode().getNodeCode(),
                relation.getSourceNode().getLabel(),
                relation.getTargetNode().getNodeCode(),
                relation.getTargetNode().getLabel(),
                relation.getRelationType(),
                relation.getLabel(),
                relation.getSortOrder()
        );
    }

    private int safeLongToInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static Integer sumIntegers(Integer left, Integer right) {
        return Integer.valueOf((left == null ? 0 : left) + (right == null ? 0 : right));
    }

    public record AdminSkillNodeRow(
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
            Instant updatedAt
    ) {
    }

    public record AdminSkillResourceRow(
            String resourceCode,
            String nodeCode,
            String nodeLabel,
            String resourceType,
            String title,
            String sourceLabel,
            String durationLabel,
            String linkUrl,
            int sortOrder,
            Instant updatedAt
    ) {
    }

    public record AdminSkillRelationRow(
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
