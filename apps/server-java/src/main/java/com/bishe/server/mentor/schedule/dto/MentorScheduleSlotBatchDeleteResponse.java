package com.bishe.server.mentor.schedule.dto;

/**
 * 批量清理导师可预约时段响应。
 */
public record MentorScheduleSlotBatchDeleteResponse(
        int matchedCount,
        int deletedCount,
        int lockedCount
) {
}
