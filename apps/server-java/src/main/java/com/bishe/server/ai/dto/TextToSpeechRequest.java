package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 文本转语音请求。
 */
@Schema(description = "文本转语音请求")
public record TextToSpeechRequest(
        @Schema(description = "要合成的文本内容", example = "你好，这是一个使用 Gemini 原生 TTS 生成的音频示例。")
        @NotBlank(message = "text required")
        @Size(max = 2000, message = "text too long")
        String text,
        @Schema(description = "自然语言风格提示，用于控制语气、语速、口音等", example = "Read aloud in a warm and friendly tone:")
        @Size(max = 300, message = "stylePrompt too long")
        String stylePrompt,
        @Schema(description = "预置音色名称", example = "Zephyr")
        @Size(max = 80, message = "voiceName too long")
        String voiceName,
        @Schema(description = "可选，会话级缓存作用域；传入后同一会话内相同文本/音色/风格会优先复用临时缓存", example = "is_10001")
        @Size(max = 64, message = "sessionId too long")
        String sessionId
) {
}
