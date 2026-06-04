package com.bishe.server.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 统一业务异常，携带错误码与 HTTP 状态。
 */
public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;
    private final Object data;
    private final String traceId;

    public ApiException(String code, String message, HttpStatus httpStatus) {
        this(code, message, httpStatus, null, null);
    }

    public ApiException(String code, String message, HttpStatus httpStatus, Object data) {
        this(code, message, httpStatus, data, null);
    }

    public ApiException(String code, String message, HttpStatus httpStatus, Object data, String traceId) {
        this(code, message, httpStatus, data, traceId, null);
    }

    public ApiException(String code, String message, HttpStatus httpStatus, Throwable cause) {
        this(code, message, httpStatus, null, null, cause);
    }

    public ApiException(String code, String message, HttpStatus httpStatus, Object data, String traceId, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
        this.data = data;
        this.traceId = traceId;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public Object getData() {
        return data;
    }

    public String getTraceId() {
        return traceId;
    }
}
