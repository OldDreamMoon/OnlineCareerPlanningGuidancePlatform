package com.bishe.server.consult;

/**
 * 咨询订单状态枚举。
 */
public enum ConsultOrderStatus {
    CREATED,
    PAYING,
    PAID,
    ANSWERED,
    CLOSED,
    FAILED,
    CANCELED,
    REFUNDED;

    public static ConsultOrderStatus parse(String value) {
        return ConsultOrderStatus.valueOf(value.trim().toUpperCase());
    }
}
