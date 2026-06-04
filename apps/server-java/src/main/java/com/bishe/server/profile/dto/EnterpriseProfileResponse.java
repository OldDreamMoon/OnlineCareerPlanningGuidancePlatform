package com.bishe.server.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 企业本人资料响应。
 */
@Schema(description = "企业认证资料响应")
public record EnterpriseProfileResponse(
        @Schema(description = "用户 ID", example = "2001")
        long userId,
        @Schema(description = "账号显示名称", example = "字节校招")
        String displayName,
        @Schema(description = "真实姓名", example = "王小明")
        String realName,
        @Schema(description = "企业名称", example = "字节跳动")
        String companyName,
        @Schema(description = "当前岗位 / Title", example = "招聘负责人")
        String jobTitle,
        @Schema(description = "所在行业", example = "互联网 / 软件服务")
        String industry,
        @Schema(description = "企业规模", example = "200-500 人")
        String companySize,
        @Schema(description = "招聘方向标签")
        List<String> hiringTags,
        @Schema(description = "企业简介", example = "专注 AI 企业服务与校招人才培养的技术团队。")
        String bio,
        @Schema(description = "联系方式与外部链接", example = "官网：https://example.com\n邮箱：hr@example.com")
        String externalLinks,
        @Schema(description = "招募偏好与学生提示", example = "偏好具备项目经历的同学，提交时可附仓库链接。")
        String preferences,
        @Schema(description = "企业 Logo 公开地址", example = "/api/v1/profiles/enterprises/2001/logo?v=1772445600000")
        String logoUrl,
        @Schema(description = "是否已配置企业 Logo", example = "true")
        boolean logoConfigured,
        @Schema(description = "企业 Logo 内容类型", example = "image/jpeg")
        String logoContentType,
        @Schema(description = "企业 Logo 最近更新时间（UTC epoch 毫秒）", example = "1772445600000")
        Long logoUpdatedAt,
        @Schema(description = "审核状态", example = "APPROVED")
        String approvalStatus
) {
}
