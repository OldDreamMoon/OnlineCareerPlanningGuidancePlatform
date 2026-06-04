package com.bishe.server.bounty.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BountySubmissionReviewRequest(
        @NotBlank(message = "decision must not be blank")
        String decision,
        @Size(max = 80, message = "contactIntent length must be <= 80")
        String contactIntent,
        @Size(max = 160, message = "rejectTemplate length must be <= 160")
        String rejectTemplate,
        @Size(max = 1000, message = "actionNote length must be <= 1000")
        String actionNote,
        @Size(max = 500, message = "comment length must be <= 500")
        String comment,
        Boolean syncEmailReminder
) {
}
