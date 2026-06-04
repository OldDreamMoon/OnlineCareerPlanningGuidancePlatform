package com.bishe.server.consult;

/**
 * 咨询售后申请状态。
 */
public enum ConsultAfterSalesRequestStatus {
    PENDING,
    APPROVED,
    REJECTED;

    public static ConsultAfterSalesRequestStatus parse(String value) {
        return ConsultAfterSalesRequestStatus.valueOf(value.trim().toUpperCase());
    }
}
