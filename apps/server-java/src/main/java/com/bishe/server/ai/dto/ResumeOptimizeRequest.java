package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 简历优化请求。
 */
@Schema(description = "简历优化请求")
public record ResumeOptimizeRequest(
        @Schema(description = "目标岗位", example = "Backend Engineer")
        @NotBlank(message = "targetRole must not be blank")
        @Size(max = 100, message = "targetRole too long")
        String targetRole,
        @Schema(description = "目标语境", example = "校招正式批")
        @Size(max = 80, message = "targetContext too long")
        String targetContext,
        @Schema(description = "岗位 JD / 岗位要求", example = "负责核心后端服务研发，要求熟悉 Java、MySQL、Redis 与工程化实践。")
        @Size(max = 4000, message = "jobDescription too long")
        String jobDescription,
        @Schema(description = "简历文本", example = "负责用户增长平台后端接口开发，支撑日活 10 万。")
        @NotBlank(message = "resumeText must not be blank")
        @Size(max = 6000, message = "resumeText too long")
        String resumeText
) {
}
