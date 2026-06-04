ALTER TABLE ai_async_task_jobs
    ALTER COLUMN input_snapshot_json TYPE JSONB
        USING CASE
                  WHEN input_snapshot_json IS NULL OR btrim(input_snapshot_json) = '' THEN NULL
                  ELSE input_snapshot_json::jsonb
            END,
    ALTER COLUMN context_json TYPE JSONB
        USING CASE
                  WHEN context_json IS NULL OR btrim(context_json) = '' THEN NULL
                  ELSE context_json::jsonb
            END,
    ALTER COLUMN prompt_snapshot_json TYPE JSONB
        USING CASE
                  WHEN prompt_snapshot_json IS NULL OR btrim(prompt_snapshot_json) = '' THEN NULL
                  ELSE prompt_snapshot_json::jsonb
            END,
    ALTER COLUMN route_snapshot_json TYPE JSONB
        USING CASE
                  WHEN route_snapshot_json IS NULL OR btrim(route_snapshot_json) = '' THEN NULL
                  ELSE route_snapshot_json::jsonb
            END,
    ALTER COLUMN result_payload_json TYPE JSONB
        USING CASE
                  WHEN result_payload_json IS NULL OR btrim(result_payload_json) = '' THEN NULL
                  ELSE result_payload_json::jsonb
            END;

ALTER TABLE ai_async_task_events
    ALTER COLUMN payload_json TYPE JSONB
        USING CASE
                  WHEN payload_json IS NULL OR btrim(payload_json) = '' THEN NULL
                  ELSE payload_json::jsonb
            END;
