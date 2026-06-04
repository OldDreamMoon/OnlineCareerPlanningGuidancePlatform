-- 统一将 AI 成本口径切换为人民币（CNY），并为系统 Gemini 路由补齐按场景拆分的真实计价。
-- 定价来源：
-- 1) Google Gemini API pricing（2026-04-13）
-- 2) USD/CNY = 6.8280（Google Finance，2026-04-13 10:35 UTC）

UPDATE ai_call_logs
SET estimated_cost = ROUND(estimated_cost * 6.8280, 6)
WHERE estimated_cost <> 0;

UPDATE ai_provider_configs
SET cost_per_1k_input = ROUND(cost_per_1k_input * 6.8280, 6),
    cost_per_1k_output = ROUND(cost_per_1k_output * 6.8280, 6)
WHERE cost_per_1k_input <> 0
   OR cost_per_1k_output <> 0;

UPDATE ai_provider_configs
SET cost_per_1k_input = 0.002048,
    cost_per_1k_output = 0.017070
WHERE provider_code IN (
    'SYSTEM_RESUME_GEMINI_NATIVE',
    'SYSTEM_INTERVIEW_GEMINI_NATIVE',
    'SYSTEM_MENTOR_PREP_GEMINI_NATIVE'
);

UPDATE ai_model_routes
SET extra_config_json = '{"bootstrapManaged":true,"supportsPdfNative":true,"pricing":{"currency":"CNY","inputPer1k":0.002048,"outputPer1k":0.017070,"source":"Google Gemini API pricing @ 2026-04-13; USD/CNY Google Finance @ 2026-04-13 10:35 UTC","exchangeRateUsdCny":6.828000}}'
WHERE route_code = 'SYSTEM_RESUME_OPTIMIZE';

UPDATE ai_model_routes
SET extra_config_json = '{"bootstrapManaged":true,"pricing":{"currency":"CNY","inputPer1k":0.002048,"outputPer1k":0.017070,"source":"Google Gemini API pricing @ 2026-04-13; USD/CNY Google Finance @ 2026-04-13 10:35 UTC","exchangeRateUsdCny":6.828000}}'
WHERE route_code IN (
    'SYSTEM_INTERVIEW_OPENING',
    'SYSTEM_INTERVIEW_REPLY',
    'SYSTEM_INTERVIEW_ANSWER_HELPER',
    'SYSTEM_INTERVIEW_SUMMARY',
    'SYSTEM_MENTOR_PREP_SHEET'
);

UPDATE ai_model_routes
SET extra_config_json = '{"bootstrapManaged":true,"transcriptionInstruction":"请将音频完整转写为纯文本，不要添加解释或格式化。","pricing":{"currency":"CNY","inputPer1k":0.006828,"outputPer1k":0.017070,"source":"Google Gemini API pricing @ 2026-04-13; USD/CNY Google Finance @ 2026-04-13 10:35 UTC","exchangeRateUsdCny":6.828000}}'
WHERE route_code = 'SYSTEM_INTERVIEW_VOICE_TRANSCRIBE';

UPDATE ai_model_routes
SET extra_config_json = '{"bootstrapManaged":true,"defaultStylePrompt":"Read aloud in a warm and friendly tone:","defaultVoiceName":"Zephyr","pricing":{"currency":"CNY","inputPer1k":0.003414,"outputPer1k":0.068280,"source":"Google Gemini API pricing @ 2026-04-13; USD/CNY Google Finance @ 2026-04-13 10:35 UTC","exchangeRateUsdCny":6.828000}}'
WHERE route_code = 'SYSTEM_INTERVIEW_TTS';
