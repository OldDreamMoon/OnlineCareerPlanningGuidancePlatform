package com.bishe.server.mentor.dto;

import java.util.List;

/**
 * 学生收藏导师摘要。
 */
public record MentorFavoritesResponse(
        long total,
        List<Long> mentorUserIds
) {
}
