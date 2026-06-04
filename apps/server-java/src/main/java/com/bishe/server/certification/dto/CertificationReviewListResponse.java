package com.bishe.server.certification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 管理员认证审核列表响应。
 */
@Schema(description = "管理员认证审核列表响应")
public record CertificationReviewListResponse(
        List<ReviewItem> records,
        @Schema(description = "总条数", example = "12")
        Long total,
        @Schema(description = "页码", example = "1")
        Integer page,
        @Schema(description = "分页大小", example = "10")
        Integer size
) {

    @Schema(description = "认证审核列表项")
    public record ReviewItem(
            @Schema(description = "用户 ID", example = "2001")
            Long userId,
            @Schema(description = "邮箱", example = "mentor@bishe.local")
            String email,
            @Schema(description = "昵称", example = "导师李然")
            String displayName,
            @Schema(description = "角色", example = "MENTOR")
            String role,
            @Schema(description = "当前资料审核状态", example = "PENDING")
            String approvalStatus,
            @Schema(description = "当前提交 ID", example = "7001")
            Long submissionId,
            @Schema(description = "提交状态", example = "PENDING")
            String submissionStatus,
            @Schema(description = "真实姓名", example = "李老师")
            String realName,
            @Schema(description = "公司 / 机构", example = "字节跳动")
            String companyName,
            @Schema(description = "岗位 / Title", example = "高级前端工程师")
            String jobTitle,
            @Schema(description = "当前有效材料数", example = "1")
            Integer activeAssetCount,
            @Schema(description = "主材料名称", example = "mentor-proof.pdf")
            String primaryAssetName,
            @Schema(description = "提交时间（ISO-8601 UTC）", example = "2026-03-17T12:00:00Z")
            Long submittedAt,
            @Schema(description = "审核时间（ISO-8601 UTC）", example = "2026-03-18T08:30:00Z")
            Long reviewedAt
    ) {
    }
}
