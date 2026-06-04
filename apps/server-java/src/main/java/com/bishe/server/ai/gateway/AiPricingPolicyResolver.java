package com.bishe.server.ai.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 统一解析 AI 成本口径，当前项目统一按人民币（CNY）维护。
 */
@Service
public class AiPricingPolicyResolver {

    public static final String CURRENCY_CNY = "CNY";
    public static final BigDecimal USD_TO_CNY_EXCHANGE_RATE = new BigDecimal("6.8280");
    public static final String GEMINI_PRICING_SOURCE =
            "Google Gemini API pricing @ 2026-04-13; USD/CNY Google Finance @ 2026-04-13 10:35 UTC";

    private static final Logger log = LoggerFactory.getLogger(AiPricingPolicyResolver.class);

    private final ObjectMapper objectMapper;

    public AiPricingPolicyResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PricingDecision resolve(AiGatewayAdminRepository.ResolvedRouteRow routeRow) {
        PricingOverride override = parsePricingOverride(routeRow == null ? null : routeRow.routeExtraConfigJson());
        if (override != null) {
            return new PricingDecision(
                    decimalOrZero(override.inputPer1k()),
                    decimalOrZero(override.outputPer1k()),
                    CURRENCY_CNY,
                    true,
                    safe(override.source())
            );
        }
        return new PricingDecision(
                routeRow == null
                        ? BigDecimal.ZERO
                        : decimalOrZero(routeRow.modelInputCostPer1k() != null ? routeRow.modelInputCostPer1k() : routeRow.costPer1kInput()),
                routeRow == null
                        ? BigDecimal.ZERO
                        : decimalOrZero(routeRow.modelOutputCostPer1k() != null ? routeRow.modelOutputCostPer1k() : routeRow.costPer1kOutput()),
                CURRENCY_CNY,
                false,
                ""
        );
    }

    public PricingOverride resolveBootstrapProviderDefaultPricing(String modelName) {
        if (isGemini25FlashTextModel(modelName)) {
            return gemini25FlashTextPricing();
        }
        return null;
    }

    public PricingOverride resolveBootstrapTextPricing(String modelName) {
        if (isGemini25FlashTextModel(modelName)) {
            return gemini25FlashTextPricing();
        }
        return null;
    }

    public PricingOverride resolveBootstrapSpeechToTextPricing(String modelName) {
        if (isGemini25FlashSpeechModel(modelName)) {
            return gemini25FlashSpeechToTextPricing();
        }
        return null;
    }

    public PricingOverride resolveBootstrapTextToSpeechPricing(String modelName) {
        if (isGemini25FlashTtsModel(modelName)) {
            return gemini25FlashPreviewTtsPricing();
        }
        return null;
    }

    public String mergePricingOverride(String extraConfigJson, PricingOverride pricingOverride) {
        ObjectNode root = readObjectNode(extraConfigJson);
        if (pricingOverride == null) {
            return writeJson(root);
        }
        ObjectNode pricingNode = root.putObject("pricing");
        pricingNode.put("currency", pricingOverride.currency());
        pricingNode.put("inputPer1k", pricingOverride.inputPer1k());
        pricingNode.put("outputPer1k", pricingOverride.outputPer1k());
        pricingNode.put("source", pricingOverride.source());
        pricingNode.put("exchangeRateUsdCny", pricingOverride.exchangeRateUsdCny());
        return writeJson(root);
    }

    public PricingOverride parsePricingOverride(String extraConfigJson) {
        if (extraConfigJson == null || extraConfigJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(extraConfigJson);
            if (root == null || !root.isObject()) {
                return null;
            }
            JsonNode pricingNode = root.path("pricing");
            if (!pricingNode.isObject()) {
                return null;
            }
            String currency = safe(pricingNode.path("currency").asText("")).trim().toUpperCase();
            if (!currency.isBlank() && !CURRENCY_CNY.equals(currency)) {
                return null;
            }
            BigDecimal inputPer1k = readDecimal(pricingNode.get("inputPer1k"));
            BigDecimal outputPer1k = readDecimal(pricingNode.get("outputPer1k"));
            if (inputPer1k == null && outputPer1k == null) {
                return null;
            }
            BigDecimal exchangeRate = readDecimal(pricingNode.get("exchangeRateUsdCny"));
            return new PricingOverride(
                    CURRENCY_CNY,
                    decimalOrZero(inputPer1k),
                    decimalOrZero(outputPer1k),
                    safe(pricingNode.path("source").asText("")).trim(),
                    exchangeRate == null ? USD_TO_CNY_EXCHANGE_RATE : exchangeRate
            );
        } catch (Exception ex) {
            log.warn("failed to parse ai pricing override json: {}", summarize(extraConfigJson), ex);
            return null;
        }
    }

