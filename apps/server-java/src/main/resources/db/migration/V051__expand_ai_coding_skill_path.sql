INSERT INTO skills(
    node_code,
    label,
    description,
    parent_code,
    sort_order,
    created_at,
    updated_at
) VALUES
('prompt_context_engineering', '提示词与上下文工程', '把 system prompt、few-shot、约束、文件上下文与历史对话组织成稳定可复用的模型输入。', 'prompt_rag_agent', 730, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_native_development', 'AI 原生开发范式', '围绕需求拆解、上下文组织、模型协作和人工验收建立新一代开发流程。', 'prompt_context_engineering', 731, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('vibe_coding_workflows', 'Vibe Coding 工作流', '通过自然语言驱动原型、快速试错与交互探索，但同时守住边界、验证与版本控制。', 'ai_native_development', 732, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_pair_programming', 'AI 结对编程', '把任务拆分、代码生成、补全、解释和人工修正组织成高频协作循环。', 'vibe_coding_workflows', 733, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_codebase_grounding', '代码库检索与上下文对齐', '让模型在真实仓库结构、接口契约、依赖边界和现有代码风格中工作，而不是脱离上下文生成代码。', 'ai_pair_programming', 734, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_coding_agent_tools', '工具调用与编码 Agent', '把搜索、读写文件、运行测试、检查日志和执行命令串成可控的编码代理链路。', 'ai_codebase_grounding', 735, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_code_review_guardrails', 'AI 代码审查与护栏', '围绕 diff 审查、测试门禁、权限边界、回滚策略和安全检查约束 AI 输出风险。', 'ai_coding_agent_tools', 736, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_eval_observability', 'AI 编程评测与可观测性', '通过任务集、通过率、人工复查、失败留痕和运行指标判断 AI 开发链路是否真正有效。', 'ai_code_review_guardrails', 737, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    label = VALUES(label),
    description = VALUES(description),
    parent_code = VALUES(parent_code),
    sort_order = VALUES(sort_order),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO skill_relations(
    source_node_code,
    target_node_code,
    relation_type,
    label,
    sort_order,
    created_at,
    updated_at
) VALUES
('prompt_context_engineering', 'requirement_analysis', 'CO_LEARN', '高质量提示词离不开清晰需求与约束表达', 250, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('vibe_coding_workflows', 'project_storytelling', 'BRIDGE', '快速原型之后仍要补齐方案说明与成果表达', 260, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_pair_programming', 'collaborative_development', 'CO_LEARN', '人与 AI 协作本质上仍需要任务拆分与协作规范', 270, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_codebase_grounding', 'api_contract_design', 'BRIDGE', '仓库上下文对齐离不开接口契约和边界定义', 280, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_coding_agent_tools', 'automated_testing', 'ADVANCE_TO', '编码 Agent 真正可用必须挂在测试与验证链路上', 290, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_code_review_guardrails', 'secure_engineering', 'CO_LEARN', 'AI 输出审查和安全工程检查需要同步建设', 300, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_eval_observability', 'observability_incident_response', 'ADVANCE_TO', 'AI 编程链路最终仍要靠评测和运行观测收口', 310, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
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
('openai-prompt-engineering-guide', 'prompt_context_engineering', 'doc', 'Prompt Engineering Guide', 'OpenAI Docs', '长期参考', 'https://platform.openai.com/docs/guides/prompt-engineering', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('anthropic-prompt-overview', 'prompt_context_engineering', 'doc', 'Prompt Engineering Overview', 'Anthropic Docs', '长期参考', 'https://docs.anthropic.com/en/docs/build-with-claude/prompt-engineering/overview', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai-native-dev-checklist', 'ai_native_development', 'article', 'AI 原生开发分层检查表', '静态推荐', '阅读 18m', 'https://docs.github.com/en/copilot', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('copilot-docs-overview', 'ai_native_development', 'doc', 'GitHub Copilot 文档中心', 'GitHub', '长期参考', 'https://docs.github.com/en/copilot', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('aider-repo', 'vibe_coding_workflows', 'doc', 'Aider：终端中的 AI 结对编程', 'GitHub', '长期参考', 'https://github.com/Aider-AI/aider', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('vibe-coding-playbook', 'vibe_coding_workflows', 'article', '从想法到原型的 Vibe Coding 迭代清单', '静态推荐', '阅读 16m', 'https://docs.github.com/en/copilot', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('openhands-repo', 'ai_coding_agent_tools', 'doc', 'OpenHands 开源编码 Agent', 'GitHub', '长期参考', 'https://github.com/OpenHands/OpenHands', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('openai-tools-guide', 'ai_coding_agent_tools', 'doc', 'Tools 指南', 'OpenAI Docs', '长期参考', 'https://platform.openai.com/docs/guides/tools', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('promptfoo-docs', 'ai_code_review_guardrails', 'doc', 'Promptfoo 开源评测与护栏工具', 'GitHub', '长期参考', 'https://github.com/promptfoo/promptfoo', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai-review-checklist', 'ai_code_review_guardrails', 'article', 'AI 代码审查与回滚检查清单', '静态推荐', '阅读 14m', 'https://github.com/promptfoo/promptfoo', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('openai-evals-guide', 'ai_eval_observability', 'doc', 'Evals 指南', 'OpenAI Docs', '长期参考', 'https://platform.openai.com/docs/guides/evals', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai-observability-checklist', 'ai_eval_observability', 'article', 'AI 开发链路评测与留痕清单', '静态推荐', '阅读 15m', 'https://github.com/promptfoo/promptfoo', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    node_code = VALUES(node_code),
    resource_type = VALUES(resource_type),
    title = VALUES(title),
    source_label = VALUES(source_label),
    duration_label = VALUES(duration_label),
    link_url = VALUES(link_url),
    sort_order = VALUES(sort_order),
    updated_at = CURRENT_TIMESTAMP;
