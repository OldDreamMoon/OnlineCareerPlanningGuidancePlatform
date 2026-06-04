package com.bishe.server.mentor.repository.jpa;

import com.bishe.server.mentor.repository.jpa.entity.AdminMentorProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 管理员导师经营台画像 JPA 仓储。
 */
public interface AdminMentorProfileJpaRepository extends JpaRepository<AdminMentorProfileEntity, Long> {

    List<AdminMentorProfileEntity> findAllByUserIdIn(Collection<Long> userIds);
}
