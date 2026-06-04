package com.bishe.server.consult;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 支付宝沙箱 PC 网页支付 URL 生成器。
 */
public final class AlipayPagePayUrlBuilder {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AlipayPagePayUrlBuilder() {
    }

    public static String build(PaymentProperties.Sandbox sandbox, ObjectMapper objectMapper, String orderNo, int amountFen) {
        try {
            String gatewayUrl = requireText(sandbox.getGatewayUrl(), "gateway-url");
            String appId = requireText(sandbox.getAppId(), "app-id");
            String appPrivateKey = requireText(sandbox.resolveAppPrivateKey(), "app-private-key or app-private-key-path");
            String notifyUrl = requireText(sandbox.getNotifyUrl(), "notify-url");
            String returnUrl = requireText(sandbox.resolveReturnUrl(orderNo), "return-url");
            String charset = defaultIfBlank(sandbox.getCharset(), "utf-8");
            String signType = defaultIfBlank(sandbox.getSignType(), "RSA2");

            Map<String, Object> bizContent = new LinkedHashMap<>();
            bizContent.put("out_trade_no", orderNo);
            bizContent.put("total_amount", fenToYuan(amountFen));
            bizContent.put("subject", buildSubject(sandbox, orderNo));
            bizContent.put("product_code", defaultIfBlank(sandbox.getProductCode(), "FAST_INSTANT_TRADE_PAY"));
            if (hasText(sandbox.getTimeoutExpress())) {
                bizContent.put("timeout_express", sandbox.getTimeoutExpress().trim());
            }

            Map<String, String> params = new LinkedHashMap<>();
            params.put("app_id", appId);
            params.put("method", "alipay.trade.page.pay");
            params.put("charset", charset);
            params.put("sign_type", signType);
            params.put("timestamp", TIMESTAMP_FORMAT.format(ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))));
            params.put("version", "1.0");
            params.put("notify_url", notifyUrl);
            params.put("return_url", returnUrl);
            params.put("biz_content", objectMapper.writeValueAsString(bizContent));
            params.put("sign", AlipaySignatureVerifier.signRequest(params, appPrivateKey, signType));
            return gatewayUrl + (gatewayUrl.contains("?") ? "&" : "?") + buildEncodedQuery(params);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to build alipay sandbox page pay url", ex);
        }
    }

    private static String buildSubject(PaymentProperties.Sandbox sandbox, String orderNo) {
        String prefix = defaultIfBlank(sandbox.getSubjectPrefix(), "职业规划咨询").trim();
        return prefix + "-" + orderNo;
    }

    private static String fenToYuan(int amountFen) {
        return BigDecimal.valueOf(amountFen)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private static String buildEncodedQuery(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> hasText(entry.getKey()))
                .filter(entry -> hasText(entry.getValue()))
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String requireText(String value, String field) {
        if (!hasText(value)) {
            throw new IllegalStateException("payment sandbox " + field + " is missing");
        }
        return value.trim();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
