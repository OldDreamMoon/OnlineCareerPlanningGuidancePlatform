package com.bishe.server.skill.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 技能树查询响应。
 */
@Schema(description = "技能树查询响应")
public record SkillTreeResponse(
        @ArraySchema(schema = @Schema(implementation = SkillNodeItem.class))
        List<SkillNodeItem> nodes,
        @ArraySchema(schema = @Schema(implementation = SkillRelationItem.class))
        List<SkillRelationItem> relations,
        ProgressSummary summary
) {
    @Schema(description = "技能节点")
    public record SkillNodeItem(
            @Schema(description = "节点编码", example = "java_programming")
            String nodeCode,
            @Schema(description = "节点名称", example = "Java 与工程语言实践")
            String label,
            @Schema(description = "节点说明", example = "掌握面向对象语法、标准库和工程开发里最常见的 Java 基础能力。")
            String description,
            @Schema(description = "父节点编码", example = "programming_language_foundations")
            String parentCode,
            @Schema(description = "排序值", example = "10")
            int sortOrder,
            @Schema(description = "当前状态", example = "LEARNING")
            String status,
            @Schema(description = "是否已解锁", example = "true")
            boolean unlocked,
            @Schema(description = "最近更新时间（UTC epoch 毫秒）", example = "1772445600000")
            Long updatedAt,
            @ArraySchema(schema = @Schema(implementation = SkillResourceItem.class))
            List<SkillResourceItem> resources
    ) {
    }

    @Schema(description = "技能节点推荐资源")
    public record SkillResourceItem(
            @Schema(description = "资源编码", example = "spring-guides")
            String id,
            @Schema(description = "资源类型", example = "doc")
            String type,
            @Schema(description = "资源标题", example = "Spring 官方 Guides")
            String title,
            @Schema(description = "来源名称", example = "Spring")
            String source,
            @Schema(description = "时间/耗时标签", example = "长期参考")
            String time,
            @Schema(description = "跳转链接", example = "https://spring.io/guides")
            String link,
            @Schema(description = "排序值", example = "10")
            int sortOrder
    ) {
    }

    @Schema(description = "技能联动关系")
    public record SkillRelationItem(
            @Schema(description = "起点节点编码", example = "api_contract_design")
            String sourceNodeCode,
            @Schema(description = "终点节点编码", example = "network_protocols")
            String targetNodeCode,
            @Schema(description = "关系类型", example = "CO_LEARN")
            String relationType,
            @Schema(description = "关系说明", example = "接口设计与协议语义适合并行补强")
            String label,
            @Schema(description = "排序值", example = "10")
            int sortOrder
    ) {
    }

    @Schema(description = "技能树进度汇总")
    public record ProgressSummary(
            @Schema(description = "总节点数", example = "105")
            int total,
            @Schema(description = "已掌握节点数", example = "2")
            int mastered,
            @Schema(description = "学习中节点数", example = "1")
            int learning,
            @Schema(description = "未开始节点数", example = "2")
            int notStarted
    ) {
    }
}
