package com.bishe.server.skill.repository.jpa;

import com.bishe.server.skill.repository.jpa.entity.SkillProgressEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 学生技能进度 JPA 仓储。
 */
public interface SkillProgressJpaRepository extends JpaRepository<SkillProgressEntity, Long> {

    @EntityGraph(attributePaths = {"node"})
    List<SkillProgressEntity> findByStudentUserId(long studentUserId);

    @EntityGraph(attributePaths = {"node"})
    Optional<SkillProgressEntity> findOneByStudentUserIdAndNode_NodeCode(long studentUserId, String nodeCode);

    long countByStudentUserIdAndProgressStatus(long studentUserId, String progressStatus);

    long countByStudentUserIdAndUpdatedAtBetween(long studentUserId, Instant startAt, Instant endAt);

    void deleteByStudentUserIdAndNode_NodeCode(long studentUserId, String nodeCode);
}
