package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文本转语音响应。
 */
@Schema(description = "文本转语音响应")
public record TextToSpeechResponse(
        @Schema(description = "本次合成的原始文本", example = "你好，这是一个使用 Gemini 原生 TTS 生成的音频示例。")
        String text,
        @Schema(description = "本次实际使用的风格提示", example = "Read aloud in a warm and friendly tone:")
        String stylePrompt,
        @Schema(description = "本次实际使用的音色名称", example = "Zephyr")
        String voiceName,
        @Schema(description = "Gemini 返回的原始音频 MIME 类型", example = "audio/L16;codec=pcm;rate=24000")
        String mimeType,
        @Schema(description = "采样率", example = "24000")
        int sampleRate,
        @Schema(description = "base64 编码的 PCM 音频数据")
        String audioBase64,
        AiMetaPayload aiMeta
) {
}
