package com.bishe.server.skill.repository.jpa;

import com.bishe.server.skill.repository.jpa.entity.SkillRelationEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 技能关系 JPA 仓储。
 */
public interface SkillRelationJpaRepository extends JpaRepository<SkillRelationEntity, Long> {

    @EntityGraph(attributePaths = {"sourceNode", "targetNode"})
    List<SkillRelationEntity> findAllByOrderBySortOrderAscSourceNode_NodeCodeAscTargetNode_NodeCodeAsc();

    @EntityGraph(attributePaths = {"sourceNode", "targetNode"})
    Optional<SkillRelationEntity> findOneBySourceNode_NodeCodeAndTargetNode_NodeCodeAndRelationType(
            String sourceNodeCode,
            String targetNodeCode,
            String relationType
    );

    boolean existsBySourceNode_NodeCodeAndTargetNode_NodeCodeAndRelationType(
            String sourceNodeCode,
            String targetNodeCode,
            String relationType
    );

    long countBySourceNode_NodeCode(String sourceNodeCode);

    long countByTargetNode_NodeCode(String targetNodeCode);
}
