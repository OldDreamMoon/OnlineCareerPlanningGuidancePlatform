package com.bishe.server.mentor.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 导师列表响应。
 */
public record MentorListResponse(
        List<MentorItem> records,
        long total,
        int page,
        int size
) {

    /**
     * 导师列表项。
     */
    public record MentorItem(
            long userId,
            String displayName,
            String realName,
            boolean showRealName,
            String companyName,
            String jobTitle,
            String avatarUrl,
            List<String> expertiseTags,
            List<String> serviceScenes,
            String bio,
            int priceFen,
            BigDecimal avgRating,
            int totalOrders,
            boolean available,
            boolean favorited
    ) {
    }
}
