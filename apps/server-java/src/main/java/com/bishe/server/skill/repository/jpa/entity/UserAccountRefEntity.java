package com.bishe.server.skill.repository.jpa.entity;

import com.bishe.server.auth.model.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 技能域只读用户引用实体，仅用于校验学生账号存在性。
 */
@Entity
@Table(name = "users")
public class UserAccountRefEntity {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    protected UserAccountRefEntity() {
    }

    public Long getId() {
        return id;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isDeleted() {
        return deleted;
    }
}
