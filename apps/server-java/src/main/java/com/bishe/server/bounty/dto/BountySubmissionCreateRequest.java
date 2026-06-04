package com.bishe.server.bounty.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record BountySubmissionCreateRequest(
        @Size(max = 5000, message = "contentText length must be <= 5000")
        String contentText,
        List<String> attachmentLinks
) {
}
