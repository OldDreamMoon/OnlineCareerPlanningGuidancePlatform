package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 面试会话中固化的简历上下文详情。
 */
@Schema(description = "面试会话简历上下文详情")
public record InterviewResumeContextDetailResponse(
        @Schema(description = "简历记录 ID", example = "1001")
        long recordId,
        @Schema(description = "简历摘要")
        String summary,
        @Schema(description = "简历优化建议")
        List<String> suggestions,
        @Schema(description = "简历评级", example = "A")
        String scoreLabel,
        @Schema(description = "简历目标岗位", example = "Backend Engineer")
        String targetRole,
        @Schema(description = "求职语境", example = "校招正式批")
        String targetContext,
        @Schema(description = "简历输入方式", example = "pdf")
        String inputMode,
        @Schema(description = "岗位 JD 摘要")
        String jobDescription,
        @Schema(description = "PDF 文件名", example = "resume.pdf")
        String pdfFileName,
        @Schema(description = "简历正文摘录")
        String resumeTextExcerpt,
        @Schema(description = "简历快照创建时间（UTC epoch 毫秒）", example = "1772787600000")
        Long createdAt
) {
}
