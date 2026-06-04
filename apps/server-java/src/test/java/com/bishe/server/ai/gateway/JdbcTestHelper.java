package com.bishe.server.ai.gateway;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * AI 网关测试辅助仓储。
 */
@Component
class JdbcTestHelper {

    private final JdbcTemplate jdbcTemplate;

    JdbcTestHelper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void clearAiGatewayConfigs() {
        jdbcTemplate.update("DELETE FROM ai_async_task_events");
        jdbcTemplate.update("DELETE FROM ai_async_task_jobs");
        jdbcTemplate.update("DELETE FROM ai_model_routes");
        jdbcTemplate.update("DELETE FROM prompt_templates");
        jdbcTemplate.update("DELETE FROM ai_provider_models");
        jdbcTemplate.update("DELETE FROM ai_provider_configs");
    }

    long insertProvider(
            String providerCode,
            String providerType,
            String displayName,
            String baseUrl,
            String apiKeyCiphertext,
            String apiKeyMasked,
            boolean enabled,
            int timeoutMs,
            int maxRetries,
            BigDecimal costPer1kInput,
            BigDecimal costPer1kOutput,
            String extraConfigJson
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_provider_configs(
                    provider_code, provider_type, display_name, base_url, api_key_ciphertext, api_key_masked,
                    enabled, timeout_ms, max_retries, cost_per_1k_input, cost_per_1k_output, extra_config_json,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                providerCode,
                providerType,
                displayName,
                baseUrl,
                apiKeyCiphertext,
                apiKeyMasked,
                enabled,
                timeoutMs,
                maxRetries,
                costPer1kInput,
                costPer1kOutput,
                extraConfigJson
        );
        Long id = jdbcTemplate.queryForObject("SELECT id FROM ai_provider_configs WHERE provider_code = ?", Long.class, providerCode);
        return id == null ? 0L : id;
    }

    long insertPromptTemplate(
            String taskType,
            String templateName,
            int versionNo,
            String status,
            String templateFormat,
            String content,
            String description,
            String variablesJson,
            String bundleJson
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO prompt_templates(
                    task_type, template_name, version_no, status, template_format, content, description, variables_json, bundle_json,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                taskType,
                templateName,
                versionNo,
                status,
                templateFormat,
                content,
                description,
                variablesJson,
                bundleJson
        );
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM prompt_templates WHERE task_type = ? AND template_name = ? AND version_no = ?",
                Long.class,
                taskType,
                templateName,
                versionNo
        );
        return id == null ? 0L : id;
    }

    void insertProviderModel(
            long providerConfigId,
            String modelCode,
            String displayName,
            boolean enabled,
            BigDecimal inputCostPer1k,
            BigDecimal outputCostPer1k,
            Integer contextWindow,
            Integer maxOutputTokens,
            String supportedTaskTypesJson,
            String notes
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_provider_models(
                    provider_config_id, model_code, display_name, enabled,
                    input_cost_per_1k, output_cost_per_1k, context_window, max_output_tokens,
                    supported_task_types_json, notes, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                providerConfigId,
                modelCode,
                displayName,
                enabled,
                inputCostPer1k,
                outputCostPer1k,
                contextWindow,
                maxOutputTokens,
                supportedTaskTypesJson,
                notes
        );
    }

    void insertRoute(
            String routeCode,
            String taskType,
            String sceneCode,
            long providerConfigId,
            String modelName,
            int priorityNo,
            boolean enabled,
            BigDecimal temperature,
            String systemPrompt,
            String extraConfigJson
    ) {
        insertRoute(
                routeCode,
                taskType,
                sceneCode,
                providerConfigId,
                modelName,
                priorityNo,
                AiExecutionMode.SYNC_BLOCKING.name(),
                enabled,
                temperature,
                systemPrompt,
                null,
                extraConfigJson
        );
    }

    void insertRoute(
            String routeCode,
            String taskType,
            String sceneCode,
            long providerConfigId,
            String modelName,
            int priorityNo,
            boolean enabled,
            BigDecimal temperature,
            String systemPrompt,
            String promptTemplateName,
            String extraConfigJson
    ) {
        insertRoute(
                routeCode,
                taskType,
                sceneCode,
                providerConfigId,
                modelName,
                priorityNo,
                AiExecutionMode.SYNC_BLOCKING.name(),
                enabled,
                temperature,
                systemPrompt,
                promptTemplateName,
                extraConfigJson
        );
    }

    void insertRoute(
            String routeCode,
            String taskType,
            String sceneCode,
            long providerConfigId,
            String modelName,
            int priorityNo,
            String executionMode,
            boolean enabled,
            BigDecimal temperature,
            String systemPrompt,
            String promptTemplateName,
            String extraConfigJson
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_model_routes(
                    route_code, task_type, scene_code, provider_config_id, model_name,
                    priority_no, execution_mode, enabled, temperature, system_prompt, prompt_template_name, extra_config_json,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                routeCode,
                taskType,
                sceneCode,
                providerConfigId,
                modelName,
                priorityNo,
                executionMode,
                enabled,
                temperature,
                systemPrompt,
                promptTemplateName,
                extraConfigJson
        );
    }
}
