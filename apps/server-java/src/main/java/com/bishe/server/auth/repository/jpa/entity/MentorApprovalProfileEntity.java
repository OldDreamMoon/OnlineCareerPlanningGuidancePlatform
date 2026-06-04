package com.bishe.server.auth.repository.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 导师认证状态只读实体。
 */
@Entity
@Table(name = "mentor_profiles")
public class MentorApprovalProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "approval_status", nullable = false, length = 20)
    private String approvalStatus;

    protected MentorApprovalProfileEntity() {
    }

    public Long getUserId() {
        return userId;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }
}
