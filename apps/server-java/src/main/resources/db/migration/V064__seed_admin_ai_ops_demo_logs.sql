/*
 * 为管理员 AI 应用运营台补充一组可复用的场景级演示日志。
 * 目标：让 application-ops 的热门 / 成本 / 风险 / 静默榜单在本地联调时具备更真实的可见样本。
 * 约束：
 * 1. 只写 ai_call_logs，不改现有路由、模板和 provider 配置。
 * 2. 使用固定 trace_id，重复执行时不会重复插入。
 * 3. 仅选择现有 ACTIVE 学生账号，避免额外造用户与资料关联。
 */

INSERT INTO ai_call_logs(
    trace_id,
    user_id,
    task_type,
    scene_code,
    provider,
    model,
    latency_ms,
    status,
    error_code,
    request_tokens,
    response_tokens,
    total_tokens,
    thoughts_tokens,
    reasoning_effort,
    thinking_budget,
    thinking_level,
    estimated_cost,
    charged_points,
    quota_weight,
    result_summary,
    result_payload_json,
    user_tier,
    created_at
)
SELECT
    demo.trace_id,
    ranked_users.id,
    demo.task_type,
    demo.scene_code,
    demo.provider,
    demo.model,
    demo.latency_ms,
    demo.status,
    demo.error_code,
    demo.request_tokens,
    demo.response_tokens,
    demo.total_tokens,
    demo.thoughts_tokens,
    demo.reasoning_effort,
    demo.thinking_budget,
    demo.thinking_level,
    demo.estimated_cost,
    demo.charged_points,
    demo.quota_weight,
    demo.result_summary,
    demo.result_payload_json,
    demo.user_tier,
    demo.created_at
