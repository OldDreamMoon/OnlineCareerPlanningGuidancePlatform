package com.bishe.server.consult;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 咨询业务配置。
 */
@Component
@ConfigurationProperties(prefix = "consult")
public class ConsultProperties {

    private int mentorReplyTimeoutHours = 24;
    private long mentorReplyReclaimFixedDelayMs = 60000L;

    public int getMentorReplyTimeoutHours() {
        return mentorReplyTimeoutHours;
    }

    public void setMentorReplyTimeoutHours(int mentorReplyTimeoutHours) {
        this.mentorReplyTimeoutHours = mentorReplyTimeoutHours;
    }

    public long getMentorReplyReclaimFixedDelayMs() {
        return mentorReplyReclaimFixedDelayMs;
    }

    public void setMentorReplyReclaimFixedDelayMs(long mentorReplyReclaimFixedDelayMs) {
        this.mentorReplyReclaimFixedDelayMs = mentorReplyReclaimFixedDelayMs;
    }
}
