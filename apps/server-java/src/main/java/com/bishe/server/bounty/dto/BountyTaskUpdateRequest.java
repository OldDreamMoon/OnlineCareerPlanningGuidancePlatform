package com.bishe.server.bounty.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BountyTaskUpdateRequest(
        @NotBlank(message = "title must not be blank")
        @Size(max = 200, message = "title length must be <= 200")
        String title,
        @NotBlank(message = "description must not be blank")
        @Size(max = 4000, message = "description length must be <= 4000")
        String description,
        @NotBlank(message = "rewardDescription must not be blank")
        @Size(max = 255, message = "rewardDescription length must be <= 255")
        String rewardDescription,
        @Size(max = 50, message = "deadlineAt length must be <= 50")
        String deadlineAt
) {
}
