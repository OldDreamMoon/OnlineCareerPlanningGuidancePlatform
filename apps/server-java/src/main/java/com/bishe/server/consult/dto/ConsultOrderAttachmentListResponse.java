package com.bishe.server.consult.dto;

import java.util.List;

/**
 * 咨询订单材料列表响应。
 */
public record ConsultOrderAttachmentListResponse(
        List<ConsultOrderAttachmentItem> records,
        int currentAttachmentCount
) {
}
