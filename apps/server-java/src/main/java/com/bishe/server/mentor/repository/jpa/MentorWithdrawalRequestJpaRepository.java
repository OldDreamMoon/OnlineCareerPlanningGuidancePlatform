package com.bishe.server.mentor.repository.jpa;

import com.bishe.server.mentor.repository.jpa.entity.MentorWithdrawalRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 导师提现申请 JPA 仓储。
 */
public interface MentorWithdrawalRequestJpaRepository extends JpaRepository<MentorWithdrawalRequestEntity, Long> {

    List<MentorWithdrawalRequestEntity> findByMentorUserIdOrderByCreatedAtDescIdDesc(long mentorUserId);

    List<MentorWithdrawalRequestEntity> findByMentorUserIdInOrderByMentorUserIdAscIdDesc(Collection<Long> mentorUserIds);

    Optional<MentorWithdrawalRequestEntity> findByMentorUserIdAndId(long mentorUserId, long id);

    @Query("""
            select coalesce(sum(entity.amountFen), 0)
              from MentorWithdrawalRequestEntity entity
             where entity.mentorUserId = :mentorUserId
               and entity.status in :statuses
            """)
    Long sumAmountFenByMentorUserIdAndStatusIn(
            @Param("mentorUserId") long mentorUserId,
            @Param("statuses") Collection<String> statuses
    );
}
