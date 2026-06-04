package com.bishe.server.profile.repository.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 面试消息只读计数实体。
 */
@Entity
@Table(name = "interview_messages")
public class InterviewMessageRefEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_pk", nullable = false)
    private InterviewSessionRefEntity session;

    @Column(name = "sender_role", nullable = false, length = 20)
    private String senderRole;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InterviewMessageRefEntity() {
    }
}
