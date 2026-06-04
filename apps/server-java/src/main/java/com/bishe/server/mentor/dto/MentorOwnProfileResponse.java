package com.bishe.server.mentor.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 导师本人资料响应。
 */
public record MentorOwnProfileResponse(
        long userId,
        String displayName,
        String realName,
        boolean showRealName,
        String companyName,
        String jobTitle,
        String avatarUrl,
        boolean avatarConfigured,
        String avatarContentType,
        Long avatarUpdatedAt,
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
        String approvalStatus
) {
}
