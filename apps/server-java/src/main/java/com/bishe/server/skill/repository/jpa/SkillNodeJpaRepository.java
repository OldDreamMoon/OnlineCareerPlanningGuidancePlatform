package com.bishe.server.skill.repository.jpa;

import com.bishe.server.skill.repository.jpa.entity.SkillNodeEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 技能节点 JPA 仓储。
 */
public interface SkillNodeJpaRepository extends JpaRepository<SkillNodeEntity, String> {

    @EntityGraph(attributePaths = {"parent"})
    List<SkillNodeEntity> findAllByOrderBySortOrderAscNodeCodeAsc();

    @EntityGraph(attributePaths = {"parent"})
    Optional<SkillNodeEntity> findOneByNodeCode(String nodeCode);

    boolean existsByNodeCode(String nodeCode);

    long countByParent_NodeCode(String parentCode);
}
