package com.bishe.server.common;

import java.util.UUID;

public final class TraceId {

    private TraceId() {
    }

    public static String next() {
        return "trc_" + UUID.randomUUID().toString().replace("-", "");
    }
}
