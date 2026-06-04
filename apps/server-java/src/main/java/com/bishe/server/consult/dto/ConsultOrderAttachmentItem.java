package com.bishe.server.consult.dto;

/**
 * 咨询订单材料摘要。
 */
public record ConsultOrderAttachmentItem(
        long attachmentId,
        String attachmentType,
        String slotCode,
        String originalFilename,
        String description,
        String sourceStage,
        long sizeBytes,
        String lifecycleStatus,
        Long uploadedAt
) {
}
