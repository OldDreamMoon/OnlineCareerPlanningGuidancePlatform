package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 简历优化结果。
 */
@Schema(description = "简历优化结果")
public record ResumeOptimizeResponse(
        @Schema(description = "整体总结", example = "简历方向清晰，但需要补充量化成果。")
        String summary,
        @ArraySchema(schema = @Schema(example = "项目经历较完整"))
        List<String> strengths,
        @ArraySchema(schema = @Schema(example = "缺少量化指标"))
        List<String> risks,
        @ArraySchema(schema = @Schema(example = "补充 2-3 条量化成果"))
        List<String> suggestions,
        @Schema(description = "推荐度等级", example = "B+")
        String scoreLabel,
        @ArraySchema(schema = @Schema(implementation = ResumeStructureItem.class))
        List<ResumeStructureItem> structureItems,
        @ArraySchema(schema = @Schema(implementation = ResumeRewriteItem.class))
        List<ResumeRewriteItem> rewriteItems,
        AiMetaPayload aiMeta,
        AiModerationPayload moderation,
        @Schema(description = "本次简历优化对应的历史记录 ID，可直接用于导出 PDF", example = "123")
        Long recordId
) {
    public ResumeOptimizeResponse(String summary,
                                  List<String> strengths,
                                  List<String> risks,
                                  List<String> suggestions,
                                  String scoreLabel,
                                  List<ResumeStructureItem> structureItems,
                                  List<ResumeRewriteItem> rewriteItems,
                                  AiMetaPayload aiMeta,
                                  AiModerationPayload moderation) {
        this(summary, strengths, risks, suggestions, scoreLabel, structureItems, rewriteItems, aiMeta, moderation, null);
    }

    public ResumeOptimizeResponse withRecordId(long nextRecordId) {
        return new ResumeOptimizeResponse(
                summary,
                strengths,
                risks,
                suggestions,
                scoreLabel,
                structureItems,
                rewriteItems,
                aiMeta,
                moderation,
                nextRecordId
        );
    }
}
