package com.bishe.server.ai.gateway;

/**
 * 统一 provider 文本消息结构。
 */
public record AiChatMessage(String role, String content) {
}
