INSERT INTO skills(
    node_code,
    label,
    description,
    parent_code,
    sort_order,
    created_at,
    updated_at
) VALUES
('ai_programming_engineering', 'AI 编程与智能开发', '把提示词、AI 协作编码、编码 Agent 与工程治理串成一棵独立的应用型学习主树。', 'programming_language_foundations', 750, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('prompt_pattern_design', '提示模式设计', '围绕角色设定、few-shot、格式约束、分步指令和输出模板沉淀可复用提示模式。', 'prompt_context_engineering', 752, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('context_window_orchestration', '上下文窗口编排', '学会选择仓库片段、文档、错误日志和历史对话，让模型看到刚好够用的上下文。', 'prompt_context_engineering', 753, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('tool_schema_design', '工具接口与 Schema 设计', '把函数调用、参数约束、结构化返回和工具契约设计得足够清晰，便于模型稳定调用。', 'prompt_context_engineering', 754, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_debug_refactor_loops', 'AI 调试与重构闭环', '让模型参与问题定位、回归验证、代码解释和重构建议，但始终保留人工判断与验收。', 'ai_native_development', 763, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_coding_agent_systems', '编码 Agent 系统', '从上下文检索、工具链调用到多阶段执行，理解编码 Agent 的系统组成。', 'ai_programming_engineering', 770, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('multi_agent_workflows', '多 Agent 协作编排', '把需求分析、实现、测试、审查等不同角色代理组织成可控的多阶段流水线。', 'ai_coding_agent_systems', 773, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_engineering_governance', 'AI 工程治理', '围绕评测、审查、路由、成本和安全边界，让 AI 编程能力真正能落地到团队流程。', 'ai_programming_engineering', 780, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('model_routing_cost_control', '模型路由与成本控制', '根据任务类型、时延要求、预算和稳定性选择模型、降级路径与缓存策略。', 'ai_engineering_governance', 783, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    label = VALUES(label),
    description = VALUES(description),
    parent_code = VALUES(parent_code),
    sort_order = VALUES(sort_order),
    updated_at = CURRENT_TIMESTAMP;

UPDATE skills
SET label = '提示词与上下文工程',
    description = '围绕 system prompt、上下文裁剪、文件片段与约束指令组织稳定可复用的模型输入。',
    parent_code = 'ai_programming_engineering',
    sort_order = 751,
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'prompt_context_engineering';

UPDATE skills
SET label = 'AI 原生开发流程',
    description = '把需求拆解、原型试错、AI 协作编码和人工验收组织成新的开发工作流。',
    parent_code = 'ai_programming_engineering',
    sort_order = 760,
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'ai_native_development';

UPDATE skills
SET label = 'Vibe Coding 原型流',
    description = '适合快速探索交互、原型和想法，但必须辅以人工审查、测试和版本管理。',
    parent_code = 'ai_native_development',
    sort_order = 761,
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'vibe_coding_workflows';

UPDATE skills
SET label = 'AI 结对编程',
    description = '把补全、解释、实现、纠错与人工修改串成高频协作循环，提升开发吞吐。',
    parent_code = 'ai_native_development',
    sort_order = 762,
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'ai_pair_programming';

UPDATE skills
SET label = '代码库检索与上下文对齐',
    description = '让模型在真实仓库结构、接口契约、依赖边界和现有代码风格中工作，而不是脱离上下文生成代码。',
    parent_code = 'ai_coding_agent_systems',
    sort_order = 771,
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'ai_codebase_grounding';

UPDATE skills
SET label = '工具调用与编码 Agent',
    description = '把搜索、读写文件、运行测试、检查日志和执行命令串成可控的编码代理链路。',
    parent_code = 'ai_coding_agent_systems',
    sort_order = 772,
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'ai_coding_agent_tools';

UPDATE skills
SET label = 'AI 代码审查与护栏',
    description = '围绕 diff 审查、测试门禁、权限边界、回滚策略和安全检查约束 AI 输出风险。',
    parent_code = 'ai_engineering_governance',
    sort_order = 781,
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'ai_code_review_guardrails';

UPDATE skills
SET label = 'AI 编程评测与可观测性',
    description = '通过任务集、通过率、人工复查、失败留痕和运行指标判断 AI 开发链路是否真正有效。',
    parent_code = 'ai_engineering_governance',
    sort_order = 782,
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'ai_eval_observability';

INSERT INTO skill_relations(
    source_node_code,
    target_node_code,
    relation_type,
    label,
    sort_order,
    created_at,
    updated_at
) VALUES
('ai_programming_engineering', 'prompt_rag_agent', 'BRIDGE', 'AI 编程工程会回流到 Prompt、RAG 与 Agent 方案设计', 320, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('tool_schema_design', 'api_contract_design', 'BRIDGE', '工具契约设计和 API 契约设计本质上是同一类边界表达', 330, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('context_window_orchestration', 'data_modeling', 'CO_LEARN', '上下文筛选能力会直接受益于数据结构和信息组织方式', 340, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_debug_refactor_loops', 'design_patterns_refactoring', 'CO_LEARN', 'AI 重构建议要建立在设计模式和重构判断力之上', 350, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('multi_agent_workflows', 'collaborative_development', 'BRIDGE', '多 Agent 编排和多人协作流程都依赖清晰的任务拆分与交接', 360, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('model_routing_cost_control', 'service_governance_resilience', 'BRIDGE', '模型路由、超时和降级策略需要和服务治理能力一起设计', 370, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    label = VALUES(label),
    sort_order = VALUES(sort_order),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO skill_node_resources(
    resource_code,
    node_code,
    resource_type,
    title,
    source_label,
    duration_label,
    link_url,
    sort_order,
    created_at,
    updated_at
) VALUES
('ai-programming-overview', 'ai_programming_engineering', 'doc', 'GitHub Copilot 文档中心', 'GitHub', '长期参考', 'https://docs.github.com/en/copilot', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('prompt-pattern-playbook', 'prompt_pattern_design', 'doc', 'Prompt Engineering Guide', 'OpenAI Docs', '长期参考', 'https://platform.openai.com/docs/guides/prompt-engineering', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('context-window-checklist', 'context_window_orchestration', 'article', '上下文筛选与窗口编排清单', '静态推荐', '阅读 15m', 'https://modelcontextprotocol.io/introduction', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('tool-schema-guide', 'tool_schema_design', 'doc', 'Tools 指南', 'OpenAI Docs', '长期参考', 'https://platform.openai.com/docs/guides/tools', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('aider-debug-loop', 'ai_debug_refactor_loops', 'doc', 'Aider：终端中的 AI 结对编程', 'GitHub', '长期参考', 'https://github.com/Aider-AI/aider', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('agent-systems-overview', 'ai_coding_agent_systems', 'doc', 'OpenHands 开源编码 Agent', 'GitHub', '长期参考', 'https://github.com/OpenHands/OpenHands', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('multi-agent-autogen', 'multi_agent_workflows', 'doc', 'AutoGen 多 Agent 框架', 'GitHub', '长期参考', 'https://github.com/microsoft/autogen', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai-governance-evals', 'ai_engineering_governance', 'doc', 'Evals 指南', 'OpenAI Docs', '长期参考', 'https://platform.openai.com/docs/guides/evals', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('model-routing-litellm', 'model_routing_cost_control', 'doc', 'LiteLLM 路由与成本控制', 'GitHub', '长期参考', 'https://github.com/BerriAI/litellm', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    node_code = VALUES(node_code),
    resource_type = VALUES(resource_type),
    title = VALUES(title),
    source_label = VALUES(source_label),
    duration_label = VALUES(duration_label),
    link_url = VALUES(link_url),
    sort_order = VALUES(sort_order),
    updated_at = CURRENT_TIMESTAMP;
