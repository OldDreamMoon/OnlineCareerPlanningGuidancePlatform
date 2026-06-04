package com.bishe.server.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 更新企业本人资料请求。
 */
@Schema(description = "更新我的企业认证资料请求")
public record EnterpriseProfileUpdateRequest(
        @Schema(description = "真实姓名", example = "王小明")
        @Size(max = 100, message = "realName too long")
        String realName,
        @Schema(description = "企业名称", example = "字节跳动")
        @Size(max = 200, message = "companyName too long")
        String companyName,
        @Schema(description = "当前岗位 / Title", example = "招聘负责人")
        @Size(max = 100, message = "jobTitle too long")
        String jobTitle,
        @Schema(description = "所在行业", example = "互联网 / 软件服务")
        @Size(max = 100, message = "industry too long")
        String industry,
        @Schema(description = "企业规模", example = "200-500 人")
        @Size(max = 50, message = "companySize too long")
        String companySize,
        @Schema(description = "招聘方向标签")
        List<String> hiringTags,
        @Schema(description = "企业简介", example = "专注 AI 企业服务与校招人才培养的技术团队。")
        @Size(max = 4000, message = "bio too long")
        String bio,
        @Schema(description = "联系方式与外部链接", example = "官网：https://example.com\n邮箱：hr@example.com")
        @Size(max = 4000, message = "externalLinks too long")
        String externalLinks,
        @Schema(description = "招募偏好与学生提示", example = "偏好具备项目经历的同学，提交时可附仓库链接。")
        @Size(max = 4000, message = "preferences too long")
        String preferences
) {
}
