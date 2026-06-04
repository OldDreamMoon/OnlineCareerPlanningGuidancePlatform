package com.bishe.server.consult.dto;

import java.math.BigDecimal;

/**
 * 咨询评价响应。
 */
public record ConsultReviewResponse(
        String orderNo,
        int rating,
        String comment,
        Long createdAt,
        BigDecimal mentorAvgRating
) {
}
