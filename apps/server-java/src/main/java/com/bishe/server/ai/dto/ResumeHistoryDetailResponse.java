package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 简历优化历史详情。
 */
@Schema(description = "简历优化历史详情")
public record ResumeHistoryDetailResponse(
        @Schema(description = "历史记录ID", example = "1")
        long recordId,
        @Schema(description = "摘要", example = "简历已具备后端岗位基础，建议进一步补齐量化成果。")
        String summary,
        @Schema(description = "优势")
        List<String> strengths,
        @Schema(description = "风险")
        List<String> risks,
        @Schema(description = "建议")
        List<String> suggestions,
        @Schema(description = "推荐度等级", example = "B+")
        String scoreLabel,
        @Schema(description = "结构完整度地图")
        List<ResumeStructureItem> structureItems,
        @Schema(description = "改写沙盘项")
        List<ResumeRewriteItem> rewriteItems,
        @Schema(description = "目标岗位", example = "Backend Engineer")
        String targetRole,
        @Schema(description = "目标语境", example = "校招正式批")
        String targetContext,
        @Schema(description = "输入模式", example = "text")
        String inputMode,
        @Schema(description = "岗位 JD / 要求")
        String jobDescription,
        @Schema(description = "本次提交的简历文本；文本模式直接返回原文，PDF 模式在成功提取正文时也会返回文本")
        String resumeText,
        @Schema(description = "本次提交的 PDF 文件名，仅 PDF 模式下返回", example = "resume.pdf")
        String pdfFileName,
        @Schema(description = "消耗积分", example = "0")
        int pointsConsumed,
        @Schema(description = "创建时间（UTC epoch 毫秒）", example = "1772787600000")
        Long createdAt,
        AiMetaPayload aiMeta,
        AiModerationPayload moderation
) {
}
