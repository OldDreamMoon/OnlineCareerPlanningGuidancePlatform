package com.bishe.server.consult;

/**
 * 咨询售后申请类型。
 */
public enum ConsultAfterSalesRequestType {
    REFUND;

    public static ConsultAfterSalesRequestType parse(String value) {
        return ConsultAfterSalesRequestType.valueOf(value.trim().toUpperCase());
    }
}
