package com.bishe.server.auth.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 管理员查看用户详情响应。
 */
@Schema(description = "管理员用户详情响应")
public record AdminUserDetailResponse(
        @Schema(description = "用户 ID", example = "1001")
        Long userId,
        @Schema(description = "邮箱", example = "alice@example.com")
        String email,
        @Schema(description = "显示名称", example = "Alice")
        String displayName,
        @Schema(description = "角色", example = "STUDENT")
        String role,
        @Schema(description = "账户套餐", example = "FREE")
        String tier,
        @Schema(description = "账号状态", example = "ACTIVE")
        String status,
        @Schema(description = "认证状态，仅导师/企业角色返回", example = "APPROVED")
        String approvalStatus,
        @Schema(description = "注册时间（ISO-8601 UTC）", example = "2026-03-05T08:00:00Z")
        Long createdAt,
        StudentProfileSummary studentProfile,
        @Schema(description = "近 7 天社区贡献分，仅学生角色返回", example = "25")
        Long communityScore7d
) {

    /**
     * 学生画像摘要。
     */
    @Schema(description = "学生画像摘要，仅学生角色返回")
    public record StudentProfileSummary(
            @Schema(description = "专业", example = "计算机科学与技术")
            String major,
            @Schema(description = "年级", example = "大三")
            String grade,
            @Schema(description = "目标岗位", example = "Java 后端开发")
            String targetPosition,
            @ArraySchema(schema = @Schema(description = "技能标签", example = "Java"))
            List<String> skillTags,
            @Schema(description = "自我介绍", example = "希望寻找后端实习，正在强化项目能力。")
            String selfIntro
    ) {
    }
}
