package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建文本面试会话请求。
 */
@Schema(description = "创建文本面试会话请求")
public record InterviewSessionCreateRequest(
        @Schema(description = "目标岗位", example = "Backend Engineer")
        @NotBlank(message = "targetRole must not be blank")
        @Size(max = 100, message = "targetRole too long")
        String targetRole,
        @Schema(description = "会话模式，仅当前支持 INTERVIEW_TEXT", example = "INTERVIEW_TEXT")
        @Size(max = 30, message = "mode too long")
        String mode,
        @Schema(description = "可选：本轮带入的简历历史记录ID", example = "12")
        @Positive(message = "resumeRecordId invalid")
        Long resumeRecordId,
        @Schema(description = "可选：本轮面试配置快照")
        @Valid
        InterviewSessionContextRequest sessionContext
) {

        @Schema(description = "本轮面试配置快照")
        public record InterviewSessionContextRequest(
                @Schema(description = "面试类型编码", example = "PROJECT_DEEP_DIVE")
                @Size(max = 40, message = "interviewType too long")
                String interviewType,
                @Schema(description = "面试官风格编码", example = "STANDARD")
                @Size(max = 40, message = "interviewerStyle too long")
                String interviewerStyle,
                @Schema(description = "练习强度编码", example = "MEDIUM")
                @Size(max = 20, message = "difficulty too long")
                String difficulty,
                @Schema(description = "作答模式编码", example = "voice")
                @Size(max = 20, message = "answerMode too long")
                String answerMode,
                @Schema(description = "目标企业", example = "字节跳动")
                @Size(max = 120, message = "targetCompany too long")
                String targetCompany,
                @Schema(description = "目标岗位 JD", example = "负责核心前端页面开发，要求熟悉 React / TypeScript / 工程化。")
                @Size(max = 4000, message = "targetJobDescription too long")
                String targetJobDescription,
                @Schema(description = "当前带入的资料编码列表")
                @Size(max = 12, message = "prepMaterialKeys too many")
                List<String> prepMaterialKeys,
                @Schema(description = "是否开启回答辅助器")
                Boolean answerHelperEnabled,
                @Schema(description = "回答辅助器提醒项编码列表")
                @Size(max = 6, message = "answerHelperCueKeys too many")
                List<String> answerHelperCueKeys,
                @Schema(description = "可直接进入提示词系统的精简上下文快照")
                @Size(max = 6000, message = "promptContext too long")
                String promptContext
        ) {
        }
}
