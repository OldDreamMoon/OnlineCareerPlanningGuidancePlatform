package com.bishe.server.community.repository.jpa;

import com.bishe.server.community.repository.jpa.entity.CommunityMentorProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CommunityMentorProfileJpaRepository extends JpaRepository<CommunityMentorProfileEntity, Long> {

    List<CommunityMentorProfileEntity> findAllByUserIdIn(Collection<Long> userIds);
}
