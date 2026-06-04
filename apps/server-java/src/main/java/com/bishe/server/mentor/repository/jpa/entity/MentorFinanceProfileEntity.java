package com.bishe.server.mentor.repository.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * 导师财务概览只读画像实体。
 */
@Entity
@Table(name = "mentor_profiles")
public class MentorFinanceProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "avg_rating", nullable = false)
    private BigDecimal avgRating;

    protected MentorFinanceProfileEntity() {
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getAvgRating() {
        return avgRating;
    }
}