FROM (
    SELECT
        'AI_OPS_DEMO_20260413_001' AS trace_id,
        1 AS user_slot,
        'INTERVIEW_TEXT' AS task_type,
        'INTERVIEW_REPLY' AS scene_code,
        'SYSTEM_INTERVIEW_GEMINI_NATIVE' AS provider,
        'gemini-2.5-flash' AS model,
        4600 AS latency_ms,
        'SUCCESS' AS status,
        NULL AS error_code,
        1820 AS request_tokens,
        1260 AS response_tokens,
        3080 AS total_tokens,
        320 AS thoughts_tokens,
        'MEDIUM' AS reasoning_effort,
        768 AS thinking_budget,
        'STANDARD' AS thinking_level,
        0.026400 AS estimated_cost,
        5 AS charged_points,
        5 AS quota_weight,
        '完成第 3 轮追问与轻点评。' AS result_summary,
        '{"stage":"followup","round":3,"quality":"stable"}' AS result_payload_json,
        'FREE' AS user_tier,
        TIMESTAMP('2026-04-13 01:12:00') AS created_at
    UNION ALL
    SELECT
        'AI_OPS_DEMO_20260413_002', 2, 'INTERVIEW_TEXT', 'INTERVIEW_REPLY', 'SYSTEM_INTERVIEW_GEMINI_NATIVE', 'gemini-2.5-flash',
        5100, 'SUCCESS', NULL, 1760, 1180, 2940, 300, 'MEDIUM', 768, 'STANDARD', 0.024600, 0, 5,
        '完成第 4 轮追问并给出结构化提示。', '{"stage":"followup","round":4,"quality":"stable"}', 'PREMIUM', TIMESTAMP('2026-04-12 14:06:00')
    UNION ALL
    SELECT
        'AI_OPS_DEMO_20260413_003', 3, 'INTERVIEW_TEXT', 'INTERVIEW_REPLY', 'SYSTEM_INTERVIEW_GEMINI_NATIVE', 'gemini-2.5-flash',
        5600, 'SUCCESS', NULL, 2010, 1400, 3410, 360, 'MEDIUM', 896, 'STANDARD', 0.029500, 5, 5,
        '完成第 2 轮追问并补充表达建议。', '{"stage":"followup","round":2,"quality":"stable"}', 'FREE', TIMESTAMP('2026-04-12 07:34:00')
    UNION ALL
    SELECT
        'AI_OPS_DEMO_20260413_004', 4, 'INTERVIEW_TEXT', 'INTERVIEW_REPLY', 'SYSTEM_INTERVIEW_GEMINI_NATIVE', 'gemini-2.5-flash',
        5900, 'SUCCESS', NULL, 1940, 1330, 3270, 340, 'MEDIUM', 896, 'STANDARD', 0.028100, 0, 5,
        '完成追问并识别到 STAR 结构缺口。', '{"stage":"followup","round":5,"quality":"stable"}', 'PREMIUM', TIMESTAMP('2026-04-11 15:28:00')
    UNION ALL
    SELECT
        'AI_OPS_DEMO_20260413_005', 5, 'INTERVIEW_TEXT', 'INTERVIEW_REPLY', 'SYSTEM_INTERVIEW_GEMINI_NATIVE', 'gemini-2.5-flash',
        6200, 'SUCCESS', NULL, 2130, 1490, 3620, 410, 'MEDIUM', 1024, 'DEEP', 0.031800, 5, 5,
        '完成追问并输出下一轮作答方向。', '{"stage":"followup","round":6,"quality":"stable"}', 'FREE', TIMESTAMP('2026-04-10 09:18:00')
    UNION ALL
    SELECT
        'AI_OPS_DEMO_20260413_006', 6, 'INTERVIEW_TEXT', 'INTERVIEW_REPLY', 'SYSTEM_INTERVIEW_GEMINI_NATIVE', 'gemini-2.5-flash',
        5400, 'SUCCESS', NULL, 1880, 1210, 3090, 300, 'MEDIUM', 768, 'STANDARD', 0.026100, 5, 5,
        '完成追问并给出行为化表达建议。', '{"stage":"followup","round":3,"quality":"stable"}', 'FREE', TIMESTAMP('2026-04-09 13:42:00')
    UNION ALL
    SELECT
        'AI_OPS_DEMO_20260413_007', 2, 'INTERVIEW_TEXT', 'INTERVIEW_REPLY', 'SYSTEM_INTERVIEW_GEMINI_NATIVE', 'gemini-2.5-flash',
        6800, 'SUCCESS', NULL, 2250, 1620, 3870, 460, 'HIGH', 1152, 'DEEP', 0.034900, 0, 5,
        '完成高复杂度追问，输出纠偏建议。', '{"stage":"followup","round":7,"quality":"watch"}', 'PREMIUM', TIMESTAMP('2026-04-08 16:20:00')
    UNION ALL
    SELECT
        'AI_OPS_DEMO_20260413_008', 3, 'INTERVIEW_TEXT', 'INTERVIEW_REPLY', 'SYSTEM_INTERVIEW_GEMINI_NATIVE', 'gemini-2.5-flash',
        6400, 'ERROR', 'MODEL_OVERLOAD', 1980, 0, 1980, 210, 'MEDIUM', 768, 'STANDARD', 0.006800, 5, 5,
        '追问生成失败，已建议回退重试。', '{"stage":"followup","round":4,"error":"MODEL_OVERLOAD"}', 'FREE', TIMESTAMP('2026-04-07 10:05:00')
    UNION ALL
    SELECT
        'AI_OPS_DEMO_20260413_009', 1, 'RESUME', 'RESUME_OPTIMIZE', 'SYSTEM_RESUME_GEMINI_NATIVE', 'gemini-2.5-flash',
        5200, 'SUCCESS', NULL, 2460, 1620, 4080, 520, 'HIGH', 1024, 'DEEP', 0.034800, 10, 1,
        '完成简历结构化优化并输出亮点提炼。', '{"module":"resume","quality":"stable","version":1}', 'FREE', TIMESTAMP('2026-04-13 02:05:00')
    UNION ALL
    SELECT
        'AI_OPS_DEMO_20260413_010', 4, 'RESUME', 'RESUME_OPTIMIZE', 'SYSTEM_RESUME_GEMINI_NATIVE', 'gemini-2.5-flash',
        6100, 'SUCCESS', NULL, 2620, 1750, 4370, 560, 'HIGH', 1024, 'DEEP', 0.037900, 0, 1,
        '完成项目经历重写与量化建议。', '{"module":"resume","quality":"stable","focus":"project"}', 'PREMIUM', TIMESTAMP('2026-04-12 09:40:00')
    UNION ALL
    SELECT
        'AI_OPS_DEMO_20260413_011', 5, 'RESUME', 'RESUME_OPTIMIZE', 'SYSTEM_RESUME_GEMINI_NATIVE', 'gemini-2.5-flash',
        6800, 'SUCCESS', NULL, 2810, 1900, 4710, 600, 'HIGH', 1280, 'DEEP', 0.041300, 10, 1,
        '完成岗位匹配度分析与关键词补齐。', '{"module":"resume","quality":"stable","focus":"job-fit"}', 'FREE', TIMESTAMP('2026-04-11 11:11:00')
    UNION ALL
    SELECT
        'AI_OPS_DEMO_20260413_012', 6, 'RESUME', 'RESUME_OPTIMIZE', 'SYSTEM_RESUME_GEMINI_NATIVE', 'gemini-2.5-flash',
        5700, 'SUCCESS', NULL, 2380, 1580, 3960, 500, 'MEDIUM', 896, 'STANDARD', 0.033500, 10, 1,
        '完成表达压缩并统一履历口径。', '{"module":"resume","quality":"stable","focus":"clarity"}', 'FREE', TIMESTAMP('2026-04-09 08:48:00')
    UNION ALL
    SELECT
        'AI_OPS_DEMO_20260413_013', 2, 'RESUME', 'RESUME_OPTIMIZE', 'SYSTEM_RESUME_GEMINI_NATIVE', 'gemini-2.5-flash',
        7200, 'SUCCESS', NULL, 2950, 2050, 5000, 640, 'HIGH', 1280, 'DEEP', 0.044500, 0, 1,
        '完成英文简历打磨与岗位关键词对齐。', '{"module":"resume","quality":"stable","focus":"english"}', 'PREMIUM', TIMESTAMP('2026-04-08 12:36:00')
    UNION ALL
    SELECT
        'AI_OPS_DEMO_20260413_014', 3, 'STT', 'INTERVIEW_VOICE_TRANSCRIBE', 'SYSTEM_INTERVIEW_GEMINI_NATIVE', 'gemini-2.5-flash',
        9800, 'SUCCESS', NULL, 1080, 910, 1990, 0, NULL, NULL, NULL, 0.007600, 3, 1,
        '语音转写完成，已回填到主会话。', '{"module":"stt","quality":"watch","result":"partial-ok"}', 'FREE', TIMESTAMP('2026-04-13 03:18:00')
    UNION ALL
    SELECT
        'AI_OPS_DEMO_20260413_015', 4, 'STT', 'INTERVIEW_VOICE_TRANSCRIBE', 'SYSTEM_INTERVIEW_GEMINI_NATIVE', 'gemini-2.5-flash',
        11200, 'TIMEOUT', 'UPSTREAM_TIMEOUT', 1240, 0, 1240, 0, NULL, NULL, NULL, 0.003900, 0, 1,
        '语音转写超时，已触发前端重试提示。', '{"module":"stt","error":"UPSTREAM_TIMEOUT"}', 'PREMIUM', TIMESTAMP('2026-04-12 16:12:00')
    UNION ALL
    SELECT
        'AI_OPS_DEMO_20260413_016', 5, 'STT', 'INTERVIEW_VOICE_TRANSCRIBE', 'SYSTEM_INTERVIEW_GEMINI_NATIVE', 'gemini-2.5-flash',
        9500, 'SUCCESS', NULL, 990, 870, 1860, 0, NULL, NULL, NULL, 0.007200, 3, 1,
        '语音转写完成，但识别质量一般。', '{"module":"stt","quality":"watch","result":"low-confidence"}', 'FREE', TIMESTAMP('2026-04-10 18:42:00')
    UNION ALL
    SELECT
        'AI_OPS_DEMO_20260413_017', 6, 'STT', 'INTERVIEW_VOICE_TRANSCRIBE', 'SYSTEM_INTERVIEW_GEMINI_NATIVE', 'gemini-2.5-flash',
        10500, 'ERROR', 'AUDIO_DECODE_FAILED', 860, 0, 860, 0, NULL, NULL, NULL, 0.001800, 3, 1,
        '音频解码失败，未进入追问链路。', '{"module":"stt","error":"AUDIO_DECODE_FAILED"}', 'FREE', TIMESTAMP('2026-04-08 21:06:00')
    UNION ALL
    SELECT
        'AI_OPS_DEMO_20260413_018', 1, 'TTS', 'TEXT_TO_SPEECH', 'SYSTEM_INTERVIEW_GEMINI_NATIVE', 'gemini-2.5-flash-preview-tts',
        1180, 'SUCCESS', NULL, 420, 360, 780, 0, NULL, NULL, NULL, 0.002900, 5, 1,
        '面试官播报音频生成完成。', '{"module":"tts","voice":"warm","durationSec":18}', 'FREE', TIMESTAMP('2026-04-13 03:46:00')
    UNION ALL
    SELECT
        'AI_OPS_DEMO_20260413_019', 2, 'TTS', 'TEXT_TO_SPEECH', 'SYSTEM_INTERVIEW_GEMINI_NATIVE', 'gemini-2.5-flash-preview-tts',
        1320, 'SUCCESS', NULL, 460, 390, 850, 0, NULL, NULL, NULL, 0.003100, 0, 1,
        '总结播报音频生成完成。', '{"module":"tts","voice":"calm","durationSec":21}', 'PREMIUM', TIMESTAMP('2026-04-11 19:22:00')
    UNION ALL
    SELECT
        'AI_OPS_DEMO_20260413_020', 5, 'TTS', 'TEXT_TO_SPEECH', 'SYSTEM_INTERVIEW_GEMINI_NATIVE', 'gemini-2.5-flash-preview-tts',
        1490, 'SUCCESS', NULL, 510, 430, 940, 0, NULL, NULL, NULL, 0.003500, 5, 1,
        '复盘音频生成完成。', '{"module":"tts","voice":"neutral","durationSec":24}', 'FREE', TIMESTAMP('2026-04-09 20:08:00')
    UNION ALL
    SELECT
        'AI_OPS_DEMO_20260413_021', 4, 'INTERVIEW_SUMMARY', 'INTERVIEW_SUMMARY', 'SYSTEM_INTERVIEW_GEMINI_NATIVE', 'gemini-2.5-flash',
        8600, 'SUCCESS', NULL, 2140, 1480, 3620, 430, 'HIGH', 1024, 'DEEP', 0.029800, 0, 3,
        '面试总结生成完成。', '{"module":"summary","quality":"watch","focus":"delivery"}', 'PREMIUM', TIMESTAMP('2026-04-12 22:14:00')
    UNION ALL
    SELECT
        'AI_OPS_DEMO_20260413_022', 6, 'INTERVIEW_SUMMARY', 'INTERVIEW_SUMMARY', 'SYSTEM_INTERVIEW_GEMINI_NATIVE', 'gemini-2.5-flash',
        9200, 'SUCCESS', NULL, 2280, 1590, 3870, 470, 'HIGH', 1024, 'DEEP', 0.032100, 5, 3,
        '面试总结完成，但耗时偏高。', '{"module":"summary","quality":"watch","focus":"latency"}', 'FREE', TIMESTAMP('2026-04-10 22:01:00')
    UNION ALL
    SELECT
        'AI_OPS_DEMO_20260413_023', 3, 'INTERVIEW_TEXT', 'INTERVIEW_ANSWER_HELPER', 'SYSTEM_INTERVIEW_GEMINI_NATIVE', 'gemini-2.5-flash',
        7400, 'SUCCESS', NULL, 1540, 1060, 2600, 280, 'MEDIUM', 640, 'STANDARD', 0.019500, 5, 2,
        '回答辅助诊断已返回表达缺口。', '{"module":"answer-helper","quality":"watch","issue":"logic"}', 'FREE', TIMESTAMP('2026-04-12 20:44:00')
    UNION ALL
    SELECT
        'AI_OPS_DEMO_20260413_024', 5, 'INTERVIEW_TEXT', 'INTERVIEW_ANSWER_HELPER', 'SYSTEM_INTERVIEW_GEMINI_NATIVE', 'gemini-2.5-flash',
        8300, 'ERROR', 'OUTPUT_INVALID', 1620, 0, 1620, 190, 'MEDIUM', 640, 'STANDARD', 0.004600, 5, 2,
        '回答辅助诊断失败，结构化输出未通过校验。', '{"module":"answer-helper","error":"OUTPUT_INVALID"}', 'FREE', TIMESTAMP('2026-04-08 14:27:00')
) AS demo
JOIN (
    SELECT ranked.id, ranked.rn
    FROM (
        SELECT
            id,
            ROW_NUMBER() OVER (ORDER BY id) AS rn
        FROM users
        WHERE role = 'STUDENT'
          AND status = 'ACTIVE'
    ) AS ranked
) AS ranked_users
    ON ranked_users.rn = demo.user_slot
LEFT JOIN ai_call_logs existing
    ON existing.trace_id = demo.trace_id
WHERE existing.id IS NULL;
