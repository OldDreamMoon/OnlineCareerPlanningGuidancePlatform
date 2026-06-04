package com.bishe.server.community.repository.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 社区列表/详情所需的导师资料轻量实体。
 */
@Entity
@Table(name = "mentor_profiles")
public class CommunityMentorProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "show_real_name", nullable = false)
    private boolean showRealName;

    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;

    @Column(name = "avatar_object_key", length = 255)
    private String avatarObjectKey;

    @Column(name = "avatar_updated_at")
    private Instant avatarUpdatedAt;

    protected CommunityMentorProfileEntity() {
    }

    public Long getUserId() {
        return userId;
    }

    public boolean isShowRealName() {
        return showRealName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getAvatarObjectKey() {
        return avatarObjectKey;
    }

    public Instant getAvatarUpdatedAt() {
        return avatarUpdatedAt;
    }
}
