package com.bishe.server.consult;

import com.bishe.server.common.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 支付宝沙箱网关客户端：封装查询、退款、退款查询、关单四类原生网关调用。
 */
@Component
public class AlipaySandboxGatewayClient {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AlipaySandboxGatewayClient(PaymentProperties paymentProperties, ObjectMapper objectMapper) {
        this.paymentProperties = paymentProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    public TradeQueryResult queryTrade(String orderNo, String providerTradeNo) {
        Map<String, Object> bizContent = new LinkedHashMap<>();
        putTradeIdentity(bizContent, orderNo, providerTradeNo);
        GatewayResponse response = execute("alipay.trade.query", bizContent);
        JsonNode payload = response.payload();
        return new TradeQueryResult(
                response.code(),
                response.message(),
                response.subCode(),
                response.subMessage(),
                payload.path("trade_status").asText(""),
                payload.path("trade_no").asText(providerTradeNo == null ? "" : providerTradeNo),
                payload.path("out_trade_no").asText(orderNo),
                yuanToFen(payload.path("total_amount").asText("0")),
                response.rawBody()
        );
    }

    public TradeRefundResult refundTrade(String orderNo, String providerTradeNo, int amountFen, String reason, String outRequestNo) {
        Map<String, Object> bizContent = new LinkedHashMap<>();
        putTradeIdentity(bizContent, orderNo, providerTradeNo);
        bizContent.put("refund_amount", fenToYuan(amountFen));
        bizContent.put("refund_reason", reason);
        bizContent.put("out_request_no", outRequestNo);
        GatewayResponse response = execute("alipay.trade.refund", bizContent);
        JsonNode payload = response.payload();
        return new TradeRefundResult(
                response.code(),
                response.message(),
                response.subCode(),
                response.subMessage(),
                payload.path("trade_no").asText(providerTradeNo == null ? "" : providerTradeNo),
                payload.path("out_trade_no").asText(orderNo),
                payload.path("out_request_no").asText(outRequestNo),
                payload.path("fund_change").asText("N"),
                yuanToFen(payload.path("refund_fee").asText("0")),
                response.rawBody()
        );
    }

    public RefundQueryResult queryRefund(String orderNo, String providerTradeNo, String outRequestNo) {
        Map<String, Object> bizContent = new LinkedHashMap<>();
        putTradeIdentity(bizContent, orderNo, providerTradeNo);
        bizContent.put("out_request_no", outRequestNo);
        GatewayResponse response = execute("alipay.trade.fastpay.refund.query", bizContent);
        JsonNode payload = response.payload();
        return new RefundQueryResult(
                response.code(),
                response.message(),
                response.subCode(),
                response.subMessage(),
                payload.path("trade_no").asText(providerTradeNo == null ? "" : providerTradeNo),
                payload.path("out_trade_no").asText(orderNo),
                payload.path("out_request_no").asText(outRequestNo),
                payload.path("refund_status").asText(""),
                yuanToFen(payload.path("refund_amount").asText("0")),
                response.rawBody()
        );
    }

    public TradeCloseResult closeTrade(String orderNo, String providerTradeNo) {
        Map<String, Object> bizContent = new LinkedHashMap<>();
        putTradeIdentity(bizContent, orderNo, providerTradeNo);
        GatewayResponse response = execute("alipay.trade.close", bizContent);
        JsonNode payload = response.payload();
        return new TradeCloseResult(
                response.code(),
                response.message(),
                response.subCode(),
                response.subMessage(),
                payload.path("trade_no").asText(providerTradeNo == null ? "" : providerTradeNo),
                payload.path("out_trade_no").asText(orderNo),
                response.rawBody()
        );
    }

    private GatewayResponse execute(String method, Map<String, Object> bizContent) {
        PaymentProperties.Sandbox sandbox = paymentProperties.getSandbox();
        String gatewayUrl = requireText(sandbox.getGatewayUrl(), "gateway-url");
        String appId = requireText(sandbox.getAppId(), "app-id");
        String appPrivateKey = requireText(sandbox.resolveAppPrivateKey(), "app-private-key or app-private-key-path");
        String charset = defaultIfBlank(sandbox.getCharset(), "utf-8");
        String signType = defaultIfBlank(sandbox.getSignType(), "RSA2");
        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("app_id", appId);
            params.put("method", method);
            params.put("format", "JSON");
            params.put("charset", charset);
            params.put("sign_type", signType);
            params.put("timestamp", TIMESTAMP_FORMAT.format(ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))));
            params.put("version", "1.0");
            params.put("biz_content", objectMapper.writeValueAsString(bizContent));
            params.put("sign", AlipaySignatureVerifier.signRequest(params, appPrivateKey, signType));

