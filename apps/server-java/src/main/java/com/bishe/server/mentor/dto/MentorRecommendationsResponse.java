package com.bishe.server.mentor.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 导师广场推荐结果。
 */
public record MentorRecommendationsResponse(
        String scene,
        String basisSummary,
        boolean weakSignal,
        List<String> basisTags,
        List<RecommendationItem> records
) {

    /**
     * 单条推荐结果。
     */
    public record RecommendationItem(
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
            boolean favorited,
            int score,
            List<String> reasons,
            String risk,
            String explainText
    ) {
    }
}
