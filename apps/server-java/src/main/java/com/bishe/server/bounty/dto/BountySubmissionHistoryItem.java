package com.bishe.server.bounty.dto;

public record BountySubmissionHistoryItem(
        long eventId,
        String eventType,
        Long occurredAt,
        String comment,
        String contactIntent,
        String rejectTemplate,
        String note
) {
}
