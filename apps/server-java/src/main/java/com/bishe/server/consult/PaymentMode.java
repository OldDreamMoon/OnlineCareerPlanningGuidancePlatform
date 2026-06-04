package com.bishe.server.consult;

/**
 * 支付模式枚举。
 */
public enum PaymentMode {
    SANDBOX,
    MOCK;

    public static PaymentMode parse(String value) {
        return PaymentMode.valueOf(value.trim().toUpperCase());
    }
}
