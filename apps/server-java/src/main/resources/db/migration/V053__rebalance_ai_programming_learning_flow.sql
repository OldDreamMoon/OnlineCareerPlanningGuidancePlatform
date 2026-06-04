UPDATE skills
SET label = 'AI 编程与智能开发',
    description = '把提示词、AI 协作编码、编码 Agent 与工程治理按真实落地流程串成一棵应用型学习主树。',
    parent_code = 'programming_language_foundations',
    sort_order = 750,
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'ai_programming_engineering';

UPDATE skills
SET label = '提示词与上下文工程',
    description = '先学会把目标、约束、仓库片段与运行信息组织成模型真正能用的输入。',
    parent_code = 'ai_programming_engineering',
    sort_order = 751,
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'prompt_context_engineering';

UPDATE skills
SET label = '提示模式设计',
    description = '围绕角色设定、few-shot、输出模板和分步指令沉淀稳定可复用的提示套路。',
    parent_code = 'prompt_context_engineering',
    sort_order = 752,
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'prompt_pattern_design';

UPDATE skills
SET label = '上下文窗口编排',
    description = '学会裁剪文件、日志、文档和历史对话，让模型在有限窗口里看到最关键的信息。',
    parent_code = 'prompt_context_engineering',
    sort_order = 753,
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'context_window_orchestration';

UPDATE skills
SET label = '工具接口与 Schema 设计',
    description = '在理解上下文裁剪之后，继续学习把工具参数、结构化返回和调用约束设计得足够清晰。',
    parent_code = 'context_window_orchestration',
    sort_order = 754,
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'tool_schema_design';

UPDATE skills
SET label = 'AI 原生开发流程',
    description = '把需求拆解、原型试错、AI 协作编码与人工验收组织成新的开发工作流。',
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
    description = '把补全、解释、实现、纠错与人工修改串成高频协作循环，逐步形成稳定的协作节奏。',
    parent_code = 'ai_native_development',
    sort_order = 762,
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'ai_pair_programming';

UPDATE skills
SET label = 'AI 调试与重构闭环',
    description = '在结对编程之后继续深入，让模型参与定位问题、验证修改和辅助重构，但仍由人工最终验收。',
    parent_code = 'ai_pair_programming',
    sort_order = 763,
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'ai_debug_refactor_loops';

UPDATE skills
SET label = '编码 Agent 系统',
    description = '从上下文检索、工具链调用到多阶段执行，理解编码 Agent 的系统组成。',
    parent_code = 'ai_programming_engineering',
    sort_order = 770,
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'ai_coding_agent_systems';

UPDATE skills
SET label = '代码库检索与上下文对齐',
    description = '先让模型在真实仓库结构、接口契约、依赖边界和既有风格中找到正确上下文。',
    parent_code = 'ai_coding_agent_systems',
    sort_order = 771,
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'ai_codebase_grounding';

UPDATE skills
SET label = '工具调用与编码 Agent',
    description = '在代码库对齐之后，再把搜索、读写文件、运行测试与命令执行串成可控的编码代理链路。',
    parent_code = 'ai_codebase_grounding',
    sort_order = 772,
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'ai_coding_agent_tools';

UPDATE skills
SET label = '多 Agent 协作编排',
    description = '当单 Agent 链路稳定之后，再把需求分析、实现、测试和审查组织成多阶段代理流水线。',
    parent_code = 'ai_coding_agent_tools',
    sort_order = 773,
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'multi_agent_workflows';

UPDATE skills
SET label = 'AI 工程治理',
    description = '围绕评测、审查、路由、成本和安全边界，让 AI 编程能力真正能落地到团队流程。',
    parent_code = 'ai_programming_engineering',
    sort_order = 780,
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'ai_engineering_governance';

UPDATE skills
SET label = 'AI 代码审查与护栏',
    description = '先围绕 diff 审查、测试门禁、权限边界和回滚策略建立 AI 输出的基础护栏。',
    parent_code = 'ai_engineering_governance',
    sort_order = 781,
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'ai_code_review_guardrails';

UPDATE skills
SET label = 'AI 编程评测与可观测性',
    description = '在护栏建立后，进一步通过任务集、通过率、失败留痕和运行指标判断链路是否真正有效。',
    parent_code = 'ai_code_review_guardrails',
    sort_order = 782,
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'ai_eval_observability';

UPDATE skills
SET label = '模型路由与成本控制',
    description = '最后再根据任务类型、时延要求、预算和稳定性选择模型、降级路径与缓存策略。',
    parent_code = 'ai_eval_observability',
    sort_order = 783,
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'model_routing_cost_control';
