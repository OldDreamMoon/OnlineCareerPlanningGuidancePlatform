UPDATE skills
SET label = 'Java 语言与工程实践',
    description = '掌握面向对象语法、标准库和工程开发里最常见的 Java 基础能力。',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'java_programming';

UPDATE skills
SET parent_code = 'software_engineering_delivery',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'software_architecture_design';

UPDATE skills
SET label = '系统与基础设施',
    description = '从组成原理、操作系统、分布式与云原生基础设施构建系统视角。',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'systems_infrastructure';

UPDATE skills
SET label = '网络与安全',
    description = '聚焦网络通信、协议分层与安全防护，建立面向真实互联网系统的基础认知。',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'network_security';

UPDATE skills
SET parent_code = 'systems_infrastructure',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'distributed_systems';

UPDATE skills
SET parent_code = 'ai_coding_agent_systems',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'multi_agent_workflows';

UPDATE skills
SET parent_code = 'ai_engineering_governance',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'ai_eval_observability';

UPDATE skills
SET parent_code = 'ai_engineering_governance',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'model_routing_cost_control';

UPDATE skills
SET parent_code = 'theory_history',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'engineering_ethics';

UPDATE skills
SET label = '职业发展与求职准备',
    description = '围绕岗位理解、简历表达与求职策略，建立从校园到就业市场的过渡能力。',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'career_employment_readiness';

UPDATE skills
SET parent_code = 'programming_language_foundations',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code IN ('internship_workplace_adaptation', 'wellbeing_self_management');

INSERT INTO skill_relations(
    source_node_code,
    target_node_code,
    relation_type,
    label,
    sort_order,
    created_at,
    updated_at
) VALUES
('requirement_analysis', 'software_architecture_design', 'ADVANCE_TO', '需求澄清后通常会进入架构与模块设计', 380, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('engineering_code_conventions', 'collaborative_development', 'CO_LEARN', '工程规范只有放进协作流程里才会真正稳定', 390, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('unit_integration_testing', 'ci_cd_pipeline', 'ADVANCE_TO', '测试能力最终会收进持续集成流水线', 400, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('end_to_end_regression', 'observability_incident_response', 'BRIDGE', '关键链路回归和线上故障排查需要共用验证思路', 410, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('browser_runtime_mechanics', 'web_security', 'CO_LEARN', '浏览器运行机制会直接影响前端安全理解', 420, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('process_thread_models', 'concurrent_programming_basics', 'ADVANCE_TO', '操作系统线程模型会回流到并发编程实践', 430, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('file_system_io', 'linux_operations', 'CO_LEARN', '文件与 I/O 理解会直接提升 Linux 排障能力', 440, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('container_kubernetes', 'observability_incident_response', 'BRIDGE', '容器编排上线后必须配套观测与故障响应', 450, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('data_cleaning_feature_engineering', 'model_evaluation_validation', 'ADVANCE_TO', '特征工程完成后自然进入模型评估与验证', 460, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('prompt_pattern_design', 'prompt_rag_agent', 'BRIDGE', '提示模式沉淀后会汇入完整的 RAG 与 Agent 设计', 470, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('teamwork_collaboration', 'collaborative_development', 'BRIDGE', '团队协作能力最终会落到真实工程协作流程', 480, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('burnout_prevention', 'self_reflection_planning', 'BRIDGE', '倦怠预防最终要回到节奏复盘与阶段规划', 490, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('workplace_professionalism', 'collaborative_development', 'CO_LEARN', '职业化表达与交付责任感会直接影响工程协作质量', 500, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    label = VALUES(label),
    sort_order = VALUES(sort_order),
    updated_at = CURRENT_TIMESTAMP;
