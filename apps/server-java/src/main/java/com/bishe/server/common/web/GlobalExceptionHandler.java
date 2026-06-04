package com.bishe.server.common.web;

import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.common.exception.ApiException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一异常出口，确保错误码与 traceId 始终可追踪。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleApiException(ApiException ex) {
        String traceId = ex.getTraceId() == null || ex.getTraceId().isBlank() ? TraceId.next() : ex.getTraceId();
        logApiException(ex, traceId);
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.error(ex.getCode(), ex.getMessage(), ex.getData(), traceId));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String traceId = TraceId.next();
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError == null ? "invalid request" : fieldError.getField() + " " + fieldError.getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("BIZ-1001", message, traceId));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException ex) {
        String traceId = TraceId.next();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("BIZ-1001", ex.getMessage(), traceId));
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(Exception ex) {
        String traceId = TraceId.next();
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("AUTH-1004", "permission denied", traceId));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException ex) {
        String traceId = TraceId.next();
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("BIZ-1002", "resource not found", traceId));
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknownException(Exception ex) {
        String traceId = TraceId.next();
        log.error("unhandled exception, traceId={}", traceId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("BIZ-5000", "internal server error", traceId));
    }

    private void logApiException(ApiException ex, String traceId) {
        if (ex == null || ex.getHttpStatus() == null || !ex.getHttpStatus().is5xxServerError()) {
            return;
        }
        if (ex.getCause() != null) {
            log.error(
                    "api exception traceId={}, code={}, status={}, message={}, data={}",
                    traceId,
                    ex.getCode(),
                    ex.getHttpStatus().value(),
                    ex.getMessage(),
                    summarizeData(ex.getData()),
                    ex
            );
            return;
        }
        log.error(
                "api exception traceId={}, code={}, status={}, message={}, data={}",
                traceId,
                ex.getCode(),
                ex.getHttpStatus().value(),
                ex.getMessage(),
                summarizeData(ex.getData())
        );
    }

    private String summarizeData(Object data) {
        if (data == null) {
            return "";
        }
        String normalized = String.valueOf(data).replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 600) {
            return normalized;
        }
        return normalized.substring(0, 600) + "...";
    }
}
