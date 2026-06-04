package com.bishe.server.ai.gateway.task;

import com.bishe.server.ai.dto.ResumeOptimizeRequest;
import com.bishe.server.ai.dto.ResumeOptimizeResponse;
import com.bishe.server.ai.service.AiPracticeService;
import com.bishe.server.ai.service.AiResumeAsyncTaskService;
import com.bishe.server.common.TraceId;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.common.web.InMemoryMultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/**
 * 简历优化异步任务处理器：复用现有简历业务链路完成真正执行。
 */
@Component
public class ResumeOptimizeAsyncTaskProcessor implements AiAsyncTaskProcessor {

    private final ObjectMapper objectMapper;
    private final AiPracticeService aiPracticeService;

    public ResumeOptimizeAsyncTaskProcessor(ObjectMapper objectMapper, AiPracticeService aiPracticeService) {
        this.objectMapper = objectMapper;
        this.aiPracticeService = aiPracticeService;
    }

    @Override
    public boolean supports(AiAsyncTaskService.TaskSnapshot task) {
        return task != null
                && AiResumeAsyncTaskService.RESUME_TASK_TYPE.equalsIgnoreCase(task.taskType())
                && AiResumeAsyncTaskService.RESUME_SCENE_CODE.equalsIgnoreCase(task.sceneCode());
    }

    @Override
    public ProcessResult process(AiAsyncTaskService.TaskSnapshot task) {
        try {
            AiResumeAsyncTaskService.ResumeAsyncTaskInput input = objectMapper.readValue(
                    task.inputSnapshotJson(),
                    AiResumeAsyncTaskService.ResumeAsyncTaskInput.class
            );
            ResumeOptimizeResponse response = executeResumeTask(task.userId(), input);
            return ProcessResult.success(response.summary(), objectMapper.writeValueAsString(response));
        } catch (ApiException ex) {
            if (ex.getHttpStatus() != null && ex.getHttpStatus().is5xxServerError()) {
                return ProcessResult.retry(ex.getCode(), ex.getMessage(), Duration.ofSeconds(15));
            }
            return ProcessResult.failure(ex.getCode(), ex.getMessage());
        } catch (Exception ex) {
            return ProcessResult.retry("AI-2001", summarizeException(ex), Duration.ofSeconds(15));
        }
    }

    private ResumeOptimizeResponse executeResumeTask(long userId, AiResumeAsyncTaskService.ResumeAsyncTaskInput input) {
        String traceId = TraceId.next();
        if (input == null || input.inputMode() == null || input.inputMode().isBlank()) {
            throw new ApiException("BIZ-1001", "async resume task payload invalid", HttpStatus.BAD_REQUEST);
        }
        if (AiResumeAsyncTaskService.INPUT_MODE_PDF.equalsIgnoreCase(input.inputMode())) {
            MultipartFile resumeFile = new InMemoryMultipartFile(
                    "resumeFile",
                    input.resumeFileName(),
                    input.resumeContentType(),
                    input.resumeFileBytes()
            );
            return aiPracticeService.optimizeResumePdf(
                    userId,
                    traceId,
                    input.targetRole(),
                    input.targetContext(),
                    input.jobDescription(),
                    resumeFile
            );
        }
        return aiPracticeService.optimizeResume(
                userId,
                traceId,
                new ResumeOptimizeRequest(
                        input.targetRole(),
                        input.targetContext(),
                        input.jobDescription(),
                        input.resumeText()
                )
        );
    }

    private String summarizeException(Exception ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return "resume async task execution failed";
        }
        String message = ex.getMessage().replaceAll("\\s+", " ").trim();
        return message.length() <= 300 ? message : message.substring(0, 300) + "...";
    }
}
