package com.bishe.server.profile.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 学生个人资料与动态画像响应。
 */
@Schema(description = "学生画像查询响应")
public record StudentProfileResponse(
        @Schema(description = "用户 ID", example = "1001")
        Long userId,
        @Schema(description = "显示名称", example = "Alice")
        String displayName,
        @Schema(description = "登录邮箱", example = "alice@example.com")
        String email,
        @Schema(description = "账号层级", example = "FREE")
        String tier,
        @Schema(description = "资料完善度百分比", example = "92")
        int completionRate,
        @Schema(description = "真实姓名", example = "王小明")
        String realName,
        @Schema(description = "当前求职状态", example = "🟢 积极找工作")
        String jobStatus,
        @Schema(description = "学校名称", example = "华东理工大学")
        String schoolName,
        @Schema(description = "专业", example = "计算机科学与技术")
        String major,
        @Schema(description = "年级", example = "大三")
        String grade,
        @Schema(description = "GPA", example = "3.8 / 4.0")
        String gpa,
        @Schema(description = "目标岗位", example = "Java 后端开发")
        String targetPosition,
        @Schema(description = "荣誉奖项", example = "2023年 国家励志奖学金")
        String honors,
        @Schema(description = "GitHub 链接", example = "github.com/alice")
        String github,
        @Schema(description = "作品集链接", example = "alice.dev")
        String portfolio,
        @ArraySchema(schema = @Schema(implementation = StudentProfileSocialLinkItem.class, description = "受控外部主页账号列表"))
        List<StudentProfileSocialLinkItem> socialLinks,
        @Schema(description = "手机号", example = "13800138000")
        String phone,
        @Schema(description = "微信号", example = "alice_job")
        String wechat,
        @ArraySchema(schema = @Schema(description = "技能标签", example = "Java"))
        List<String> skillTags,
        @Schema(description = "自我介绍", example = "希望寻找后端实习，正在强化项目能力。")
        String selfIntro,
        AvatarPayload avatar,
        PrivacySettingsPayload privacy,
        PortraitPayload portrait,
        @Schema(description = "近 7 天社区贡献分", example = "25")
        long communityScore7d
) {

    /**
     * 学生头像元数据。
     */
    @Schema(description = "学生头像元数据")
    public record AvatarPayload(
            @Schema(description = "当前是否已上传头像", example = "true")
            boolean uploaded,
            @Schema(description = "头像内容类型", example = "image/jpeg")
            String contentType,
            @Schema(description = "头像最近更新时间（UTC epoch 毫秒）", example = "1772445600000")
            Long updatedAt
    ) {
    }

    /**
     * 隐私矩阵容器。
     */
    @Schema(description = "隐私可见性矩阵")
    public record PrivacySettingsPayload(
            VisibilityItem realName,
            VisibilityItem jobStatus,
            VisibilityItem eduInfo,
            VisibilityItem targetPos,
            VisibilityItem academic,
            VisibilityItem skills,
            VisibilityItem intro,
            VisibilityItem social,
            VisibilityItem email,
            VisibilityItem phone,
            VisibilityItem wechat,
            VisibilityItem portrait
    ) {
    }

    /**
     * 单个信息项的可见性。
     */
    @Schema(description = "单个信息项的可见范围")
    public record VisibilityItem(
            @Schema(description = "游客可见", example = "false")
            boolean guest,
            @Schema(description = "同校学生可见", example = "true")
            boolean student,
            @Schema(description = "平台学生可见", example = "true")
            boolean platformStudent,
            @Schema(description = "导师可见", example = "true")
            boolean mentor,
            @Schema(description = "企业可见", example = "true")
            boolean enterprise
    ) {
    }

    /**
     * 动态画像容器。
     */
    @Schema(description = "动态画像信息")
    public record PortraitPayload(
            List<PortraitTagItem> tags,
            @ArraySchema(schema = @Schema(description = "当前优势标签", example = "技能成长清晰"))
            List<String> strengthTags,
            @ArraySchema(schema = @Schema(description = "当前风险标签", example = "面试表达结构待补强"))
            List<String> riskTags,
            @Schema(description = "画像信号强度", example = "NORMAL")
            String signalLevel,
            @Schema(description = "画像新鲜度", example = "FRESH")
            String freshnessLevel,
            @Schema(description = "当前画像标题", example = "你在后端方向已经形成初步优势，当前最需要补的是面试表达结构。")
            String headline,
            @Schema(description = "当前画像概述", example = "你已经具备较清晰的技能成长与目标岗位方向，但最近的面试与简历信号显示，项目表达和回答结构仍有补强空间。")
            String summary,
            @ArraySchema(schema = @Schema(description = "下一步建议", example = "围绕一个核心项目练习 2 分钟的背景-动作-结果表达。"))
            List<String> nextActions,
            @Schema(description = "画像总结版本", example = "TEMPLATE_V1")
            String summaryVersion,
            PortraitEvidencePayload evidence,
            @Schema(description = "画像最近更新时间（UTC epoch 毫秒）", example = "1772445600000")
            Long updatedAt
    ) {
    }

    /**
     * 单个画像标签。
     */
    @Schema(description = "画像标签")
    public record PortraitTagItem(
            @Schema(description = "标签编码", example = "COMMUNITY_ACTIVE")
            String code,
            @Schema(description = "标签名称", example = "社区互动积极")
            String label,
            @Schema(description = "标签来源", example = "COMMUNITY")
            String source,
            @Schema(description = "置信度", example = "0.78")
            Double confidence
    ) {
    }

    /**
     * 画像证据计数。
     */
    @Schema(description = "画像证据统计")
    public record PortraitEvidencePayload(
            @Schema(description = "已掌握技能数", example = "3")
            int masteredSkills,
            @Schema(description = "学习中技能数", example = "2")
            int learningSkills,
            @Schema(description = "近 7 天面试消息数", example = "6")
            int interviewMessages7d,
            @Schema(description = "近 7 天发帖数", example = "2")
            int posts7d,
            @Schema(description = "近 7 天评论数", example = "5")
            int comments7d,
            @Schema(description = "近 7 天获赞数", example = "11")
            int likesReceived7d
    ) {
    }
}
