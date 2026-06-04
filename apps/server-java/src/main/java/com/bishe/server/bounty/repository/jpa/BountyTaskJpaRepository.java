package com.bishe.server.bounty.repository.jpa;

import com.bishe.server.bounty.repository.jpa.entity.BountyTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BountyTaskJpaRepository extends JpaRepository<BountyTaskEntity, Long>, JpaSpecificationExecutor<BountyTaskEntity> {

    List<BountyTaskEntity> findByEnterpriseUserIdOrderByCreatedAtDescIdDesc(long enterpriseUserId);

    @Query("""
            select task.id
              from BountyTaskEntity task
             where task.enterpriseUserId = :enterpriseUserId
          order by task.id asc
            """)
    List<Long> findIdsByEnterpriseUserId(@Param("enterpriseUserId") long enterpriseUserId);
}
