package com.bishe.server.skill.repository.jpa;

import com.bishe.server.skill.repository.jpa.entity.SkillNodeResourceEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 技能节点资源 JPA 仓储。
 */
public interface SkillNodeResourceJpaRepository extends JpaRepository<SkillNodeResourceEntity, String> {

    @EntityGraph(attributePaths = {"node"})
    List<SkillNodeResourceEntity> findAllByOrderByNode_NodeCodeAscSortOrderAscResourceCodeAsc();

    @EntityGraph(attributePaths = {"node"})
    Optional<SkillNodeResourceEntity> findOneByResourceCode(String resourceCode);

    boolean existsByResourceCode(String resourceCode);

    long countByNode_NodeCode(String nodeCode);
}
