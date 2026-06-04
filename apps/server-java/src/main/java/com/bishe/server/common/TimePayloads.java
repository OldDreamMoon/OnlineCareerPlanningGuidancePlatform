package com.bishe.server.common;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * 统一将时间对象转换为前端可直接消费的 UTC epoch 毫秒值。
 */
public final class TimePayloads {

    private TimePayloads() {
    }

    public static Long toEpochMillis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    public static Long toEpochMillis(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toEpochMilli();
    }
}
