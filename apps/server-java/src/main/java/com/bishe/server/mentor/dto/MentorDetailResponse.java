package com.bishe.server.mentor.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 导师详情响应。
 */
public record MentorDetailResponse(
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
        String suitableFor,
        String notSuitableFor,
        String prepMaterials,
        String replyRhythm,
        int priceFen,
        List<MentorServicePackageResponse> packages,
        BigDecimal avgRating,
        int totalOrders,
        boolean available,
        boolean favorited,
        List<RecentReviewItem> recentReviews
) {

    /**
     * 近期评价条目。
     */
    public record RecentReviewItem(
            String orderNo,
            String studentDisplayName,
            int rating,
            String comment,
            Long createdAt
    ) {
    }
}
