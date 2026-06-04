package com.bishe.server.consult.dto;

import java.util.List;

/**
 * 咨询消息列表响应。
 */
public record ConsultMessageListResponse(
        List<MessageItem> records
) {

    /**
     * 消息项。
     */
    public record MessageItem(
            long id,
            long senderUserId,
            String senderDisplayName,
            String senderRole,
            String messageText,
            Long createdAt
    ) {
    }
}
