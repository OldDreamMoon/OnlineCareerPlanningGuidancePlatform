package com.bishe.server.mentor.dto;

/**
 * 收藏操作响应。
 */
public record MentorFavoriteToggleResponse(
        long mentorUserId,
        boolean favorited,
        long totalFavorites
) {
}
