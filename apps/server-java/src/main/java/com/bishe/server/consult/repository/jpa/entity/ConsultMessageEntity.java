package com.bishe.server.consult.repository.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
        name = "consult_messages",
        indexes = {
                @Index(name = "idx_consult_messages_order_time", columnList = "order_no, created_at"),
                @Index(name = "idx_consult_messages_order_id_time", columnList = "order_id, created_at")
        }
)
public class ConsultMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 64)
    private String orderNo;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "sender_user_id", nullable = false)
    private Long senderUserId;

    @Column(name = "sender_role", nullable = false, length = 20)
    private String senderRole;

    @Column(name = "message_text", nullable = false, columnDefinition = "TEXT")
    private String messageText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ConsultMessageEntity() {
    }

    public static ConsultMessageEntity create(Long orderId, String orderNo, long senderUserId, String senderRole, String messageText) {
        ConsultMessageEntity entity = new ConsultMessageEntity();
        entity.orderId = orderId;
        entity.orderNo = orderNo;
        entity.senderUserId = senderUserId;
        entity.senderRole = senderRole;
        entity.messageText = messageText;
        return entity;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getSenderUserId() {
        return senderUserId;
    }

    public String getSenderRole() {
        return senderRole;
    }

    public String getMessageText() {
        return messageText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
