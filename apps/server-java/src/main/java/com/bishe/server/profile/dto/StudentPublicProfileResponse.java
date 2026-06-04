package com.bishe.server.profile.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 学生个人空间资料响应。
 */
@Schema(description = "学生个人空间资料响应")
public record StudentPublicProfileResponse(
        @Schema(description = "用户 ID", example = "1001")
        Long userId,
        @Schema(description = "显示名称", example = "Alice")
        String displayName,
        @Schema(description = "账号层级", example = "PREMIUM")
        String tier,
        @Schema(description = "公开真实姓名（按访问者身份裁切）", example = "王小明")
        String realName,
        @Schema(description = "公开求职状态（按访问者身份裁切）", example = "🟢 积极找工作")
        String jobStatus,
        @Schema(description = "公开学校名称（按访问者身份裁切）", example = "华东理工大学")
        String schoolName,
        @Schema(description = "公开专业（按访问者身份裁切）", example = "计算机科学与技术")
        String major,
        @Schema(description = "公开年级（按访问者身份裁切）", example = "2025届")
        String grade,
        @Schema(description = "公开 GPA（按访问者身份裁切）", example = "3.8 / 4.0")
        String gpa,
        @Schema(description = "公开目标岗位（按访问者身份裁切）", example = "Java 后端开发")
        String targetPosition,
        @Schema(description = "公开荣誉与成就（按访问者身份裁切）", example = "国家励志奖学金")
        String honors,
        @ArraySchema(schema = @Schema(description = "公开技能标签"))
        List<String> skillTags,
        @Schema(description = "公开自我介绍（按访问者身份裁切）", example = "持续准备后端求职，关注真实业务项目。")
        String selfIntro,
        StudentProfileResponse.AvatarPayload avatar,
        @ArraySchema(schema = @Schema(implementation = StudentProfileSocialLinkItem.class, description = "公开社交入口"))
        List<StudentProfileSocialLinkItem> socialLinks,
        StudentProfileResponse.PrivacySettingsPayload privacy,
        StudentProfileResponse.PortraitPayload portrait,
        @Schema(description = "近 7 天社区贡献分", example = "25")
        long communityScore7d
) {
}
