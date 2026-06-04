package com.bishe.server.governance;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 后台审计日志列表响应。
 */
@Schema(description = "后台审计日志列表")
public record AdminAuditLogListResponse(
        @ArraySchema(schema = @Schema(implementation = AuditLogItem.class))
        List<AuditLogItem> records,
        @Schema(description = "总记录数", example = "12")
        long total,
        @Schema(description = "当前页码", example = "1")
        int page,
        @Schema(description = "分页大小", example = "10")
        int size
) {

    /**
     * 审计日志项。
     */
    @Schema(description = "审计日志项")
    public record AuditLogItem(
            @Schema(description = "审计日志 ID", example = "9001")
            long id,
            @Schema(description = "链路追踪 ID", example = "trc_20260308_000001")
            String traceId,
            @Schema(description = "操作人用户 ID；系统自动动作可能为 0", example = "1")
            long operatorUserId,
            @Schema(description = "操作人显示名", example = "系统管理员")
            String operatorDisplayName,
            @Schema(description = "动作类型", example = "REPORT_DECISION")
            String actionType,
            @Schema(description = "目标类型", example = "POST")
            String targetType,
            @Schema(description = "目标 ID", example = "9001")
            String targetId,
            @Schema(description = "审计明细 JSON", example = "{\"decision\":\"ACCEPTED\"}")
            String detailJson,
            @Schema(description = "创建时间（ISO-8601 UTC）", example = "2026-03-08T15:00:00Z")
            Long createdAt
    ) {
    }
}
