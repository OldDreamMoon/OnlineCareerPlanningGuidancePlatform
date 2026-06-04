package com.bishe.server.mentor.schedule.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建导师可预约时段请求。
 */
public record MentorScheduleSlotCreateRequest(
        @NotBlank(message = "startAt is required")
        String startAt,
        @NotBlank(message = "endAt is required")
        String endAt
) {
}
