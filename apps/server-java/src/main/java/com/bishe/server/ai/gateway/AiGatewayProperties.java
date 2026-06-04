package com.bishe.server.ai.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * AI 网关配置：控制 mock / legacy / routed 模式，以及旧版 OpenAI-compatible 兼容参数。
 */
@Component
@ConfigurationProperties(prefix = "ai.gateway")
public class AiGatewayProperties {

    private AiGatewayMode mode = AiGatewayMode.MOCK;
    private String configSecret = "";
    private boolean allowLegacyFallback = true;
    private final Logging logging = new Logging();
    private final OpenAiCompatible openaiCompatible = new OpenAiCompatible();

    public AiGatewayMode getMode() {
        return mode;
    }

    public void setMode(AiGatewayMode mode) {
        this.mode = mode == null ? AiGatewayMode.MOCK : mode;
    }

    public String getConfigSecret() {
        return configSecret;
    }

    public void setConfigSecret(String configSecret) {
        this.configSecret = configSecret;
    }

    public boolean isAllowLegacyFallback() {
        return allowLegacyFallback;
    }

    public void setAllowLegacyFallback(boolean allowLegacyFallback) {
        this.allowLegacyFallback = allowLegacyFallback;
    }

    public Logging getLogging() {
        return logging;
    }

    public OpenAiCompatible getOpenaiCompatible() {
        return openaiCompatible;
    }

    /**
     * AI 网关日志运行时默认配置；若后台未落库则回退到这里。
     */
    public static class Logging {
        private boolean debugEnabled = false;
        private boolean aiRequestLogEnabled = false;

        public boolean isDebugEnabled() {
            return debugEnabled;
        }

        public void setDebugEnabled(boolean debugEnabled) {
            this.debugEnabled = debugEnabled;
        }

        public boolean isAiRequestLogEnabled() {
            return aiRequestLogEnabled;
        }

        public void setAiRequestLogEnabled(boolean aiRequestLogEnabled) {
            this.aiRequestLogEnabled = aiRequestLogEnabled;
        }
    }

    /**
     * 旧版 OpenAI-compatible provider 兼容配置，用于迁移期 fallback。
     */
    public static class OpenAiCompatible {
        private String providerKey = "openai-compatible";
        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey = "";
        private String defaultModel = "gpt-4o-mini";
        private String speechToTextModel = "";
        private String textToSpeechModel = "";
        private int timeoutMs = 15000;
        private int maxRetries = 1;
        private BigDecimal costPer1kInput = BigDecimal.ZERO;
        private BigDecimal costPer1kOutput = BigDecimal.ZERO;
        private double temperature = 0.2d;

        public String getProviderKey() {
            return providerKey;
        }

        public void setProviderKey(String providerKey) {
            this.providerKey = providerKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
        }

        public String getSpeechToTextModel() {
            return speechToTextModel;
        }

        public void setSpeechToTextModel(String speechToTextModel) {
            this.speechToTextModel = speechToTextModel;
        }

        public String getTextToSpeechModel() {
            return textToSpeechModel;
        }

        public void setTextToSpeechModel(String textToSpeechModel) {
            this.textToSpeechModel = textToSpeechModel;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public BigDecimal getCostPer1kInput() {
            return costPer1kInput;
        }

        public void setCostPer1kInput(BigDecimal costPer1kInput) {
            this.costPer1kInput = costPer1kInput == null ? BigDecimal.ZERO : costPer1kInput;
        }

        public BigDecimal getCostPer1kOutput() {
            return costPer1kOutput;
        }

        public void setCostPer1kOutput(BigDecimal costPer1kOutput) {
            this.costPer1kOutput = costPer1kOutput == null ? BigDecimal.ZERO : costPer1kOutput;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }
    }
}