            HttpRequest request = HttpRequest.newBuilder(URI.create(gatewayUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=" + charset)
                    .POST(HttpRequest.BodyPublishers.ofString(buildEncodedQuery(params), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException("PAY-1003", "sandbox gateway http error", HttpStatus.BAD_GATEWAY);
            }
            String body = response.body();
            JsonNode root = objectMapper.readTree(body);
            JsonNode payload = root.path(method.replace('.', '_') + "_response");
            if (payload.isMissingNode() || payload.isNull()) {
                throw new ApiException("PAY-1003", "sandbox gateway response invalid", HttpStatus.BAD_GATEWAY);
            }
            return new GatewayResponse(
                    payload.path("code").asText(""),
                    payload.path("msg").asText(""),
                    payload.path("sub_code").asText(""),
                    payload.path("sub_msg").asText(""),
                    payload,
                    body
            );
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ApiException("PAY-1003", "sandbox gateway request failed", HttpStatus.BAD_GATEWAY);
        } catch (Exception ex) {
            throw new ApiException("PAY-1003", "sandbox gateway request failed", HttpStatus.BAD_GATEWAY);
        }
    }

    private void putTradeIdentity(Map<String, Object> bizContent, String orderNo, String providerTradeNo) {
        if (providerTradeNo != null && !providerTradeNo.isBlank()) {
            bizContent.put("trade_no", providerTradeNo.trim());
            return;
        }
        if (orderNo == null || orderNo.isBlank()) {
            throw new ApiException("BIZ-1001", "orderNo required", HttpStatus.BAD_REQUEST);
        }
        bizContent.put("out_trade_no", orderNo.trim());
    }

    private int yuanToFen(String yuanText) {
        try {
            return new BigDecimal(defaultIfBlank(yuanText, "0"))
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValueExact();
        } catch (Exception ex) {
            return 0;
        }
    }

    private String fenToYuan(int amountFen) {
        return BigDecimal.valueOf(amountFen)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private String buildEncodedQuery(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> hasText(entry.getKey()))
                .filter(entry -> hasText(entry.getValue()))
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String requireText(String value, String field) {
        if (!hasText(value)) {
            throw new ApiException("PAY-1001", "sandbox payment config invalid: missing " + field, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return value.trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record GatewayResponse(
            String code,
            String message,
            String subCode,
            String subMessage,
            JsonNode payload,
            String rawBody
    ) {
    }

    public record TradeQueryResult(
            String code,
            String message,
            String subCode,
            String subMessage,
            String tradeStatus,
            String providerTradeNo,
            String outTradeNo,
            int totalAmountFen,
            String rawBody
    ) {
        public boolean success() {
            return "10000".equals(code);
        }
    }

    public record TradeRefundResult(
            String code,
            String message,
            String subCode,
            String subMessage,
            String providerTradeNo,
            String outTradeNo,
            String outRequestNo,
            String fundChange,
            int refundAmountFen,
            String rawBody
    ) {
        public boolean success() {
            return "10000".equals(code);
        }

        public boolean refundSucceeded() {
            return success() && "Y".equalsIgnoreCase(fundChange);
        }
    }

    public record RefundQueryResult(
            String code,
            String message,
            String subCode,
            String subMessage,
            String providerTradeNo,
            String outTradeNo,
            String outRequestNo,
            String refundStatus,
            int refundAmountFen,
            String rawBody
    ) {
        public boolean success() {
            return "10000".equals(code);
        }
    }

    public record TradeCloseResult(
            String code,
            String message,
            String subCode,
            String subMessage,
            String providerTradeNo,
            String outTradeNo,
            String rawBody
    ) {
        public boolean success() {
            return "10000".equals(code);
        }
    }
}