    private PricingOverride gemini25FlashTextPricing() {
        return new PricingOverride(
                CURRENCY_CNY,
                usdPerMillionToCnyPer1k("0.30"),
                usdPerMillionToCnyPer1k("2.50"),
                GEMINI_PRICING_SOURCE,
                USD_TO_CNY_EXCHANGE_RATE
        );
    }

    private PricingOverride gemini25FlashSpeechToTextPricing() {
        return new PricingOverride(
                CURRENCY_CNY,
                usdPerMillionToCnyPer1k("1.00"),
                usdPerMillionToCnyPer1k("2.50"),
                GEMINI_PRICING_SOURCE,
                USD_TO_CNY_EXCHANGE_RATE
        );
    }

    private PricingOverride gemini25FlashPreviewTtsPricing() {
        return new PricingOverride(
                CURRENCY_CNY,
                usdPerMillionToCnyPer1k("0.50"),
                usdPerMillionToCnyPer1k("10.00"),
                GEMINI_PRICING_SOURCE,
                USD_TO_CNY_EXCHANGE_RATE
        );
    }

    private BigDecimal usdPerMillionToCnyPer1k(String usdPerMillion) {
        return new BigDecimal(usdPerMillion)
                .multiply(USD_TO_CNY_EXCHANGE_RATE)
                .divide(new BigDecimal("1000"), 6, RoundingMode.HALF_UP);
    }

    private boolean isGemini25FlashTextModel(String modelName) {
        String normalized = normalizeModel(modelName);
        return normalized.contains("gemini-2.5-flash") && !normalized.contains("preview-tts");
    }

    private boolean isGemini25FlashSpeechModel(String modelName) {
        String normalized = normalizeModel(modelName);
        return normalized.contains("gemini-2.5-flash") && !normalized.contains("preview-tts");
    }

    private boolean isGemini25FlashTtsModel(String modelName) {
        return normalizeModel(modelName).contains("gemini-2.5-flash-preview-tts");
    }

    private String normalizeModel(String modelName) {
        return safe(modelName).trim().toLowerCase();
    }

    private ObjectNode readObjectNode(String extraConfigJson) {
        if (extraConfigJson == null || extraConfigJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode root = objectMapper.readTree(extraConfigJson);
            if (root instanceof ObjectNode objectNode) {
                return objectNode.deepCopy();
            }
        } catch (Exception ex) {
            log.warn("failed to read ai extra config json, fallback to empty object: {}", summarize(extraConfigJson), ex);
        }
        return objectMapper.createObjectNode();
    }

    private String writeJson(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to write ai pricing config json", ex);
        }
    }

    private BigDecimal readDecimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue().setScale(6, RoundingMode.HALF_UP);
        }
        String rawValue = safe(node.asText("")).trim();
        if (rawValue.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(rawValue).setScale(6, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal decimalOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(6, RoundingMode.HALF_UP);
    }

    private String safe(String rawValue) {
        return rawValue == null ? "" : rawValue;
    }

    private String summarize(String rawValue) {
        String normalized = safe(rawValue).replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 117) + "...";
    }

    public record PricingDecision(
            BigDecimal inputPer1k,
            BigDecimal outputPer1k,
            String currency,
            boolean overriddenByRoute,
            String source
    ) {
    }

    public record PricingOverride(
            String currency,
            BigDecimal inputPer1k,
            BigDecimal outputPer1k,
            String source,
            BigDecimal exchangeRateUsdCny
    ) {
    }
}
