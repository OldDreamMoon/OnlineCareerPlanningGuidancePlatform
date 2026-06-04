package com.bishe.server.mentor.schedule.dto;

/**
 * 导师可预约时段响应。
 */
public record MentorScheduleSlotResponse(
        long id,
        long mentorUserId,
        Long startAt,
        Long endAt,
        String status,
        String bookedOrderNo
) {
}
