package com.bishe.server.ai.interview;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文本面试会话配置：控制安全轮次上限与固定会话包配额权重。
 */
@Component
@ConfigurationProperties(prefix = "ai.interview")
public class AiInterviewProperties {

    private int maxReplyRounds = 30;
    private int reservedQuotaWeight = 5;

    public int getMaxReplyRounds() {
        return maxReplyRounds;
    }

    public void setMaxReplyRounds(int maxReplyRounds) {
        this.maxReplyRounds = Math.max(maxReplyRounds, 1);
    }

    public int getReservedQuotaWeight() {
        return reservedQuotaWeight;
    }

    public void setReservedQuotaWeight(int reservedQuotaWeight) {
        this.reservedQuotaWeight = Math.max(reservedQuotaWeight, 1);
    }
}
