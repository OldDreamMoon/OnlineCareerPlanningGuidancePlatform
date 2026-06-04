package com.bishe.server.bounty.dto;

import jakarta.validation.constraints.NotBlank;

public record BountyTaskManageRequest(
        @NotBlank(message = "action must not be blank")
        String action
) {
}
