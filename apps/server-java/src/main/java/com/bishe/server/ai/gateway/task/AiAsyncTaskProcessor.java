package com.bishe.server.ai.gateway.task;

import java.time.Duration;

/**
 * 异步 AI 任务处理器：不同任务类型在这里注册自己的执行逻辑。
 */
public interface AiAsyncTaskProcessor {

    boolean supports(AiAsyncTaskService.TaskSnapshot task);

    ProcessResult process(AiAsyncTaskService.TaskSnapshot task);

    record ProcessResult(
            boolean succeeded,
            String resultSummary,
            String resultPayloadJson,
            String errorCode,
            String errorMessage,
            boolean retryable,
            Duration retryDelay
    ) {
        public static ProcessResult success(String resultSummary, String resultPayloadJson) {
            return new ProcessResult(true, resultSummary, resultPayloadJson, null, null, false, null);
        }

        public static ProcessResult failure(String errorCode, String errorMessage) {
            return new ProcessResult(false, null, null, errorCode, errorMessage, false, null);
        }

        public static ProcessResult retry(String errorCode, String errorMessage, Duration retryDelay) {
            return new ProcessResult(false, null, null, errorCode, errorMessage, true, retryDelay == null ? Duration.ofSeconds(10) : retryDelay);
        }
    }
}
