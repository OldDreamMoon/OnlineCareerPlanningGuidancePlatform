package com.bishe.server.consult.dto;

import java.util.List;

/**
 * 咨询订单材料批量上传响应。
 */
public record ConsultOrderAttachmentBatchUploadResponse(
        List<ConsultOrderAttachmentItem> records,
        int currentAttachmentCount
) {
}
