package com.bishe.server.ai.gateway;

import com.bishe.server.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.function.Consumer;

/**
 * AI provider 执行接口。
 */
public interface AiProviderClient {

    AiProviderType providerType();

    AiProviderChatResult chatJson(AiProviderInvocation invocation, List<AiChatMessage> messages);

    default AiProviderChatResult chatJsonStream(
            AiProviderInvocation invocation,
            List<AiChatMessage> messages,
            Consumer<String> deltaConsumer
    ) {
        AiProviderChatResult result = chatJson(invocation, messages);
        if (deltaConsumer != null && result != null && result.content() != null && !result.content().isBlank()) {
            deltaConsumer.accept(result.content());
        }
        return result;
    }

    AiProviderSpeechResult transcribeAudio(AiProviderInvocation invocation, MultipartFile audioFile);

    default AiProviderChatResult optimizeResumePdf(
            AiProviderInvocation invocation,
            String targetRole,
            String targetContext,
            String jobDescription,
            MultipartFile resumeFile
    ) {
        throw new ApiException("AI-2001", "provider resume pdf unsupported", HttpStatus.BAD_GATEWAY);
    }

    default boolean supportsResumePdf() {
        return false;
    }

    AiProviderTextToSpeechResult synthesizeSpeech(
            AiProviderInvocation invocation,
            String text,
            String stylePrompt,
            String voiceName
    );
}
