package com.bishe.server.governance;

import com.bishe.server.ai.dto.AiModerationPayload;
import com.bishe.server.community.dto.ModerationPayload;

/**
 * 统一内容审查决策结果。
 */
public record ModerationDecision(
        long eventId,
        String sourceType,
        String targetType,
        String riskLevel,
        String action,
        String reasonCode,
        String maskedText
) {

    public ModerationPayload toCommunityPayload() {
        return new ModerationPayload(sourceType, riskLevel, action, reasonCode);
    }

    public AiModerationPayload toAiPayload() {
        return new AiModerationPayload(sourceType, riskLevel, action, reasonCode);
    }

    public boolean isBlock() {
        return "BLOCK".equalsIgnoreCase(action);
    }

    public boolean isReview() {
        return "REVIEW".equalsIgnoreCase(action);
    }

    public String communityVisibilityStatus() {
        if (isReview() || "MASK".equalsIgnoreCase(action)) {
            return "REVIEW";
        }
        if (isBlock()) {
            return "BLOCK";
        }
        return "PASS";
    }
}
