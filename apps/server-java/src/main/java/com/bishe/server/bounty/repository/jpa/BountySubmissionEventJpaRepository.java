package com.bishe.server.bounty.repository.jpa;

import com.bishe.server.bounty.repository.jpa.entity.BountySubmissionEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BountySubmissionEventJpaRepository extends JpaRepository<BountySubmissionEventEntity, Long> {

    List<BountySubmissionEventEntity> findBySubmissionIdInOrderBySubmissionIdAscCreatedAtAscIdAsc(Collection<Long> submissionIds);
}
