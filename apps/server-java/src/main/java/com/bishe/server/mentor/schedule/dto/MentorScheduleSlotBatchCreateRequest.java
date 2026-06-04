package com.bishe.server.mentor.schedule.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量创建导师可预约时段请求。
 */
public record MentorScheduleSlotBatchCreateRequest(
        @NotEmpty(message = "records is required")
        @Size(max = 84, message = "records too many")
        List<@Valid MentorScheduleSlotCreateRequest> records
) {
}
