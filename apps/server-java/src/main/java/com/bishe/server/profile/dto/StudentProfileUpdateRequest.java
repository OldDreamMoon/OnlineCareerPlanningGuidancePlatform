package com.bishe.server.profile.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 更新学生个人资料请求。
 */
@Schema(description = "更新我的学生资料请求")
public record StudentProfileUpdateRequest(
        @Schema(description = "昵称", example = "前端小林")
        @Size(max = 100, message = "displayName too long")
        String displayName,
        @Schema(description = "真实姓名", example = "王小明")
        @Size(max = 100, message = "realName too long")
        String realName,
        @Schema(description = "求职状态", example = "🟢 积极找工作")
        @Size(max = 50, message = "jobStatus too long")
        String jobStatus,
        @Schema(description = "学校名称", example = "华东理工大学")
        @Size(max = 150, message = "schoolName too long")
        String schoolName,
        @Schema(description = "专业", example = "软件工程")
        @Size(max = 100, message = "major too long")
        String major,
        @Schema(description = "年级", example = "大二")
        @Size(max = 20, message = "grade too long")
        String grade,
        @Schema(description = "GPA", example = "3.8 / 4.0")
        @Size(max = 50, message = "gpa too long")
        String gpa,
        @Schema(description = "目标岗位", example = "后端开发")
        @Size(max = 100, message = "targetPosition too long")
        String targetPosition,
        @Schema(description = "荣誉奖项", example = "2023年 国家励志奖学金")
        @Size(max = 4000, message = "honors too long")
        String honors,
        @Schema(description = "GitHub 链接", example = "github.com/alice")
        @Size(max = 255, message = "github too long")
        String github,
        @Schema(description = "作品集链接", example = "alice.dev")
        @Size(max = 255, message = "portfolio too long")
        String portfolio,
        @ArraySchema(schema = @Schema(implementation = StudentProfileSocialLinkItem.class, description = "受控外部主页账号列表"))
        List<@Valid StudentProfileSocialLinkItem> socialLinks,
        @Schema(description = "手机号", example = "13800138000")
        @Size(max = 50, message = "phone too long")
        String phone,
        @Schema(description = "微信号", example = "alice_job")
        @Size(max = 100, message = "wechat too long")
        String wechat,
        @ArraySchema(schema = @Schema(description = "技能标签", example = "Java"))
        List<@Size(max = 50, message = "skillTag too long") String> skillTags,
        @Schema(description = "冷启动自我介绍", example = "希望通过真实项目训练提升工程能力。")
        @Size(max = 200, message = "selfIntro too long")
        String selfIntro
) {
}
