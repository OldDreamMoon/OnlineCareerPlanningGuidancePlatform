package com.bishe.server.consult.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 咨询评价提交请求。
 */
public record ConsultReviewCreateRequest(
        @Min(value = 1, message = "rating must be between 1 and 5")
        @Max(value = 5, message = "rating must be between 1 and 5")
        int rating,
        @Size(max = 1000, message = "comment too long")
        String comment
) {
}
