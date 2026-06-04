package com.bishe.server.growth.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 积分账本分页结果。
 */
@Schema(description = "积分账本分页结果")
public record PointsLedgerResponse(
        @Schema(description = "当前积分余额", example = "130")
        int balance,
        @ArraySchema(schema = @Schema(implementation = LedgerRecordItem.class))
        List<LedgerRecordItem> records,
        @Schema(description = "总记录数", example = "25")
        long total
) {
    @Schema(description = "积分账本记录")
    public record LedgerRecordItem(
            @Schema(description = "积分变化值", example = "10")
            int deltaPoints,
            @Schema(description = "变更原因编码", example = "CHECKIN")
            String reasonCode,
            @Schema(description = "变更后余额", example = "130")
            int balanceAfter,
            @Schema(description = "创建时间（UTC epoch 毫秒）", example = "1772445600000")
            Long createdAt
    ) {
    }
}
