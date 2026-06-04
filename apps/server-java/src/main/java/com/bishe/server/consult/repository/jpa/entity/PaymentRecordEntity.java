package com.bishe.server.consult.repository.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "payment_records",
        indexes = {
                @Index(name = "idx_payment_records_order_time", columnList = "order_no, created_at"),
                @Index(name = "idx_payment_records_order_id_time", columnList = "order_id, created_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_records_idempotency", columnNames = "idempotency_key")
        }
)
public class PaymentRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 64)
    private String orderNo;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "mode", nullable = false, length = 20)
    private String mode;

    @Column(name = "provider_trade_no", length = 128)
    private String providerTradeNo;

    @Column(name = "amount_fen", nullable = false)
    private Integer amountFen;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "raw_callback", columnDefinition = "TEXT")
    private String rawCallback;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentRecordEntity() {
    }

    public static PaymentRecordEntity create(
            Long orderId,
            String orderNo,
            String channel,
            String mode,
            String providerTradeNo,
            int amountFen,
            String status,
            String idempotencyKey,
            String rawCallback
    ) {
        PaymentRecordEntity entity = new PaymentRecordEntity();
        Instant now = Instant.now();
        entity.orderId = orderId;
        entity.orderNo = orderNo;
        entity.channel = channel;
        entity.mode = mode;
        entity.providerTradeNo = providerTradeNo;
        entity.amountFen = amountFen;
        entity.status = status;
        entity.idempotencyKey = idempotencyKey;
        entity.rawCallback = rawCallback;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
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

    public String getChannel() {
        return channel;
    }

    public String getMode() {
        return mode;
    }

    public String getProviderTradeNo() {
        return providerTradeNo;
    }

    public Integer getAmountFen() {
        return amountFen;
    }

    public String getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRawCallback() {
        return rawCallback;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
