package com.bishe.server.mentor.schedule.dto;

import java.util.List;

/**
 * 批量创建导师可预约时段响应。
 */
public record MentorScheduleSlotBatchCreateResponse(
        List<MentorScheduleSlotResponse> records,
        int createdCount,
        int skippedCount
) {
}
