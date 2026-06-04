DELETE FROM skill_relations
WHERE (source_node_code = 'requirement_analysis' AND target_node_code = 'software_architecture_design' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'engineering_code_conventions' AND target_node_code = 'collaborative_development' AND relation_type = 'CO_LEARN')
   OR (source_node_code = 'unit_integration_testing' AND target_node_code = 'ci_cd_pipeline' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'end_to_end_regression' AND target_node_code = 'observability_incident_response' AND relation_type = 'BRIDGE')
   OR (source_node_code = 'browser_runtime_mechanics' AND target_node_code = 'web_security' AND relation_type = 'CO_LEARN')
   OR (source_node_code = 'process_thread_models' AND target_node_code = 'concurrent_programming_basics' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'file_system_io' AND target_node_code = 'linux_operations' AND relation_type = 'CO_LEARN')
   OR (source_node_code = 'container_kubernetes' AND target_node_code = 'observability_incident_response' AND relation_type = 'BRIDGE')
   OR (source_node_code = 'data_cleaning_feature_engineering' AND target_node_code = 'model_evaluation_validation' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'prompt_pattern_design' AND target_node_code = 'prompt_rag_agent' AND relation_type = 'BRIDGE')
   OR (source_node_code = 'teamwork_collaboration' AND target_node_code = 'collaborative_development' AND relation_type = 'BRIDGE')
   OR (source_node_code = 'burnout_prevention' AND target_node_code = 'self_reflection_planning' AND relation_type = 'BRIDGE')
   OR (source_node_code = 'workplace_professionalism' AND target_node_code = 'collaborative_development' AND relation_type = 'CO_LEARN')
   OR (source_node_code = 'memory_model_pointers' AND target_node_code = 'memory_management' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'graph_problem_modeling' AND target_node_code = 'data_modeling' AND relation_type = 'BRIDGE')
   OR (source_node_code = 'sql_query_writing' AND target_node_code = 'query_optimization' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'big_data_foundations' AND target_node_code = 'distributed_systems' AND relation_type = 'BRIDGE')
   OR (source_node_code = 'shell_tooling_automation' AND target_node_code = 'ci_cd_pipeline' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'digital_logic' AND target_node_code = 'microcomputer_principles' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'iot_system_design' AND target_node_code = 'computer_networks' AND relation_type = 'CO_LEARN')
   OR (source_node_code = 'probability_inference' AND target_node_code = 'machine_learning' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'tree_linear_models' AND target_node_code = 'recommendation_systems' AND relation_type = 'BRIDGE')
   OR (source_node_code = 'neural_network_training' AND target_node_code = 'llm_applications' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'logic_and_proofs' AND target_node_code = 'compiler_principles' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'technology_society_governance' AND target_node_code = 'ai_engineering_governance' AND relation_type = 'BRIDGE')
   OR (source_node_code = 'habit_energy_management' AND target_node_code = 'burnout_prevention' AND relation_type = 'CO_LEARN')
   OR (source_node_code = 'networking_personal_brand' AND target_node_code = 'project_storytelling' AND relation_type = 'CO_LEARN')
   OR (source_node_code = 'backend_service_development' AND target_node_code = 'service_layer_design' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'cloud_native_basics' AND target_node_code = 'container_kubernetes' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'context_window_orchestration' AND target_node_code = 'tool_schema_design' AND relation_type = 'CO_LEARN')
   OR (source_node_code = 'ai_codebase_grounding' AND target_node_code = 'ai_coding_agent_tools' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'job_market_research' AND target_node_code = 'networking_personal_brand' AND relation_type = 'BRIDGE')
   OR (source_node_code = 'task_execution_followup' AND target_node_code = 'workplace_professionalism' AND relation_type = 'BRIDGE')
   OR (source_node_code = 'emotion_regulation' AND target_node_code = 'burnout_prevention' AND relation_type = 'CO_LEARN')
   OR (source_node_code = 'network_protocols' AND target_node_code = 'web_security' AND relation_type = 'CO_LEARN')
   OR (source_node_code = 'applied_cryptography' AND target_node_code = 'secure_engineering' AND relation_type = 'CO_LEARN')
   OR (source_node_code = 'microcomputer_principles' AND target_node_code = 'embedded_systems' AND relation_type = 'ADVANCE_TO');

UPDATE skills
SET parent_code = 'requirement_analysis',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'software_architecture_design';

UPDATE skills
SET parent_code = 'network_security',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'distributed_systems';

UPDATE skills
SET parent_code = 'ai_coding_agent_tools',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'multi_agent_workflows';

UPDATE skills
SET parent_code = 'ai_code_review_guardrails',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'ai_eval_observability';

UPDATE skills
SET parent_code = 'ai_eval_observability',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'model_routing_cost_control';

UPDATE skills
SET parent_code = 'open_source_culture',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'engineering_ethics';

UPDATE skills
SET parent_code = 'career_employment_readiness',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code IN ('internship_workplace_adaptation', 'wellbeing_self_management');

UPDATE skills
SET parent_code = 'backend_service_development',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'service_layer_design';

UPDATE skills
SET parent_code = 'microcomputer_principles',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'embedded_systems';

UPDATE skills
SET parent_code = 'cloud_native_basics',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'container_kubernetes';

UPDATE skills
SET parent_code = 'job_market_research',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'networking_personal_brand';

UPDATE skills
SET parent_code = 'emotion_regulation',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'burnout_prevention';

UPDATE skills
SET parent_code = 'task_execution_followup',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'workplace_professionalism';

UPDATE skills
SET parent_code = 'context_window_orchestration',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'tool_schema_design';

UPDATE skills
SET parent_code = 'ai_codebase_grounding',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'ai_coding_agent_tools';

UPDATE skills
SET parent_code = 'network_protocols',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'web_security';

UPDATE skills
SET parent_code = 'applied_cryptography',
    updated_at = CURRENT_TIMESTAMP
WHERE node_code = 'secure_engineering';
