package com.bishe.server.mentor.repository.jpa;

import com.bishe.server.mentor.repository.jpa.entity.MentorProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 导师资料 JPA 仓储。
 */
public interface MentorProfileJpaRepository extends JpaRepository<MentorProfileEntity, Long> {

    Optional<MentorProfileEntity> findByUserId(long userId);

    List<MentorProfileEntity> findAllByUserIdIn(Collection<Long> userIds);
}
