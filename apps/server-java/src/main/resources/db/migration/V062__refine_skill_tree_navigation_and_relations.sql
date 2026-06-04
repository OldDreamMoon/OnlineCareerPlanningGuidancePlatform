UPDATE skills
SET parent_code = 'software_architecture_design',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'service_layer_design';

UPDATE skills
SET parent_code = 'hardware_embedded',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'embedded_systems';

UPDATE skills
SET parent_code = 'distributed_systems',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'container_kubernetes';

UPDATE skills
SET parent_code = 'career_positioning',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'networking_personal_brand';

UPDATE skills
SET parent_code = 'stress_management',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'burnout_prevention';

UPDATE skills
SET parent_code = 'workplace_communication',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'workplace_professionalism';

UPDATE skills
SET parent_code = 'prompt_context_engineering',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'tool_schema_design';

UPDATE skills
SET parent_code = 'ai_coding_agent_systems',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'ai_coding_agent_tools';

UPDATE skills
SET parent_code = 'network_security',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'web_security';

UPDATE skills
SET parent_code = 'web_security',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'secure_engineering';

INSERT INTO skill_relations(
    source_node_code,
    target_node_code,
    relation_type,
    label,
    sort_order,
    created_at,
    updated_at
) VALUES
('memory_model_pointers', 'memory_management', 'ADVANCE_TO', '理解指针与内存布局后，会更容易掌握系统中的内存管理', 510, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('graph_problem_modeling', 'data_modeling', 'BRIDGE', '图结构抽象能力会迁移到复杂业务与数据关系建模', 520, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('sql_query_writing', 'query_optimization', 'ADVANCE_TO', '能写出正确 SQL 后，下一步就是关注执行计划与优化', 530, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('big_data_foundations', 'distributed_systems', 'BRIDGE', '大数据系统最终会落到分布式存储与计算基础', 540, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('shell_tooling_automation', 'ci_cd_pipeline', 'ADVANCE_TO', '脚本自动化能力最终会接到流水线编排', 550, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('digital_logic', 'microcomputer_principles', 'ADVANCE_TO', '数字逻辑理解会继续进入微机结构与接口系统', 560, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('iot_system_design', 'computer_networks', 'CO_LEARN', '物联网系统设计离不开设备联网与协议理解', 570, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('probability_inference', 'machine_learning', 'ADVANCE_TO', '概率推断是理解机器学习建模假设的入口', 580, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('tree_linear_models', 'recommendation_systems', 'BRIDGE', '经典监督学习模型常会落到推荐排序与召回场景', 590, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('neural_network_training', 'llm_applications', 'ADVANCE_TO', '掌握神经网络训练流程后，更容易理解大模型应用边界', 600, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('logic_and_proofs', 'compiler_principles', 'ADVANCE_TO', '形式化推理能力会继续支撑编译与程序语义理解', 610, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('technology_society_governance', 'ai_engineering_governance', 'BRIDGE', '技术治理视角会直接回流到 AI 工程治理实践', 620, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('habit_energy_management', 'burnout_prevention', 'CO_LEARN', '稳定作息与精力管理能直接帮助倦怠预防', 630, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('networking_personal_brand', 'project_storytelling', 'CO_LEARN', '个人品牌表达最终仍要靠项目叙事来支撑', 640, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('backend_service_development', 'service_layer_design', 'ADVANCE_TO', '后端开发深入后会进入服务分层与边界设计', 650, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('cloud_native_basics', 'container_kubernetes', 'ADVANCE_TO', '理解云原生抽象后，下一步通常就是容器编排实践', 660, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('context_window_orchestration', 'tool_schema_design', 'CO_LEARN', '上下文编排与工具契约设计需要一起打磨', 670, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_codebase_grounding', 'ai_coding_agent_tools', 'ADVANCE_TO', '先做好代码库对齐，工具调用才真正稳定可控', 680, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('job_market_research', 'networking_personal_brand', 'BRIDGE', '岗位研究最终要延伸到行业连接与个人曝光', 690, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('task_execution_followup', 'workplace_professionalism', 'BRIDGE', '任务推进习惯最终会沉淀为职场专业度', 700, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('emotion_regulation', 'burnout_prevention', 'CO_LEARN', '情绪调节能力会直接影响倦怠识别与恢复', 710, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('network_protocols', 'web_security', 'CO_LEARN', '协议语义理解会直接影响 Web 安全判断', 720, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('applied_cryptography', 'secure_engineering', 'CO_LEARN', '密码学原理只有落到工程实践中才真正有价值', 730, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('microcomputer_principles', 'embedded_systems', 'ADVANCE_TO', '理解微机与接口后，更容易进入嵌入式系统设计', 740, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    label = VALUES(label),
    sort_order = VALUES(sort_order),
    updated_at = CURRENT_TIMESTAMP;
