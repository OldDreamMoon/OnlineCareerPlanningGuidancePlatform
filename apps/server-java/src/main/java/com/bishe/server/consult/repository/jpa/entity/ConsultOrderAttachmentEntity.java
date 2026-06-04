package com.bishe.server.consult.repository.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
        name = "consult_order_attachments",
        indexes = {
                @Index(name = "idx_consult_order_attachments_order_time", columnList = "order_no, created_at"),
                @Index(name = "idx_consult_order_attachments_order_lifecycle", columnList = "order_no, lifecycle_status"),
                @Index(name = "idx_consult_order_attachments_slot", columnList = "order_no, slot_code, lifecycle_status"),
                @Index(name = "idx_consult_order_attachments_order_id_time", columnList = "order_id, created_at"),
                @Index(name = "idx_consult_order_attachments_order_id_lifecycle", columnList = "order_id, lifecycle_status"),
                @Index(name = "idx_consult_order_attachments_order_id_slot", columnList = "order_id, slot_code, lifecycle_status")
        }
)
public class ConsultOrderAttachmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 64)
    private String orderNo;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "uploaded_by_user_id", nullable = false)
    private Long uploadedByUserId;

    @Column(name = "attachment_type", nullable = false, length = 40)
    private String attachmentType;

    @Column(name = "slot_code", nullable = false, length = 60)
    private String slotCode;

    @Column(name = "source_stage", nullable = false, length = 30)
    private String sourceStage;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "storage_bucket", nullable = false, length = 120)
    private String storageBucket;

    @Column(name = "object_key", nullable = false, length = 255)
    private String objectKey;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private String lifecycleStatus;

    @Column(name = "replaced_attachment_id")
    private Long replacedAttachmentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConsultOrderAttachmentEntity() {
    }

    public static ConsultOrderAttachmentEntity create(
            Long orderId,
            String orderNo,
            long uploadedByUserId,
            String attachmentType,
            String slotCode,
            String sourceStage,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String storageBucket,
            String objectKey,
            String description,
            String lifecycleStatus,
            Long replacedAttachmentId
    ) {
        ConsultOrderAttachmentEntity entity = new ConsultOrderAttachmentEntity();
        entity.orderId = orderId;
        entity.orderNo = orderNo;
        entity.uploadedByUserId = uploadedByUserId;
        entity.attachmentType = attachmentType;
        entity.slotCode = slotCode;
        entity.sourceStage = sourceStage;
        entity.originalFilename = originalFilename;
        entity.contentType = contentType;
        entity.sizeBytes = sizeBytes;
        entity.storageBucket = storageBucket;
        entity.objectKey = objectKey;
        entity.description = description;
        entity.lifecycleStatus = lifecycleStatus;
        entity.replacedAttachmentId = replacedAttachmentId;
        return entity;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (lifecycleStatus == null) {
            lifecycleStatus = "CURRENT";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean markSuperseded() {
        if (!"CURRENT".equals(lifecycleStatus)) {
            return false;
        }
        lifecycleStatus = "SUPERSEDED";
        updatedAt = Instant.now();
        return true;
    }

    public boolean markDeleted() {
        if (!"CURRENT".equals(lifecycleStatus)) {
            return false;
        }
        lifecycleStatus = "DELETED";
        updatedAt = Instant.now();
        return true;
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

    public Long getUploadedByUserId() {
        return uploadedByUserId;
    }

    public String getAttachmentType() {
        return attachmentType;
    }

    public String getSlotCode() {
        return slotCode;
    }

    public String getSourceStage() {
        return sourceStage;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageBucket() {
        return storageBucket;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getDescription() {
        return description;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public Long getReplacedAttachmentId() {
        return replacedAttachmentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
