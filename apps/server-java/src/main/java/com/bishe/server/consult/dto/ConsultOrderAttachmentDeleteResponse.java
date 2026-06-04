package com.bishe.server.consult.dto;

/**
 * 咨询订单材料删除响应。
 */
public record ConsultOrderAttachmentDeleteResponse(
        long attachmentId,
        boolean deleted,
        int currentAttachmentCount
) {
}
