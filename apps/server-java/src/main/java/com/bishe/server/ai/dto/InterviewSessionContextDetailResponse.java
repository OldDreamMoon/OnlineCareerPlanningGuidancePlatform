package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 面试会话中固化的准备配置详情。
 */
@Schema(description = "面试会话准备配置详情")
public record InterviewSessionContextDetailResponse(
        @Schema(description = "面试类型", example = "PROJECT_DEEP_DIVE")
        String interviewType,
        @Schema(description = "面试官风格", example = "COACHING")
        String interviewerStyle,
        @Schema(description = "练习强度", example = "HARD")
        String difficulty,
        @Schema(description = "作答方式", example = "VOICE")
        String answerMode,
        @Schema(description = "目标企业", example = "字节跳动")
        String targetCompany,
        @Schema(description = "岗位 JD 摘要")
        String targetJobDescription,
        @Schema(description = "已带入资料 key 列表")
        List<String> prepMaterialKeys,
        @Schema(description = "是否开启回答辅助器")
        Boolean answerHelperEnabled,
        @Schema(description = "回答辅助器提醒项编码列表")
        List<String> answerHelperCueKeys,
        @Schema(description = "本轮授权带入的个人资料摘要")
        String promptContext
) {
}
