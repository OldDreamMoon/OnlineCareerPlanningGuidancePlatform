package com.bishe.server.dashboard.jpa;

import com.bishe.server.auth.model.UserRole;
import com.bishe.server.profile.repository.jpa.entity.StudentPortraitSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminDashboardStudentPortraitJpaRepository extends JpaRepository<StudentPortraitSnapshotEntity, Long> {

    @Query("""
            select count(snapshot)
              from StudentPortraitSnapshotEntity snapshot
             where snapshot.portraitTags is not null
               and trim(snapshot.portraitTags) <> ''
               and trim(snapshot.portraitTags) <> '[]'
               and exists (
                    select u.id
                      from UserAccountEntity u
                     where u.id = snapshot.studentUserId
                       and u.role = :role
                       and u.deleted = false
               )
            """)
    long countCompletedStudentPortraits(@Param("role") UserRole role);
}
