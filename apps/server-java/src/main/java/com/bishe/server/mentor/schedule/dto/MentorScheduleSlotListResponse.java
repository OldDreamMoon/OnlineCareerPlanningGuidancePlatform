package com.bishe.server.mentor.schedule.dto;

import java.util.List;

/**
 * 导师可预约时段列表响应。
 */
public record MentorScheduleSlotListResponse(
        List<MentorScheduleSlotResponse> records
) {
}
