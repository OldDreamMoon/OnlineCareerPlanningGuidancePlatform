package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 简历改写沙盘项。
 */
@Schema(description = "简历改写沙盘项")
public record ResumeRewriteItem(
        @Schema(description = "改写项标识", example = "rewrite-1")
        String id,
        @Schema(description = "改写标题", example = "补强量化结果")
        String title,
        @Schema(description = "问题概述", example = "当前表述缺少业务结果与指标变化。")
        String problem,
        @Schema(description = "原写法", example = "负责订单中心后端接口开发与维护。")
        String beforeText,
        @Schema(description = "推荐写法", example = "负责订单中心核心接口与缓存链路重构，通过索引优化与预热机制将接口延迟降低 30%。")
        String afterText,
        @Schema(description = "是否为后端 fallback 生成结果", example = "false")
        boolean isHeuristic
) {
}
